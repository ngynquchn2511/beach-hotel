package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.DutyShiftRepository;
import com.hotel.service.HousekeepingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Giao viec buong phong: le tan chon nguoi kiem phong truoc khi tra (goi y nguoi trong ca, it viec),
// chi nguoi duoc giao bao ket qua; tu chia phong cho don cho nhung nguoi dang trong ca
class HousekeepingTeamIT extends ItFixtures {

    @Autowired private DutyShiftRepository dutyRepository;
    @Autowired private HousekeepingService housekeepingService;

    private User staff;
    private User lan;
    private User mai;
    private User offShift;
    private Booking leaving;

    @BeforeEach
    void seed() {
        staff = persistUser(UserRole.STAFF, null);
        lan = persistUser(UserRole.HOUSEKEEPER, null);
        mai = persistUser(UserRole.HOUSEKEEPER, null);
        offShift = persistUser(UserRole.HOUSEKEEPER, null);
        Room room = persistRoom(persistRoomType("Hang Doi Buong IT", 800_000, 2), 800_000, RoomStatus.OCCUPIED);
        leaving = persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), room, BookingStatus.CHECKED_IN, -2, 2);
    }

    private void onCurrentShift(User u) {
        LocalDateTime now = LocalDateTime.now();
        dutyRepository.save(DutyShift.builder().staff(u).workDate(DutyShift.Shift.workDateAt(now))
                .shift(DutyShift.Shift.at(now.toLocalTime())).createdBy("Test").build());
    }

    private Booking reload() {
        return bookingRepository.findById(leaving.getId()).orElseThrow();
    }

    @Test
    void checkoutPage_suggestsHousekeeperOnShift_andAssignedOneReports() throws Exception {
        onCurrentShift(mai);
        mockMvc.perform(get("/admin/bookings/{id}/check-out", leaving.getId()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giao kiểm phòng")))
                .andExpect(content().string(containsString(mai.getFullName())));
        HousekeepingService.Member suggested = housekeepingService.recommended(housekeepingService.team(LocalDateTime.now()));
        assertThat(suggested.user().getId()).isEqualTo(mai.getId());

        mockMvc.perform(post("/admin/bookings/{id}/room-check", leaving.getId()).with(csrf()).with(as(staff))
                        .param("assigneeId", mai.getId().toString()))
                .andExpect(flash().attribute("successMessage", containsString("Đã giao " + mai.getFullName())));
        assertThat(reload().getRoomCheckAssignee().getId()).isEqualTo(mai.getId());

        // Nguoi khac khong bao thay duoc; "Viec cua toi" cua nguoi khac khong thay yeu cau nay
        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}", leaving.getId()).with(csrf()).with(as(lan)).param("issue", "false"))
                .andExpect(flash().attribute("errorMessage", containsString("đã giao cho " + mai.getFullName())));
        assertThat(housekeepingService.pendingRoomChecks(lan.getId())).isEmpty();
        assertThat(housekeepingService.pendingRoomChecks(mai.getId())).hasSize(1);

        // Le tan doi nguoi kiem ngay tren man tra phong
        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}/assign", leaving.getId()).with(csrf()).with(as(staff))
                        .param("assigneeId", lan.getId().toString()).param("back", "checkout"))
                .andExpect(redirectedUrl("/admin/bookings/" + leaving.getId() + "/check-out#kiem-phong"));
        assertThat(reload().getRoomCheckAssignee().getId()).isEqualTo(lan.getId());
        mockMvc.perform(get("/admin/bookings/{id}/check-out", leaving.getId()).with(as(staff)))
                .andExpect(content().string(containsString("Đang chờ " + lan.getFullName() + " kiểm phòng")));

        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}", leaving.getId()).with(csrf()).with(as(lan))
                        .param("issue", "true").param("note", "Uống 2 lon bia minibar"))
                .andExpect(flash().attribute("successMessage", containsString("Đã báo")));
        assertThat(reload().getRoomCheckedBy()).isEqualTo(lan.getFullName());
        assertThat(reload().isRoomCheckHasIssue()).isTrue();

        // Buong phong khong tu doi nguoi kiem duoc
        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}/assign", leaving.getId()).with(csrf()).with(as(lan)))
                .andExpect(status().isForbidden());
    }

    @Test
    void roomCheck_assigneeMustBeHousekeeper_unassignedMeansAnyone() throws Exception {
        mockMvc.perform(post("/admin/bookings/{id}/room-check", leaving.getId()).with(csrf()).with(as(staff))
                        .param("assigneeId", staff.getId().toString()))
                .andExpect(flash().attribute("errorMessage", containsString("Buồng phòng")));
        assertThat(reload().getRoomCheckRequestedAt()).isNull();

        mockMvc.perform(post("/admin/bookings/{id}/room-check", leaving.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("báo chung")));
        assertThat(reload().getRoomCheckAssignee()).isNull();
        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}", leaving.getId()).with(csrf()).with(as(offShift)).param("issue", "false"))
                .andExpect(flash().attribute("successMessage", containsString("Đã báo")));
    }

    @Test
    void autoAssign_splitsUnassignedRoomsAmongOnShift() throws Exception {
        RoomType type = persistRoomType("Hang Chia IT", 700_000, 2);
        List<Room> dirty = List.of(persistRoom(type, 700_000, RoomStatus.NOT_READY), persistRoom(type, 700_000, RoomStatus.NOT_READY),
                persistRoom(type, 700_000, RoomStatus.NOT_READY), persistRoom(type, 700_000, RoomStatus.NOT_READY));

        // Chua ai trong ca -> bao loi, khong giao
        mockMvc.perform(post("/admin/housekeeping/auto-assign").with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("trong ca")));

        onCurrentShift(lan);
        onCurrentShift(mai);
        mockMvc.perform(post("/admin/housekeeping/auto-assign").with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("Đã chia")));
        Map<Long, Long> perPerson = dirty.stream().map(r -> roomRepository.findById(r.getId()).orElseThrow())
                .map(r -> r.getCleaningAssignee().getId())
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        assertThat(perPerson.keySet()).containsExactlyInAnyOrder(lan.getId(), mai.getId());
        assertThat(perPerson.values()).containsOnly(2L);

        // Bang don phong hien doi buong phong + khoi luong viec
        mockMvc.perform(get("/admin/housekeeping").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đội buồng phòng")))
                .andExpect(content().string(containsString("Chờ 2 phòng")));

        // Buong phong khong tu chia duoc
        mockMvc.perform(post("/admin/housekeeping/auto-assign").with(csrf()).with(as(lan))).andExpect(status().isForbidden());
    }
}
