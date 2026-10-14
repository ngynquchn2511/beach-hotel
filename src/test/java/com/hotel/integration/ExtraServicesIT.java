package com.hotel.integration;

import com.hotel.entity.AirportTransfer;
import com.hotel.entity.Booking;
import com.hotel.entity.BookingCharge;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.ChargeType;
import com.hotel.entity.CustomerType;
import com.hotel.entity.DivingTour;
import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.ServiceStatus;
import com.hotel.entity.TourBooking;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.AirportTransferRepository;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.DivingTourRepository;
import com.hotel.repository.TourBookingRepository;
import com.hotel.service.TransferRateService;
import com.hotel.entity.Airport;
import com.hotel.entity.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Dich vu them: dat tour lan bien + xe dua don san bay kem don phong (khach dat -> le tan xac nhan -> phu phi cua don)
class ExtraServicesIT extends ItFixtures {

    @Autowired private DivingTourRepository tourRepository;
    @Autowired private TourBookingRepository tourBookingRepository;
    @Autowired private AirportTransferRepository transferRepository;
    @Autowired private BookingChargeRepository chargeRepository;
    @Autowired private TransferRateService transferRateService;

    private User customer;
    private User staff;
    private User admin;
    private Booking booking;
    private DivingTour tour;

    @BeforeEach
    void seed() {
        customer = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        staff = persistUser(UserRole.STAFF, null);
        admin = persistUser(UserRole.ADMIN, null);
        Room room = persistRoom(persistRoomType("Phòng view vịnh IT", 900000, 4), 900000, RoomStatus.AVAILABLE);
        // O 3 dem, nhan phong sau 5 ngay
        booking = persistBooking(customer, room, BookingStatus.CONFIRMED, 5, 3);
        booking.setNumberOfGuests(2);
        tour = persistTour(3, LocalTime.of(8, 0), true);
    }

    private DivingTour persistTour(int maxParticipants, LocalTime departure, boolean active) {
        return tourRepository.save(DivingTour.builder().name("Lặn ống thở IT " + seq()).nameEn("Snorkel IT")
                .pricePerPerson(BigDecimal.valueOf(650000)).departureTime(departure).durationHours(4)
                .maxParticipants(maxParticipants).active(active).build());
    }

