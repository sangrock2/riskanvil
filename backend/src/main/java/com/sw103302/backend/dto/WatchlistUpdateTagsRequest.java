package com.sw103302.backend.dto;

import java.util.List;

public record WatchlistUpdateTagsRequest(
        List<Long> tagIds
) {}
