package com.hotel.service;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Chong spam tro ly "Song": gioi han so tin moi phut / moi gio cho moi tai khoan, chan gui lai y het trong 30 giay,
// nhan dien cau vo nghia (khong du chu, 1 ky tu lap lai) de khong ton tien goi AI. Luu trong bo nho (1 may chu)
@Service
public class ChatGuardService {

    static final long DUPLICATE_WINDOW_MS = 30_000;

    public enum Verdict { OK, RATE_LIMIT, DUPLICATE }

    private record Last(String text, long at) {
    }

    private final Map<Long, Deque<Long>> times = new ConcurrentHashMap<>();
    private final Map<Long, Last> last = new ConcurrentHashMap<>();

    // Kiem tra truoc khi Song xu ly. userId null (chua dang nhap) -> khong gioi han o day (API da bat dang nhap)
    public Verdict check(Long userId, String message, int perMinute, int perHour) {
        return check(userId, message, perMinute, perHour, System.currentTimeMillis());
    }

    Verdict check(Long userId, String message, int perMinute, int perHour, long now) {
        if (userId == null) {
            return Verdict.OK;
        }
        String norm = ChatRoomQuery.normalize(message);
        Last prev = last.get(userId);
        if (prev != null && !norm.isEmpty() && prev.text().equals(norm) && now - prev.at() < DUPLICATE_WINDOW_MS) {
            return Verdict.DUPLICATE;
        }
        Deque<Long> q = times.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 3_600_000L) {
                q.pollFirst();
            }
            long lastMinute = q.stream().filter(t -> now - t <= 60_000L).count();
            if ((perMinute > 0 && lastMinute >= perMinute) || (perHour > 0 && q.size() >= perHour)) {
                return Verdict.RATE_LIMIT;
            }
            q.addLast(now);
        }
        last.put(userId, new Last(norm, now));
        return Verdict.OK;
    }

    // Cau dang hoi AI: it nhat 3 chu cai, khong phai 1 ky tu / 1 tu lap lai lien tuc
    public static boolean worthAsking(String message) {
        if (message == null) {
            return false;
        }
        String t = message.trim();
        if (t.length() < 4 || t.length() > 300) {
            return false;
        }
        long letters = t.codePoints().filter(Character::isLetter).count();
        if (letters < 3) {
            return false;
        }
        String norm = ChatRoomQuery.normalize(t);
        if (norm.matches("(.)\\1{5,}.*") || norm.matches(".*(.{1,3})\\1{6,}.*")) {
            return false;
        }
        return norm.chars().distinct().count() >= 4;
    }

    void reset() {
        times.clear();
        last.clear();
    }
}
