package com.sw103302.backend.service;

import com.sw103302.backend.dto.UpdateSettingsRequest;
import com.sw103302.backend.dto.UserSettingsResponse;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.entity.UserSettings;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.repository.UserSettingsRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSettingsService {
    private final UserSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    public UserSettingsService(UserSettingsRepository settingsRepository,
                               UserRepository userRepository) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserSettingsResponse get() {
        User user = currentUser();
        UserSettings settings = settingsRepository.findByUser_Id(user.getId())
            .orElseGet(() -> new UserSettings(user));
        return toResponse(settings);
    }

    @Transactional
    public UserSettingsResponse update(UpdateSettingsRequest req) {
        User user = currentUser();
        UserSettings settings = getOrCreateSettings(user);

        if (req.emailOnAlerts() != null) {
            settings.setEmailOnAlerts(req.emailOnAlerts());
        }
        if (req.dailySummaryEnabled() != null) {
            settings.setDailySummaryEnabled(req.dailySummaryEnabled());
        }
        if (req.theme() != null) {
            settings.setTheme(req.theme().trim().toLowerCase());
        }
        if (req.language() != null) {
            settings.setLanguage(req.language().trim().toLowerCase());
        }
        if (req.defaultMarket() != null) {
            settings.setDefaultMarket(req.defaultMarket().trim().toUpperCase());
        }

        UserSettings saved = settingsRepository.save(settings);
        return toResponse(saved);
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
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("unauthenticated");
        }
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private UserSettingsResponse toResponse(UserSettings settings) {
        return new UserSettingsResponse(
            settings.getId(),
            settings.isTotpEnabled(),
            settings.isEmailOnAlerts(),
            settings.isDailySummaryEnabled(),
            settings.getTheme(),
            settings.getLanguage(),
            settings.getDefaultMarket()
        );
    }
}
