package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.DivingTourRepository;
import com.hotel.service.HaLongSpot;
import com.hotel.service.TripPlanService;
import com.hotel.service.WeatherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Ke hoach tung ngay o trang Goi y phong: 24 gio, 4 buoi, diem du lich theo thoi tiet, phong trong dem do, dat tour dung ngay
class TripPlanIT extends ItFixtures {

    @Autowired private WeatherService weather;
    @Autowired private TripPlanService tripPlan;
    @Autowired private DivingTourRepository tourRepository;

    @AfterEach
    void off() {
        weather.useFetcherForTest(null);
    }

    private DivingTour tour(String name) {
        return tourRepository.save(DivingTour.builder().name(name).nameEn(name).description("d").pricePerPerson(BigDecimal.valueOf(500_000))
                .departureTime(LocalTime.of(8, 0)).durationHours(4).maxParticipants(20).active(true).build());
    }

    @Test
    void spotsFollowWeather_andRoomsFreeThatNight() {
        LocalDate today = LocalDate.now();
        weather.useFetcherForTest(uri -> WeatherIT.json(today));
        DivingTour titop = tour("Tắm biển đảo Ti Tốp IT");
        RoomType type = persistRoomType("Hang Ke Hoach IT", 800_000, 2);
        Room free = persistRoom(type, 800_000, RoomStatus.AVAILABLE);
        Room taken = persistRoom(type, 800_000, RoomStatus.AVAILABLE);
        persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), taken, BookingStatus.CONFIRMED, 2, 1);

        List<WeatherService.Day> days = weather.next(7);
        Map<LocalDate, TripPlanService.DayPlan> plans = tripPlan.plan(days, 2);
        assertThat(plans).hasSize(7);

        // 24 gio + 4 buoi
        WeatherService.Day d2 = days.get(2);
        assertThat(d2.getAllHours()).hasSize(24);
        assertThat(d2.getPeriods()).extracting(WeatherService.Period::key).containsExactly("night", "morning", "afternoon", "evening");

        // Ngay giong (hom nay): khong ra bien / vinh
        assertThat(plans.get(today).spots()).extracting(TripPlanService.SpotPick::spot)
                .noneMatch(s -> s.getKind() == HaLongSpot.Kind.SEA || s.getKind() == HaLongSpot.Kind.BEACH);
        // Ngay mua (+1): bao tang / spa len dau
        assertThat(plans.get(today.plusDays(1)).spots()).extracting(TripPlanService.SpotPick::spot).contains(HaLongSpot.MUSEUM)
                .doesNotContain(HaLongSpot.BAI_CHAY);
        // Ngay nang (+2): tam bien, du thuyen, Ti Top (gan tour cua resort), gio dep nhat 09:00
        TripPlanService.DayPlan sunny = plans.get(today.plusDays(2));
        assertThat(sunny.spots()).extracting(TripPlanService.SpotPick::spot).contains(HaLongSpot.BAI_CHAY, HaLongSpot.BAY_CRUISE);
        TripPlanService.SpotPick ti = sunny.spots().stream().filter(s -> s.spot() == HaLongSpot.TI_TOP).findFirst().orElseThrow();
        assertThat(ti.tour().getId()).isEqualTo(titop.getId());
        assertThat(ti.bestHour().hour()).isEqualTo(9);

        // Phong trong dem +2: chi con 1 phong cua loai nay
        assertThat(sunny.rooms()).filteredOn(r -> r.type().getId().equals(type.getId()))
                .singleElement().satisfies(r -> {
                    assertThat(r.available()).isEqualTo(1);
                    assertThat(r.room().getId()).isEqualTo(free.getId());
                });
        // Qua so khach toi da thi khong goi y
        assertThat(tripPlan.plan(days, 3).get(today.plusDays(2)).rooms()).noneMatch(r -> r.type().getId().equals(type.getId()));
    }

    @Test
    void suggestPageShowsDayPlan_andTourLinkCarriesDate() throws Exception {
        LocalDate today = LocalDate.now();
        weather.useFetcherForTest(uri -> WeatherIT.json(today));
        DivingTour titop = tour("Ti Tốp lặn san hô IT");
        RoomType type = persistRoomType("Hang Ke Hoach Trang IT", 700_000, 4);
        Room room = persistRoom(type, 700_000, RoomStatus.AVAILABLE);
        LocalDate d2 = today.plusDays(2);

        mockMvc.perform(get("/rooms/suggest").param("guests", "2").param("checkIn", d2.toString())
                        .param("checkOut", d2.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thời tiết từng giờ (24 giờ)")))
                .andExpect(content().string(containsString("Sáng (6 – 12h)")))
                .andExpect(content().string(containsString("Bảo tàng Quảng Ninh")))
                .andExpect(content().string(containsString("Đảo Ti Tốp")))
                .andExpect(content().string(containsString("Phòng còn trống đêm")))
                .andExpect(content().string(containsString("/customer/rooms/" + room.getId() + "?checkIn=" + d2 + "&amp;checkOut=" + d2.plusDays(1))))
                .andExpect(content().string(containsString("/customer/services/tours/" + titop.getId() + "?date=" + d2)))
                .andExpect(content().string(containsString("google.com/maps/dir")))
                // ngay khach chon nam trong 7 ngay -> mo san
                .andExpect(content().string(containsString("data-wx-open=\"2\"")));

        // Dat tour tu ke hoach ngay: khach co 1 don bao trum ngay do -> mo form tour voi ngay dien san
        User c = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Booking b = persistBooking(c, room, BookingStatus.CONFIRMED, 1, 3);
        mockMvc.perform(get("/customer/services/tours/" + titop.getId()).param("date", d2.toString()).with(as(c)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings/" + b.getId() + "?tourId=" + titop.getId() + "&tourDate=" + d2 + "#dich-vu"));
        mockMvc.perform(get("/customer/bookings/" + b.getId()).param("tourId", titop.getId().toString())
                        .param("tourDate", d2.toString()).with(as(c)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("min=\"" + b.getCheckInDate()
                        + "\" max=\"" + b.getCheckOutDate() + "\" value=\"" + d2 + "\"")));
    }
}
