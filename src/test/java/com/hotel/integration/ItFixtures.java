package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.*;
import com.hotel.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

// Du lieu mau dung chung cho cac lop Integration test tham so hoa (MockMvc + H2, rollback sau moi test)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class ItFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mockMvc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RoomTypeRepository roomTypeRepository;
    @Autowired protected RoomRepository roomRepository;
    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected ComboRepository comboRepository;
    @Autowired protected DiscountCodeRepository discountCodeRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected BookingGuestRepository bookingGuestRepository;

    protected static int seq() {
        return SEQ.incrementAndGet();
    }

    protected User persistUser(UserRole role, CustomerType type) {
        int n = seq();
        return userRepository.save(User.builder().fullName("Người dùng IT " + n).email("it" + n + "@test.vn")
                .phoneNumber(String.format("09%08d", n)).password(passwordEncoder.encode("matkhau123"))
                .role(role).customerType(type).build());
    }

    protected RequestPostProcessor as(User u) {
        return user(new CustomUserDetails(u));
    }

    protected RoomType persistRoomType(String name, long price, int maxGuests) {
        return roomTypeRepository.save(RoomType.builder().name(name).basePrice(BigDecimal.valueOf(price)).maxGuests(maxGuests).build());
    }

    protected Room persistRoom(RoomType type, long price, RoomStatus status) {
        return roomRepository.save(Room.builder().roomNumber("IT-" + seq()).roomType(type)
                .price(BigDecimal.valueOf(price)).status(status).build());
    }

    protected Combo persistCombo(String name, long price, boolean active) {
        return comboRepository.save(Combo.builder().name(name).price(BigDecimal.valueOf(price)).active(active).build());
    }

    protected DiscountCode persistCode(String code, DiscountType type, long value, CustomerType applicable, boolean active) {
        return discountCodeRepository.save(DiscountCode.builder().code(code).discountType(type).discountValue(BigDecimal.valueOf(value))
                .applicableCustomerType(applicable).active(active).build());
    }

    // Khai bao luu tru 1 khach cho don (dieu kien de check-in)
    protected BookingGuest declareGuest(Booking booking) {
        return bookingGuestRepository.save(BookingGuest.builder().booking(booking).fullName(booking.getGuestName())
                .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE).idType(IdDocumentType.CCCD)
                .idNumber(String.format("0010900%05d", seq())).nationality("Việt Nam").build());
    }

    protected Booking persistBooking(User customer, Room room, BookingStatus status, int inDays, int nights) {
        return bookingRepository.save(Booking.builder().customer(customer).room(room)
                .checkInDate(LocalDate.now().plusDays(inDays)).checkOutDate(LocalDate.now().plusDays(inDays + nights))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName(customer.getFullName()).guestPhone(customer.getPhoneNumber()).guestEmail(customer.getEmail())
                .totalAmount(room.getPrice().multiply(BigDecimal.valueOf(nights))).discountAmount(BigDecimal.ZERO)
                .status(status).build());
    }
}
