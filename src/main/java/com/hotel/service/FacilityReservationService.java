package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.FacilityReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Dat lich spa / ban nha hang kem don phong
@Service
public class FacilityReservationService {

    // Spa co 3 giuong: moi khung gio toi da 3 luot dat
    public static final int SPA_BEDS = 3;
    public static final int SPA_MAX_PEOPLE = 2;
    // Nha hang: moi khung gio toi da 40 khach
    public static final int RESTAURANT_SEATS = 40;
    public static final int RESTAURANT_MAX_PEOPLE = 20;
    // Dat / huy truoc gio hen it nhat 2 tieng
    public static final int LEAD_HOURS = 2;
    public static final List<LocalTime> SPA_SLOTS = slots(LocalTime.of(9, 0), LocalTime.of(19, 0), 60);
    public static final List<LocalTime> RESTAURANT_SLOTS;

    static {
        List<LocalTime> list = new ArrayList<>(slots(LocalTime.of(11, 0), LocalTime.of(13, 30), 30));
        list.addAll(slots(LocalTime.of(18, 0), LocalTime.of(21, 0), 30));
        RESTAURANT_SLOTS = List.copyOf(list);
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FacilityReservationRepository repository;
    private final BookingChargeRepository chargeRepository;
    private final ServiceOrderService serviceOrderService;

    public FacilityReservationService(FacilityReservationRepository repository, BookingChargeRepository chargeRepository,
                                      ServiceOrderService serviceOrderService) {
        this.repository = repository;
        this.chargeRepository = chargeRepository;
        this.serviceOrderService = serviceOrderService;
    }

    private static List<LocalTime> slots(LocalTime from, LocalTime to, int stepMinutes) {
        List<LocalTime> list = new ArrayList<>();
        for (LocalTime t = from; !t.isAfter(to); t = t.plusMinutes(stepMinutes)) {
            list.add(t);
        }
        return list;
    }

    public List<FacilityReservation> ofBooking(Long bookingId) {
        return repository.findByBookingIdOrderByReservedDateAscReservedTimeAsc(bookingId);
    }

    public List<FacilityReservation> forStaff(LocalDate from, ServiceStatus status) {
        return repository.findForStaff(from, status);
    }

    public long countPending() {
        return repository.countByStatus(ServiceStatus.PENDING);
    }

    public boolean canReserve(Booking booking) {
        return serviceOrderService.canOrderServices(booking);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FacilityReservation reserve(Booking booking, FacilityReservation.Kind kind, SpaTreatment treatment,
                                       LocalDate date, LocalTime time, Integer people, String note) {
        if (!canReserve(booking)) {
            throw BusinessException.of("err.svc.bookingClosed");
        }
        if (kind == null || date == null || time == null || people == null || (kind == FacilityReservation.Kind.SPA && treatment == null)) {
            throw BusinessException.of("err.svc.required");
        }
        if (date.isBefore(booking.getCheckInDate()) || date.isAfter(booking.getCheckOutDate())) {
            throw BusinessException.of("err.fac.date", booking.getCheckInDate().format(DAY), booking.getCheckOutDate().format(DAY));
        }
        boolean spa = kind == FacilityReservation.Kind.SPA;
        if (!(spa ? SPA_SLOTS : RESTAURANT_SLOTS).contains(time)) {
            throw BusinessException.of("err.fac.time");
        }
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now().plusHours(LEAD_HOURS))) {
            throw BusinessException.of("err.fac.tooSoon", LEAD_HOURS);
        }
        int maxPeople = spa ? SPA_MAX_PEOPLE : RESTAURANT_MAX_PEOPLE;
        if (people < 1 || people > maxPeople) {
            throw BusinessException.of("err.fac.people", maxPeople);
        }
        if (spa && repository.countActive(kind, date, time) >= SPA_BEDS) {
            throw BusinessException.of("err.fac.spaFull", time.toString(), date.format(DAY));
        }
        if (!spa && repository.sumPeople(kind, date, time) + people > RESTAURANT_SEATS) {
            throw BusinessException.of("err.fac.restaurantFull", time.toString(), date.format(DAY));
        }
        String text = note == null || note.isBlank() ? null : note.trim();
        if (text != null && text.length() > 300) {
            throw BusinessException.of("err.svc.note");
        }
        return repository.save(FacilityReservation.builder().booking(booking).kind(kind).treatment(spa ? treatment : null)
                .reservedDate(date).reservedTime(time).people(people)
                .totalPrice(spa ? treatment.getPrice().multiply(BigDecimal.valueOf(people)) : null)
                .note(text).status(ServiceStatus.PENDING).build());
    }

    // Le tan xac nhan: spa -> tien vao phu phi don phong
    @Transactional
    public FacilityReservation confirm(Long id) {
        FacilityReservation r = find(id);
        if (r.getStatus() != ServiceStatus.PENDING) {
            throw new BusinessException("Chỉ xác nhận được lịch đang chờ xác nhận");
        }
        if (!canReserve(r.getBooking())) {
            throw new BusinessException("Đơn phòng đã hủy hoặc đã trả phòng");
        }
        if (r.isSpa()) {
            String label = "Spa " + r.getTreatment().getName() + " - " + r.getReservedDate().format(DateTimeFormatter.ofPattern("dd/MM"))
                    + " " + r.getReservedTime() + " - " + r.getPeople() + " người";
            r.setCharge(chargeRepository.save(BookingCharge.builder().booking(r.getBooking()).type(ChargeType.SPA)
                    .description(label.length() > 200 ? label.substring(0, 200) : label).amount(r.getTotalPrice()).build()));
        }
        r.setStatus(ServiceStatus.CONFIRMED);
        return repository.save(r);
    }

    @Transactional
    public void cancelByStaff(Long id) {
        FacilityReservation r = find(id);
        requireNotSettled(r.getBooking());
        cancel(r);
    }

    @Transactional
    public void cancelByGuest(Booking booking, Long id) {
        FacilityReservation r = find(id);
        if (!r.getBooking().getId().equals(booking.getId())) {
            throw BusinessException.of("err.svc.notFound");
        }
        requireNotSettled(booking);
        if (r.getStatus() != ServiceStatus.CANCELLED && r.getStartAt().isBefore(LocalDateTime.now().plusHours(LEAD_HOURS))) {
            throw BusinessException.of("err.fac.cancelTooLate", LEAD_HOURS);
        }
        cancel(r);
    }

    private void cancel(FacilityReservation r) {
        if (r.getStatus() == ServiceStatus.CANCELLED) {
            return;
        }
        if (r.getCharge() != null) {
            chargeRepository.delete(r.getCharge());
            r.setCharge(null);
        }
        r.setStatus(ServiceStatus.CANCELLED);
        repository.save(r);
    }

    private static void requireNotSettled(Booking booking) {
        if (booking.getStatus() == BookingStatus.CHECKED_OUT) {
            throw BusinessException.of("err.svc.settled");
        }
    }

    private FacilityReservation find(Long id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.of("err.svc.notFound"));
    }
}
