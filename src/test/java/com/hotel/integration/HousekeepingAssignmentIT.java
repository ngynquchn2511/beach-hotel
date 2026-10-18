package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.PricingSettingsRepository;
import com.hotel.service.StaffNotificationService;
import com.hotel.service.StaffNotificationService.Audience;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Phan cong don phong, buoc kiem phong, thong bao tuc thoi theo nhom (le tan / buong phong)
class HousekeepingAssignmentIT extends ItFixtures {

    @Autowired private PricingSettingsRepository settingsRepository;
    @Autowired private StaffNotificationService notificationService;
    @Autowired private com.hotel.service.HousekeepingService housekeepingService;

    private User staff;
    private User lan;
    private User minh;
    private Room room;
    private long start;

    @BeforeEach
    void seed() {
        staff = persistUser(UserRole.STAFF, null);
        lan = persistUser(UserRole.HOUSEKEEPER, null);
        minh = persistUser(UserRole.HOUSEKEEPER, null);
        room = persistRoom(persistRoomType("Hang PC IT", 900_000, 2), 900_000, RoomStatus.NOT_READY);
        room.setDirtySince(LocalDateTime.now().minusHours(1));
        roomRepository.save(room);
        start = notificationService.lastId();
    }

    private List<StaffNotificationService.Notice> committed() {
        TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(s -> s.getClass().getName().startsWith(StaffNotificationService.class.getName()))
                .forEach(TransactionSynchronization::afterCommit);
        return notificationService.recent(start);
    }

    private void inspection(boolean on) throws Exception {
        mockMvc.perform(post("/admin/housekeeping/settings").with(csrf()).with(as(staff)).param("inspectionRequired", String.valueOf(on)));
    }

    @Test
    void assign_onlyAssigneeCanStart_notifiesAssigneeOnly() throws Exception {
        mockMvc.perform(post("/admin/housekeeping/{id}/assign", room.getId()).with(csrf()).with(as(staff)).param("assigneeId", lan.getId().toString()))
                .andExpect(flash().attribute("successMessage", containsString("Đã giao")));
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getCleaningAssignee().getId()).isEqualTo(lan.getId());

        mockMvc.perform(post("/admin/housekeeping/{id}/start", room.getId()).with(csrf()).with(as(minh)))
                .andExpect(flash().attribute("errorMessage", containsString("đã giao cho")));
        mockMvc.perform(post("/admin/housekeeping/{id}/start", room.getId()).with(csrf()).with(as(lan)))
                .andExpect(flash().attribute("successMessage", containsString("Đã nhận dọn")));

        StaffNotificationService.Notice n = committed().stream().filter(x -> x.title().contains("được giao")).findFirst().orElseThrow();
        assertThat(n.visibleTo(EnumSet.of(Audience.HOUSEKEEPING), lan.getId())).isTrue();
        assertThat(n.visibleTo(EnumSet.of(Audience.HOUSEKEEPING), minh.getId())).isFalse();
        assertThat(n.visibleTo(EnumSet.of(Audience.FRONT_DESK), staff.getId())).isFalse();

        // Nhan vien buong khong giao phong / doi cai dat duoc; xem "Phong cua toi"
        mockMvc.perform(post("/admin/housekeeping/{id}/assign", room.getId()).with(csrf()).with(as(lan))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/housekeeping/settings").with(csrf()).with(as(lan))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/housekeeping").with(as(minh)).param("mine", "true"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Phòng " + room.getRoomNumber() + "<"))));
    }

