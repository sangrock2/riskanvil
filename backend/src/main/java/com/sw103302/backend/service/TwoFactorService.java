package com.sw103302.backend.service;

import com.sw103302.backend.dto.DisableTotpRequest;
import com.sw103302.backend.dto.SetupTotpResponse;
import com.sw103302.backend.dto.VerifyTotpRequest;
import com.sw103302.backend.entity.BackupCode;
import com.sw103302.backend.entity.TotpSecret;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.entity.UserSettings;
import com.sw103302.backend.repository.BackupCodeRepository;
import com.sw103302.backend.repository.TotpSecretRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.repository.UserSettingsRepository;
import com.sw103302.backend.util.SecurityUtil;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TwoFactorService {
    private final UserSettingsRepository settingsRepository;
    private final TotpSecretRepository totpRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpSecretCryptoService totpSecretCryptoService;
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    public TwoFactorService(UserSettingsRepository settingsRepository,
                            TotpSecretRepository totpRepository,
                            BackupCodeRepository backupCodeRepository,
                            UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            TotpSecretCryptoService totpSecretCryptoService) {
        this.settingsRepository = settingsRepository;
        this.totpRepository = totpRepository;
        this.backupCodeRepository = backupCodeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpSecretCryptoService = totpSecretCryptoService;
    }

    @Transactional
    public SetupTotpResponse setupTotp() {
        User user = currentUser();

        // Generate secret
        final GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();

        // Save to DB (not enabled yet)
        TotpSecret totpSecret = totpRepository.findByUser_Id(user.getId())
            .orElse(new TotpSecret(user));
        totpSecret.setSecret(totpSecretCryptoService.encrypt(secret));
        totpSecret.setEnabled(false);
        totpSecret.setVerifiedAt(null);
        totpRepository.save(totpSecret);

        backupCodeRepository.deleteByUser_Id(user.getId());

        // Generate QR code URL
        String issuer = "Stock-AI";
        String qrUrl = GoogleAuthenticatorQRGenerator.getOtpAuthURL(
            issuer, user.getEmail(), key);

        // Generate backup codes (10 codes)
        List<String> backupCodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String code = generateBackupCode();
            backupCodes.add(code);

            BackupCode bc = new BackupCode(user, passwordEncoder.encode(code));
            backupCodeRepository.save(bc);
        }

        return new SetupTotpResponse(secret, qrUrl, backupCodes);
    }

    @Transactional
    public boolean verifyAndEnable(VerifyTotpRequest req) {
        User user = currentUser();

        TotpSecret totpSecret = totpRepository.findByUser_Id(user.getId())
            .orElseThrow(() -> new IllegalStateException("TOTP not set up"));

        int code = Integer.parseInt(req.code());
        boolean valid = gAuth.authorize(readSecret(totpSecret, true), code);

        if (valid) {
            totpSecret.setEnabled(true);
            totpSecret.setVerifiedAt(LocalDateTime.now());
            totpRepository.save(totpSecret);

            // Update settings
            UserSettings settings = getOrCreateSettings(user);
            settings.setTotpEnabled(true);
            settingsRepository.save(settings);
        }

        return valid;
    }

    @Transactional
    public void disable(DisableTotpRequest req) {
        User user = currentUser();

        // Verify password
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password");
        }

        // Verify TOTP code
        TotpSecret totpSecret = totpRepository.findByUser_Id(user.getId())
            .orElseThrow(() -> new IllegalStateException("TOTP not enabled"));

        int code = Integer.parseInt(req.totpCode());
        if (!gAuth.authorize(readSecret(totpSecret, true), code)) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }

        backupCodeRepository.deleteByUser_Id(user.getId());
        totpRepository.delete(totpSecret);

        UserSettings settings = getOrCreateSettings(user);
        settings.setTotpEnabled(false);
        settingsRepository.save(settings);
    }

    @Transactional(readOnly = true)
    public boolean verifyTotp(String email, String code) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found"));

        TotpSecret totpSecret = totpRepository.findByUser_Id(user.getId())
            .orElseThrow(() -> new IllegalStateException("TOTP not enabled"));

        if (!totpSecret.isEnabled()) {
            throw new IllegalStateException("TOTP not enabled");
        }

        int codeInt = Integer.parseInt(code);
        return gAuth.authorize(readSecret(totpSecret, false), codeInt);
    }

    @Transactional
    public boolean verifyBackupCode(String email, String code) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found"));

        List<BackupCode> codes = backupCodeRepository.findByUser_IdAndUsed(user.getId(), false);

        for (BackupCode bc : codes) {
            if (passwordEncoder.matches(code, bc.getCodeHash())) {
                bc.setUsed(true);
                bc.setUsedAt(LocalDateTime.now());
                backupCodeRepository.save(bc);
                return true;
            }
        }

        return false;
    }

    private String generateBackupCode() {
        // Generate 10-character alphanumeric code
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private UserSettings getOrCreateSettings(User user) {
        return settingsRepository.findByUser_Id(user.getId())
            .orElseGet(() -> {
                UserSettings settings = new UserSettings(user);
                return settingsRepository.save(settings);
            });
    }

    private User currentUser() {
        String email = SecurityUtil.currentEmail();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private String readSecret(TotpSecret totpSecret, boolean migrateLegacySecret) {
        String storedSecret = totpSecret.getSecret();
        String plainSecret = totpSecretCryptoService.decrypt(storedSecret);
        if (migrateLegacySecret && !totpSecretCryptoService.isEncrypted(storedSecret)) {
            totpSecret.setSecret(totpSecretCryptoService.encrypt(plainSecret));
            totpRepository.save(totpSecret);
        }
        return plainSecret;
    }
}
