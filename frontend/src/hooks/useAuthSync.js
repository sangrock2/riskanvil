import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { getToken, clearAllTokens } from "../auth/token";

/**
 * Synchronize authentication state across multiple browser tabs
 * Listens to custom auth events and localStorage changes
 */
export function useAuthSync() {
  const navigate = useNavigate();

  useEffect(() => {
    // Handle login event from another tab
    function handleLogin(event) {
      const { accessToken, refreshToken } = event.detail || {};
      if (accessToken && refreshToken) {
        // Tokens were set in localStorage by the event dispatcher
        // Just reload the page or navigate to dashboard if on login page
        if (window.location.pathname === "/login" || window.location.pathname === "/register") {
          navigate("/dashboard");
        }
      }
    }

    // Handle logout event from another tab
    function handleLogout() {
      // If currently logged in, redirect to login page
      if (getToken()) {
        clearAllTokens();
        navigate("/login?reason=logout");
      }
    }

    // Handle localStorage changes (cross-tab sync)
    function handleStorageChange(event) {
      // Access token removed in another tab
      if (event.key === "accessToken" && !event.newValue) {
        // Token was cleared - logout
        if (window.location.pathname !== "/login" && window.location.pathname !== "/register") {
          navigate("/login?reason=logout");
        }
      }

      // Access token added in another tab
      if (event.key === "accessToken" && event.newValue && !event.oldValue) {
        // User logged in from another tab
        if (window.location.pathname === "/login" || window.location.pathname === "/register") {
          navigate("/dashboard");
        }
      }

      // Refresh token updated (token refresh happened in another tab)
      if (event.key === "refreshToken" && event.newValue) {
        // Token was refreshed - no action needed, next API call will use new token
      }
    }

    // Listen to custom auth events
    window.addEventListener("auth:login", handleLogin);
    window.addEventListener("auth:logout", handleLogout);

    // Listen to localStorage changes (cross-tab)
    window.addEventListener("storage", handleStorageChange);

    return () => {
      window.removeEventListener("auth:login", handleLogin);
      window.removeEventListener("auth:logout", handleLogout);
      window.removeEventListener("storage", handleStorageChange);
    };
  }, [navigate]);
}