    @Test
    void inspectionOn_housekeeperFinish_waits_failReturns_passCleans() throws Exception {
        inspection(true);
        assertThat(settingsRepository.findById(PricingSettings.SINGLETON_ID).orElseThrow().isInspectionRequired()).isTrue();
        mockMvc.perform(post("/admin/housekeeping/{id}/start", room.getId()).with(csrf()).with(as(lan)));
        mockMvc.perform(post("/admin/housekeeping/{id}/finish", room.getId()).with(csrf()).with(as(lan)))
                .andExpect(flash().attribute("successMessage", containsString("chờ lễ tân kiểm")));
        Room waiting = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(RoomStatus.NOT_READY);
        assertThat(waiting.isAwaitingInspection()).isTrue();
        mockMvc.perform(get("/admin/housekeeping").with(as(staff))).andExpect(content().string(containsString("Chưa đạt")));

        mockMvc.perform(post("/admin/housekeeping/{id}/inspect/fail", room.getId()).with(csrf()).with(as(staff)).param("note", "Còn tóc trong phòng tắm"))
                .andExpect(flash().attribute("successMessage", containsString("làm lại")));
        Room redo = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(redo.isBeingCleaned()).isTrue();
        assertThat(redo.getInspectionNote()).isEqualTo("Còn tóc trong phòng tắm");

        mockMvc.perform(post("/admin/housekeeping/{id}/finish", room.getId()).with(csrf()).with(as(lan)));
        mockMvc.perform(post("/admin/housekeeping/{id}/inspect/pass", room.getId()).with(csrf()).with(as(lan))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/housekeeping/{id}/inspect/pass", room.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("Kiểm đạt")));
        Room clean = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(clean.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        assertThat(clean.getLastCleanedBy()).isEqualTo(lan.getFullName());
        assertThat(clean.getCleaningAssignee()).isNull();
        assertThat(clean.getInspectionNote()).isNull();

        List<StaffNotificationService.Notice> notices = committed();
        assertThat(notices).extracting(StaffNotificationService.Notice::title)
                .contains("Phòng " + room.getRoomNumber() + " chờ kiểm", "Phòng " + room.getRoomNumber() + " kiểm chưa đạt",
                        "Phòng " + room.getRoomNumber() + " đã sạch");
    }

    @Test
    void inspectionOn_staffFinishingIsCleanImmediately_inspectionOffKeepsOldFlow() throws Exception {
        inspection(true);
        mockMvc.perform(post("/admin/housekeeping/{id}/finish", room.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("sẵn sàng")));
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.AVAILABLE);

        inspection(false);
        Room other = persistRoom(room.getRoomType(), 900_000, RoomStatus.NOT_READY);
        mockMvc.perform(post("/admin/housekeeping/{id}/finish", other.getId()).with(csrf()).with(as(lan)))
                .andExpect(flash().attribute("successMessage", containsString("sẵn sàng")));
        assertThat(roomRepository.findById(other.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    void checkoutAndMaintenance_notifyRightAudience_recentFilteredByRole() throws Exception {
        Room busy = persistRoom(room.getRoomType(), 900_000, RoomStatus.AVAILABLE);
        mockMvc.perform(post("/admin/maintenance").with(csrf()).with(as(lan))
                .param("roomId", busy.getId().toString()).param("title", "Vòi sen rỉ nước").param("priority", "URGENT"));
        List<StaffNotificationService.Notice> notices = committed();
        StaffNotificationService.Notice ticket = notices.stream().filter(n -> n.kind().equals("maintenance")).findFirst().orElseThrow();
        assertThat(ticket.audiences()).containsExactlyInAnyOrder(Audience.FRONT_DESK, Audience.HOUSEKEEPING);
        assertThat(ticket.level()).isEqualTo(StaffNotificationService.Level.WARNING);

        // Nhan vien buong lay thong bao qua /recent chi thay cua minh; le tan khong thay "phong duoc giao" cua nguoi khac
        notificationService.notify(EnumSet.of(Audience.HOUSEKEEPING), minh.getId(), "housekeeping", StaffNotificationService.Level.INFO,
                "Riêng cho Minh", "", "/admin/housekeeping");
        committed();
        String forLan = mockMvc.perform(get("/admin/notifications/recent").with(as(lan)).param("after", String.valueOf(start)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String forStaff = mockMvc.perform(get("/admin/notifications/recent").with(as(staff)).param("after", String.valueOf(start)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(forLan).contains("Vòi sen rỉ nước").doesNotContain("Riêng cho Minh");
        assertThat(forStaff).contains("Vòi sen rỉ nước").doesNotContain("Riêng cho Minh");
        mockMvc.perform(get("/admin/notifications/stream").with(as(lan))).andExpect(request().asyncStarted());
        mockMvc.perform(get("/admin/housekeeping").with(as(lan))).andExpect(content().string(containsString("/js/staff-notify.js")));
    }

    @Test
    void stats_countRoomsMinutesAndFailRatePerCleaner() throws Exception {
        inspection(true);
        mockMvc.perform(post("/admin/housekeeping/{id}/start", room.getId()).with(csrf()).with(as(lan)));
        Room r = roomRepository.findById(room.getId()).orElseThrow();
        r.setCleaningStartedAt(LocalDateTime.now().minusMinutes(30));
        roomRepository.save(r);
        mockMvc.perform(post("/admin/housekeeping/{id}/finish", room.getId()).with(csrf()).with(as(lan)));
        mockMvc.perform(post("/admin/housekeeping/{id}/inspect/fail", room.getId()).with(csrf()).with(as(staff)).param("note", "Gương còn vết"));
        mockMvc.perform(post("/admin/housekeeping/{id}/finish", room.getId()).with(csrf()).with(as(lan)));
        mockMvc.perform(post("/admin/housekeeping/{id}/inspect/pass", room.getId()).with(csrf()).with(as(staff)));

        var stats = housekeepingService.stats(java.time.LocalDate.now(), java.time.LocalDate.now());
        var lanStats = stats.cleaners().stream().filter(c -> c.name().equals(lan.getFullName())).findFirst().orElseThrow();
        assertThat(lanStats.cleaned()).isEqualTo(1);
        assertThat(lanStats.inspected()).isEqualTo(2);
        assertThat(lanStats.failed()).isEqualTo(1);
        assertThat(lanStats.failRate()).isEqualTo(50.0);
        assertThat(lanStats.avgMinutes()).isNotNull();
        assertThat(stats.recentFailures()).extracting(com.hotel.entity.HousekeepingLog::getNote).contains("Gương còn vết");

        mockMvc.perform(get("/admin/housekeeping/stats").with(as(staff)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Gương còn vết")))
                .andExpect(content().string(containsString("50.0%")));
        mockMvc.perform(get("/admin/housekeeping/stats").with(as(lan))).andExpect(status().isForbidden());
    }
}
