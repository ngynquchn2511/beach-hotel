package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.repository.UserRepository;
import com.hotel.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @Autowired private RoomService roomService;

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

    @Test
    void invalidDates_leadDoesNotClaimAvailability() throws Exception {
        long n = System.nanoTime() % 10000;
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Hang SimilarIT2").basePrice(BigDecimal.valueOf(900000)).maxGuests(2).build());
        Room current = save("SN" + n + "A", rt);
        save("SN" + n + "B", rt);
        LocalDate past = LocalDate.now().minusDays(5);

        mockMvc.perform(get("/customer/rooms/{id}", current.getId())
                        .param("checkIn", past.toString()).param("checkOut", past.plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cùng hạng phòng hoặc mức giá gần nhất.")))
                .andExpect(content().string(not(containsString("Còn trống đúng ngày bạn đã chọn"))));
    }

    @Test
    void ordering_sameTypeFirstThenClosestPrice_excludesMaintenanceAndSmallRooms() {
        long n = System.nanoTime() % 10000;
        // Gia rat lon de khong lan voi phong co san trong DB
        RoomType deluxe = roomTypeRepository.save(RoomType.builder().name("Deluxe IT").basePrice(BigDecimal.ONE).maxGuests(2).build());
        RoomType family = roomTypeRepository.save(RoomType.builder().name("Family IT").basePrice(BigDecimal.ONE).maxGuests(4).build());
        Room current = save("SO" + n + "1", deluxe, 900_000_000, RoomStatus.AVAILABLE);
        Room sameFar = save("SO" + n + "2", deluxe, 950_000_000, RoomStatus.BOOKED);
        Room sameNear = save("SO" + n + "3", deluxe, 910_000_000, RoomStatus.AVAILABLE);
        Room otherNear = save("SO" + n + "4", family, 905_000_000, RoomStatus.AVAILABLE);
        save("SO" + n + "5", deluxe, 900_000_000, RoomStatus.MAINTENANCE);

        assertEquals(List.of(sameNear, sameFar, otherNear), roomService.findSimilarRooms(current, null, null, null, 3));
        // 3 khach: chi hang Family du cho
        assertEquals(otherNear, roomService.findSimilarRooms(current, null, null, 3, 3).get(0));
    }

    // Lich chon ngay hien gia tung dem 12 thang toi (gia JSON nhung vao trang)
    @Test
    void roomDetail_calendarHasNightlyPrices() throws Exception {
        long n = System.nanoTime() % 10000;
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Hang LichGia IT").basePrice(BigDecimal.valueOf(1_210_000)).maxGuests(2).build());
        Room room = save("SP" + n + "A", rt, 1_210_000, RoomStatus.AVAILABLE);
        String tomorrow = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(get("/customer/rooms/{id}", room.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("calendarPrices", org.hamcrest.Matchers.aMapWithSize(366)))
                .andExpect(content().string(containsString("\"" + tomorrow + "\":{\"p\":")))
                .andExpect(content().string(containsString("Cuối tuần có phụ thu")));
    }

    private Room save(String number, RoomType rt, long price, RoomStatus status) {
        return roomRepository.save(Room.builder().roomNumber(number).roomType(rt)
                .price(BigDecimal.valueOf(price)).status(status).build());
    }

    private Room save(String number, RoomType rt) {
        return roomRepository.save(Room.builder().roomNumber(number).roomType(rt)
                .price(BigDecimal.valueOf(900000)).status(RoomStatus.AVAILABLE).build());
    }
}
