package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.GuestPreferenceRepository;
import com.hotel.service.RoomSuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Goi y phong theo so thich: cham diem theo so nguoi / ngan sach / canh / uu tien, loc phong trong theo ngay, luu so thich cho khach
class RoomSuggestionIT extends ItFixtures {

    @Autowired private RoomSuggestionService service;
    @Autowired private GuestPreferenceRepository preferenceRepository;

    private RoomType type(String name, long price, int guests, int area, String amenities) {
        RoomType t = persistRoomType(name, price, guests);
        t.setArea(BigDecimal.valueOf(area));
        t.setAmenities(amenities);
        return roomTypeRepository.save(t);
    }

    @Test
    void ranksByPreferences_respectsCapacity_budget_andDates() {
        RoomType sea = type("Goi Y Deluxe view biển IT", 1_100_000, 2, 32, "Ban công hướng biển, bồn tắm, Wi-Fi");
        RoomType garden = type("Goi Y Superior hướng vườn IT", 800_000, 2, 28, "Cửa sổ hướng vườn, vòi sen, Wi-Fi");
        RoomType family = type("Goi Y Gia đình IT", 1_600_000, 4, 48, "2 giường đôi, sofa, ban công hướng biển");
        Room seaRoom = persistRoom(sea, 1_100_000, RoomStatus.AVAILABLE);
        persistRoom(garden, 800_000, RoomStatus.AVAILABLE);
        persistRoom(family, 1_600_000, RoomStatus.AVAILABLE);

        // Cap doi thich bien + bon tam, ngan sach 1,2 trieu -> Deluxe view bien dung dau
        var couple = service.suggest(new RoomSuggestionService.Answers(2, GuestPreference.Purpose.COUPLE, 1_200_000L,
                GuestPreference.View.SEA, EnumSet.of(GuestPreference.Priority.BATHTUB), null, null));
        var best = couple.top().get(0);
        assertThat(best.type().getId()).isEqualTo(sea.getId());
        assertThat(best.match()).isGreaterThanOrEqualTo(90);
        assertThat(best.reasons()).extracting(RoomSuggestionService.Reason::key)
                .contains("suggest.reason.inBudget", "suggest.reason.view.SEA", "suggest.reason.priority.BATHTUB");

        // 4 nguoi -> chi loai Gia dinh (cac loai 2 nguoi bi loai)
        var fam = service.suggest(new RoomSuggestionService.Answers(4, GuestPreference.Purpose.FAMILY, null, null, Set.of(), null, null));
        assertThat(fam.top()).allMatch(s -> s.type().getMaxGuests() >= 4);
        assertThat(fam.top()).anyMatch(s -> s.type().getId().equals(family.getId()));

        // Huong vuon, ngan sach 900k -> Superior vuon; loai Gia dinh vuot ngan sach co ly do
        var g = service.suggest(new RoomSuggestionService.Answers(2, null, 900_000L, GuestPreference.View.GARDEN, Set.of(), null, null));
        assertThat(g.top().get(0).type().getId()).isEqualTo(garden.getId());

        // Co ngay o: phong Deluxe bien da kin -> khong goi y loai do nua
        LocalDate in = LocalDate.now().plusDays(40);
        Booking b = persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), seaRoom, BookingStatus.CONFIRMED, 40, 2);
        assertThat(b.getCheckInDate()).isEqualTo(in);
        var byDates = service.suggest(new RoomSuggestionService.Answers(2, GuestPreference.Purpose.COUPLE, 1_200_000L,
                GuestPreference.View.SEA, Set.of(), in, in.plusDays(2)));
        assertThat(byDates.byDates()).isTrue();
        assertThat(byDates.top()).noneMatch(s -> s.type().getId().equals(sea.getId()));
    }

    @Test
    void page_showsSuggestions_savesPreferenceForCustomer_prefillsNextTime_staffSeesIt() throws Exception {
        RoomType sea = type("Goi Y Trang IT", 1_000_000, 2, 30, "Ban công hướng biển");
        Room room = persistRoom(sea, 1_000_000, RoomStatus.AVAILABLE);

        // Khach vang lai: xem duoc, khong luu
        mockMvc.perform(get("/rooms/suggest")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Gợi ý cho tôi")));
        mockMvc.perform(get("/rooms/suggest").param("guests", "2").param("view", "SEA").param("purpose", "COUPLE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hợp nhất")))
                .andExpect(content().string(containsString("Nhìn ra biển / vịnh")));

        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        mockMvc.perform(get("/rooms/suggest").with(as(guest)).param("guests", "3").param("purpose", "FAMILY")
                        .param("budget", "2000000").param("view", "SEA").param("priorities", "BALCONY", "KHONG_CO")
                        .param("note", "Tầng cao"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đã lưu sở thích")));
        GuestPreference p = preferenceRepository.findByUserId(guest.getId()).orElseThrow();
        assertThat(p.getGuests()).isEqualTo(3);
        assertThat(p.getMaxBudget()).isEqualTo(2_000_000L);
        assertThat(p.getPrioritySet()).containsExactly(GuestPreference.Priority.BALCONY);
        assertThat(p.getNote()).isEqualTo("Tầng cao");

        mockMvc.perform(get("/rooms/suggest").with(as(guest))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Đã điền sẵn sở thích")));

        // Le tan thay so thich o trang don cua khach
        Booking b = persistBooking(guest, room, BookingStatus.CONFIRMED, 5, 1);
        mockMvc.perform(get("/admin/bookings/" + b.getId()).with(as(persistUser(UserRole.STAFF, null))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sở thích khách tự khai")))
                .andExpect(content().string(containsString("Tầng cao")));

        // Tieng Anh
        mockMvc.perform(get("/rooms/suggest").param("lang", "en")).andExpect(content().string(containsString("Suggest rooms")));
    }
}
