package com.sw103302.backend.service;

import com.sw103302.backend.dto.AuthResponse;
import com.sw103302.backend.dto.EmailAvailabilityResponse;
import com.sw103302.backend.dto.LoginRequest;
import com.sw103302.backend.dto.RegisterRequest;
import com.sw103302.backend.dto.VerifyTwoFactorLoginRequest;
import com.sw103302.backend.component.AuthMetricsRecorder;
import com.sw103302.backend.entity.RefreshToken;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.entity.UserSettings;
import com.sw103302.backend.repository.RefreshTokenRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DEFAULT_ROLE = "ROLE_USER";
    private static final int REFRESH_TOKEN_VALIDITY_DAYS = 7;
    private static final String PENDING_2FA_PREFIX = "2fa:pending:";
    private static final Duration PENDING_2FA_TTL = Duration.ofMinutes(5);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHashService tokenHashService;
    private final TwoFactorService twoFactorService;
    private final StringRedisTemplate redisTemplate;
    private final AuthMetricsRecorder authMetricsRecorder;

    public AuthService(UserRepository users,
                       RefreshTokenRepository refreshTokenRepository,
                       UserSettingsRepository userSettingsRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenHashService tokenHashService,
                       TwoFactorService twoFactorService,
                       StringRedisTemplate redisTemplate,
                       AuthMetricsRecorder authMetricsRecorder) {
        this.users = users;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHashService = tokenHashService;
        this.twoFactorService = twoFactorService;
        this.redisTemplate = redisTemplate;
        this.authMetricsRecorder = authMetricsRecorder;
    }

    @Transactional(readOnly = true)
    public EmailAvailabilityResponse checkEmailAvailability(String rawEmail) {
        long startedNs = System.nanoTime();
        String email = normalizeEmail(rawEmail);
        boolean available = !users.existsByEmailIgnoreCase(email);
        enforceMinimumLatency(startedNs, 120);
        return new EmailAvailabilityResponse(available);
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        long totalStartNs = System.nanoTime();
        String outcome = "success";
        try {
            String email = normalizeEmail(req.email());

            if (users.existsByEmailIgnoreCase(email)) {
                outcome = "duplicate_email";
                throw new IllegalStateException("email already exists");
            }

            String hash = passwordEncoder.encode(req.password());
            User saved = users.save(new User(email, hash, DEFAULT_ROLE));

            String accessToken = jwtService.createAccessToken(saved.getEmail(), saved.getRole());
            String refreshToken = createRefreshToken(saved);
            return AuthResponse.of(accessToken, refreshToken);
        } catch (RuntimeException e) {
            if ("success".equals(outcome)) {
                outcome = "error";
            }
            throw e;
        } finally {
            authMetricsRecorder.recordFlow("register", outcome, elapsedMs(totalStartNs));
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        long totalStartNs = System.nanoTime();
        String outcome = "success";

        try {
            String email = normalizeEmail(req.email());

            long stageStartNs = System.nanoTime();
            User user = users.findByEmailIgnoreCase(email).orElse(null);
            authMetricsRecorder.recordStage("login", "lookup_user", elapsedMs(stageStartNs));
            if (user == null) {
                outcome = "invalid_credentials";
                throw new IllegalArgumentException("invalid credentials");
            }

            stageStartNs = System.nanoTime();
            boolean passwordMatched = passwordEncoder.matches(req.password(), user.getPasswordHash());
            authMetricsRecorder.recordStage("login", "verify_password", elapsedMs(stageStartNs));
            if (!passwordMatched) {
                outcome = "invalid_credentials";
                throw new IllegalArgumentException("invalid credentials");
            }

            stageStartNs = System.nanoTime();
            boolean totpEnabled = userSettingsRepository.findByUser_Id(user.getId())
                    .map(UserSettings::isTotpEnabled)
                    .orElse(false);
            authMetricsRecorder.recordStage("login", "lookup_settings", elapsedMs(stageStartNs));

            if (totpEnabled) {
                stageStartNs = System.nanoTime();
                String pendingToken = UUID.randomUUID().toString();
                redisTemplate.opsForValue().set(
                        PENDING_2FA_PREFIX + pendingToken,
                        user.getEmail(),
                        PENDING_2FA_TTL
                );
                authMetricsRecorder.recordStage("login", "store_pending_2fa", elapsedMs(stageStartNs));
                outcome = "requires_2fa";
                return AuthResponse.pending2FA(pendingToken);
            }

            stageStartNs = System.nanoTime();
            refreshTokenRepository.deleteByUser(user);
            authMetricsRecorder.recordStage("login", "delete_refresh_tokens", elapsedMs(stageStartNs));

            stageStartNs = System.nanoTime();
            String accessToken = jwtService.createAccessToken(user.getEmail(), user.getRole());
            authMetricsRecorder.recordStage("login", "issue_access_token", elapsedMs(stageStartNs));

            stageStartNs = System.nanoTime();
            String refreshToken = createRefreshToken(user);
            authMetricsRecorder.recordStage("login", "create_refresh_token", elapsedMs(stageStartNs));

            return AuthResponse.of(accessToken, refreshToken);
        } catch (RuntimeException e) {
            if ("success".equals(outcome)) {
                outcome = "error";
            }
            throw e;
        } finally {
            authMetricsRecorder.recordFlow("login", outcome, elapsedMs(totalStartNs));
        }
    }

    @Transactional
    public AuthResponse verifyTwoFactorLogin(VerifyTwoFactorLoginRequest req) {
        long totalStartNs = System.nanoTime();
        String outcome = "success";

        try {
            String redisKey = PENDING_2FA_PREFIX + req.pendingToken();

            long stageStartNs = System.nanoTime();
            String email = redisTemplate.opsForValue().get(redisKey);
            authMetricsRecorder.recordStage("verify_2fa", "lookup_pending_token", elapsedMs(stageStartNs));
            if (email == null) {
                outcome = "invalid_pending_token";
                throw new IllegalArgumentException("Invalid or expired pending token");
            }

            stageStartNs = System.nanoTime();
            User user = users.findByEmailIgnoreCase(email).orElse(null);
            authMetricsRecorder.recordStage("verify_2fa", "lookup_user", elapsedMs(stageStartNs));
            if (user == null) {
                outcome = "user_not_found";
                throw new IllegalStateException("User not found");
            }

            stageStartNs = System.nanoTime();
            boolean verified = false;
            if (req.totpCode() != null && !req.totpCode().isBlank()) {
                verified = twoFactorService.verifyTotp(email, req.totpCode());
            } else if (req.backupCode() != null && !req.backupCode().isBlank()) {
                verified = twoFactorService.verifyBackupCode(email, req.backupCode());
            }
            authMetricsRecorder.recordStage("verify_2fa", "verify_code", elapsedMs(stageStartNs));

            if (!verified) {
                outcome = "invalid_2fa_code";
                throw new IllegalArgumentException("Invalid 2FA code");
            }

            stageStartNs = System.nanoTime();
            redisTemplate.delete(redisKey);
            authMetricsRecorder.recordStage("verify_2fa", "delete_pending_token", elapsedMs(stageStartNs));

            stageStartNs = System.nanoTime();
            refreshTokenRepository.deleteByUser(user);
            authMetricsRecorder.recordStage("verify_2fa", "delete_refresh_tokens", elapsedMs(stageStartNs));

            stageStartNs = System.nanoTime();
            String accessToken = jwtService.createAccessToken(user.getEmail(), user.getRole());
            authMetricsRecorder.recordStage("verify_2fa", "issue_access_token", elapsedMs(stageStartNs));

            stageStartNs = System.nanoTime();
            String refreshToken = createRefreshToken(user);
            authMetricsRecorder.recordStage("verify_2fa", "create_refresh_token", elapsedMs(stageStartNs));

            return AuthResponse.of(accessToken, refreshToken);
        } catch (RuntimeException e) {
            if ("success".equals(outcome)) {
                outcome = "error";
            }
            throw e;
        } finally {
            authMetricsRecorder.recordFlow("verify_2fa", outcome, elapsedMs(totalStartNs));
        }
    }

    @Transactional
    public AuthResponse refreshAccessToken(String refreshTokenString) {
        long totalStartNs = System.nanoTime();
        String outcome = "success";

        try {
            if (refreshTokenString == null || refreshTokenString.isBlank()) {
                outcome = "invalid_token";
                throw new IllegalArgumentException("Invalid refresh token");
            }

            long stageStartNs = System.nanoTime();
            String tokenHash = tokenHashService.hash(refreshTokenString);
            RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                    .or(() -> refreshTokenRepository.findByToken(refreshTokenString))
                    .orElse(null);
            authMetricsRecorder.recordStage("refresh", "lookup_refresh_token", elapsedMs(stageStartNs));
            if (refreshToken == null) {
                outcome = "invalid_token";
                throw new IllegalArgumentException("Invalid refresh token");
            }

            if (refreshToken.isExpired()) {
                stageStartNs = System.nanoTime();
                refreshTokenRepository.delete(refreshToken);
                authMetricsRecorder.recordStage("refresh", "delete_expired_token", elapsedMs(stageStartNs));
                outcome = "expired_token";
                throw new IllegalArgumentException("Refresh token expired");
            }

            stageStartNs = System.nanoTime();
            String rotatedRefreshToken = UUID.randomUUID().toString();
            refreshToken.setToken(UUID.randomUUID().toString());
            refreshToken.setTokenHash(tokenHashService.hash(rotatedRefreshToken));
            refreshToken.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_VALIDITY_DAYS));
            refreshToken.updateLastUsed();
            refreshTokenRepository.save(refreshToken);
            authMetricsRecorder.recordStage("refresh", "rotate_refresh_token", elapsedMs(stageStartNs));

            stageStartNs = System.nanoTime();
            User user = refreshToken.getUser();
            String newAccessToken = jwtService.createAccessToken(user.getEmail(), user.getRole());
            authMetricsRecorder.recordStage("refresh", "issue_access_token", elapsedMs(stageStartNs));

            return AuthResponse.of(newAccessToken, rotatedRefreshToken);
        } catch (RuntimeException e) {
            if ("success".equals(outcome)) {
                outcome = "error";
            }
            throw e;
        } finally {
            authMetricsRecorder.recordFlow("refresh", outcome, elapsedMs(totalStartNs));
        }
    }

    @Transactional
    public void logout(String refreshTokenString) {
        if (refreshTokenString == null || refreshTokenString.isBlank()) {
            return;
        }

        String tokenHash = tokenHashService.hash(refreshTokenString);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresentOrElse(refreshTokenRepository::delete, () ->
                        refreshTokenRepository.findByToken(refreshTokenString)
                                .ifPresent(refreshTokenRepository::delete)
                );
    }

    private String createRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(REFRESH_TOKEN_VALIDITY_DAYS);

        RefreshToken refreshToken = new RefreshToken(
                user,
                UUID.randomUUID().toString(),
                tokenHashService.hash(rawToken),
                expiresAt
        );
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    /**
     * Scheduled task to clean up expired refresh tokens
     */
    @Scheduled(cron = "${security.refresh-token.cleanup-cron:0 */30 * * * *}")
    @Transactional
    public int cleanupExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired refresh tokens", deleted);
        }
        return deleted;
    }

    private String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            return "";
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    private void enforceMinimumLatency(long startedNs, long minimumMs) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
        long remainingMs = minimumMs - elapsedMs;
        if (remainingMs <= 0) {
            return;
        }
        try {
            Thread.sleep(remainingMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private long elapsedMs(long startedNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
    }
}
