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
                .andExpect(content().string(containsString("Đã giao " + lan.getFullName() + " - chờ bấm «Nhận kiểm»")));

        // Lan bam "Nhan kiem" -> le tan thay dang len phong; Mai (nguoi cu) khong nhan thay duoc
        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}/accept", leaving.getId()).with(csrf()).with(as(mai)))
                .andExpect(flash().attribute("errorMessage", containsString("đã giao cho " + lan.getFullName())));
        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}/accept", leaving.getId()).with(csrf()).with(as(lan)))
                .andExpect(flash().attribute("successMessage", containsString("Đã nhận kiểm")));
        assertThat(reload().isRoomCheckAccepted()).isTrue();
        mockMvc.perform(get("/admin/bookings/{id}/check-out", leaving.getId()).with(as(staff)))
                .andExpect(content().string(containsString(lan.getFullName() + " đang lên kiểm phòng")));

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
    void roomCheck_alwaysHasOwner_noOneOnShift_frontDeskChecksItself() throws Exception {
        // Chi giao cho tai khoan buong phong
        mockMvc.perform(post("/admin/bookings/{id}/room-check", leaving.getId()).with(csrf()).with(as(staff))
                        .param("assigneeId", staff.getId().toString()))
                .andExpect(flash().attribute("errorMessage", containsString("Buồng phòng")));
        // Khong chon ai + khong ai trong ca -> khong "bao chung" ma bao loi
        mockMvc.perform(post("/admin/bookings/{id}/room-check", leaving.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("lễ tân tự kiểm")));
        assertThat(reload().getRoomCheckRequestedAt()).isNull();

        // Khong chon ai nhung co nguoi trong ca -> he thong giao nguoi do
        onCurrentShift(mai);
        mockMvc.perform(post("/admin/bookings/{id}/room-check", leaving.getId()).with(csrf()).with(as(staff)));
        assertThat(reload().getRoomCheckAssignee().getId()).isEqualTo(mai.getId());

        // Le tan tu kiem va ghi ket qua
        mockMvc.perform(post("/admin/bookings/{id}/room-check/self", leaving.getId()).with(csrf()).with(as(staff))
                        .param("issue", "true").param("note", "Vỡ 1 ly"))
                .andExpect(redirectedUrl("/admin/bookings/" + leaving.getId() + "/check-out#kiem-phong"));
        Booking b = reload();
        assertThat(b.isRoomCheckDone()).isTrue();
        assertThat(b.getRoomCheckNote()).isEqualTo("Vỡ 1 ly");
        assertThat(b.getRoomCheckedBy()).contains("lễ tân");
        mockMvc.perform(post("/admin/bookings/{id}/room-check/self", leaving.getId()).with(csrf()).with(as(lan))
                .param("issue", "false")).andExpect(status().isForbidden());
    }

    // Qua 3 phut chua bam "Nhan kiem" -> chuyen nguoi khac trong ca; het nguoi -> bao le tan (khong treo mai)
    @Test
    void roomCheck_notAccepted_escalatesThenGivesUp() throws Exception {
        onCurrentShift(lan);
        onCurrentShift(mai);
        mockMvc.perform(post("/admin/bookings/{id}/room-check", leaving.getId()).with(csrf()).with(as(staff))
                .param("assigneeId", lan.getId().toString()));
        LocalDateTime now = LocalDateTime.now();
        assertThat(housekeepingService.escalateRoomChecks(now.plusMinutes(1))).isZero();

        assertThat(housekeepingService.escalateRoomChecks(now.plusMinutes(4))).isEqualTo(1);
        assertThat(reload().getRoomCheckAssignee().getId()).isEqualTo(mai.getId());
        assertThat(reload().getRoomCheckAutoMoves()).isEqualTo(1);

        // Mai cung khong nhan -> chuyen lai Lan (nguoi khac Mai), lan 3 thi bo cuoc, bao le tan
        housekeepingService.escalateRoomChecks(now.plusMinutes(8));
        assertThat(reload().getRoomCheckAssignee().getId()).isEqualTo(lan.getId());
        housekeepingService.escalateRoomChecks(now.plusMinutes(12));
        assertThat(reload().isRoomCheckStuck()).isTrue();
        assertThat(housekeepingService.escalateRoomChecks(now.plusMinutes(20))).isZero();
        mockMvc.perform(get("/admin/bookings/{id}/check-out", leaving.getId()).with(as(staff)))
                .andExpect(content().string(containsString("Không ai nhận kiểm phòng")));

        // Da nhan thi khong tu chuyen nua
        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}/assign", leaving.getId()).with(csrf()).with(as(staff))
                .param("assigneeId", mai.getId().toString()));
        assertThat(reload().isRoomCheckStuck()).isFalse();
        housekeepingService.acceptRoomCheck(leaving.getId(), mai);
        assertThat(housekeepingService.escalateRoomChecks(now.plusMinutes(30))).isZero();
        assertThat(reload().getRoomCheckAssignee().getId()).isEqualTo(mai.getId());
    }

    // Khach tra phong -> phong cho don duoc giao ngay cho nguoi trong ca; nguoi het ca chua bat dau -> chuyen nguoi khac
    @Test
    void checkout_assignsRoomImmediately_andHandsOverWhenAssigneeOffShift() throws Exception {
        onCurrentShift(mai);
        mockMvc.perform(post("/admin/bookings/{id}/check-out", leaving.getId()).with(csrf()).with(as(staff))
                .param("paymentMethod", "CASH"));
        Room room = roomRepository.findById(leaving.getRoom().getId()).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.NOT_READY);
        assertThat(room.getCleaningAssignee().getId()).isEqualTo(mai.getId());
        assertThat(room.getCleaningAssignedAt()).isNotNull();

        // Giao cho nguoi khong trong ca, 31 phut sau van chua bat dau -> chuyen cho Mai
        room.setCleaningAssignee(offShift);
        room.setCleaningAssignedAt(LocalDateTime.now().minusMinutes(31));
        roomRepository.save(room);
        assertThat(housekeepingService.dispatchRooms(LocalDateTime.now(), null)).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getCleaningAssignee().getId()).isEqualTo(mai.getId());
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
