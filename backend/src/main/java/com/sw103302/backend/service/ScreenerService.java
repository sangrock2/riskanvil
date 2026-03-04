package com.sw103302.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.*;
import com.sw103302.backend.entity.ScreenerPreset;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.repository.ScreenerPresetRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScreenerService {
    private final AiClient aiClient;
    private final ScreenerPresetRepository presetRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ScreenerService(AiClient aiClient,
                           ScreenerPresetRepository presetRepository,
                           UserRepository userRepository,
                           ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.presetRepository = presetRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public List<ScreenerResult> screen(ScreenerRequest req) {
        try {
            String jsonResponse = aiClient.post("/screener", req);
            return objectMapper.readValue(jsonResponse, new TypeReference<List<ScreenerResult>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to screen stocks: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void savePreset(SaveScreenerPresetRequest req) {
        User user = currentUser();

        // Check duplicate name
        if (presetRepository.findByUser_IdAndName(user.getId(), req.name()).isPresent()) {
            throw new IllegalArgumentException("Preset with this name already exists");
        }

        try {
            String filtersJson = objectMapper.writeValueAsString(req.filters());

            ScreenerPreset preset = new ScreenerPreset(user, req.name(),
                req.description(), filtersJson, req.isPublic());
            presetRepository.save(preset);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save preset", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ScreenerPresetResponse> listPresets() {
        User user = currentUser();
        List<ScreenerPreset> presets = presetRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        return presets.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScreenerPresetResponse> listPublicPresets() {
        List<ScreenerPreset> presets = presetRepository.findByIsPublicTrueOrderByCreatedAtDesc();
        return presets.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private ScreenerPresetResponse toResponse(ScreenerPreset preset) {
        try {
            ScreenerRequest.ScreenerFilters filters = objectMapper.readValue(
                preset.getFiltersJson(),
                ScreenerRequest.ScreenerFilters.class
            );

            return new ScreenerPresetResponse(
                preset.getId(),
                preset.getName(),
                preset.getDescription(),
                filters,
                preset.isPublic(),
                preset.getCreatedAt()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse preset", e);
        }
    }

    private User currentUser() {
        String email = SecurityUtil.currentEmail();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
