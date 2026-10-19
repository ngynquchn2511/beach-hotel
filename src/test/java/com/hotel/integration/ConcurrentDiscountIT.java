package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.*;
import com.hotel.service.BookingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Ma giam gia con dung 1 luot, nhieu khach dat cung luc (moi nguoi 1 phong khac nhau) -> chi 1 nguoi duoc giam.
// Khoa dong ma (SELECT ... FOR UPDATE) bat nguoi sau cho nguoi truoc ghi xong roi moi dem lai luot
@SpringBootTest
class ConcurrentDiscountIT {

    private static final int THREADS = 6;

    @Autowired private BookingService bookingService;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DiscountCodeRepository discountCodeRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private RoomType roomType;
    private DiscountCode code;
    private final List<Room> rooms = new ArrayList<>();
    private final List<User> customers = new ArrayList<>();

    @BeforeEach
    void seed() {
        long n = System.nanoTime() % 1_000_000;
        roomType = roomTypeRepository.save(RoomType.builder()
                .name("Phong DiscountRaceIT " + n).basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        code = discountCodeRepository.save(DiscountCode.builder().code("LUOTCUOI" + n).discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN).active(true).maxUses(1).build());
        for (int i = 0; i < THREADS; i++) {
            rooms.add(roomRepository.save(Room.builder().roomNumber("DR-" + n + "-" + i).roomType(roomType)
                    .price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build()));
            customers.add(userRepository.save(User.builder()
                    .fullName("Khach giam gia " + i).email("discountrace" + n + "_" + i + "@mail.com")
                    .phoneNumber("09" + String.format("%08d", i)).password(passwordEncoder.encode("x"))
                    .role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build()));
        }
    }

    @AfterEach
    void cleanUp() {
        for (Room r : rooms) {
            bookingRepository.deleteAll(bookingRepository.findByRoomIdAndStatusIn(r.getId(), List.of(BookingStatus.values())));
        }
        roomRepository.deleteAll(rooms);
        roomTypeRepository.delete(roomType);
        userRepository.deleteAll(customers);
        discountCodeRepository.delete(code);
    }

    @Test
    void lastUseClaimedByManyAtOnce_onlyOneGetsDiscount() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(12);
        LocalDate checkOut = checkIn.plusDays(1);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            User customer = customers.get(i);
            Room room = rooms.get(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    bookingService.createBooking(customer, room.getId(), checkIn, checkOut,
                            LocalTime.of(14, 0), LocalTime.of(12, 0), 1, null, code.getCode(),
                            customer.getFullName(), customer.getPhoneNumber(), customer.getEmail());
                    succeeded.incrementAndGet();
                } catch (BusinessException ex) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }

        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, succeeded.get(), "Chi 1 nguoi dung duoc luot cuoi");
        assertEquals(THREADS - 1, rejected.get(), "Nguoi den sau nhan thong bao ma da het luot");
        assertEquals(1, bookingRepository.countSingleUses(code.getId()), "Trong CSDL ma chi duoc dung dung 1 lan");
    }
}
