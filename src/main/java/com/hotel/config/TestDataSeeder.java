package com.hotel.config;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.CustomerType;
import com.hotel.entity.Room;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

// Sinh 50 khach hang + don dat phong gia lap de co du lieu ve dashboard (bieu do tron/cot/duong).
// Chi chay 1 lan duy nhat (bo qua neu khach hang gia lap dau tien da ton tai) va can it nhat 1 phong
// da duoc tao san trong he thong (qua man hinh quan ly phong cua admin) thi moi sinh duoc booking.
// Chi bat khi app.seed-fake-data=true (may dev) - profile prod tat de khong lan du lieu gia vao he thong that.
@Component
@ConditionalOnProperty(name = "app.seed-fake-data", havingValue = "true")
public class TestDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestDataSeeder.class);

    private static final String[][] FAKE_CUSTOMERS = {
            {"Nguyễn Văn An", "0361427527", "annv01@gmail.com"},
            {"Trần Thị Hoa", "0768879223", "hoatt02@gmail.com"},
            {"Lê Minh Tuấn", "0363175675", "tuanlm03@gmail.com"},
            {"Phạm Thu Hương", "0882078024", "huongpt04@gmail.com"},
            {"Hoàng Quốc Bảo", "0931209061", "baohq05@gmail.com"},
            {"Huỳnh Ngọc Anh", "0962072808", "anhhn06@gmail.com"},
            {"Phan Thanh Tùng", "0344145823", "tungpt07@gmail.com"},
            {"Vũ Thị Lan", "0789742733", "lanvt08@gmail.com"},
            {"Võ Văn Đức", "0763990430", "ducvv09@gmail.com"},
            {"Đặng Thị Mai", "0385428871", "maidt10@gmail.com"},
            {"Bùi Minh Khoa", "0913430037", "khoabm11@gmail.com"},
            {"Đỗ Thu Trang", "0795593568", "trangdt12@gmail.com"},
            {"Hồ Hữu Phát", "0362322946", "phathh13@gmail.com"},
            {"Ngô Thị Thảo", "0365318974", "thaont14@gmail.com"},
            {"Dương Văn Hùng", "0378437364", "hungdv15@gmail.com"},
            {"Nguyễn Ngọc Linh", "0322427982", "linhnn16@gmail.com"},
            {"Trần Đình Khang", "0991359511", "khangtd17@gmail.com"},
            {"Lê Thị Nga", "0704881586", "ngalt18@gmail.com"},
            {"Phạm Xuân Sơn", "0708284882", "sonpx19@gmail.com"},
            {"Hoàng Thị Yến", "0367923076", "yenht20@gmail.com"},
            {"Huỳnh Công Danh", "0835161270", "danhhc21@gmail.com"},
            {"Phan Thị Hằng", "0944243304", "hangpt22@gmail.com"},
            {"Vũ Trọng Nghĩa", "0833266188", "nghiavt23@gmail.com"},
            {"Võ Thị Huyền", "0799312364", "huyenvt24@gmail.com"},
            {"Đặng Anh Tuấn", "0885855798", "tuanda25@gmail.com"},
            {"Bùi Thị Thu", "0837185536", "thubt26@gmail.com"},
            {"Đỗ Việt Anh", "0763337354", "anhdv27@gmail.com"},
            {"Hồ Thị Kim", "0918422440", "kimht28@gmail.com"},
            {"Ngô Đức Long", "0816439907", "longnd29@gmail.com"},
            {"Dương Thị Phương", "0326124998", "phuongdt30@gmail.com"},
            {"Nguyễn Gia Bảo", "0934441444", "baong31@gmail.com"},
            {"Trần Thị Loan", "0321680952", "loantt32@gmail.com"},
            {"Lê Hoàng Nam", "0918538565", "namlh33@gmail.com"},
            {"Phạm Thị Nhung", "0777674593", "nhungpt34@gmail.com"},
            {"Hoàng Minh Quân", "0387556487", "quanhm35@gmail.com"},
            {"Huỳnh Thị Vân", "0964551956", "vanht36@gmail.com"},
            {"Phan Tuấn Kiệt", "0785024915", "kietpt37@gmail.com"},
            {"Vũ Thị Diệp", "0889680162", "diepvt38@gmail.com"},
            {"Võ Quang Huy", "0978932552", "huyvq39@gmail.com"},
            {"Đặng Thị Ngọc", "0338179856", "ngocdt40@gmail.com"},
            {"Bùi Bảo Long", "0374141370", "longbb41@gmail.com"},
            {"Đỗ Diễm My", "0783590836", "mydd42@gmail.com"},
            {"Hồ Thế Vinh", "0995596138", "vinhht43@gmail.com"},
            {"Ngô Bảo Trân", "0993488177", "trannb44@gmail.com"},
            {"Dương Duy Khánh", "0931121927", "khanhdd45@gmail.com"},
            {"Nguyễn Khánh Linh", "0856263018", "linhnk46@gmail.com"},
            {"Trần Trung Kiên", "0961080622", "kientt47@gmail.com"},
            {"Lê Tường Vi", "0939740211", "vilt48@gmail.com"},
            {"Phạm Nhật Minh", "0927502777", "minhpn49@gmail.com"},
            {"Hoàng Mỹ Duyên", "0912190376", "duyenhm50@gmail.com"},
    };

    private static final LocalTime[] CHECK_IN_TIMES = {LocalTime.of(12, 0), LocalTime.of(14, 0), LocalTime.of(15, 0)};
    private static final LocalTime[] CHECK_OUT_TIMES = {LocalTime.of(11, 0), LocalTime.of(12, 0)};

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataSeeder(UserRepository userRepository, RoomRepository roomRepository,
                           BookingRepository bookingRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(FAKE_CUSTOMERS[0][2])) {
            return;
        }

        List<Room> rooms = roomRepository.findAll();
        if (rooms.isEmpty()) {
            log.warn("Bo qua sinh du lieu gia lap: chua co phong nao trong he thong. " +
                    "Hay tao loai phong + phong o trang quan ly cua admin roi khoi dong lai ung dung.");
            return;
        }

        Random random = new Random();
        LocalDate today = LocalDate.now();
        String encodedPassword = passwordEncoder.encode("customer123");

        for (String[] data : FAKE_CUSTOMERS) {
            String fullName = data[0];
            String phone = data[1];
            String email = data[2];

            User customer = User.builder()
                    .fullName(fullName)
                    .email(email)
                    .password(encodedPassword)
                    .phoneNumber(phone)
                    .role(UserRole.CUSTOMER)
                    .customerType(randomCustomerType(random))
                    .build();
            customer = userRepository.save(customer);

            Room room = rooms.get(random.nextInt(rooms.size()));
            int nights = 1 + random.nextInt(4);
            LocalDate checkInDate = randomCheckInDate(random, today);
            LocalDate checkOutDate = checkInDate.plusDays(nights);

            int maxGuests = room.getRoomType() != null && room.getRoomType().getMaxGuests() != null
                    ? room.getRoomType().getMaxGuests() : 2;
            int guests = 1 + random.nextInt(Math.max(maxGuests, 1));

            BigDecimal totalAmount = room.getPrice().multiply(BigDecimal.valueOf(nights));
            BookingStatus status = randomStatus(random, today, checkInDate, checkOutDate);

            Booking booking = Booking.builder()
                    .customer(customer)
                    .room(room)
                    .checkInDate(checkInDate)
                    .checkOutDate(checkOutDate)
                    .checkInTime(CHECK_IN_TIMES[random.nextInt(CHECK_IN_TIMES.length)])
                    .checkOutTime(CHECK_OUT_TIMES[random.nextInt(CHECK_OUT_TIMES.length)])
                    .numberOfGuests(guests)
                    .guestName(fullName)
                    .guestPhone(phone)
                    .guestEmail(email)
                    .totalAmount(totalAmount)
                    .discountAmount(BigDecimal.ZERO)
                    .status(status)
                    .newBooking(false)
                    .build();
            booking = bookingRepository.save(booking);

            // Dat lai ngay tao don rai deu trong ~6 thang gan day de bieu do doanh thu theo thang co du lieu
            LocalDateTime createdAt = randomCreatedAt(random, today, checkInDate);
            booking.setCreatedAt(createdAt);
            bookingRepository.save(booking);
        }

        log.info("Da sinh {} khach hang va don dat phong gia lap de test dashboard.", FAKE_CUSTOMERS.length);
    }

    private CustomerType randomCustomerType(Random random) {
        int r = random.nextInt(100);
        if (r < 50) return CustomerType.NEW;
        if (r < 85) return CustomerType.REGULAR;
        return CustomerType.VIP;
    }

    // 65% ngay nhan phong trong qua khu (10-180 ngay truoc), 20% quanh hom nay (+-5 ngay), 15% trong tuong lai gan (1-30 ngay)
    private LocalDate randomCheckInDate(Random random, LocalDate today) {
        int bucket = random.nextInt(100);
        if (bucket < 65) {
            return today.minusDays(10 + random.nextInt(171));
        } else if (bucket < 85) {
            return today.minusDays(random.nextInt(6));
        } else {
            return today.plusDays(1 + random.nextInt(30));
        }
    }

    private BookingStatus randomStatus(Random random, LocalDate today, LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkOutDate.isBefore(today)) {
            return random.nextInt(100) < 85 ? BookingStatus.CHECKED_OUT : BookingStatus.CANCELLED;
        }
        if (!checkInDate.isAfter(today)) {
            return random.nextInt(100) < 80 ? BookingStatus.CHECKED_IN : BookingStatus.CONFIRMED;
        }
        int r = random.nextInt(100);
        if (r < 45) return BookingStatus.PENDING;
        if (r < 90) return BookingStatus.CONFIRMED;
        return BookingStatus.CANCELLED;
    }

    // Ngay tao don phai truoc ngay nhan phong (thoi gian dat truoc 1-20 ngay), va khong duoc o tuong lai
    private LocalDateTime randomCreatedAt(Random random, LocalDate today, LocalDate checkInDate) {
        int leadDays = 1 + random.nextInt(20);
        LocalDate createdDate = checkInDate.minusDays(leadDays);
        if (createdDate.isAfter(today)) {
            createdDate = today.minusDays(random.nextInt(4));
        }
        return createdDate.atTime(8 + random.nextInt(13), random.nextInt(60));
    }
}