    private void bookTourAsGuest(LocalDate date, int participants) throws Exception {
        mockMvc.perform(post("/customer/bookings/" + booking.getId() + "/tours").with(csrf())
                        .param("token", booking.getAccessToken())
                        .param("tourId", tour.getId().toString())
                        .param("tourDate", date.toString())
                        .param("participants", String.valueOf(participants)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void guestBooksTourWithTokenLink_staffConfirms_priceBecomesBookingCharge() throws Exception {
        mockMvc.perform(post("/customer/bookings/" + booking.getId() + "/tours").with(csrf())
                        .param("token", booking.getAccessToken())
                        .param("tourId", tour.getId().toString())
                        .param("tourDate", booking.getCheckInDate().plusDays(1).toString())
                        .param("participants", "2")
                        .param("note", "Có 1 bé 10 tuổi"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("serviceSuccess"));

        List<TourBooking> orders = tourBookingRepository.findByBookingIdOrderByTourDateAscIdAsc(booking.getId());
        assertThat(orders).hasSize(1);
        TourBooking order = orders.get(0);
        assertThat(order.getStatus()).isEqualTo(ServiceStatus.PENDING);
        assertThat(order.getTotalPrice()).isEqualByComparingTo("1300000");
        assertThat(chargeRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId())).isEmpty();

        mockMvc.perform(post("/admin/service-orders/tours/" + order.getId() + "/confirm").with(csrf()).with(as(staff)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        assertThat(order.getStatus()).isEqualTo(ServiceStatus.CONFIRMED);
        List<BookingCharge> charges = chargeRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId());
        assertThat(charges).singleElement().satisfies(c -> {
            assertThat(c.getType()).isEqualTo(ChargeType.DIVING_TOUR);
            assertThat(c.getAmount()).isEqualByComparingTo("1300000");
        });
    }

    @Test
    void cancellingConfirmedTour_removesItsCharge() throws Exception {
        bookTourAsGuest(booking.getCheckInDate(), 1);
        TourBooking order = tourBookingRepository.findByBookingIdOrderByTourDateAscIdAsc(booking.getId()).get(0);
        mockMvc.perform(post("/admin/service-orders/tours/" + order.getId() + "/confirm").with(csrf()).with(as(staff)));
        assertThat(chargeRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId())).hasSize(1);

        mockMvc.perform(post("/admin/service-orders/tours/" + order.getId() + "/cancel").with(csrf()).with(as(staff)))
                .andExpect(status().is3xxRedirection());

        assertThat(order.getStatus()).isEqualTo(ServiceStatus.CANCELLED);
        assertThat(chargeRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId())).isEmpty();
    }

    @Test
    void tourSeats_areNotOversold() throws Exception {
        LocalDate day = booking.getCheckInDate().plusDays(1);
        bookTourAsGuest(day, 2);
        // Chuyen chi con 1 cho -> dat them 2 nguoi bi tu choi
        mockMvc.perform(post("/customer/bookings/" + booking.getId() + "/tours").with(csrf())
                        .param("token", booking.getAccessToken())
                        .param("tourId", tour.getId().toString())
                        .param("tourDate", day.toString())
                        .param("participants", "2"))
                .andExpect(flash().attribute("serviceError", org.hamcrest.Matchers.containsString("chỉ còn 1 chỗ")));
        assertThat(tourBookingRepository.sumBookedParticipants(tour.getId(), day)).isEqualTo(2);
    }

    @Test
    void tourDate_mustBeDuringTheStay() throws Exception {
        mockMvc.perform(post("/customer/bookings/" + booking.getId() + "/tours").with(csrf())
                        .param("token", booking.getAccessToken())
                        .param("tourId", tour.getId().toString())
                        .param("tourDate", booking.getCheckOutDate().plusDays(1).toString())
                        .param("participants", "1"))
                .andExpect(flash().attribute("serviceError", org.hamcrest.Matchers.containsString("thời gian lưu trú")));
        assertThat(tourBookingRepository.findByBookingIdOrderByTourDateAscIdAsc(booking.getId())).isEmpty();
    }

    @Test
    void tourStartingSoon_customerRejected_butReceptionCanBookForGuest() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusHours(3).withSecond(0).withNano(0);
        tour = persistTour(10, start.toLocalTime(), true);
        booking.setCheckInDate(LocalDate.now());
        booking.setCheckOutDate(LocalDate.now().plusDays(3));

        mockMvc.perform(post("/customer/bookings/" + booking.getId() + "/tours").with(csrf())
                        .param("token", booking.getAccessToken())
                        .param("tourId", tour.getId().toString())
                        .param("tourDate", start.toLocalDate().toString())
                        .param("participants", "1"))
                .andExpect(flash().attribute("serviceError", org.hamcrest.Matchers.containsString("12 tiếng")));

        mockMvc.perform(post("/admin/bookings/" + booking.getId() + "/tours").with(csrf()).with(as(staff))
                        .param("tourId", tour.getId().toString())
                        .param("tourDate", start.toLocalDate().toString())
                        .param("participants", "1"))
                .andExpect(flash().attributeExists("successMessage"));
        // Le tan dat ho -> xac nhan luon, co phu phi ngay
        assertThat(tourBookingRepository.findByBookingIdOrderByTourDateAscIdAsc(booking.getId()))
                .singleElement().extracting(TourBooking::getStatus).isEqualTo(ServiceStatus.CONFIRMED);
        assertThat(chargeRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId())).hasSize(1);
    }

    @Test
    void anonymousWithoutToken_cannotBookServices() throws Exception {
        mockMvc.perform(post("/customer/bookings/" + booking.getId() + "/tours").with(csrf())
                        .param("token", "sai-ma")
                        .param("tourId", tour.getId().toString())
                        .param("tourDate", booking.getCheckInDate().toString())
                        .param("participants", "1"))
                .andExpect(flash().attributeExists("serviceError"));
        assertThat(tourBookingRepository.findByBookingIdOrderByTourDateAscIdAsc(booking.getId())).isEmpty();
    }

