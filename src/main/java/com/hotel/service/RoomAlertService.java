package com.hotel.service;

import com.hotel.entity.Room;
import com.hotel.entity.RoomAlert;
import com.hotel.exception.BusinessException;
import com.hotel.repository.RoomAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

// "Bao toi khi phong trong lai": khach de lai email cho khoang ngay dang kin; RoomAlertScheduler dinh ky kiem tra,
// phong trong lai (don huy, doi ngay, het bao tri...) thi gui email 1 lan. Khong can moc vao tung cho huy don.
@Service
public class RoomAlertService {

    public static final int MAX_PENDING_PER_EMAIL = 5;
    public static final int MAX_NIGHTS = 30;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final RoomAlertRepository alertRepository;
    private final RoomService roomService;
    private final EmailService emailService;

    public RoomAlertService(RoomAlertRepository alertRepository, RoomService roomService, EmailService emailService) {
        this.alertRepository = alertRepository;
        this.roomService = roomService;
        this.emailService = emailService;
    }

    @Transactional
    public void subscribe(Long roomId, String email, LocalDate checkIn, LocalDate checkOut, Integer guests, String language) {
        String mail = email == null ? "" : email.trim();
        if (mail.length() > 100 || !EMAIL.matcher(mail).matches()) {
            throw BusinessException.of("alert.error.email");
        }
        LocalDate today = LocalDate.now();
        if (checkIn == null || checkOut == null || checkIn.isBefore(today) || !checkOut.isAfter(checkIn)
                || ChronoUnit.DAYS.between(checkIn, checkOut) > MAX_NIGHTS) {
            throw BusinessException.of("alert.error.dates", MAX_NIGHTS);
        }
        Room room = roomService.findById(roomId);
        if (roomService.isAvailableForDates(roomId, checkIn, checkOut)) {
            throw BusinessException.of("alert.error.available");
        }
        // Dang ky lai dung yeu cau cu -> coi nhu thanh cong, khong tao trung
        if (alertRepository.existsPending(roomId, mail, checkIn, checkOut)) {
            return;
        }
        if (alertRepository.countPendingForEmail(mail, today) >= MAX_PENDING_PER_EMAIL) {
            throw BusinessException.of("alert.error.tooMany", MAX_PENDING_PER_EMAIL);
        }
        alertRepository.save(RoomAlert.builder().room(room).email(mail).checkIn(checkIn).checkOut(checkOut)
                .guests(guests != null && guests > 0 ? guests : null)
                .language("en".equals(language) ? "en" : "vi")
                .build());
    }

    // Bo yeu cau da qua ngay nhan phong; gui email cho yeu cau co phong trong lai. Tra ve so email da gui.
    // Chua cau hinh mail -> giu yeu cau lai, gui khi co cau hinh (van con han)
    @Transactional
    public int notifyAvailable(LocalDate today) {
        alertRepository.deleteExpired(today);
        int sent = 0;
        for (RoomAlert alert : alertRepository.findPending(today)) {
            if (roomService.isAvailableForDates(alert.getRoom().getId(), alert.getCheckIn(), alert.getCheckOut())
                    && emailService.sendRoomAvailable(alert)) {
                alert.setNotifiedAt(LocalDateTime.now());
                sent++;
            }
        }
        return sent;
    }
}
