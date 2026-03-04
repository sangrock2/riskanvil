package com.sw103302.backend.service;

import com.sw103302.backend.dto.TagCreateRequest;
import com.sw103302.backend.dto.TagResponse;
import com.sw103302.backend.dto.TagUpdateRequest;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.entity.WatchlistTag;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.repository.WatchlistTagRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WatchlistTagService {
    private final WatchlistTagRepository tagRepository;
    private final UserRepository userRepository;

    public WatchlistTagService(WatchlistTagRepository tagRepository, UserRepository userRepository) {
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TagResponse> list() {
        User user = currentUser();
        List<WatchlistTag> tags = tagRepository.findByUser_IdOrderByNameAsc(user.getId());

        return tags.stream()
                .map(tag -> new TagResponse(
                        tag.getId(),
                        tag.getName(),
                        tag.getColor(),
                        tag.getCreatedAt(),
                        tag.getWatchlistItems().size()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public TagResponse create(TagCreateRequest req) {
        User user = currentUser();

        String name = req.name().trim();
        if (name.isEmpty() || name.length() > 50) {
            throw new IllegalArgumentException("Tag name must be 1-50 characters");
        }

        // Check for duplicate
        tagRepository.findByUser_IdAndName(user.getId(), name)
                .ifPresent(t -> {
                    throw new IllegalStateException("Tag already exists: " + name);
                });

        String color = req.color();
        if (color == null || !color.matches("^#[0-9a-fA-F]{6}$")) {
            color = "#3b82f6"; // Default blue
        }

        WatchlistTag tag = new WatchlistTag(user, name, color);
        tag = tagRepository.save(tag);

        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getColor(),
                tag.getCreatedAt(),
                0
        );
    }

    @Transactional
    public TagResponse update(Long tagId, TagUpdateRequest req) {
        User user = currentUser();

        WatchlistTag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found"));

        if (!tag.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Unauthorized");
        }

        if (req.name() != null) {
            String name = req.name().trim();
            if (!name.isEmpty() && name.length() <= 50) {
                // Check for duplicate (excluding current tag)
                tagRepository.findByUser_IdAndName(user.getId(), name)
                        .ifPresent(existing -> {
                            if (!existing.getId().equals(tagId)) {
                                throw new IllegalStateException("Tag name already exists: " + name);
                            }
                        });
                tag.setName(name);
            }
        }

        if (req.color() != null && req.color().matches("^#[0-9a-fA-F]{6}$")) {
            tag.setColor(req.color());
        }

        tag = tagRepository.save(tag);

        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getColor(),
                tag.getCreatedAt(),
                tag.getWatchlistItems().size()
        );
    }

    @Transactional
    public void delete(Long tagId) {
        User user = currentUser();

        WatchlistTag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found"));

        if (!tag.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Unauthorized");
        }

        // Remove tag from all watchlist items
        tag.getWatchlistItems().forEach(item -> item.removeTag(tag));

        tagRepository.delete(tag);
    }

    private User currentUser() {
        String email = SecurityUtil.currentEmail();
        if (email == null) throw new IllegalStateException("unauthenticated");
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found"));
    }
}
