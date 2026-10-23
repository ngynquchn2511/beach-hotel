package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.StayRuleRepository;
import com.hotel.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Luat ban phong: so dem toi thieu theo dip, dong ban theo loai phong, gioi han dat truoc; dat tai quay khong bi chan
class StayRuleIT extends ItFixtures {

    @Autowired private StayRuleRepository ruleRepository;
    @Autowired private BookingService bookingService;

    private User admin;
    private RoomType villa;
    private RoomType standard;
    private Room villaRoom;
    private Room standardRoom;
    private LocalDate holiday;

    @BeforeEach
    void seed() {
        admin = persistUser(UserRole.ADMIN, null);
        villa = persistRoomType("Villa Luat IT", 3_000_000, 4);
        standard = persistRoomType("Standard Luat IT", 900_000, 2);
        villaRoom = persistRoom(villa, 3_000_000, RoomStatus.AVAILABLE);
        standardRoom = persistRoom(standard, 900_000, RoomStatus.AVAILABLE);
        holiday = LocalDate.now().plusDays(60);
    }

    private ResultActions book(Room room, LocalDate in, LocalDate out) throws Exception {
        return mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(asNewCustomer())
                .param("roomId", room.getId().toString()).param("checkIn", in.toString()).param("checkOut", out.toString())
                .param("guests", "2").param("checkInTime", "14:00").param("checkOutTime", "12:00")
                .param("guestName", "Khách Luật").param("guestPhone", "0909001122").param("guestEmail", "luat" + seq() + "@mail.vn"));
    }

    private void addRule(String name, LocalDate from, LocalDate to, RoomType type, Integer minNights, boolean stopSell) throws Exception {
        var req = post("/admin/pricing/stay-rules").with(csrf()).with(as(admin))
                .param("name", name).param("startDate", from.toString()).param("endDate", to.toString())
                .param("stopSell", String.valueOf(stopSell));
        if (type != null) {
            req.param("roomTypeId", type.getId().toString());
        }
        if (minNights != null) {
            req.param("minNights", minNights.toString());
        }
        mockMvc.perform(req).andExpect(flash().attribute("successMessage", containsString("Đã thêm luật")));
    }

    @Test
    void minNights_byArrivalDate_strictestWins() throws Exception {
        addRule("Lễ 2/9", holiday, holiday.plusDays(2), null, 2, false);
        addRule("Lễ 2/9 villa", holiday, holiday, villa, 3, false);
        book(standardRoom, holiday, holiday.plusDays(1)).andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", containsString("tối thiểu 2 đêm")));
        book(villaRoom, holiday, holiday.plusDays(2)).andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", containsString("tối thiểu 3 đêm")));
        book(standardRoom, holiday, holiday.plusDays(2)).andExpect(status().is3xxRedirection());
        // Nhan phong truoc dip (khong thuoc luat) -> 1 dem van duoc
        book(villaRoom, holiday.minusDays(1), holiday).andExpect(status().is3xxRedirection());
    }

    @Test
    void stopSell_blocksAnyNightInRange_onlyForThatType_andSearchHidesIt() throws Exception {
        addRule("Bảo trì hồ bơi villa", holiday.plusDays(3), holiday.plusDays(5), villa, null, true);
        book(villaRoom, holiday.plusDays(1), holiday.plusDays(4)).andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", containsString("tạm ngừng nhận đặt")));
        // Tra phong dung ngay bat dau dong ban -> khong o dem nao trong khoang
        book(villaRoom, holiday.plusDays(1), holiday.plusDays(3)).andExpect(status().is3xxRedirection());
        book(standardRoom, holiday.plusDays(3), holiday.plusDays(4)).andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/customer/rooms").param("checkIn", holiday.plusDays(4).toString())
                        .param("checkOut", holiday.plusDays(6).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bảo trì hồ bơi villa")))
                .andExpect(content().string(not(containsString("/customer/rooms/" + villaRoom.getId() + "?"))));

        // Dat tai quay (le tan) khong bi chan
        Booking walkIn = bookingService.createWalkInBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), villaRoom.getId(),
                holiday.plusDays(4), holiday.plusDays(5), LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, "Khách quầy", "0909009999", "quay@mail.vn");
        assertThat(walkIn.getId()).isNotNull();
    }

    @Test
    void maxAdvanceDays_andAdminValidation() throws Exception {
        mockMvc.perform(post("/admin/pricing/max-advance").with(csrf()).with(as(admin)).param("maxAdvanceDays", "30"))
                .andExpect(flash().attribute("successMessage", containsString("30 ngày")));
        book(standardRoom, LocalDate.now().plusDays(31), LocalDate.now().plusDays(32)).andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", containsString("tối đa 30 ngày")));
        book(standardRoom, LocalDate.now().plusDays(30), LocalDate.now().plusDays(31)).andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/pricing/max-advance").with(csrf()).with(as(admin)).param("maxAdvanceDays", "0"))
                .andExpect(flash().attribute("successMessage", containsString("bỏ giới hạn")));
        book(standardRoom, LocalDate.now().plusDays(90), LocalDate.now().plusDays(91)).andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/pricing/stay-rules").with(csrf()).with(as(admin))
                        .param("name", "Rỗng").param("startDate", holiday.toString()).param("endDate", holiday.toString()))
                .andExpect(flash().attribute("errorMessage", containsString("số đêm tối thiểu hoặc đóng bán")));
        mockMvc.perform(post("/admin/pricing/stay-rules").with(csrf()).with(as(persistUser(UserRole.STAFF, null)))
                        .param("name", "X").param("startDate", holiday.toString()).param("endDate", holiday.toString()).param("stopSell", "true"))
                .andExpect(status().isForbidden());
        assertThat(ruleRepository.count()).isZero();
        mockMvc.perform(get("/admin/pricing").with(as(admin))).andExpect(content().string(containsString("Luật bán phòng")));
    }
}
