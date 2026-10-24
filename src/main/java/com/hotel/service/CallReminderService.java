package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.CallReminder;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.CallReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Goi dien nhac khach: le tan xem danh sach khach can goi (sap den nhan phong / sap het han chuyen coc),
// bam so de goi, ghi lai ket qua. Khach chua nghe may / hen goi lai van nam trong danh sach; ket qua "xong" thi roi khoi danh sach
@Service
public class CallReminderService {

    // Goi nhac khach den trong N ngay toi (tinh tu ngay mai)
    public static final int ARRIVAL_DAYS = 2;
    // Goi nhac don co han coc trong N gio toi
    public static final int DEPOSIT_HOURS = 48;
    public static final int NOTE_MAX = 300;

    private final CallReminderRepository repository;
    private final BookingRepository bookingRepository;

    public CallReminderService(CallReminderRepository repository, BookingRepository bookingRepository) {
        this.repository = repository;
        this.bookingRepository = bookingRepository;
    }

    // Mot dong trong danh sach can goi: so lan da goi + lan goi gan nhat (null neu chua goi)
    public record CallTask(Booking booking, CallReminder.Purpose purpose, String phone, int attempts, CallReminder last) {
        public boolean hasPhone() {
            return phone != null && !phone.isBlank();
        }
    }

    @Transactional(readOnly = true)
    public List<CallTask> due(LocalDateTime now) {
        List<Booking> arrivals = bookingRepository.findArrivalsToCall(now.toLocalDate().plusDays(1), now.toLocalDate().plusDays(ARRIVAL_DAYS));
        List<Booking> deposits = bookingRepository.findDepositsToCall(now, now.plusHours(DEPOSIT_HOURS));
        List<Long> ids = new ArrayList<>();
        arrivals.forEach(b -> ids.add(b.getId()));
        deposits.forEach(b -> ids.add(b.getId()));
        Map<String, List<CallReminder>> calls = ids.isEmpty() ? Map.of()
                : repository.findByBookingIdInOrderByCalledAtAsc(ids).stream()
                .collect(Collectors.groupingBy(c -> key(c.getBooking().getId(), c.getPurpose())));

        List<CallTask> tasks = new ArrayList<>();
        deposits.forEach(b -> addTask(tasks, b, CallReminder.Purpose.DEPOSIT, calls));
        arrivals.forEach(b -> addTask(tasks, b, CallReminder.Purpose.ARRIVAL, calls));
        // Chua goi lan nao len truoc; trong moi nhom giu thu tu han coc / ngay den
        tasks.sort(Comparator.comparing((CallTask t) -> t.attempts() > 0));
        return tasks;
    }

    public long countDue() {
        return due(LocalDateTime.now()).size();
    }

    private static void addTask(List<CallTask> tasks, Booking b, CallReminder.Purpose purpose, Map<String, List<CallReminder>> calls) {
        List<CallReminder> done = calls.getOrDefault(key(b.getId(), purpose), List.of());
        if (done.stream().anyMatch(c -> c.getResult().isDone())) {
            return;
        }
        tasks.add(new CallTask(b, purpose, phoneOf(b), done.size(), done.isEmpty() ? null : done.get(done.size() - 1)));
    }

    // So tren don; don khong ghi so thi lay so trong tai khoan khach
    static String phoneOf(Booking b) {
        if (b.getGuestPhone() != null && !b.getGuestPhone().isBlank()) {
            return b.getGuestPhone().trim();
        }
        return b.getCustomer() != null ? b.getCustomer().getPhoneNumber() : null;
    }

    private static String key(Long bookingId, CallReminder.Purpose purpose) {
        return bookingId + ":" + purpose;
    }

    @Transactional
    public CallReminder record(Long bookingId, CallReminder.Purpose purpose, CallReminder.Result result, String note, String staffName) {
        if (purpose == null || result == null) {
            throw new BusinessException("Vui lòng chọn kết quả cuộc gọi");
        }
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn #" + bookingId));
        String text = note == null ? null : note.trim();
        if (text != null && text.length() > NOTE_MAX) {
            throw new BusinessException("Ghi chú tối đa " + NOTE_MAX + " ký tự");
        }
        return repository.save(CallReminder.builder().booking(booking).purpose(purpose).result(result)
                .note(text == null || text.isEmpty() ? null : text).staffName(staffName).build());
    }

    public List<CallReminder> history() {
        return repository.findAllForHistory();
    }

    public List<CallReminder> forBooking(Long bookingId) {
        return repository.findByBookingIdOrderByCalledAtDesc(bookingId);
    }
}
