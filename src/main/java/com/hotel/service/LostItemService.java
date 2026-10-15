package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.LostItem;
import com.hotel.entity.Room;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.LostItemRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

// So do that lac: ghi nhan -> (tu gan don khach vua tra phong do) -> email bao khach -> tra khach / thanh ly
@Service
public class LostItemService {

    // Tu gan voi don da tra phong trong vong N ngay
    public static final int LINK_DAYS = 7;
    // Cat qua N ngay -> nhac thanh ly
    public static final int KEEP_DAYS = 90;

    private final LostItemRepository repository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final EmailService emailService;

    public LostItemService(LostItemRepository repository, RoomRepository roomRepository,
                           BookingRepository bookingRepository, EmailService emailService) {
        this.repository = repository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
    }

    public List<LostItem> all() {
        return repository.findAllForList();
    }

    public long countStored() {
        return repository.countByStatus(LostItem.Status.STORED);
    }

    public static boolean overdue(LostItem item) {
        return item.isStored() && item.getFoundAt().isBefore(LocalDateTime.now().minusDays(KEEP_DAYS));
    }

    // bookingId null -> tu tim don tra phong gan nhat cua phong do trong LINK_DAYS ngay
    @Transactional
    public LostItem record(Long roomId, Long bookingId, String description, String storageLocation, String foundBy) {
        String text = description == null ? "" : description.trim();
        if (text.isEmpty() || text.length() > 200) {
            throw new BusinessException("Vui lòng mô tả đồ vật (tối đa 200 ký tự)");
        }
        Room room = roomId != null ? roomRepository.findById(roomId).orElse(null) : null;
        Booking booking = null;
        if (bookingId != null) {
            booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy đơn #" + bookingId));
            if (room == null) {
                room = booking.getRoom();
            }
        } else if (room != null) {
            booking = lastGuestOf(room);
        }
        String location = storageLocation == null || storageLocation.isBlank() ? null : storageLocation.trim();
        return repository.save(LostItem.builder().room(room).booking(booking).description(text)
                .storageLocation(location != null && location.length() > 100 ? location.substring(0, 100) : location)
                .foundBy(foundBy).status(LostItem.Status.STORED).build());
    }

    // Khach o gan nhat (dang o hoac vua tra phong trong LINK_DAYS ngay)
    private Booking lastGuestOf(Room room) {
        LocalDate since = LocalDate.now().minusDays(LINK_DAYS);
        return bookingRepository.findByRoomIdAndStatusIn(room.getId(), List.of(BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT))
                .stream()
                .filter(b -> !b.getCheckOutDate().isBefore(since) && !b.getCheckInDate().isAfter(LocalDate.now()))
                .max(Comparator.comparing(Booking::getCheckOutDate))
                .orElse(null);
    }

    @Transactional
    public LostItem notifyGuest(Long itemId) {
        LostItem item = requireStored(itemId);
        if (item.getBooking() == null) {
            throw new BusinessException("Đồ này chưa gắn với đơn của khách nào - không biết email để báo");
        }
        if (!emailService.sendLostItemFound(item)) {
            throw new BusinessException("Không gửi được email (đơn không có email khách hoặc chưa cấu hình mail)");
        }
        item.setGuestNotifiedAt(LocalDateTime.now());
        return repository.save(item);
    }

    @Transactional
    public LostItem close(Long itemId, LostItem.Status status, String note) {
        if (status != LostItem.Status.RETURNED && status != LostItem.Status.DISPOSED) {
            throw new BusinessException("Trạng thái không hợp lệ");
        }
        LostItem item = requireStored(itemId);
        item.setStatus(status);
        item.setClosedAt(LocalDateTime.now());
        String text = note == null ? null : note.trim();
        item.setCloseNote(text == null || text.isEmpty() ? null : text.substring(0, Math.min(200, text.length())));
        return repository.save(item);
    }

    private LostItem requireStored(Long itemId) {
        LostItem item = repository.findById(itemId).orElseThrow(() -> new BusinessException("Không tìm thấy đồ thất lạc"));
        if (!item.isStored()) {
            throw new BusinessException("Đồ này đã được xử lý (" + item.getStatus().getVietnameseLabel() + ")");
        }
        return item;
    }
}
