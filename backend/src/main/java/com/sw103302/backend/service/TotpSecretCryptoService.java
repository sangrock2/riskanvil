package com.sw103302.backend.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.StringJoiner;

@Service
public class TotpSecretCryptoService {
    private static final Logger log = LoggerFactory.getLogger(TotpSecretCryptoService.class);
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ENCRYPTED_PREFIX = "enc:";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String encryptionKeyText;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired(required = false)
    private Environment environment;

    private SecretKeySpec secretKeySpec;

    public TotpSecretCryptoService(@Value("${security.totp.encryption-key:}") String encryptionKeyText) {
        this.encryptionKeyText = encryptionKeyText == null ? "" : encryptionKeyText;
    }

    @PostConstruct
    void init() {
        byte[] rawBytes = encryptionKeyText.getBytes(StandardCharsets.UTF_8);
        if (rawBytes.length < 32) {
            throw new IllegalStateException(
                    "SECURITY ERROR: TOTP_ENCRYPTION_KEY must be at least 32 bytes to protect 2FA secrets."
            );
        }
        if (usesDevelopmentKey() && isProdLikeEnvironment()) {
            throw new IllegalStateException(
                    "SECURITY ERROR: Default TOTP encryption key is not allowed in active profiles [" +
                            activeProfilesSummary() + "]. Set TOTP_ENCRYPTION_KEY to a strong random value."
            );
        }
        if (usesDevelopmentKey()) {
            log.warn("Using development TOTP encryption key for active profiles [{}].", activeProfilesSummary());
        }

        this.secretKeySpec = new SecretKeySpec(sha256(rawBytes), "AES");
    }

    public String encrypt(String plainSecret) {
        if (plainSecret == null || plainSecret.isBlank()) {
            throw new IllegalArgumentException("plainSecret is required");
        }
        if (isEncrypted(plainSecret)) {
            return plainSecret;
        }

        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));

            byte[] packed = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", e);
        }
    }

    public String decrypt(String storedSecret) {
        if (storedSecret == null || storedSecret.isBlank()) {
            throw new IllegalArgumentException("storedSecret is required");
        }
        if (!isEncrypted(storedSecret)) {
            return storedSecret;
        }

        try {
            byte[] packed = Base64.getDecoder().decode(storedSecret.substring(ENCRYPTED_PREFIX.length()));
            if (packed.length <= IV_BYTES) {
                throw new IllegalStateException("Stored TOTP secret payload is invalid");
            }

            byte[] iv = new byte[IV_BYTES];
            byte[] cipherText = new byte[packed.length - IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_BYTES);
            System.arraycopy(packed, IV_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to decrypt stored TOTP secret", e);
        }
    }

    public boolean isEncrypted(String storedSecret) {
        return storedSecret != null && storedSecret.startsWith(ENCRYPTED_PREFIX);
    }

    private boolean usesDevelopmentKey() {
        String normalized = encryptionKeyText.toLowerCase(Locale.ROOT);
        return normalized.contains("dev-only") || normalized.contains("change-me");
    }

    private boolean isProdLikeEnvironment() {
        if (environment == null) {
            return false;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return false;
        }

        for (String profile : activeProfiles) {
            String normalized = profile.toLowerCase(Locale.ROOT);
            if ("dev".equals(normalized) || "test".equals(normalized)) {
                return false;
            }
        }
        return true;
    }

    private String activeProfilesSummary() {
        if (environment == null || environment.getActiveProfiles().length == 0) {
            return "default";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (String profile : environment.getActiveProfiles()) {
            joiner.add(profile);
        }
        return joiner.toString();
    }

    private byte[] sha256(byte[] rawBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(rawBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
