package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Muc "Phong tuong tu" duoi trang chi tiet phong: chi goi y phong con trong dung ngay khach da chon
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SimilarRoomsIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void similarRooms_listedAndBookedOnesHiddenForSelectedDates() throws Exception {
        long n = System.nanoTime() % 10000;
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Hang SimilarIT").basePrice(BigDecimal.valueOf(900000)).maxGuests(2).build());
        Room current = save("SM" + n + "A", rt);
        Room free = save("SM" + n + "B", rt);
        Room busy = save("SM" + n + "C", rt);

        LocalDate in = LocalDate.now().plusDays(10);
        LocalDate out = in.plusDays(2);
        User guest = userRepository.save(User.builder().fullName("Khach Giu Cho").email("giucho@mail.com").phoneNumber("0911000222")
                .password("x").role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
        bookingRepository.save(Booking.builder().customer(guest).room(busy).checkInDate(in).checkOutDate(out)
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Khach Giu Cho").guestPhone("0911000222").guestEmail("giucho@mail.com")
                .totalAmount(BigDecimal.valueOf(1800000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.CONFIRMED).build());

        // Chua chon ngay: ca 2 phong cung hang deu duoc goi y
        mockMvc.perform(get("/customer/rooms/{id}", current.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phòng tương tự")))
                .andExpect(content().string(containsString("Phòng " + free.getRoomNumber())))
                .andExpect(content().string(containsString("Phòng " + busy.getRoomNumber())));

        // Da chon ngay: phong bi dat trung ngay bi loai, link goi y giu nguyen ngay da chon
        mockMvc.perform(get("/customer/rooms/{id}", current.getId())
                        .param("checkIn", in.toString()).param("checkOut", out.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Còn trống đúng ngày bạn đã chọn")))
                .andExpect(content().string(containsString("Phòng " + free.getRoomNumber())))
                .andExpect(content().string(not(containsString("Phòng " + busy.getRoomNumber()))))
                .andExpect(content().string(containsString("/customer/rooms/" + free.getId() + "?checkIn=" + in + "&amp;checkOut=" + out)));
    }

    private Room save(String number, RoomType rt) {
        return roomRepository.save(Room.builder().roomNumber(number).roomType(rt)
                .price(BigDecimal.valueOf(900000)).status(RoomStatus.AVAILABLE).build());
    }
}
