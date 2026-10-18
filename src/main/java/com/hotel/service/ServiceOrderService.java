package com.hotel.service;

import com.hotel.entity.AirportTransfer;
import com.hotel.entity.Airport;
import com.hotel.entity.Booking;
import com.hotel.entity.BookingCharge;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.ChargeType;
import com.hotel.entity.DivingTour;
import com.hotel.entity.ServiceStatus;
import com.hotel.entity.TourBooking;
import com.hotel.entity.TransferDirection;
import com.hotel.entity.VehicleType;
import com.hotel.exception.BusinessException;
import com.hotel.repository.AirportTransferRepository;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.DivingTourRepository;
import com.hotel.repository.TourBookingRepository;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

// Dich vu them gan voi don dat phong: dat tour lan bien va xe dua don san bay.
// Khach dat -> PENDING (chua tinh tien). Le tan xac nhan -> CONFIRMED + tao phu phi (BookingCharge) cho don phong,
// nho vay tien dich vu tu dong vao hoa don, so tien thu khi check-out va bao cao doanh thu. Huy -> xoa phu phi do.
@Service
public class ServiceOrderService {

    private com.hotel.service.StaffNotificationService staffNotifier;

    @org.springframework.beans.factory.annotation.Autowired
    void setStaffNotifier(com.hotel.service.StaffNotificationService staffNotifier) {
        this.staffNotifier = staffNotifier;
    }

    private void notifyStaff(String kind, com.hotel.service.StaffNotificationService.Level level, String title, String message, String url) {
        if (staffNotifier != null) {
            staffNotifier.notify(kind, level, title, message, url);
        }
    }

    // Khach phai dat truoc gio bat dau bao nhieu tieng (de resort sap xep HLV / tai xe)
    public static final int TOUR_LEAD_HOURS = 12;
    public static final int TRANSFER_LEAD_HOURS = 6;
    // Khach tu huy dich vu da xac nhan phai truoc gio bat dau it nhat bay nhieu tieng
    public static final int CUSTOMER_CANCEL_HOURS = 24;

    private static final Pattern FLIGHT_NUMBER = Pattern.compile("^[A-Z0-9]{2}\\s?\\d{1,4}[A-Z]?$");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final BookingService bookingService;
    private final DivingTourRepository tourRepository;
    private final TourBookingRepository tourBookingRepository;
    private final AirportTransferRepository transferRepository;
    private final BookingChargeRepository chargeRepository;
    private final TransferRateService transferRateService;

    public ServiceOrderService(BookingService bookingService, DivingTourRepository tourRepository,
                               TourBookingRepository tourBookingRepository, AirportTransferRepository transferRepository,
                               BookingChargeRepository chargeRepository, TransferRateService transferRateService) {
        this.bookingService = bookingService;
        this.tourRepository = tourRepository;
        this.tourBookingRepository = tourBookingRepository;
        this.transferRepository = transferRepository;
        this.chargeRepository = chargeRepository;
        this.transferRateService = transferRateService;
    }

    // Don con dat them dich vu duoc: chua huy, chua tra phong
    public boolean canOrderServices(Booking booking) {
        return booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.CHECKED_IN;
    }

    public List<TourBooking> toursOf(Long bookingId) {
        return tourBookingRepository.findByBookingIdOrderByTourDateAscIdAsc(bookingId);
    }

    public List<AirportTransfer> transfersOf(Long bookingId) {
        return transferRepository.findByBookingIdOrderByPickupAtAsc(bookingId);
    }

    public long countPending() {
        return tourBookingRepository.countPending() + transferRepository.countPending();
    }

    public List<TourBooking> toursForStaff(LocalDate from, ServiceStatus status) {
        return tourBookingRepository.findForStaff(from, status);
    }

    public List<AirportTransfer> transfersForStaff(LocalDate from, ServiceStatus status) {
        return transferRepository.findForStaff(from.atStartOfDay(), status);
    }

