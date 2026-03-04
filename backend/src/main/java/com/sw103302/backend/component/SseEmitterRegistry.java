package com.sw103302.backend.component;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseEmitterRegistry {
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    /** 등록 + 자동 해제 콜백 설정 */
    public void register(SseEmitter emitter) {
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            try { emitter.complete(); } catch (Exception ignore) {}
        });
        emitter.onError((e) -> {
            emitters.remove(emitter);
            try { emitter.completeWithError(e); } catch (Exception ignore) {}
        });
    }

    /** 서버 종료 시 모든 SSE 연결 종료 */
    public void closeAll(String reason) {
        for (SseEmitter e : emitters) {
            try {
                // 종료 이벤트를 한 번 보내고 닫기(실패해도 그냥 닫음)
                e.send(SseEmitter.event().name("server_shutdown").data(reason));
            } catch (Exception ignore) {}
            try { e.complete(); } catch (Exception ignore) {}
        }
        emitters.clear();
    }
}
