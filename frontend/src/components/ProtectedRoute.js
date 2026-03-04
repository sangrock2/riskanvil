import { getToken, clearToken } from "../auth/token";
import { Navigate } from "react-router-dom";
import { isTokenExpired } from "../api/http";

export default function ProtectedRoute({ children }) {
    const token = getToken();

    // No token - redirect to login
    if (!token) {
        return <Navigate to="/login?reason=missing" replace />;
    }

    // Token expired - clear and redirect to login
    if (isTokenExpired(token)) {
        clearToken();
        return <Navigate to="/login?reason=expired" replace />;
    }

    return children;
}