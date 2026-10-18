package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.PaymentRepository;
import com.hotel.service.DailyReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Bao cao cuoi ngay: so lieu dung, trang xem / in, quyen gui email
class DailyReportIT extends ItFixtures {

    @Autowired private DailyReportService reportService;
    @Autowired private PaymentRepository paymentRepository;

    private User guest;
    private Room room;

    @BeforeEach
    void seed() {
        guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        room = persistRoom(persistRoomType("Hang BC IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
    }

    @Test
    void build_countsArrivalsNoShowsMoneyAndPending() {
        LocalDate today = LocalDate.now();
        // Khach den hom nay da nhan phong + 1 khach den hom nay chua den (gio nhan 00:00 -> da qua gio)
        persistBooking(guest, room, BookingStatus.CHECKED_IN, 0, 2);
        Booking late = persistBooking(guest, persistRoom(room.getRoomType(), 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CONFIRMED, 0, 1);
        late.setCheckInTime(LocalTime.MIDNIGHT);
        bookingRepository.save(late);
        // No-show danh dau hom nay
        Booking ns = persistBooking(guest, persistRoom(room.getRoomType(), 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CANCELLED, -1, 1);
        ns.setCancelledAt(LocalDateTime.now());
        ns.setNoShow(true);
        ns.setNoShowAt(LocalDateTime.now());
        bookingRepository.save(ns);
        // Thu tien mat 1.200.000 hom nay
        Booking out = persistBooking(guest, persistRoom(room.getRoomType(), 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CHECKED_OUT, -2, 2);
        paymentRepository.save(Payment.builder().booking(out).amount(BigDecimal.valueOf(1_200_000)).paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PAID).paymentDate(LocalDateTime.now()).build());
        persistBooking(guest, persistRoom(room.getRoomType(), 1_000_000, RoomStatus.AVAILABLE), BookingStatus.PENDING, 10, 1);

        DailyReportService.DailyReport r = reportService.build(today);
        assertThat(r.arrivalsExpected()).isGreaterThanOrEqualTo(2);
        assertThat(r.arrivalsDone()).isGreaterThanOrEqualTo(1);
        assertThat(r.noShowsMarked()).extracting(Booking::getId).contains(ns.getId());
        assertThat(r.cancellations()).extracting(Booking::getId).doesNotContain(ns.getId());
        assertThat(r.stillNotArrived()).extracting(Booking::getId).contains(late.getId());
        assertThat(r.income().get(PaymentMethod.CASH)).isGreaterThanOrEqualTo(BigDecimal.valueOf(1_200_000));
        assertThat(r.pending().bookingsToConfirm()).isGreaterThanOrEqualTo(1);

        String text = DailyReportService.text(r);
        assertThat(text).contains("BÁO CÁO CUỐI NGÀY").contains("Công suất").contains("Thực thu").contains("Đơn chờ xác nhận");
    }

    @Test
    void page_forStaffAndAccountant_sendOnlyAdmin() throws Exception {
        mockMvc.perform(get("/admin/reports/daily").with(as(persistUser(UserRole.STAFF, null))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Báo cáo cuối ngày")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Gửi email ngay"))));
        mockMvc.perform(get("/admin/reports/daily").with(as(persistUser(UserRole.ACCOUNTANT, null))).param("day", "2025-01-05"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("05/01/2025")));
        mockMvc.perform(get("/admin/reports/daily").with(as(persistUser(UserRole.HOUSEKEEPER, null)))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/reports/daily/send").with(csrf()).with(as(persistUser(UserRole.STAFF, null))))
                .andExpect(status().isForbidden());
        // Moi truong test co cau hinh mail (gui toi localhost) -> bao da gui toi cac tai khoan ADMIN
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(get("/admin/reports/daily").with(as(admin))).andExpect(content().string(containsString("Gửi email ngay")));
        mockMvc.perform(post("/admin/reports/daily/send").with(csrf()).with(as(admin)))
                .andExpect(flash().attribute("successMessage", containsString(admin.getEmail())));
        assertThat(reportService.recipients()).contains(admin.getEmail());
    }
}
