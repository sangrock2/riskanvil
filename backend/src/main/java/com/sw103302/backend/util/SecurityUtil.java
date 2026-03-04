package com.sw103302.backend.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {
    /**
     * Gets the current authenticated user's email.
     * @return email if authenticated, null otherwise
     */
    public static String currentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return auth.getName(); // CustomUserDetailsService에서 username=email로 설정했음
    }

    /**
     * Gets the current authenticated user's email.
     * Throws exception if not authenticated (for required auth contexts).
     * @return email of authenticated user
     * @throws IllegalStateException if user is not authenticated
     */
    public static String requireCurrentEmail() {
        String email = currentEmail();
        if (email == null) {
            throw new IllegalStateException("User is not authenticated");
        }
        return email;
    }

    /**
     * Verifies that the current user owns the resource with the given email.
     * Throws exception if not authenticated or email doesn't match.
     * @param resourceOwnerEmail the email of the resource owner
     * @throws IllegalStateException if user is not authenticated
     * @throws SecurityException if authenticated user doesn't own the resource
     */
    public static void requireOwnership(String resourceOwnerEmail) {
        String currentEmail = requireCurrentEmail();
        if (!currentEmail.equals(resourceOwnerEmail)) {
            throw new SecurityException("Access denied: user does not own this resource");
        }
    }
}
