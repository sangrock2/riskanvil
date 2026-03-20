package com.sw103302.backend.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    Boolean requires2FA,
    String pendingToken
) {
    public static AuthResponse accessOnly(String accessToken) {
        return new AuthResponse(accessToken, null, false, null);
    }

    /** Normal login (no 2FA) */
    public static AuthResponse of(String accessToken, String refreshToken) {
        return new AuthResponse(accessToken, refreshToken, false, null);
    }

    /** 2FA required - return pending token, no JWT yet */
    public static AuthResponse pending2FA(String pendingToken) {
        return new AuthResponse(null, null, true, pendingToken);
    }
}
