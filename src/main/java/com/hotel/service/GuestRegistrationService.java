package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingGuest;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.Gender;
import com.hotel.entity.IdDocumentType;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingGuestRepository;
import com.hotel.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// Khai bao luu tru: le tan nhap giay to tung khach cua don (truoc/khi check-in),
// tong hop danh sach khach luu tru theo ngay de khai bao voi cong an (ung dung ASM / cong dich vu cong)
@Service
public class GuestRegistrationService {

    // Chi nhap/sua danh sach khach khi don da xac nhan hoac khach dang o
    private static final Set<BookingStatus> EDITABLE_STATUSES = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);
    // Khach thuc su da den o
    private static final Set<BookingStatus> STAYED_STATUSES = EnumSet.of(BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT);

    private final BookingGuestRepository guestRepository;
    private final BookingRepository bookingRepository;

    public GuestRegistrationService(BookingGuestRepository guestRepository, BookingRepository bookingRepository) {
        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<BookingGuest> findByBooking(Long bookingId) {
        return guestRepository.findByBookingIdOrderByIdAsc(bookingId);
    }

    public boolean canEdit(Booking booking) {
        return EDITABLE_STATUSES.contains(booking.getStatus());
    }

    @Transactional
    public BookingGuest addGuest(Long bookingId, String fullName, LocalDate dateOfBirth, Gender gender,
                                 IdDocumentType idType, String idNumber, String nationality, String address) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn đặt phòng"));
        if (!canEdit(booking)) {
            throw new BusinessException("Chỉ khai báo khách lưu trú cho đơn đã xác nhận hoặc đang ở");
        }

        String name = trim(fullName);
        if (name == null || name.length() > 100) {
            throw new BusinessException("Vui lòng nhập họ tên khách (tối đa 100 ký tự)");
        }
        if (dateOfBirth == null || !dateOfBirth.isBefore(LocalDate.now()) || dateOfBirth.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new BusinessException("Ngày sinh không hợp lệ");
        }
        if (gender == null) {
            throw new BusinessException("Vui lòng chọn giới tính");
        }
        if (idType == null) {
            throw new BusinessException("Vui lòng chọn loại giấy tờ");
        }
        String number = normalizeIdNumber(idType, idNumber);
        String country = trim(nationality);
        if (country == null || country.length() > 60) {
            throw new BusinessException("Vui lòng nhập quốc tịch");
        }
        String addr = trim(address);
        if (addr != null && addr.length() > 255) {
            throw new BusinessException("Địa chỉ thường trú tối đa 255 ký tự");
        }

        // Cung 1 giay to khong nhap 2 lan trong cung don
        boolean duplicated = findByBooking(bookingId).stream()
                .anyMatch(g -> g.getIdType() == idType && g.getIdNumber().equalsIgnoreCase(number));
        if (duplicated) {
            throw new BusinessException("Giấy tờ số " + number + " đã được khai báo trong đơn này");
        }

        return guestRepository.save(BookingGuest.builder()
                .booking(booking)
                .fullName(name)
                .dateOfBirth(dateOfBirth)
                .gender(gender)
                .idType(idType)
                .idNumber(number)
                .nationality(country)
                .address(addr)
                .build());
    }

    @Transactional
    public void removeGuest(Long bookingId, Long guestId) {
        BookingGuest guest = guestRepository.findById(guestId)
                .filter(g -> g.getBooking().getId().equals(bookingId))
                .orElseThrow(() -> new BusinessException("Không tìm thấy khách lưu trú"));
        if (!canEdit(guest.getBooking())) {
            throw new BusinessException("Không thể sửa danh sách khách của đơn ở trạng thái hiện tại");
        }
        guestRepository.delete(guest);
    }

    // Khach da den o (da/dang check-in) co thoi gian o giao voi [from, to]
    public List<BookingGuest> findStaying(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessException("Khoảng ngày không hợp lệ");
        }
        return guestRepository.findStaying(from, to, STAYED_STATUSES);
    }

    // CCCD: dung 12 chu so. Ho chieu: 6-9 ky tu chu/so. Giay khai sinh: so/quyen so tu do, toi da 30 ky tu
    private static String normalizeIdNumber(IdDocumentType idType, String idNumber) {
        String value = trim(idNumber);
        if (value == null) {
            throw new BusinessException("Vui lòng nhập số giấy tờ");
        }
        value = value.replace(" ", "").toUpperCase();
        switch (idType) {
            case CCCD -> {
                if (!value.matches("\\d{12}")) {
                    throw new BusinessException("Số căn cước công dân phải gồm đúng 12 chữ số");
                }
            }
            case PASSPORT -> {
                if (!value.matches("[A-Z0-9]{6,9}")) {
                    throw new BusinessException("Số hộ chiếu phải gồm 6–9 ký tự chữ hoặc số");
                }
            }
            case BIRTH_CERTIFICATE -> {
                if (value.length() > 30) {
                    throw new BusinessException("Số giấy khai sinh tối đa 30 ký tự");
                }
            }
        }
        return value;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
