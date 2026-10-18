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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

// Thong bao tuc thoi cho nhan vien (SSE). Moi thong bao gui toi 1 hoac nhieu nhom (le tan / buong phong),
// co the chi 1 nguoi (targetUserId - VD phong vua giao cho nhan vien buong do).
// Gui SAU KHI giao dich commit (rollback thi khong bao nham). Giu 50 thong bao gan nhat de trinh duyet
// ket noi lai (mat mang, doi trang) nhan bu nhung cai bi lo qua Last-Event-ID
@Service
public class StaffNotificationService {

    private static final Logger log = LoggerFactory.getLogger(StaffNotificationService.class);
    public static final int BUFFER = 50;
    static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    public enum Level { INFO, SUCCESS, WARNING }

    // FRONT_DESK = ADMIN + STAFF, HOUSEKEEPING = HOUSEKEEPER
    public enum Audience { FRONT_DESK, HOUSEKEEPING }

    public record Notice(long id, String kind, Level level, String title, String message, String url, LocalDateTime time,
                         Set<Audience> audiences, Long targetUserId) {

        public boolean visibleTo(Set<Audience> viewer, Long userId) {
            return audiences.stream().anyMatch(viewer::contains) && (targetUserId == null || targetUserId.equals(userId));
        }
    }

    private record Subscriber(SseEmitter emitter, Set<Audience> audiences, Long userId) {
    }

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final Deque<Notice> recent = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();

    // Mac dinh: gui le tan
    public void notify(String kind, String title, String message, String url) {
        notify(kind, Level.INFO, title, message, url);
    }

    public void notify(String kind, Level level, String title, String message, String url) {
        notify(EnumSet.of(Audience.FRONT_DESK), null, kind, level, title, message, url);
    }

    public void notify(Set<Audience> audiences, Long targetUserId, String kind, Level level, String title, String message, String url) {
        Set<Audience> to = EnumSet.copyOf(audiences);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(to, targetUserId, kind, level, title, message, url);
                }
            });
        } else {
            publish(to, targetUserId, kind, level, title, message, url);
        }
    }

    Notice publish(Set<Audience> audiences, Long targetUserId, String kind, Level level, String title, String message, String url) {
        Notice notice = new Notice(sequence.incrementAndGet(), kind, level, title, message, url, LocalDateTime.now(),
                audiences, targetUserId);
        synchronized (recent) {
            recent.addLast(notice);
            while (recent.size() > BUFFER) {
                recent.removeFirst();
            }
        }
        for (Subscriber s : subscribers) {
            if (notice.visibleTo(s.audiences(), s.userId())) {
                send(s, notice);
            }
        }
        return notice;
    }

    // Tat ca thong bao co id > afterId (khong loc nguoi nhan - dung trong test / thong ke)
    public List<Notice> recent(long afterId) {
        synchronized (recent) {
            return recent.stream().filter(n -> n.id() > afterId).toList();
        }
    }

    // Thong bao 1 nguoi xem duoc (ket noi lai / trang du phong)
    public List<Notice> recent(long afterId, Set<Audience> viewer, Long userId) {
        return recent(afterId).stream().filter(n -> n.visibleTo(viewer, userId)).toList();
    }

    public long lastId() {
        return sequence.get();
    }

    public int connections() {
        return subscribers.size();
    }

    // Ket noi cua le tan (khong rieng ai) - giu cho cac cho goi cu
    public SseEmitter subscribe(Long lastEventId) {
        return subscribe(lastEventId, EnumSet.of(Audience.FRONT_DESK), null);
    }

    // lastEventId: trinh duyet tu gui khi ket noi lai -> gui bu thong bao bi lo
    public SseEmitter subscribe(Long lastEventId, Set<Audience> audiences, Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Subscriber sub = new Subscriber(emitter, EnumSet.copyOf(audiences), userId);
        subscribers.add(sub);
        emitter.onCompletion(() -> subscribers.remove(sub));
        emitter.onTimeout(() -> {
            subscribers.remove(sub);
            emitter.complete();
        });
        emitter.onError(ex -> subscribers.remove(sub));
        try {
            // retry: trinh duyet tu ket noi lai sau 5s; id hien tai de lan sau chi nhan thong bao moi
            emitter.send(SseEmitter.event().name("hello").id(String.valueOf(lastId())).reconnectTime(5000).data("ok"));
        } catch (IOException | IllegalStateException ex) {
            subscribers.remove(sub);
        }
        if (lastEventId != null) {
            for (Notice n : recent(lastEventId, sub.audiences(), userId)) {
                send(sub, n);
            }
        }
        return emitter;
    }

    // Giu ket noi qua proxy / load balancer (dong ket noi im lang sau 30-60s)
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        for (Subscriber s : subscribers) {
            try {
                s.emitter().send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                subscribers.remove(s);
            }
        }
    }

    private void send(Subscriber s, Notice notice) {
        try {
            s.emitter().send(SseEmitter.event().name("staff").id(String.valueOf(notice.id())).data(notice, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            log.debug("Bo ket noi thong bao da dong: {}", ex.getMessage());
            subscribers.remove(s);
        }
    }

    // Chi de test
    List<SseEmitter> emittersSnapshot() {
        return new ArrayList<>(subscribers.stream().map(Subscriber::emitter).toList());
    }
}
