package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.FacilityReservationRepository;
import com.hotel.repository.SpaPackageRepository;
import com.hotel.service.StaffNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Khach danh gia spa / nha hang sau khi dung; diem trung binh hien cho admin va trong o chon goi spa
class FacilityRatingIT extends ItFixtures {

    @Autowired private FacilityReservationRepository reservationRepository;
    @Autowired private SpaPackageRepository spaPackageRepository;
    @Autowired private StaffNotificationService notificationService;

    private User guest;
    private Booking stay;
    private SpaPackage facial;

    @BeforeEach
    void seed() {
        guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Room room = persistRoom(persistRoomType("Hang DG IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        // Dang o: nhan phong 2 ngay truoc, tra phong 2 ngay nua
        stay = persistBooking(guest, room, BookingStatus.CHECKED_IN, -2, 4);
        facial = spaPackageRepository.findAll().stream().filter(p -> "Facial care".equals(p.getNameEn())).findFirst().orElseThrow();
    }

    private FacilityReservation reservation(FacilityReservation.Kind kind, LocalDate date, LocalTime time, ServiceStatus status) {
        return reservationRepository.save(FacilityReservation.builder().booking(stay).kind(kind)
                .spaPackage(kind == FacilityReservation.Kind.SPA ? facial : null).reservedDate(date).reservedTime(time).people(1)
                .totalPrice(kind == FacilityReservation.Kind.SPA ? facial.getPrice() : null).status(status).build());
    }

    private ResultActions rate(FacilityReservation r, String stars, String feedback) throws Exception {
        return mockMvc.perform(post("/customer/bookings/{id}/facility/{rid}/rate", stay.getId(), r.getId()).with(csrf()).with(as(guest))
                .param("rating", stars).param("feedback", feedback));
    }

    @Test
    void guestRatesUsedSpa_onlyOnce_averageShown() throws Exception {
        FacilityReservation done = reservation(FacilityReservation.Kind.SPA, LocalDate.now().minusDays(1), LocalTime.of(10, 0), ServiceStatus.CONFIRMED);
        mockMvc.perform(get("/customer/bookings/{id}", stay.getId()).with(as(guest)))
                .andExpect(content().string(containsString("Bạn thấy Chăm sóc da mặt thế nào?")));

        rate(done, "4", "Nhân viên nhẹ nhàng").andExpect(flash().attribute("facilitySuccess", containsString("Cảm ơn")));
        FacilityReservation saved = reservationRepository.findById(done.getId()).orElseThrow();
        assertThat(saved.getRating()).isEqualTo(4);
        assertThat(saved.getFeedback()).isEqualTo("Nhân viên nhẹ nhàng");
        assertThat(saved.getRatedAt()).isNotNull();
        rate(done, "5", "").andExpect(flash().attribute("facilityError", containsString("đã đánh giá")));

        // O chon goi spa hien diem trung binh; admin thay sao + gop y
        mockMvc.perform(get("/customer/bookings/{id}", stay.getId()).with(as(guest)))
                .andExpect(content().string(containsString("★4.0 (1)")));
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(get("/admin/facility-reservations").with(as(admin)).param("from", LocalDate.now().minusDays(3).toString()))
                .andExpect(content().string(containsString("★ 4.0 / 5 (1 đánh giá)")))
                .andExpect(content().string(containsString("Nhân viên nhẹ nhàng")));
        mockMvc.perform(get("/admin/spa-packages").with(as(admin)))
                .andExpect(content().string(containsString("★ 4.0 / 5 · 1 đánh giá")));
    }

    @Test
    void cannotRate_futurePendingOrInvalidStars() throws Exception {
        FacilityReservation future = reservation(FacilityReservation.Kind.RESTAURANT, LocalDate.now().plusDays(1), LocalTime.of(19, 0), ServiceStatus.CONFIRMED);
        FacilityReservation pending = reservation(FacilityReservation.Kind.RESTAURANT, LocalDate.now().minusDays(1), LocalTime.of(19, 0), ServiceStatus.PENDING);
        FacilityReservation done = reservation(FacilityReservation.Kind.RESTAURANT, LocalDate.now().minusDays(1), LocalTime.of(12, 0), ServiceStatus.CONFIRMED);
        rate(future, "5", "").andExpect(flash().attribute("facilityError", containsString("sau khi dùng")));
        rate(pending, "5", "").andExpect(flash().attribute("facilityError", containsString("sau khi dùng")));
        rate(done, "6", "").andExpect(flash().attribute("facilityError", containsString("1 đến 5")));
        rate(done, "3", "x".repeat(501)).andExpect(flash().attribute("facilityError", containsString("500")));
        // Khach khac khong danh gia duoc lich cua don nay
        User stranger = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        mockMvc.perform(post("/customer/bookings/{id}/facility/{rid}/rate", stay.getId(), done.getId()).with(csrf()).with(as(stranger))
                .param("rating", "1"));
        assertThat(reservationRepository.findById(done.getId()).orElseThrow().getRating()).isNull();
    }

    @Test
    void lowRating_alertsFrontDesk() throws Exception {
        long start = notificationService.lastId();
        FacilityReservation done = reservation(FacilityReservation.Kind.RESTAURANT, LocalDate.now().minusDays(1), LocalTime.of(12, 0), ServiceStatus.CONFIRMED);
        rate(done, "2", "Món ra chậm, đồ nguội").andExpect(flash().attributeExists("facilitySuccess"));
        TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(s -> s.getClass().getName().startsWith(StaffNotificationService.class.getName()))
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(notificationService.recent(start)).singleElement().satisfies(n -> {
            assertThat(n.level()).isEqualTo(StaffNotificationService.Level.WARNING);
            assertThat(n.title()).startsWith("Khách chấm 2 sao nhà hàng");
            assertThat(n.message()).isEqualTo("Món ra chậm, đồ nguội");
        });
    }

    @Test
    void publicServicesPage_showsPackagesRatingsAndShortGuestNames() throws Exception {
        stay.setGuestName("Nguyễn Thị Lan");
        bookingRepository.save(stay);
        FacilityReservation done = reservation(FacilityReservation.Kind.SPA, LocalDate.now().minusDays(1), LocalTime.of(10, 0), ServiceStatus.CONFIRMED);
        rate(done, "5", "Chuyên viên tay nghề rất tốt");
        FacilityReservation meh = reservation(FacilityReservation.Kind.RESTAURANT, LocalDate.now().minusDays(1), LocalTime.of(12, 0), ServiceStatus.CONFIRMED);
        rate(meh, "3", "Bình thường");

        mockMvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chăm sóc da mặt")))
                .andExpect(content().string(containsString("★ 5.0")))
                .andExpect(content().string(containsString("Chuyên viên tay nghề rất tốt")))
                .andExpect(content().string(containsString("Lan N.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Nguyễn Thị Lan"))))
                // Gop y 3 sao khong dua len trang cong khai
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Bình thường"))));
    }
}
