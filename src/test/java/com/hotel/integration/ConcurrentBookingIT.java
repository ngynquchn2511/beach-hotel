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

import static org.junit.jupiter.api.Assertions.*;

// Nhieu nguoi bam "Dat phong" cung 1 phong, cung khoang ngay, cung 1 thoi diem -> chi duoc dung 1 don.
// Khong dung @Transactional: moi luong phai co giao dich rieng va thay du lieu da commit cua nhau
@SpringBootTest
class ConcurrentBookingIT {

    private static final int THREADS = 10;

    @Autowired private BookingService bookingService;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private RoomType roomType;
    private Room room;
    private final List<User> customers = new ArrayList<>();

    @BeforeEach
    void seed() {
        long n = System.nanoTime() % 1_000_000;
        roomType = roomTypeRepository.save(RoomType.builder()
                .name("Phong ConcurrentIT " + n).basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        room = roomRepository.save(Room.builder()
                .roomNumber("CC-" + n).roomType(roomType)
                .price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build());
        for (int i = 0; i < THREADS; i++) {
            customers.add(userRepository.save(User.builder()
                    .fullName("Khach dong thoi " + i).email("concurrent" + n + "_" + i + "@mail.com")
                    .phoneNumber("09" + String.format("%08d", i)).password(passwordEncoder.encode("x"))
                    .role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build()));
        }
    }

    @AfterEach
    void cleanUp() {
        bookingRepository.deleteAll(bookingRepository.findByRoomIdAndStatusIn(room.getId(), List.of(BookingStatus.values())));
        roomRepository.delete(room);
        roomTypeRepository.delete(roomType);
        userRepository.deleteAll(customers);
    }

    @Test
    void tenCustomersBookSameRoomAtOnce_onlyOneSucceeds() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = checkIn.plusDays(2);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (User customer : customers) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    bookingService.createBooking(customer, room.getId(), checkIn, checkOut,
                            LocalTime.of(14, 0), LocalTime.of(12, 0), 1, null, null,
                            customer.getFullName(), customer.getPhoneNumber(), customer.getEmail());
                    succeeded.incrementAndGet();
                } catch (BusinessException ex) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }

        ready.await();
        go.countDown(); // tha tat ca cung luc
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS); // loi khac BusinessException (VD loi SQL) se lam test do o day
        }
        pool.shutdown();

        assertEquals(1, succeeded.get(), "Chi duoc dung 1 nguoi dat thanh cong");
        assertEquals(THREADS - 1, rejected.get(), "Nhung nguoi con lai phai nhan thong bao loi than thien");
        long saved = bookingRepository.findByRoomIdAndStatusIn(room.getId(), List.of(BookingStatus.PENDING)).size();
        assertEquals(1, saved, "Trong CSDL chi co 1 don cho phong nay");
    }
}
