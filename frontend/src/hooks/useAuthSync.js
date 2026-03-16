import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  getToken,
  clearTokensLocally,
  subscribeAuthSync,
  syncTokens,
} from "../auth/token";
import { ensureValidAccessToken } from "../api/http";

/**
 * Synchronize authentication state across multiple browser tabs
 * Uses BroadcastChannel/localStorage sync events to propagate login/logout
 */
export function useAuthSync() {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const unsubscribe = subscribeAuthSync((message) => {
      if (message.type === "tokens" && message.accessToken) {
        syncTokens(message.accessToken);
        if (location.pathname === "/login" || location.pathname === "/register") {
          navigate("/dashboard", { replace: true });
        }
        return;
      }

      if (message.type === "session-updated") {
        ensureValidAccessToken({
          redirectOnFail: false,
          forceRefresh: true,
          allowSessionProbe: true,
        })
          .then((token) => {
            if (!token) return;
            if (location.pathname === "/login" || location.pathname === "/register") {
              navigate("/dashboard", { replace: true });
            }
          })
          .catch(() => {});
        return;
      }

      if (message.type === "logout" && getToken()) {
        clearTokensLocally();
        if (location.pathname !== "/login" && location.pathname !== "/register") {
          const reason = message.reason || "logout";
          navigate(`/login?reason=${reason}`, { replace: true });
        }
      }
    });

    return () => {
      unsubscribe();
    };
  }, [location.pathname, navigate]);
}