    // ===================== Tour lan =====================

    // byStaff = le tan dat ho tai quay: khong ap thoi gian dat truoc toi thieu va xac nhan luon
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TourBooking bookTour(Booking booking, Long tourId, LocalDate tourDate, Integer participants,
                                String note, boolean byStaff) {
        requireOrderable(booking);
        if (tourId == null || tourDate == null || participants == null) {
            throw BusinessException.of("err.svc.required");
        }
        DivingTour tour = lockTour(tourId);
        if (!tour.isActive()) {
            throw BusinessException.of("err.svc.tourNotFound");
        }
        if (tourDate.isBefore(booking.getCheckInDate()) || tourDate.isAfter(booking.getCheckOutDate())) {
            throw BusinessException.of("err.svc.tourDate", booking.getCheckInDate().format(DAY),
                    booking.getCheckOutDate().format(DAY));
        }
        LocalDateTime startAt = LocalDateTime.of(tourDate, tour.getDepartureTime());
        requireLeadTime(startAt, byStaff ? 0 : TOUR_LEAD_HOURS, "err.svc.tourTooSoon");
        if (participants < 1 || participants > tour.getMaxParticipants()) {
            throw BusinessException.of("err.svc.participants", tour.getMaxParticipants());
        }
        long booked = tourBookingRepository.sumBookedParticipants(tourId, tourDate);
        long left = tour.getMaxParticipants() - booked;
        if (participants > left) {
            throw BusinessException.of("err.svc.tourFull", tour.getLocalizedName(), tourDate.format(DAY), Math.max(0, left));
        }
        BigDecimal unitPrice = tour.getPricePerPerson();
        TourBooking order = tourBookingRepository.save(TourBooking.builder()
                .booking(booking)
                .tour(tour)
                .tourDate(tourDate)
                .participants(participants)
                .unitPrice(unitPrice)
                .totalPrice(unitPrice.multiply(BigDecimal.valueOf(participants)))
                .note(cleanNote(note))
                .status(ServiceStatus.PENDING)
                .build());
        if (byStaff) {
            confirmTour(order.getId());
        } else {
            notifyStaff("service", com.hotel.service.StaffNotificationService.Level.INFO, "Đặt tour mới · đơn #" + booking.getId(),
                    participants + " khách · " + tourDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")), "/admin/service-orders");
        }
        return order;
    }

    @Transactional
    public void confirmTour(Long orderId) {
        TourBooking order = findTour(orderId);
        requireOrderable(order.getBooking());
        if (order.getStatus() != ServiceStatus.PENDING) {
            throw new BusinessException("Chỉ xác nhận được dịch vụ đang chờ xác nhận");
        }
        order.setCharge(createCharge(order.getBooking(), ChargeType.DIVING_TOUR,
                order.getTour().getName() + " - " + order.getTourDate().format(DateTimeFormatter.ofPattern("dd/MM"))
                        + " - " + order.getParticipants() + " người",
                order.getTotalPrice()));
        order.setStatus(ServiceStatus.CONFIRMED);
        tourBookingRepository.save(order);
    }

    @Transactional
    public void cancelTourByStaff(Long orderId) {
        TourBooking order = findTour(orderId);
        requireNotSettled(order.getBooking());
        cancelTour(order);
    }

    @Transactional
    public void cancelTourByCustomer(Booking booking, Long orderId) {
        TourBooking order = findTour(orderId);
        requireBelongs(order.getBooking(), booking);
        requireNotSettled(booking);
        requireCustomerCanCancel(order.getStatus(), order.getStartAt());
        cancelTour(order);
    }

    private void cancelTour(TourBooking order) {
        if (order.getStatus() == ServiceStatus.CANCELLED) {
            return;
        }
        removeCharge(order.getCharge());
        order.setCharge(null);
        order.setStatus(ServiceStatus.CANCELLED);
        tourBookingRepository.save(order);
    }

    // ===================== Dua don san bay =====================

    @Transactional
    public AirportTransfer bookTransfer(Booking booking, TransferDirection direction, Airport airport,
                                        VehicleType vehicle, LocalDate date, LocalTime time, String flightNumber,
                                        Integer passengers, String note, boolean byStaff) {
        requireOrderable(booking);
        if (direction == null || airport == null || vehicle == null || time == null || passengers == null) {
            throw BusinessException.of("err.svc.required");
        }
        // Don vao ngay nhan phong, tien vao ngay tra phong (form khong gui ngay -> lay theo chieu)
        LocalDate expected = direction == TransferDirection.PICKUP ? booking.getCheckInDate() : booking.getCheckOutDate();
        if (date == null) {
            date = expected;
        }
        if (!date.equals(expected)) {
            throw BusinessException.of(direction == TransferDirection.PICKUP
                    ? "err.svc.pickupDate" : "err.svc.dropoffDate", expected.format(DAY));
        }
        LocalDateTime pickupAt = LocalDateTime.of(date, time);
        requireLeadTime(pickupAt, byStaff ? 0 : TRANSFER_LEAD_HOURS, "err.svc.transferTooSoon");
        if (passengers < 1) {
            throw BusinessException.of("err.svc.required");
        }
        if (passengers > vehicle.getMaxPassengers()) {
            throw BusinessException.of("err.svc.passengers", vehicle.getMaxPassengers());
        }
        String flight = flightNumber == null ? "" : flightNumber.trim().toUpperCase();
        if (direction == TransferDirection.PICKUP && flight.isEmpty()) {
            throw BusinessException.of("err.svc.flightRequired");
        }
        if (!flight.isEmpty() && !FLIGHT_NUMBER.matcher(flight).matches()) {
            throw BusinessException.of("err.svc.flight");
        }
        if (transferRepository.existsByBookingIdAndDirectionAndStatusNot(booking.getId(), direction, ServiceStatus.CANCELLED)) {
            throw BusinessException.of(direction == TransferDirection.PICKUP ? "err.svc.dupPickup" : "err.svc.dupDropoff");
        }
        AirportTransfer order = transferRepository.save(AirportTransfer.builder()
                .booking(booking)
                .direction(direction)
                .airport(airport)
                .vehicleType(vehicle)
                .pickupAt(pickupAt)
                .flightNumber(flight.isEmpty() ? null : flight)
                .passengers(passengers)
                .price(transferRateService.priceOf(airport, vehicle))
                .note(cleanNote(note))
                .status(ServiceStatus.PENDING)
                .build());
        if (byStaff) {
            confirmTransfer(order.getId());
        } else {
            notifyStaff("service", com.hotel.service.StaffNotificationService.Level.INFO, "Đặt xe đưa đón · đơn #" + booking.getId(),
                    passengers + " khách · " + airport.getVietnameseLabel(), "/admin/service-orders");
        }
        return order;
    }

    @Transactional
    public void confirmTransfer(Long orderId) {
        AirportTransfer order = findTransfer(orderId);
        requireOrderable(order.getBooking());
        if (order.getStatus() != ServiceStatus.PENDING) {
            throw new BusinessException("Chỉ xác nhận được dịch vụ đang chờ xác nhận");
        }
        String label = (order.getDirection() == TransferDirection.PICKUP ? "Đón " : "Tiễn ")
                + order.getAirport().getVietnameseLabel() + " - " + order.getVehicleType().getVietnameseLabel()
                + " - " + order.getPickupAt().format(DAY_TIME);
        order.setCharge(createCharge(order.getBooking(), ChargeType.AIRPORT_TRANSFER, label, order.getPrice()));
        order.setStatus(ServiceStatus.CONFIRMED);
        transferRepository.save(order);
    }

    @Transactional
    public void cancelTransferByStaff(Long orderId) {
        AirportTransfer order = findTransfer(orderId);
        requireNotSettled(order.getBooking());
        cancelTransfer(order);
    }

    @Transactional
    public void cancelTransferByCustomer(Booking booking, Long orderId) {
        AirportTransfer order = findTransfer(orderId);
        requireBelongs(order.getBooking(), booking);
        requireNotSettled(booking);
        requireCustomerCanCancel(order.getStatus(), order.getPickupAt());
        cancelTransfer(order);
    }

    private void cancelTransfer(AirportTransfer order) {
        if (order.getStatus() == ServiceStatus.CANCELLED) {
            return;
        }
        removeCharge(order.getCharge());
        order.setCharge(null);
        order.setStatus(ServiceStatus.CANCELLED);
        transferRepository.save(order);
    }

    // ===================== Dung chung =====================

    private DivingTour lockTour(Long tourId) {
        try {
            return tourRepository.findByIdForUpdate(tourId)
                    .orElseThrow(() -> BusinessException.of("err.svc.tourNotFound"));
        } catch (PessimisticLockingFailureException | QueryTimeoutException ex) {
            throw BusinessException.of("err.svc.tourBusy");
        }
    }

    private TourBooking findTour(Long orderId) {
        return tourBookingRepository.findById(orderId).orElseThrow(() -> BusinessException.of("err.svc.notFound"));
    }

    private AirportTransfer findTransfer(Long orderId) {
        return transferRepository.findById(orderId).orElseThrow(() -> BusinessException.of("err.svc.notFound"));
    }

    private void requireOrderable(Booking booking) {
        if (!canOrderServices(booking)) {
            throw BusinessException.of("err.svc.bookingClosed");
        }
    }

    // Don da tra phong (da thanh toan) thi khong doi dich vu nua - tranh lech so tien da thu
    private void requireNotSettled(Booking booking) {
        if (booking.getStatus() == BookingStatus.CHECKED_OUT) {
            throw BusinessException.of("err.svc.settled");
        }
    }

    private void requireBelongs(Booking orderBooking, Booking booking) {
        if (!orderBooking.getId().equals(booking.getId())) {
            throw BusinessException.of("err.svc.notFound");
        }
    }

    private void requireLeadTime(LocalDateTime startAt, int hours, String errorCode) {
        if (!startAt.isAfter(LocalDateTime.now().plusHours(hours))) {
            throw hours == 0 ? BusinessException.of("err.svc.past") : BusinessException.of(errorCode, hours);
        }
    }

    // Khach tu huy: don cho xac nhan huy luc nao cung duoc (truoc gio bat dau);
    // don da xac nhan (resort da xep HLV/tai xe) phai huy truoc CUSTOMER_CANCEL_HOURS tieng
    private void requireCustomerCanCancel(ServiceStatus status, LocalDateTime startAt) {
        int hours = status == ServiceStatus.CONFIRMED ? CUSTOMER_CANCEL_HOURS : 0;
        if (status != ServiceStatus.CANCELLED && !startAt.isAfter(LocalDateTime.now().plusHours(hours))) {
            throw BusinessException.of("err.svc.cancelClosed", CUSTOMER_CANCEL_HOURS);
        }
    }

    private BookingCharge createCharge(Booking booking, ChargeType type, String description, BigDecimal amount) {
        String text = description.length() > 200 ? description.substring(0, 200) : description;
        return chargeRepository.save(BookingCharge.builder()
                .booking(booking)
                .type(type)
                .description(text)
                .amount(amount)
                .build());
    }

    private void removeCharge(BookingCharge charge) {
        if (charge != null) {
            chargeRepository.delete(charge);
        }
    }

    private String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String text = note.trim();
        if (text.length() > 300) {
            throw BusinessException.of("err.svc.note");
        }
        return text;
    }

    // Lay don phong de le tan dat ho (trang quan tri)
    public Booking bookingForStaff(Long bookingId) {
        return bookingService.findById(bookingId);
    }
}
