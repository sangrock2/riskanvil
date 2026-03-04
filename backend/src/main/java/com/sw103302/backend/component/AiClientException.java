package com.sw103302.backend.component;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class AiClientException extends RuntimeException {
    private final int status;
    private final String body;

    public AiClientException(HttpStatusCode statusCode, String body) {
        super("AI server error: " + statusCode + " body=" + trim(body));
        this.status = statusCode.value();
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    private static String trim(String s) {
        if (s == null) return "";
        String t = s.strip();
        // 너무 길면 로그/예외 메시지 폭발 방지
        return t.length() <= 2000 ? t : t.substring(0, 2000);
    }
}
