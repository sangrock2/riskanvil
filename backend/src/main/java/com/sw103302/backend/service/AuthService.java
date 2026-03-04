package com.sw103302.backend.service;

import com.sw103302.backend.dto.AuthResponse;
import com.sw103302.backend.dto.LoginRequest;
import com.sw103302.backend.dto.RegisterRequest;
import com.sw103302.backend.dto.VerifyTwoFactorLoginRequest;
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
import java.util.UUID;

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

    public AuthService(UserRepository users,
                       RefreshTokenRepository refreshTokenRepository,
                       UserSettingsRepository userSettingsRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenHashService tokenHashService,
                       TwoFactorService twoFactorService,
                       StringRedisTemplate redisTemplate) {
        this.users = users;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHashService = tokenHashService;
        this.twoFactorService = twoFactorService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new IllegalStateException("email already exists");
        }

        String hash = passwordEncoder.encode(req.password());
        User saved = users.save(new User(req.email(), hash, DEFAULT_ROLE));

        String accessToken = jwtService.createAccessToken(saved.getEmail(), saved.getRole());
        String refreshToken = createRefreshToken(saved);

        return AuthResponse.of(accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("invalid credentials");
        }

        // Check if 2FA is enabled for this user
        boolean totpEnabled = userSettingsRepository.findByUser_Id(user.getId())
                .map(UserSettings::isTotpEnabled)
                .orElse(false);

        if (totpEnabled) {
            // Issue a pending token; do NOT issue JWT yet
            String pendingToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    PENDING_2FA_PREFIX + pendingToken,
                    user.getEmail(),
                    PENDING_2FA_TTL
            );
            return AuthResponse.pending2FA(pendingToken);
        }

        // No 2FA - issue tokens immediately
        refreshTokenRepository.deleteByUser(user);

        String accessToken = jwtService.createAccessToken(user.getEmail(), user.getRole());
        String refreshToken = createRefreshToken(user);

        return AuthResponse.of(accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse verifyTwoFactorLogin(VerifyTwoFactorLoginRequest req) {
        String redisKey = PENDING_2FA_PREFIX + req.pendingToken();
        String email = redisTemplate.opsForValue().get(redisKey);

        if (email == null) {
            throw new IllegalArgumentException("Invalid or expired pending token");
        }

        User user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        boolean verified = false;

        if (req.totpCode() != null && !req.totpCode().isBlank()) {
            verified = twoFactorService.verifyTotp(email, req.totpCode());
        } else if (req.backupCode() != null && !req.backupCode().isBlank()) {
            verified = twoFactorService.verifyBackupCode(email, req.backupCode());
        }

        if (!verified) {
            throw new IllegalArgumentException("Invalid 2FA code");
        }

        // Consume the pending token
        redisTemplate.delete(redisKey);

        // Issue final tokens
        refreshTokenRepository.deleteByUser(user);
        String accessToken = jwtService.createAccessToken(user.getEmail(), user.getRole());
        String refreshToken = createRefreshToken(user);

        return AuthResponse.of(accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse refreshAccessToken(String refreshTokenString) {
        if (refreshTokenString == null || refreshTokenString.isBlank()) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String tokenHash = tokenHashService.hash(refreshTokenString);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                // Backward compatibility for pre-hash tokens.
                .or(() -> refreshTokenRepository.findByToken(refreshTokenString))
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Refresh token expired");
        }

        String rotatedRefreshToken = UUID.randomUUID().toString();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setTokenHash(tokenHashService.hash(rotatedRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_VALIDITY_DAYS));
        refreshToken.updateLastUsed();
        refreshTokenRepository.save(refreshToken);

        User user = refreshToken.getUser();
        String newAccessToken = jwtService.createAccessToken(user.getEmail(), user.getRole());

        return AuthResponse.of(newAccessToken, rotatedRefreshToken);
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
}
