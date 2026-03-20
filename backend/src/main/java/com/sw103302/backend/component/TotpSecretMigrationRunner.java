package com.sw103302.backend.component;

import com.sw103302.backend.entity.TotpSecret;
import com.sw103302.backend.repository.TotpSecretRepository;
import com.sw103302.backend.service.TotpSecretCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class TotpSecretMigrationRunner {
    private static final Logger log = LoggerFactory.getLogger(TotpSecretMigrationRunner.class);

    private final TotpSecretRepository totpSecretRepository;
    private final TotpSecretCryptoService totpSecretCryptoService;

    public TotpSecretMigrationRunner(
            TotpSecretRepository totpSecretRepository,
            TotpSecretCryptoService totpSecretCryptoService
    ) {
        this.totpSecretRepository = totpSecretRepository;
        this.totpSecretCryptoService = totpSecretCryptoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyPlaintextSecrets() {
        List<TotpSecret> allSecrets;
        try {
            allSecrets = totpSecretRepository.findAll();
        } catch (InvalidDataAccessResourceUsageException e) {
            log.info("Skipping legacy TOTP secret migration because totp_secrets is not available yet.");
            return;
        }

        int migrated = 0;
        for (TotpSecret totpSecret : allSecrets) {
            String storedSecret = totpSecret.getSecret();
            if (storedSecret == null || storedSecret.isBlank() || totpSecretCryptoService.isEncrypted(storedSecret)) {
                continue;
            }

            totpSecret.setSecret(totpSecretCryptoService.encrypt(storedSecret));
            migrated++;
        }

        if (migrated > 0) {
            totpSecretRepository.saveAll(allSecrets);
            log.info("Migrated {} legacy plaintext TOTP secret(s) to encrypted storage.", migrated);
        }
    }
}
