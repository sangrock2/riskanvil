import { useEffect, useState } from "react";
import { getRefreshToken, getToken, hasSessionHint } from "../auth/token";
import { Navigate } from "react-router-dom";
import { ensureValidAccessToken, isTokenExpired } from "../api/http";
import { Spinner } from "./ui/Loading";

export default function ProtectedRoute({ children }) {
    const [state, setState] = useState(() => {
        const token = getToken();
        if (token && !isTokenExpired(token)) return "ready";
        if (getRefreshToken() || hasSessionHint()) return "refreshing";
        return "unauthorized";
    });

    useEffect(() => {
        if (state !== "refreshing") return undefined;

        let cancelled = false;

        ensureValidAccessToken({ redirectOnFail: false })
            .then((token) => {
                if (cancelled) return;
                setState(token ? "ready" : "unauthorized");
            })
            .catch((error) => {
                if (!cancelled) {
                    setState(error?.isTransientAuthFailure ? "retrying" : "unauthorized");
                }
            });

        return () => {
            cancelled = true;
        };
    }, [state]);

    useEffect(() => {
        if (state !== "retrying") return undefined;

        const timer = window.setTimeout(() => {
            setState("refreshing");
        }, 3000);

        return () => {
            window.clearTimeout(timer);
        };
    }, [state]);

    if (state === "refreshing") {
        return <Spinner center text="Loading..." />;
    }

    if (state === "retrying") {
        return <Spinner center text="Connection issue. Retrying..." />;
    }

    if (state === "unauthorized") {
        const reason = getRefreshToken() || hasSessionHint() ? "expired" : "missing";
        return <Navigate to={`/login?reason=${reason}`} replace />;
    }

    return children;
}
