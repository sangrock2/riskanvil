import { getToken, clearToken } from "../auth/token.js";
const API_BASE_URL = (
    import.meta.env.VITE_API_BASE_URL ||
    import.meta.env.REACT_APP_API_BASE_URL ||
    ""
).replace(/\/+$/, "");

function isTestMode() {
    try {
        const qs = new URLSearchParams(window.location.search);
        return qs.get("test") === "true";
    } catch {
        return false;
    }
}

function withTestParam(path) {
    if (!isTestMode()) return path;

    const url = new URL(path, window.location.origin);
    if (!url.searchParams.has("test")) url.searchParams.set("test", "true");

    const isAbsolute = /^https?:\/\//i.test(path);
    return isAbsolute ? url.toString() : url.pathname + url.search + url.hash;
}

function isAbsoluteHttpUrl(path) {
    return /^https?:\/\//i.test(path);
}

function resolveApiPath(path) {
    const testedPath = withTestParam(path);
    if (!API_BASE_URL || isAbsoluteHttpUrl(testedPath)) {
        return testedPath;
    }
    if (testedPath.startsWith("/")) {
        return `${API_BASE_URL}${testedPath}`;
    }
    return `${API_BASE_URL}/${testedPath}`;
}

/* ---- 토큰 만료 선제 체크(선택) ---- */
function decodeJwtPayload(token) {
    try {
        const [, payload] = token.split(".");
        if (!payload) return null;
        const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
        return JSON.parse(decodeURIComponent(escape(json)));
    } catch {
        return null;
    }
}

function isTokenExpired(token, skewSec = 10) {
    const p = decodeJwtPayload(token);
    const exp = p?.exp;
    if (!exp) return false;
    const now = Math.floor(Date.now() / 1000);
    return exp <= now + skewSec;
}

/* ---- SSE 파서(text/event-stream) ---- */
function createSseParser(onEvent) {
    let buffer = "";
    let evtName = "message";
    let dataBuf = "";

    const emit = () => {
        if (dataBuf === "") return;
        const data = dataBuf.endsWith("\n") ? dataBuf.slice(0, -1) : dataBuf;
        onEvent({ event: evtName || "message", data });
        evtName = "message";
        dataBuf = "";
    };

    return (chunk) => {
        buffer += chunk;

        let nl;
        while ((nl = buffer.indexOf("\n")) >= 0) {
            let line = buffer.slice(0, nl);
            buffer = buffer.slice(nl + 1);

            if (line.endsWith("\r")) line = line.slice(0, -1);

            // 이벤트 경계(빈 줄)
            if (line === "") {
                emit();
                continue;
            }

            // 주석 라인
            if (line.startsWith(":")) continue;

            const colon = line.indexOf(":");
            let field = "";
            let value = "";

            if (colon === -1) {
                field = line;
                value = "";
            } else {
                field = line.slice(0, colon);
                value = line.slice(colon + 1);
                if (value.startsWith(" ")) value = value.slice(1);
            }

            if (field === "event") evtName = value;
            else if (field === "data") dataBuf += value + "\n";
            // id, retry 등은 필요시 추가 가능
        }
    };
}

/**
 * POST 기반 SSE 스트리밍 (Authorization 헤더 지원)
 *
 * @param {string} path - 예: "/api/market/report/stream?web=true"
 * @param {any} body - 보통 JSON 객체
 * @param {object} opts
 * @param {(evt:{event:string,data:string})=>void} opts.onMessage
 * @param {(err:Error)=>void} opts.onError
 * @param {(res:Response)=>void} opts.onOpen
 * @param {AbortSignal} opts.signal
 * @param {object} opts.headers
 *
 * @returns {{ close: ()=>void, done: Promise<void> }}
 */

export function ssePost(path, body, opts = {}) {
    const token = getToken();

    if (token && isTokenExpired(token)) {
        clearToken();
        window.location.replace("/login?reason=expired");
        throw new Error("token_expired");
    }

    const finalPath = resolveApiPath(path);

    const controller = new AbortController();
    if (opts.signal) {
        if (opts.signal.aborted) controller.abort();
        else opts.signal.addEventListener("abort", () => controller.abort(), { once: true });
    }

    const headers = { ...(opts.headers || {}) };
    headers.Accept = "text/event-stream";

    const isFormData = body instanceof FormData;
    let payload = body;

    if (!isFormData) {
        headers["Content-Type"] = headers["Content-Type"] || "application/json";
        payload = body == null ? null : JSON.stringify(body);
    }

    if (token) headers.Authorization = `Bearer ${token}`;

    const done = (async () => {
        try {
            const res = await fetch(finalPath, {
                method: "POST",
                headers,
                body: payload,
                signal: controller.signal,
            });

            opts.onOpen?.(res);

            if (res.status === 401 || res.status === 403) {
                clearToken();
                window.location.replace("/login?reason=expired");
                throw new Error(`HTTP ${res.status}`);
            }

            if (!res.ok) {
                const text = await res.text().catch(() => "");
                throw new Error(text || `HTTP ${res.status}`);
            }

            const ct = res.headers.get("content-type") || "";
            if (!ct.includes("text/event-stream")) {
                const text = await res.text().catch(() => "");
                throw new Error(`Not SSE response. content-type=${ct} body=${text.slice(0, 500)}`);
            }

            const reader = res.body?.getReader?.();
            if (!reader) throw new Error("Response body is not readable (no stream).");

            const decoder = new TextDecoder("utf-8");
            const parse = createSseParser((evt) => opts.onMessage?.(evt));

            while (true) {
                const { value, done } = await reader.read();
                if (done) break;
                parse(decoder.decode(value, { stream: true }));
            }
        } catch (e) {
            if (e?.name === "AbortError") return;
            opts.onError?.(e);
            throw e;
        }
    })();

    return {
        close: () => controller.abort(),
        done,
    };
}
