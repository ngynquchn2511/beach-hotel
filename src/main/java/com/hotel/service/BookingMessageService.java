package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingMessage;
import com.hotel.entity.BookingStatus;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Nhan tin khach <-> le tan theo don: khach gui -> bao tuc thoi le tan; le tan tra loi -> email cho khach kem link don
@Service
public class BookingMessageService {

    public static final int BODY_MAX = 1000;
    // Chong spam: toi da 10 tin / gio / don tu phia khach
    public static final int GUEST_MAX_PER_HOUR = 10;
    // Van nhan tin duoc den 7 ngay sau tra phong (hoi do that lac, hoa don...)
    public static final int DAYS_AFTER_CHECKOUT = 7;

    private final BookingMessageRepository repository;
    private final StaffNotificationService notifier;
    private final EmailService emailService;

    public BookingMessageService(BookingMessageRepository repository, StaffNotificationService notifier, EmailService emailService) {
        this.repository = repository;
        this.notifier = notifier;
        this.emailService = emailService;
    }

    public boolean canGuestMessage(Booking booking) {
        return booking.getStatus() != BookingStatus.CANCELLED
                && !LocalDate.now().isAfter(booking.getCheckOutDate().plusDays(DAYS_AFTER_CHECKOUT));
    }

    public List<BookingMessage> thread(Long bookingId) {
        return repository.findByBookingIdOrderByCreatedAtAscIdAsc(bookingId);
    }

    @Transactional
    public BookingMessage guestSend(Booking booking, String text) {
        if (!canGuestMessage(booking)) {
            throw BusinessException.of("msg.error.closed");
        }
        String body = clean(text);
        if (repository.countByBookingIdAndFromGuestTrueAndCreatedAtAfter(booking.getId(), LocalDateTime.now().minusHours(1)) >= GUEST_MAX_PER_HOUR) {
            throw BusinessException.of("msg.error.tooMany");
        }
        BookingMessage m = repository.save(BookingMessage.builder().booking(booking).fromGuest(true).body(body).build());
        notifier.notify("message", StaffNotificationService.Level.INFO,
                "Tin nhắn từ " + booking.getGuestName() + " · đơn #" + booking.getId(),
                (booking.getRoom() != null ? "Phòng " + booking.getRoom().getRoomNumber() + ": " : "")
                        + (body.length() > 100 ? body.substring(0, 100) + "…" : body),
                "/admin/bookings/" + booking.getId() + "#tin-nhan");
        return m;
    }

    @Transactional
    public BookingMessage staffReply(Booking booking, String text, String staffName) {
        String body = clean(text);
        markReadByStaff(booking.getId());
        BookingMessage m = repository.save(BookingMessage.builder().booking(booking).fromGuest(false).body(body)
                .staffName(staffName).build());
        emailService.sendStaffMessage(booking, body);
        return m;
    }

    // Le tan mo don -> tin cua khach coi la da doc
    @Transactional
    public void markReadByStaff(Long bookingId) {
        markRead(bookingId, true);
    }

    // Khach mo trang don -> tin cua le tan coi la da doc
    @Transactional
    public void markReadByGuest(Long bookingId) {
        markRead(bookingId, false);
    }

    private void markRead(Long bookingId, boolean fromGuest) {
        LocalDateTime now = LocalDateTime.now();
        for (BookingMessage m : repository.findUnread(bookingId, fromGuest)) {
            m.setReadAt(now);
            repository.save(m);
        }
    }

    public long unreadForStaff() {
        return repository.countByFromGuestTrueAndReadAtIsNull();
    }

    public record Conversation(BookingMessage latest, long unread) {
    }

    // Hop tin nhan: moi don 1 dong, tin moi nhat len dau
    public List<Conversation> inbox() {
        Map<Long, Long> unread = new HashMap<>();
        for (Object[] row : repository.countUnreadPerBooking()) {
            unread.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return repository.findLatestPerBooking().stream()
                .map(m -> new Conversation(m, unread.getOrDefault(m.getBooking().getId(), 0L)))
                .toList();
    }

    private static String clean(String text) {
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) {
            throw BusinessException.of("msg.error.empty");
        }
        if (body.length() > BODY_MAX) {
            throw BusinessException.of("msg.error.tooLong", BODY_MAX);
        }
        return body;
    }
}
