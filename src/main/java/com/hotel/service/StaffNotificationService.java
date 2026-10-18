package com.hotel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

// Thong bao tuc thoi cho le tan (SSE): don moi, coc VNPay ve, lich spa / ban, goi len phong, tour / dua don, lien he...
// Gui SAU KHI giao dich commit (don bi rollback thi khong bao nham). Giu 50 thong bao gan nhat de trinh duyet
// ket noi lai (mat mang, doi trang) nhan bu nhung cai bi lo qua Last-Event-ID
@Service
public class StaffNotificationService {

    private static final Logger log = LoggerFactory.getLogger(StaffNotificationService.class);
    public static final int BUFFER = 50;
    static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    public enum Level { INFO, SUCCESS, WARNING }

    public record Notice(long id, String kind, Level level, String title, String message, String url, LocalDateTime time) {
    }

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Deque<Notice> recent = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();

    public void notify(String kind, String title, String message, String url) {
        notify(kind, Level.INFO, title, message, url);
    }

    public void notify(String kind, Level level, String title, String message, String url) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(kind, level, title, message, url);
                }
            });
        } else {
            publish(kind, level, title, message, url);
        }
    }

    Notice publish(String kind, Level level, String title, String message, String url) {
        Notice notice = new Notice(sequence.incrementAndGet(), kind, level, title, message, url, LocalDateTime.now());
        synchronized (recent) {
            recent.addLast(notice);
            while (recent.size() > BUFFER) {
                recent.removeFirst();
            }
        }
        for (SseEmitter emitter : emitters) {
            send(emitter, notice);
        }
        return notice;
    }

    // Thong bao co id > afterId (dung cho ket noi lai va trang du phong khong ho tro SSE)
    public List<Notice> recent(long afterId) {
        synchronized (recent) {
            return recent.stream().filter(n -> n.id() > afterId).toList();
        }
    }

    public long lastId() {
        return sequence.get();
    }

    public int connections() {
        return emitters.size();
    }

    // lastEventId: trinh duyet tu gui khi ket noi lai -> gui bu thong bao bi lo
    public SseEmitter subscribe(Long lastEventId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(ex -> emitters.remove(emitter));
        try {
            // retry: trinh duyet tu ket noi lai sau 5s; id hien tai de lan sau chi nhan thong bao moi
            emitter.send(SseEmitter.event().name("hello").id(String.valueOf(lastId())).reconnectTime(5000).data("ok"));
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
        }
        if (lastEventId != null) {
            for (Notice n : recent(lastEventId)) {
                send(emitter, n);
            }
        }
        return emitter;
    }

    // Giu ket noi qua proxy / load balancer (dong ket noi im lang sau 30-60s)
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(emitter);
            }
        }
    }

    private void send(SseEmitter emitter, Notice notice) {
        try {
            emitter.send(SseEmitter.event().name("staff").id(String.valueOf(notice.id())).data(notice, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            log.debug("Bo ket noi thong bao da dong: {}", ex.getMessage());
            emitters.remove(emitter);
        }
    }

    // Chi de test
    List<SseEmitter> emittersSnapshot() {
        return new ArrayList<>(emitters);
    }
}
