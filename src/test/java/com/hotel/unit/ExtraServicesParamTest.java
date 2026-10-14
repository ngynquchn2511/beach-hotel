package com.hotel.unit;

import com.hotel.dto.DivingTourRequest;
import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.*;
import com.hotel.service.BookingService;
import com.hotel.service.DivingTourService;
import com.hotel.service.FileStorageService;
import com.hotel.service.ServiceOrderService;
import com.hotel.service.TransferRateService;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.PessimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TcSuite(level = "UT", module = "Dịch vụ thêm: tour lặn biển & xe đưa đón sân bay")
class ExtraServicesParamTest {

    @Mock private BookingService bookingService;
    @Mock private DivingTourRepository tourRepository;
    @Mock private TourBookingRepository tourBookingRepository;
    @Mock private AirportTransferRepository transferRepository;
    @Mock private BookingChargeRepository chargeRepository;
    @Mock private TransferRateRepository rateRepository;
    @Mock private FileStorageService fileStorageService;

    private ServiceOrderService service;
    private TransferRateService rateService;
    private DivingTourService tourService;
    private Booking booking;
    private DivingTour tour;

    @BeforeEach
    void setUp() {
        rateService = new TransferRateService(rateRepository);
        service = new ServiceOrderService(bookingService, tourRepository, tourBookingRepository, transferRepository,
                chargeRepository, rateService);
        tourService = new DivingTourService(tourRepository, tourBookingRepository, fileStorageService);

        // Don o 3 dem, nhan phong sau 5 ngay
        booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCheckInDate(LocalDate.now().plusDays(5));
        booking.setCheckOutDate(LocalDate.now().plusDays(8));

        tour = DivingTour.builder().id(10L).name("Lặn ống thở").pricePerPerson(BigDecimal.valueOf(650000))
                .departureTime(LocalTime.of(8, 0)).durationHours(4).maxParticipants(8).active(true).build();
        when(tourRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(tour));
        when(tourBookingRepository.sumBookedParticipants(anyLong(), any())).thenReturn(0L);
        when(tourBookingRepository.save(any(TourBooking.class))).thenAnswer(inv -> {
            TourBooking o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(100L);
            }
            when(tourBookingRepository.findById(o.getId())).thenReturn(Optional.of(o));
            return o;
        });
        when(transferRepository.save(any(AirportTransfer.class))).thenAnswer(inv -> {
            AirportTransfer o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(200L);
            }
            when(transferRepository.findById(o.getId())).thenReturn(Optional.of(o));
            return o;
        });
        when(chargeRepository.save(any(BookingCharge.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rateRepository.findByAirportAndVehicleType(any(), any())).thenAnswer(inv -> Optional.of(
                TransferRate.builder().airport(inv.getArgument(0)).vehicleType(inv.getArgument(1))
                        .price(BigDecimal.valueOf(800000)).build()));
    }

    private TourBooking bookTour(int dayOffset, int participants, boolean byStaff) {
        return service.bookTour(booking, 10L, booking.getCheckInDate().plusDays(dayOffset), participants, null, byStaff);
    }

    private AirportTransfer bookTransfer(TransferDirection dir, VehicleType vehicle, String flight, int passengers) {
        return service.bookTransfer(booking, dir, Airport.VAN_DON, vehicle, null, LocalTime.of(10, 30), flight,
                passengers, null, false);
    }

    // ===================== Điều kiện đơn phòng =====================

    @TcSteps("Gọi canOrderServices với đơn phòng ở trạng thái S")
    @ParameterizedTest(name = "Đơn {0} có được đặt thêm dịch vụ ¦ booking.status={0} ¦ Cho phép = {1}")
    @CsvSource({"PENDING,true", "CONFIRMED,true", "CHECKED_IN,true", "CHECKED_OUT,false", "CANCELLED,false"})
    void canOrderServices_byBookingStatus(BookingStatus status, boolean allowed) {
        booking.setStatus(status);
        assertThat(service.canOrderServices(booking)).isEqualTo(allowed);
    }

    @TcSteps("Đơn phòng đã trả phòng / đã hủy, khách đặt tour hợp lệ")
    @ParameterizedTest(name = "Đặt tour trên đơn {0} bị từ chối ¦ booking.status={0} ¦ BusinessException 'đã hủy hoặc đã trả phòng', không lưu")
    @EnumSource(value = BookingStatus.class, names = {"CHECKED_OUT", "CANCELLED"})
    void bookTour_closedBooking_rejected(BookingStatus status) {
        booking.setStatus(status);
        assertThatThrownBy(() -> bookTour(1, 1, false)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã hủy hoặc đã trả phòng");
        verify(tourBookingRepository, never()).save(any());
    }

    // ===================== Tour lặn =====================

    @TcSteps("Khách đặt tour 650.000đ/người với N người (chưa ai đặt chuyến đó)")
    @ParameterizedTest(name = "Tính tiền tour {0} người ¦ 650.000 × {0} ¦ totalPrice = {1}, trạng thái PENDING, chưa tạo phụ phí")
    @CsvSource({"1,650000", "2,1300000", "5,3250000", "8,5200000"})
    void bookTour_totalPrice(int participants, long total) {
        TourBooking order = bookTour(1, participants, false);
        assertThat(order.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(total));
        assertThat(order.getUnitPrice()).isEqualByComparingTo("650000");
        assertThat(order.getStatus()).isEqualTo(ServiceStatus.PENDING);
        verify(chargeRepository, never()).save(any());
    }

    @TcSteps("Chuyến 8 chỗ đã có B người đặt, khách đặt thêm P người")
    @ParameterizedTest(name = "Chống bán quá chỗ - đã đặt {0}, đặt thêm {1} ¦ max=8, booked={0}, participants={1} ¦ Thành công = {2}")
    @CsvSource({"0,8,true", "5,3,true", "7,1,true", "7,2,false", "8,1,false", "6,3,false"})
    void bookTour_seatLimit(long booked, int participants, boolean ok) {
        when(tourBookingRepository.sumBookedParticipants(eq(10L), any())).thenReturn(booked);
        if (ok) {
            assertThatCode(() -> bookTour(1, participants, false)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> bookTour(1, participants, false)).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("chỉ còn " + (8 - booked) + " chỗ");
        }
    }

    @TcSteps("Khách đặt tour với số người ngoài khoảng [1, maxParticipants=8]")
    @ParameterizedTest(name = "Số người không hợp lệ ¦ participants={0} ¦ BusinessException 'từ 1 đến 8'")
    @ValueSource(ints = {0, -1, 9, 50})
    void bookTour_invalidParticipants(int participants) {
        assertThatThrownBy(() -> bookTour(1, participants, false)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("từ 1 đến 8");
    }

    @TcSteps("Đơn ở từ +5 đến +8, khách chọn ngày đi tour lệch D ngày so với ngày nhận phòng")
    @ParameterizedTest(name = "Ngày đi tour phải trong thời gian lưu trú ¦ tourDate = nhận phòng {0} ngày ¦ Hợp lệ = {1}")
    @CsvSource({"-1,false", "0,true", "2,true", "3,true", "4,false"})
    void bookTour_dateWithinStay(int offset, boolean ok) {
        if (ok) {
            assertThat(bookTour(offset, 1, false)).isNotNull();
        } else {
            assertThatThrownBy(() -> bookTour(offset, 1, false)).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("thời gian lưu trú");
        }
    }

    @TcSteps("Tour khởi hành sau H tiếng kể từ hiện tại, khách tự đặt / lễ tân đặt hộ")
    @ParameterizedTest(name = "Thời gian đặt trước tối thiểu 12 tiếng - còn {0} tiếng, lễ tân đặt = {1} ¦ startAt = now + {0}h ¦ Thành công = {2}")
    @CsvSource({"30,false,true", "13,false,true", "11,false,false", "3,false,false", "3,true,true", "-2,true,false"})
    void bookTour_leadTime(int hoursAhead, boolean byStaff, boolean ok) {
        LocalDateTime start = LocalDateTime.now().plusHours(hoursAhead).withSecond(0).withNano(0);
        tour.setDepartureTime(start.toLocalTime());
        booking.setCheckInDate(start.toLocalDate().minusDays(1));
        booking.setCheckOutDate(start.toLocalDate().plusDays(1));
        if (ok) {
            assertThat(service.bookTour(booking, 10L, start.toLocalDate(), 1, null, byStaff)).isNotNull();
        } else {
            assertThatThrownBy(() -> service.bookTour(booking, 10L, start.toLocalDate(), 1, null, byStaff))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(byStaff ? "đã qua" : "12 tiếng");
        }
    }

    @TcSteps("Lễ tân đặt tour hộ khách (byStaff=true)")
    @ParameterizedTest(name = "Lễ tân đặt hộ {0} người được xác nhận ngay ¦ byStaff=true, participants={0} ¦ CONFIRMED + phụ phí DIVING_TOUR = {1}")
    @CsvSource({"1,650000", "4,2600000"})
    void bookTour_byStaff_confirmedWithCharge(int participants, long amount) {
        TourBooking order = bookTour(1, participants, true);
        assertThat(order.getStatus()).isEqualTo(ServiceStatus.CONFIRMED);
        ArgumentCaptor<BookingCharge> charge = ArgumentCaptor.forClass(BookingCharge.class);
        verify(chargeRepository).save(charge.capture());
        assertThat(charge.getValue().getType()).isEqualTo(ChargeType.DIVING_TOUR);
        assertThat(charge.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(amount));
        assertThat(charge.getValue().getBooking()).isSameAs(booking);
    }

    @TcSteps("Gọi bookTour thiếu một trường bắt buộc")
    @ParameterizedTest(name = "Thiếu thông tin tour - {0} ¦ {0} = null ¦ BusinessException 'điền đủ thông tin'")
    @ValueSource(strings = {"tourId", "tourDate", "participants"})
    void bookTour_missingField(String field) {
        Long id = field.equals("tourId") ? null : 10L;
        LocalDate date = field.equals("tourDate") ? null : booking.getCheckInDate();
        Integer n = field.equals("participants") ? null : 1;
        assertThatThrownBy(() -> service.bookTour(booking, id, date, n, null, false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("điền đủ thông tin");
    }

    @TcSteps("Tour bị ẩn / không tồn tại / đang bị khóa bởi giao dịch khác")
    @ParameterizedTest(name = "Không đặt được tour - {0} ¦ {0} ¦ BusinessException '{1}'")
    @CsvSource({"tour đã ẩn,ngừng bán", "tour không tồn tại,ngừng bán", "đang khóa dòng tour,thử lại sau"})
    void bookTour_unavailableTour(String label, String message) {
        if (label.contains("ẩn")) {
            tour.setActive(false);
        } else if (label.contains("không tồn tại")) {
            when(tourRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());
        } else {
            when(tourRepository.findByIdForUpdate(10L)).thenThrow(new PessimisticLockingFailureException("lock"));
        }
        assertThatThrownBy(() -> bookTour(1, 1, false)).isInstanceOf(BusinessException.class).hasMessageContaining(message);
    }

    @TcSteps("Khách nhập ghi chú độ dài L ký tự khi đặt tour")
    @ParameterizedTest(name = "Ghi chú dài {0} ký tự ¦ note.length={0} ¦ Hợp lệ = {1}")
    @CsvSource({"0,true", "1,true", "300,true", "301,false"})
    void bookTour_noteLength(int length, boolean ok) {
        String note = "a".repeat(length);
        if (ok) {
            TourBooking order = service.bookTour(booking, 10L, booking.getCheckInDate(), 1, note, false);
            assertThat(order.getNote()).isEqualTo(length == 0 ? null : note);
        } else {
            assertThatThrownBy(() -> service.bookTour(booking, 10L, booking.getCheckInDate(), 1, note, false))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("300 ký tự");
        }
    }

    @TcSteps("Lễ tân xác nhận đơn tour đang ở trạng thái S")
    @ParameterizedTest(name = "Xác nhận đơn tour đang {0} ¦ status={0} ¦ Thành công = {1}")
    @CsvSource({"PENDING,true", "CONFIRMED,false", "CANCELLED,false"})
    void confirmTour_onlyPending(ServiceStatus status, boolean ok) {
        TourBooking order = bookTour(1, 2, false);
        order.setStatus(status);
        if (ok) {
            service.confirmTour(order.getId());
            assertThat(order.getStatus()).isEqualTo(ServiceStatus.CONFIRMED);
            assertThat(order.getCharge()).isNotNull();
        } else {
            assertThatThrownBy(() -> service.confirmTour(order.getId())).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("đang chờ xác nhận");
        }
    }

    @TcSteps("Lễ tân hủy đơn tour (đã/chưa xác nhận) trên đơn phòng ở trạng thái S")
    @ParameterizedTest(name = "Lễ tân hủy tour - đơn phòng {0}, tour đã xác nhận = {1} ¦ booking.status={0} ¦ Hủy được = {2}, xóa phụ phí = {3}")
    @CsvSource({"CONFIRMED,true,true,true", "CONFIRMED,false,true,false", "CHECKED_IN,true,true,true", "CHECKED_OUT,true,false,false"})
    void cancelTourByStaff(BookingStatus bookingStatus, boolean confirmed, boolean ok, boolean chargeRemoved) {
        TourBooking order = bookTour(1, 2, confirmed);
        BookingCharge charge = order.getCharge();
        booking.setStatus(bookingStatus);
        if (ok) {
            service.cancelTourByStaff(order.getId());
            assertThat(order.getStatus()).isEqualTo(ServiceStatus.CANCELLED);
            assertThat(order.getCharge()).isNull();
        } else {
            assertThatThrownBy(() -> service.cancelTourByStaff(order.getId())).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("đã trả phòng");
        }
        verify(chargeRepository, chargeRemoved ? times(1) : never()).delete(charge);
    }

    @TcSteps("Khách tự hủy tour (trạng thái S) còn H tiếng nữa là khởi hành")
    @ParameterizedTest(name = "Khách tự hủy tour {0} khi còn {1} tiếng ¦ status={0}, startAt=now+{1}h ¦ Hủy được = {2}")
    @CsvSource({"PENDING,2,true", "PENDING,30,true", "CONFIRMED,30,true", "CONFIRMED,20,false", "CONFIRMED,2,false", "PENDING,-1,false"})
    void cancelTourByCustomer_window(ServiceStatus status, int hoursAhead, boolean ok) {
        LocalDateTime start = LocalDateTime.now().plusHours(hoursAhead).withSecond(0).withNano(0);
        tour.setDepartureTime(start.toLocalTime());
        TourBooking order = TourBooking.builder().id(300L).booking(booking).tour(tour).tourDate(start.toLocalDate())
                .participants(1).unitPrice(tour.getPricePerPerson()).totalPrice(tour.getPricePerPerson()).status(status).build();
        when(tourBookingRepository.findById(300L)).thenReturn(Optional.of(order));
        if (ok) {
            service.cancelTourByCustomer(booking, 300L);
            assertThat(order.getStatus()).isEqualTo(ServiceStatus.CANCELLED);
        } else {
            assertThatThrownBy(() -> service.cancelTourByCustomer(booking, 300L)).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("24 tiếng");
        }
    }

    @TcSteps("Khách dùng link đơn A để hủy dịch vụ thuộc đơn B")
    @ParameterizedTest(name = "Không hủy được dịch vụ của đơn khác - {0} ¦ order.booking ≠ booking ¦ BusinessException 'Không tìm thấy dịch vụ'")
    @ValueSource(strings = {"tour", "xe đưa đón"})
    void cancelByCustomer_otherBooking(String kind) {
        Booking other = new Booking();
        other.setId(2L);
        other.setStatus(BookingStatus.CONFIRMED);
        other.setCheckInDate(booking.getCheckInDate());
        other.setCheckOutDate(booking.getCheckOutDate());
        if (kind.equals("tour")) {
            TourBooking order = bookTour(1, 1, false);
            assertThatThrownBy(() -> service.cancelTourByCustomer(other, order.getId()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Không tìm thấy dịch vụ");
            assertThat(order.getStatus()).isEqualTo(ServiceStatus.PENDING);
        } else {
            AirportTransfer order = bookTransfer(TransferDirection.PICKUP, VehicleType.CAR_4, "VN1511", 1);
            assertThatThrownBy(() -> service.cancelTransferByCustomer(other, order.getId()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Không tìm thấy dịch vụ");
            assertThat(order.getStatus()).isEqualTo(ServiceStatus.PENDING);
        }
    }

    // ===================== Xe đưa đón sân bay =====================

    @TcSteps("Khách đặt xe loại V cho P hành khách")
    @ParameterizedTest(name = "Sức chứa {0} - {1} khách ¦ vehicle={0}, passengers={1} ¦ Hợp lệ = {2}")
    @CsvSource({"CAR_4,1,true", "CAR_4,3,true", "CAR_4,4,false", "CAR_7,6,true", "CAR_7,7,false",
            "VAN_16,15,true", "VAN_16,16,false", "CAR_4,0,false"})
    void bookTransfer_capacity(VehicleType vehicle, int passengers, boolean ok) {
        if (ok) {
            assertThat(bookTransfer(TransferDirection.PICKUP, vehicle, "VN1511", passengers).getPassengers()).isEqualTo(passengers);
        } else {
            assertThatThrownBy(() -> bookTransfer(TransferDirection.PICKUP, vehicle, "VN1511", passengers))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @TcSteps("Khách đặt xe đón sân bay với số hiệu chuyến bay F")
    @ParameterizedTest(name = "Số hiệu chuyến bay \"{0}\" ¦ flightNumber=\"{0}\" ¦ Hợp lệ = {1}, lưu thành \"{2}\"")
    @CsvSource(value = {"VN1511|true|VN1511", "vn1511|true|VN1511", " vj123 |true|VJ123", "QH 202|true|QH 202",
            "VN1511A|true|VN1511A", "VN|false|", "ABCD|false|", "VN-1511|false|", "VN15111|false|"}, delimiter = '|')
    void bookTransfer_flightNumber(String flight, boolean ok, String saved) {
        if (ok) {
            assertThat(bookTransfer(TransferDirection.PICKUP, VehicleType.CAR_4, flight, 2).getFlightNumber()).isEqualTo(saved);
        } else {
            assertThatThrownBy(() -> bookTransfer(TransferDirection.PICKUP, VehicleType.CAR_4, flight, 2))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("không hợp lệ");
        }
    }

    @TcSteps("Khách đặt xe chiều D, không nhập số hiệu chuyến bay")
    @ParameterizedTest(name = "Bắt buộc số hiệu chuyến bay theo chiều {0} ¦ direction={0}, flightNumber trống ¦ Hợp lệ = {1}")
    @CsvSource({"PICKUP,false", "DROPOFF,true"})
    void bookTransfer_flightRequiredOnlyForPickup(TransferDirection dir, boolean ok) {
        if (ok) {
            assertThat(bookTransfer(dir, VehicleType.CAR_4, "  ", 2).getFlightNumber()).isNull();
        } else {
            assertThatThrownBy(() -> bookTransfer(dir, VehicleType.CAR_4, null, 2))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("số hiệu chuyến bay");
        }
    }

    @TcSteps("Khách đặt xe chiều D, không gửi ngày (hệ thống tự lấy) hoặc gửi ngày lệch")
    @ParameterizedTest(name = "Ngày đưa đón chiều {0} - gửi ngày {1} ¦ direction={0}, date={1} ¦ Hợp lệ = {2}")
    @CsvSource({"PICKUP,trống,true", "PICKUP,ngày nhận phòng,true", "PICKUP,ngày trả phòng,false",
            "DROPOFF,trống,true", "DROPOFF,ngày trả phòng,true", "DROPOFF,ngày nhận phòng,false"})
    void bookTransfer_dateByDirection(TransferDirection dir, String dateLabel, boolean ok) {
        LocalDate date = switch (dateLabel) {
            case "ngày nhận phòng" -> booking.getCheckInDate();
            case "ngày trả phòng" -> booking.getCheckOutDate();
            default -> null;
        };
        LocalDate expected = dir == TransferDirection.PICKUP ? booking.getCheckInDate() : booking.getCheckOutDate();
        if (ok) {
            AirportTransfer t = service.bookTransfer(booking, dir, Airport.NOI_BAI, VehicleType.CAR_7, date,
                    LocalTime.of(9, 0), "VN1511", 2, null, false);
            assertThat(t.getPickupAt()).isEqualTo(expected.atTime(9, 0));
        } else {
            assertThatThrownBy(() -> service.bookTransfer(booking, dir, Airport.NOI_BAI, VehicleType.CAR_7, date,
                    LocalTime.of(9, 0), "VN1511", 2, null, false))
                    .isInstanceOf(BusinessException.class).hasMessageContaining(dir == TransferDirection.PICKUP ? "ngày nhận phòng" : "ngày trả phòng");
        }
    }

    @TcSteps("Đơn phòng đã có yêu cầu xe chiều D ở trạng thái S, khách đặt thêm xe cùng chiều")
    @ParameterizedTest(name = "Một chiều chỉ 1 xe - {0} ¦ existsByBookingIdAndDirectionAndStatusNot(CANCELLED) = {1} ¦ Thành công = {2}")
    @CsvSource({"PICKUP,true,false", "PICKUP,false,true", "DROPOFF,true,false", "DROPOFF,false,true"})
    void bookTransfer_onePerDirection(TransferDirection dir, boolean exists, boolean ok) {
        when(transferRepository.existsByBookingIdAndDirectionAndStatusNot(1L, dir, ServiceStatus.CANCELLED)).thenReturn(exists);
        if (ok) {
            assertThat(bookTransfer(dir, VehicleType.CAR_4, "VN1511", 1)).isNotNull();
        } else {
            assertThatThrownBy(() -> bookTransfer(dir, VehicleType.CAR_4, "VN1511", 1))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("đã có yêu cầu xe");
        }
    }

    @TcSteps("Xe đón khởi hành sau H tiếng, khách tự đặt / lễ tân đặt hộ")
    @ParameterizedTest(name = "Đặt xe trước tối thiểu 6 tiếng - còn {0} tiếng, lễ tân = {1} ¦ pickupAt=now+{0}h ¦ Thành công = {2}")
    @CsvSource({"10,false,true", "7,false,true", "5,false,false", "2,true,true", "-1,true,false"})
    void bookTransfer_leadTime(int hoursAhead, boolean byStaff, boolean ok) {
        LocalDateTime at = LocalDateTime.now().plusHours(hoursAhead).withSecond(0).withNano(0);
        booking.setCheckInDate(at.toLocalDate());
        booking.setCheckOutDate(at.toLocalDate().plusDays(2));
        if (ok) {
            assertThat(service.bookTransfer(booking, TransferDirection.PICKUP, Airport.VAN_DON, VehicleType.CAR_4,
                    null, at.toLocalTime(), "VN1511", 1, null, byStaff)).isNotNull();
        } else {
            assertThatThrownBy(() -> service.bookTransfer(booking, TransferDirection.PICKUP, Airport.VAN_DON,
                    VehicleType.CAR_4, null, at.toLocalTime(), "VN1511", 1, null, byStaff))
                    .isInstanceOf(BusinessException.class).hasMessageContaining(byStaff ? "đã qua" : "6 tiếng");
        }
    }

    @TcSteps("Lễ tân xác nhận đơn xe chiều D tới sân bay A")
    @ParameterizedTest(name = "Phụ phí xe {0} - {1} ¦ confirmTransfer ¦ BookingCharge AIRPORT_TRANSFER = giá bảng, mô tả bắt đầu \"{2}\"")
    @CsvSource({"PICKUP,VAN_DON,Đón Sân bay Vân Đồn", "DROPOFF,NOI_BAI,Tiễn Sân bay Nội Bài", "DROPOFF,CAT_BI,Tiễn Sân bay Cát Bi"})
    void confirmTransfer_createsCharge(TransferDirection dir, Airport airport, String prefix) {
        AirportTransfer t = service.bookTransfer(booking, dir, airport, VehicleType.CAR_7, null, LocalTime.of(9, 0),
                "VN1511", 2, null, false);
        service.confirmTransfer(t.getId());
        assertThat(t.getStatus()).isEqualTo(ServiceStatus.CONFIRMED);
        assertThat(t.getCharge().getType()).isEqualTo(ChargeType.AIRPORT_TRANSFER);
        assertThat(t.getCharge().getAmount()).isEqualByComparingTo("800000");
        assertThat(t.getCharge().getDescription()).startsWith(prefix).contains("Xe 7 chỗ");
    }

    @TcSteps("Hủy đơn xe đã xác nhận rồi hủy lại lần nữa")
    @ParameterizedTest(name = "Hủy xe 2 lần không lỗi - {0} hủy ¦ cancel × 2 ¦ CANCELLED, phụ phí chỉ xóa 1 lần")
    @ValueSource(strings = {"lễ tân", "khách"})
    void cancelTransfer_idempotent(String who) {
        AirportTransfer t = service.bookTransfer(booking, TransferDirection.PICKUP, Airport.VAN_DON, VehicleType.CAR_4,
                null, LocalTime.of(10, 0), "VN1511", 1, null, true);
        BookingCharge charge = t.getCharge();
        for (int i = 0; i < 2; i++) {
            if (who.equals("lễ tân")) {
                service.cancelTransferByStaff(t.getId());
            } else {
                service.cancelTransferByCustomer(booking, t.getId());
            }
        }
        assertThat(t.getStatus()).isEqualTo(ServiceStatus.CANCELLED);
        verify(chargeRepository, times(1)).delete(charge);
    }

    // ===================== Bảng giá đưa đón =====================

    @TcSteps("Bảng giá còn thiếu một số ô, gọi ensureDefaults khi khởi động")
    @ParameterizedTest(name = "Tạo giá mặc định khi thiếu {0} ô ¦ {0}/9 ô chưa có giá ¦ save được gọi {0} lần")
    @ValueSource(ints = {0, 1, 9})
    void ensureDefaults_fillsMissing(int missing) {
        int[] counter = {0};
        when(rateRepository.findByAirportAndVehicleType(any(), any())).thenAnswer(inv ->
                counter[0]++ < missing ? Optional.empty()
                        : Optional.of(TransferRate.builder().price(BigDecimal.ONE).build()));
        rateService.ensureDefaults();
        verify(rateRepository, times(missing)).save(any(TransferRate.class));
    }

    @TcSteps("Bảng giá trống, ensureDefaults tạo giá mặc định cho sân bay A, xe V")
    @ParameterizedTest(name = "Giá mặc định {0} - {1} ¦ bảng giá trống ¦ price = {2}")
    @CsvSource({"VAN_DON,CAR_4,650000", "VAN_DON,VAN_16,1300000", "NOI_BAI,CAR_7,1800000", "CAT_BI,CAR_4,850000"})
    void ensureDefaults_prices(Airport airport, VehicleType vehicle, long price) {
        when(rateRepository.findByAirportAndVehicleType(any(), any())).thenReturn(Optional.empty());
        ArgumentCaptor<TransferRate> saved = ArgumentCaptor.forClass(TransferRate.class);
        rateService.ensureDefaults();
        verify(rateRepository, times(9)).save(saved.capture());
        assertThat(saved.getAllValues()).filteredOn(r -> r.getAirport() == airport && r.getVehicleType() == vehicle)
                .singleElement().extracting(TransferRate::getPrice).satisfies(p -> assertThat(p).isEqualByComparingTo(BigDecimal.valueOf(price)));
    }

    @TcSteps("Admin gửi form bảng giá, 8 ô hợp lệ và ô VAN_DON/CAR_4 = X")
    @ParameterizedTest(name = "Cập nhật giá đưa đón - nhập \"{0}\" ¦ price_VAN_DON_CAR_4=\"{0}\" ¦ Hợp lệ = {1}, lưu = {2}")
    @CsvSource(value = {"700000|true|700000", "700.000|true|700000", "1,200,000|true|1200000", "50000000|true|50000000",
            "0|false|", "-5|false|", "50000001|false|", "abc|false|", "|false|"}, delimiter = '|')
    void updatePrices_validation(String raw, boolean ok, Long saved) {
        Map<String, String> form = new HashMap<>();
        for (Airport a : Airport.values()) {
            for (VehicleType v : VehicleType.values()) {
                form.put("price_" + a + "_" + v, "900000");
            }
        }
        form.put("price_VAN_DON_CAR_4", raw);
        TransferRate vanDonCar4 = TransferRate.builder().airport(Airport.VAN_DON).vehicleType(VehicleType.CAR_4)
                .price(BigDecimal.valueOf(650000)).build();
        when(rateRepository.findByAirportAndVehicleType(Airport.VAN_DON, VehicleType.CAR_4)).thenReturn(Optional.of(vanDonCar4));
        if (ok) {
            rateService.updatePrices(form);
            assertThat(vanDonCar4.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(saved));
        } else {
            assertThatThrownBy(() -> rateService.updatePrices(form)).isInstanceOf(BusinessException.class);
            assertThat(vanDonCar4.getPrice()).isEqualByComparingTo("650000");
        }
    }

    @TcSteps("Gọi priceOf với sân bay/loại xe chưa có giá")
    @ParameterizedTest(name = "Chưa có giá cho {0} ¦ findByAirportAndVehicleType rỗng ¦ BusinessException 'Chưa có giá'")
    @EnumSource(VehicleType.class)
    void priceOf_missingRate(VehicleType vehicle) {
        when(rateRepository.findByAirportAndVehicleType(Airport.CAT_BI, vehicle)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> rateService.priceOf(Airport.CAT_BI, vehicle))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Chưa có giá");
    }

    // ===================== Danh mục tour (admin) =====================

    @TcSteps("Admin xóa tour đã/chưa có khách đặt")
    @ParameterizedTest(name = "Xóa tour - đã có khách đặt = {0} ¦ existsByTourId = {0} ¦ Xóa được = {1}")
    @CsvSource({"false,true", "true,false"})
    void deleteTour(boolean hasBookings, boolean ok) {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(tour));
        when(tourBookingRepository.existsByTourId(10L)).thenReturn(hasBookings);
        if (ok) {
            tourService.delete(10L);
            verify(tourRepository).delete(tour);
        } else {
            assertThatThrownBy(() -> tourService.delete(10L)).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Ẩn");
            verify(tourRepository, never()).delete(any(DivingTour.class));
        }
    }

    @TcSteps("Admin bấm Ẩn/Hiện tour đang ở trạng thái active=A")
    @ParameterizedTest(name = "Ẩn/hiện tour đang active={0} ¦ toggleActive ¦ active = {1}")
    @CsvSource({"true,false", "false,true"})
    void toggleActive(boolean before, boolean after) {
        tour.setActive(before);
        when(tourRepository.findById(10L)).thenReturn(Optional.of(tour));
        tourService.toggleActive(10L);
        assertThat(tour.isActive()).isEqualTo(after);
        verify(tourRepository).save(tour);
    }

    @TcSteps("Admin tạo tour với các trường mô tả có khoảng trắng / để trống")
    @ParameterizedTest(name = "Chuẩn hóa dữ liệu tour - mô tả \"{0}\" ¦ description=\"{0}\" ¦ lưu = {1}, tour mới active")
    @CsvSource(value = {"'  Ngắm san hô  '|Ngắm san hô", "'   '|null", "|null"}, delimiter = '|')
    void createTour_trimsFields(String description, String expected) {
        DivingTourRequest req = new DivingTourRequest();
        req.setName("  Kayak hang Luồn  ");
        req.setDescription(description);
        req.setPricePerPerson(BigDecimal.valueOf(450000));
        req.setDepartureTime(LocalTime.of(8, 0));
        req.setDurationHours(3);
        req.setMaxParticipants(12);
        when(tourRepository.save(any(DivingTour.class))).thenAnswer(inv -> inv.getArgument(0));
        DivingTour saved = tourService.create(req, null);
        assertThat(saved.getName()).isEqualTo("Kayak hang Luồn");
        assertThat(saved.getDescription()).isEqualTo(expected.equals("null") ? null : expected);
        assertThat(saved.isActive()).isTrue();
    }

    @TcSteps("Có đơn tour và đơn xe đang chờ xác nhận, lễ tân xem số dịch vụ chờ")
    @ParameterizedTest(name = "Đếm dịch vụ chờ xác nhận ¦ tour chờ={0}, xe chờ={1} ¦ countPending = {2}")
    @CsvSource({"0,0,0", "2,1,3", "0,4,4"})
    void countPending(long tours, long transfers, long total) {
        when(tourBookingRepository.countPending()).thenReturn(tours);
        when(transferRepository.countPending()).thenReturn(transfers);
        assertThat(service.countPending()).isEqualTo(total);
    }

    @TcSteps("Danh sách tour đang bán lấy từ repository")
    @ParameterizedTest(name = "findActive trả đúng danh sách ¦ {0} tour đang bán ¦ kích thước = {0}")
    @ValueSource(ints = {0, 3})
    void findActive(int n) {
        List<DivingTour> tours = java.util.stream.IntStream.range(0, n).mapToObj(i -> tour).toList();
        when(tourRepository.findByActiveTrueOrderByPricePerPersonAsc()).thenReturn(tours);
        assertThat(tourService.findActive()).hasSize(n);
    }
}
