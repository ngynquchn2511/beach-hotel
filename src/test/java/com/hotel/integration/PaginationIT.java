package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.DivingTourRepository;
import com.hotel.repository.FavoriteRoomRepository;
import com.hotel.repository.SpecialRateRepository;
import com.hotel.repository.TourBookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Cac trang co danh sach deu chia trang (Pager + fragments/pagination :: pager)
class PaginationIT extends ItFixtures {

    @Autowired private DivingTourRepository tourRepository;
    @Autowired private TourBookingRepository tourBookingRepository;
    @Autowired private SpecialRateRepository specialRateRepository;
    @Autowired private FavoriteRoomRepository favoriteRoomRepository;

    private DivingTour persistTour(boolean active) {
        return tourRepository.save(DivingTour.builder().name("Tour IT " + seq()).nameEn("Tour IT")
                .pricePerPerson(BigDecimal.valueOf(500000)).departureTime(LocalTime.of(8, 0)).durationHours(4)
                .maxParticipants(50).active(active).build());
    }

    @Test
    void guestRegistrations_10PerPage_numberingContinues() throws Exception {
        User staff = persistUser(UserRole.STAFF, null);
        User customer = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        Room room = persistRoom(persistRoomType("Hang KBLT IT", 800000, 30), 800000, RoomStatus.AVAILABLE);
        Booking booking = persistBooking(customer, room, BookingStatus.CHECKED_IN, 0, 2);
        for (int i = 0; i < 25; i++) {
            declareGuest(booking);
        }
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/admin/guest-registrations").param("from", today).param("to", today).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("guests", hasSize(10)))
                .andExpect(content().string(containsString("Tổng: 25 khách")))
                .andExpect(content().string(containsString("from=" + today + "&amp;to=" + today + "&amp;page=2")));
        mockMvc.perform(get("/admin/guest-registrations").param("from", today).param("to", today)
                        .param("page", "3").with(as(staff)))
                .andExpect(model().attribute("guests", hasSize(5)))
                .andExpect(content().string(containsString("<td>21</td>")));
    }

    @Test
    void serviceOrders_eachTableHasItsOwnPage_andActionsReturnToSamePage() throws Exception {
        User staff = persistUser(UserRole.STAFF, null);
        User customer = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        Room room = persistRoom(persistRoomType("Hang DV IT", 800000, 4), 800000, RoomStatus.AVAILABLE);
        Booking booking = persistBooking(customer, room, BookingStatus.CONFIRMED, 3, 5);
        DivingTour tour = persistTour(true);
        for (int i = 0; i < 12; i++) {
            tourBookingRepository.save(TourBooking.builder().booking(booking).tour(tour)
                    .tourDate(LocalDate.now().plusDays(4)).participants(1)
                    .unitPrice(BigDecimal.valueOf(500000)).totalPrice(BigDecimal.valueOf(500000))
                    .status(ServiceStatus.PENDING).build());
        }

        mockMvc.perform(get("/admin/service-orders").param("tourPage", "2").param("transferPage", "3").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tours", hasSize(2)))
                .andExpect(content().string(containsString("Hiển thị 11–12 trên tổng 12")))
                // Link trang tour giu nguyen trang cua bang xe
                .andExpect(content().string(containsString("transferPage=3&amp;tourPage=1")))
                // Xac nhan / huy xong quay lai dung trang 2
                .andExpect(content().string(containsString("name=\"back\" value=\"/admin/service-orders?transferPage=3&amp;tourPage=2\"")));
    }

    @Test
    void specialRates_paginated() throws Exception {
        User admin = persistUser(UserRole.ADMIN, null);
        for (int i = 0; i < 12; i++) {
            LocalDate start = LocalDate.now().plusDays(100 + i * 3);
            specialRateRepository.save(SpecialRate.builder().name("Dip IT " + i).startDate(start)
                    .endDate(start.plusDays(1)).surchargePercent(20).build());
        }

        mockMvc.perform(get("/admin/pricing").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("specialRates", hasSize(10)))
                .andExpect(content().string(containsString("/admin/pricing?page=2")));
    }

    @Test
    void favorites_9PerPage() throws Exception {
        User customer = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        RoomType type = persistRoomType("Hang YT IT", 700000, 2);
        for (int i = 0; i < 11; i++) {
            favoriteRoomRepository.save(FavoriteRoom.builder().user(customer)
                    .room(persistRoom(type, 700000, RoomStatus.AVAILABLE)).build());
        }

        mockMvc.perform(get("/customer/favorites").with(as(customer)))
                .andExpect(model().attribute("rooms", hasSize(9)));
        mockMvc.perform(get("/customer/favorites").param("page", "2").with(as(customer)))
                .andExpect(model().attribute("rooms", hasSize(2)))
                .andExpect(content().string(containsString("Hiển thị 10–11 trên tổng 11")));
    }

    @Test
    void servicesPage_tours6PerPage_hiddenToursNotCounted() throws Exception {
        for (int i = 0; i < 7; i++) {
            persistTour(true);
        }
        persistTour(false);

        mockMvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tours", hasSize(6)))
                .andExpect(content().string(containsString("/services?page=2")));
        mockMvc.perform(get("/services").param("page", "2"))
                .andExpect(model().attribute("tours", hasSize(1)));
    }

    @Test
    void shortList_hasNoPager() throws Exception {
        persistTour(true);

        mockMvc.perform(get("/services"))
                .andExpect(content().string(not(containsString("aria-label=\"Phân trang\""))));
    }

    @Test
    void roomCalendar_10RoomsPerPage_navigationKeepsPage() throws Exception {
        User staff = persistUser(UserRole.STAFF, null);
        RoomType type = persistRoomType("Hang Lich IT", 700000, 2);
        for (int i = 0; i < 25; i++) {
            persistRoom(type, 700000, RoomStatus.AVAILABLE);
        }
        long total = roomRepository.count();

        mockMvc.perform(get("/admin/room-calendar").param("page", "2").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hiển thị 11–" + Math.min(20, total) + " trên tổng " + total)))
                // Nut sang khoang ngay sau van o trang 2
                .andExpect(content().string(containsString("days=14&amp;page=2")));
    }

    // Trang du bao cong suat mo duoc cho le tan, chon 14 / 30 / 60 ngay
    @Test
    void forecastPage_renders() throws Exception {
        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(get("/admin/forecast").param("days", "14").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dự báo công suất")))
                .andExpect(content().string(containsString("Theo hạng phòng")));
        mockMvc.perform(get("/admin/forecast").param("days", "999").with(as(staff)))
                .andExpect(model().attribute("days", 30));
    }
}