    @Test
    void airportPickup_usesRateTable_requiresFlight_andAllowsOnlyOnePerDirection() throws Exception {
        String url = "/customer/bookings/" + booking.getId() + "/transfers";
        // Thieu so hieu chuyen bay
        mockMvc.perform(post(url).with(csrf()).with(as(customer))
                        .param("direction", "PICKUP").param("airport", "VAN_DON").param("vehicleType", "CAR_4")
                        .param("time", "10:30").param("passengers", "2"))
                .andExpect(flash().attribute("serviceError", org.hamcrest.Matchers.containsString("số hiệu chuyến bay")));
        // Qua so khach cua xe 4 cho
        mockMvc.perform(post(url).with(csrf()).with(as(customer))
                        .param("direction", "PICKUP").param("airport", "VAN_DON").param("vehicleType", "CAR_4")
                        .param("time", "10:30").param("flightNumber", "vn1511").param("passengers", "5"))
                .andExpect(flash().attribute("serviceError", org.hamcrest.Matchers.containsString("tối đa 3 khách")));
        // Hop le
        mockMvc.perform(post(url).with(csrf()).with(as(customer))
                        .param("direction", "PICKUP").param("airport", "VAN_DON").param("vehicleType", "CAR_7")
                        .param("time", "10:30").param("flightNumber", "vn1511").param("passengers", "5"))
                .andExpect(flash().attributeExists("serviceSuccess"));

        List<AirportTransfer> transfers = transferRepository.findByBookingIdOrderByPickupAtAsc(booking.getId());
        assertThat(transfers).singleElement().satisfies(t -> {
            assertThat(t.getPickupAt()).isEqualTo(booking.getCheckInDate().atTime(10, 30));
            assertThat(t.getFlightNumber()).isEqualTo("VN1511");
            assertThat(t.getPrice()).isEqualByComparingTo(transferRateService.priceOf(Airport.VAN_DON, VehicleType.CAR_7));
        });
        // Don thu 2 cung chieu bi chan
        mockMvc.perform(post(url).with(csrf()).with(as(customer))
                        .param("direction", "PICKUP").param("airport", "NOI_BAI").param("vehicleType", "CAR_4")
                        .param("time", "09:00").param("flightNumber", "VJ123").param("passengers", "1"))
                .andExpect(flash().attribute("serviceError", org.hamcrest.Matchers.containsString("đã có yêu cầu xe đón")));
        // Chieu tien ra san bay vao ngay tra phong, khong can so hieu chuyen bay
        mockMvc.perform(post(url).with(csrf()).with(as(customer))
                        .param("direction", "DROPOFF").param("airport", "NOI_BAI").param("vehicleType", "CAR_4")
                        .param("time", "09:00").param("passengers", "2"))
                .andExpect(flash().attributeExists("serviceSuccess"));
        assertThat(transferRepository.findByBookingIdOrderByPickupAtAsc(booking.getId())).hasSize(2)
                .last().extracting(AirportTransfer::getPickupAt).isEqualTo(booking.getCheckOutDate().atTime(9, 0));
    }

    @Test
    void cancelledBooking_cannotOrderServices() throws Exception {
        booking.setStatus(BookingStatus.CANCELLED);
        bookTourAsGuest(booking.getCheckInDate(), 1);
        assertThat(tourBookingRepository.findByBookingIdOrderByTourDateAscIdAsc(booking.getId())).isEmpty();
    }

    @Test
    void servicesPage_isPublic_andShowsToursAndRates() throws Exception {
        mockMvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(tour.getName())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sân bay Vân Đồn")));
    }

    @Test
    void bookingPage_showsServicesSection() throws Exception {
        mockMvc.perform(get("/customer/bookings/" + booking.getId()).param("token", booking.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dịch vụ thêm")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(tour.getName())));
    }

    @Test
    void transferRates_onlyAdminCanEdit() throws Exception {
        mockMvc.perform(get("/admin/transfer-rates").with(as(staff))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/transfer-rates").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/service-orders").with(as(staff))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/bookings/" + booking.getId()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đặt tour hộ khách")));
    }
}
