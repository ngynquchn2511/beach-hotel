package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.repository.UserRepository;
import com.hotel.security.CustomUserDetails;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Bao cao chi tiet tren dashboard + sheet rieng trong file Excel; don ghi dung kenh dat luc tao
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardReportIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void walkInAndOnlineBookings_recordChannel_reportShownAndExported() throws Exception {
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Phong ReportIT").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        Room room = roomRepository.save(Room.builder().roomNumber("RP-" + System.nanoTime() % 100000).roomType(rt)
                .price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build());
        CustomUserDetails staff = new CustomUserDetails(userRepository.findByEmail("staff@hotel.com").orElseThrow());
        LocalDate in = LocalDate.now().plusDays(30);

        mockMvc.perform(post("/admin/bookings/walk-in/new").with(csrf()).with(user(staff))
                        .param("guestName", "Khach Quay").param("guestPhone", "0977000111").param("guestEmail", "khachquay@mail.com")
                        .param("roomId", room.getId().toString()).param("checkIn", in.toString()).param("checkOut", in.plusDays(1).toString())
                        .param("checkInTime", "14:00").param("checkOutTime", "12:00"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/customer/bookings/new").with(csrf())
                        .param("roomId", room.getId().toString()).param("checkIn", in.plusDays(3).toString())
                        .param("checkOut", in.plusDays(4).toString()).param("guests", "2")
                        .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                        .param("guestName", "Khach Mang").param("guestPhone", "0977000222").param("guestEmail", "khachmang@mail.com"))
                .andExpect(status().is3xxRedirection());

        assertThat(bookingRepository.findAll().stream().filter(b -> b.getRoom().getId().equals(room.getId()))
                .map(Booking::getChannel)).containsExactlyInAnyOrder(BookingChannel.WALK_IN, BookingChannel.ONLINE);

        CustomUserDetails admin = new CustomUserDetails(userRepository.findByEmail("admin@hotel.com").orElseThrow());
        mockMvc.perform(get("/admin/dashboard").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Báo cáo chi tiết")))
                .andExpect(content().string(containsString("Doanh thu theo kênh đặt")))
                .andExpect(content().string(containsString("Tại quầy")))
                .andExpect(content().string(containsString("Mã giảm giá được dùng")))
                .andExpect(content().string(containsString("Dịch vụ thêm &amp; phụ phí")));

        byte[] xlsx = mockMvc.perform(get("/admin/dashboard/export").with(user(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheet("Báo cáo chi tiết")).isNotNull();
            assertThat(wb.getSheet("Báo cáo chi tiết").getRow(3).getCell(0).getStringCellValue()).isEqualTo("Doanh thu theo kênh đặt");
        }
    }
}
