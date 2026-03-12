import { useEffect, useState } from "react";
import { getToken, getRefreshToken } from "../auth/token";
import { Navigate } from "react-router-dom";
import { ensureValidAccessToken, isTokenExpired } from "../api/http";
import { Spinner } from "./ui/Loading";

export default function ProtectedRoute({ children }) {
    const [state, setState] = useState(() => {
        const token = getToken();
        if (token && !isTokenExpired(token)) return "ready";
        if (getRefreshToken()) return "refreshing";
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
            .catch(() => {
                if (!cancelled) {
                    setState("unauthorized");
                }
            });

        return () => {
            cancelled = true;
        };
    }, [state]);

    if (state === "refreshing") {
        return <Spinner center text="Loading..." />;
    }

    if (state === "unauthorized") {
        const reason = getRefreshToken() ? "expired" : "missing";
        return <Navigate to={`/login?reason=${reason}`} replace />;
    }

    return children;
}
