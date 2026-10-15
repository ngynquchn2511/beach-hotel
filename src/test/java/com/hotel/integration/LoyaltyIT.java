package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.repository.UserRepository;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Khach than thiet qua luong check-out that: lan luu tru thu 2 -> len "Khach quen", trang tai khoan hien tien do
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoyaltyIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void secondCheckOut_upgradesToRegular_accountShowsProgress() throws Exception {
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Phong LoyaltyIT").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        Room room = roomRepository.save(Room.builder().roomNumber("LY-" + System.nanoTime() % 100000).roomType(rt)
                .price(BigDecimal.valueOf(500000)).status(RoomStatus.OCCUPIED).build());
        User guest = userRepository.save(User.builder().fullName("Khach Than Thiet").email("thanthiet@mail.com").phoneNumber("0966111222")
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
        LocalDate past = LocalDate.now().minusDays(20);
        save(guest, room, past, past.plusDays(1), BookingStatus.CHECKED_OUT);
        Booking current = save(guest, room, LocalDate.now().minusDays(1), LocalDate.now(), BookingStatus.CHECKED_IN);

        // Truoc khi tra phong: 1 lan, con 1 lan nua len Khach quen
        mockMvc.perform(get("/customer/account").with(user(new CustomUserDetails(guest))))
                .andExpect(content().string(containsString("Đã lưu trú hoàn tất 1 lần")))
                .andExpect(content().string(containsString("Còn 1 lần lưu trú nữa để lên hạng Khách quen")));

        CustomUserDetails staff = new CustomUserDetails(userRepository.findByEmail("staff@hotel.com").orElseThrow());
        mockMvc.perform(post("/admin/bookings/{id}/check-out", current.getId()).with(csrf()).with(user(staff))
                        .param("paymentMethod", "CASH"))
                .andExpect(flash().attributeExists("successMessage"));

        User upgraded = userRepository.findByEmail("thanthiet@mail.com").orElseThrow();
        assertThat(upgraded.getCustomerType()).isEqualTo(CustomerType.REGULAR);
        mockMvc.perform(get("/customer/account").with(user(new CustomUserDetails(upgraded))))
                .andExpect(content().string(containsString("Đã lưu trú hoàn tất 2 lần")))
                .andExpect(content().string(containsString("Còn 3 lần lưu trú nữa để lên hạng Khách VIP")));
    }

    private Booking save(User guest, Room room, LocalDate in, LocalDate out, BookingStatus status) {
        return bookingRepository.save(Booking.builder().customer(guest).room(room).checkInDate(in).checkOutDate(out)
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Khach Than Thiet").guestPhone("0966111222").guestEmail("thanthiet@mail.com")
                .totalAmount(BigDecimal.valueOf(500000)).discountAmount(BigDecimal.ZERO).status(status).build());
    }
}
