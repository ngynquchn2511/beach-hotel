package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.ReviewRepository;
import com.hotel.service.ExecutiveBoardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Bang dieu hanh o trang Tong quan: viec can xu ly, so do phong, 14 ngay toi, danh gia, lai lo theo quyen
class ExecutiveBoardIT extends ItFixtures {

    @Autowired ExecutiveBoardService boardService;
    @Autowired ReviewRepository reviewRepository;

    @Test
    void boardShowsAlerts_roomMap_reviews_andFinanceByRole() throws Exception {
        RoomType type = persistRoomType("Hang Dieu Hanh IT", 1_000_000, 2);
        Room overdueRoom = persistRoom(type, 1_000_000, RoomStatus.OCCUPIED);
        Room pendingRoom = persistRoom(type, 1_000_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);

        // Khach dang o qua ngay tra phong -> viec gap; don cho xac nhan -> viec can lam
        Booking overdue = persistBooking(guest, overdueRoom, BookingStatus.CHECKED_IN, -3, 2);
        overdue.setGuestName("Khách Quá Hạn IT");
        bookingRepository.save(overdue);
        persistBooking(guest, pendingRoom, BookingStatus.PENDING, 5, 2);

        // Danh gia 2 sao chua tra loi
        Booking past = persistBooking(guest, pendingRoom, BookingStatus.CHECKED_OUT, -10, 2);
        Review low = new Review();
        low.setBooking(past);
        low.setCustomer(guest);
        low.setRating(2);
        low.setComment("Phòng ồn quá IT");
        low.setCreatedAt(LocalDateTime.now().minusDays(1));
        reviewRepository.save(low);

        ExecutiveBoardService.Board board = boardService.build(LocalDateTime.now(), true, true);
        assertThat(board.alerts()).anySatisfy(a -> {
            assertThat(a.level()).isEqualTo("danger");
            assertThat(a.label()).contains("quá hạn trả phòng");
        });
        assertThat(board.alerts()).anySatisfy(a -> assertThat(a.link()).isEqualTo("/admin/bookings?status=PENDING"));
        assertThat(board.alerts()).anySatisfy(a -> assertThat(a.link()).isEqualTo("/admin/reviews?replied=false"));
        // Viec gap dung dau danh sach
        assertThat(board.alerts().get(0).level()).isEqualTo("danger");
        assertThat(board.rooms()).anySatisfy(t -> {
            assertThat(t.room().getId()).isEqualTo(overdueRoom.getId());
            assertThat(t.getCss()).isEqualTo("overdue");
            assertThat(t.guest().getId()).isEqualTo(overdue.getId());
        });
        assertThat(board.forecast().days()).hasSize(ExecutiveBoardService.FORECAST_DAYS);
        assertThat(board.reviews().low()).isGreaterThanOrEqualTo(1);
        assertThat(board.finance()).isNotNull();
        assertThat(board.finance().pnl().trend()).hasSize(12);

        // Ke toan: thay lai lo, khong thay viec van hanh
        ExecutiveBoardService.Board acc = boardService.build(LocalDateTime.now(), false, true);
        assertThat(acc.alerts()).noneMatch(a -> a.link().startsWith("/admin/bookings"));

        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(get("/admin/dashboard").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bảng điều hành")))
                .andExpect(content().string(containsString("Khách quá hạn trả phòng chưa check-out")))
                .andExpect(content().string(containsString("so-do-phong")))
                .andExpect(content().string(containsString("Công suất 14 ngày tới")))
                .andExpect(content().string(containsString("Phòng ồn quá IT")))
                .andExpect(content().string(containsString("lai-lo-thang")))
                .andExpect(content().string(containsString("Phân tích theo khoảng ngày")));

        // Le tan: thay viec can xu ly, khong thay lai lo
        mockMvc.perform(get("/admin/dashboard").with(as(persistUser(UserRole.STAFF, null))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("can-xu-ly")))
                .andExpect(content().string(not(containsString("id=\"lai-lo-thang\""))));

        // Ke toan: thay lai lo, khong thay khung viec van hanh
        mockMvc.perform(get("/admin/dashboard").with(as(persistUser(UserRole.ACCOUNTANT, null))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"lai-lo-thang\"")))
                .andExpect(content().string(not(containsString("Khách quá hạn trả phòng chưa check-out"))));
    }

    @Test
    void roomNumbersSortNumerically() {
        assertThat(ExecutiveBoardService.compareRoomNumber("101", "1001")).isNegative();
        assertThat(ExecutiveBoardService.compareRoomNumber("201", "105")).isPositive();
        assertThat(ExecutiveBoardService.compareRoomNumber("A2", "A1")).isPositive();
    }
}
