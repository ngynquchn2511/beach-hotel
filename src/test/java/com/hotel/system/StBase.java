package com.hotel.system;

import com.hotel.entity.*;
import com.hotel.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// System test: khoi dong TOAN BO ung dung tren Tomcat nhung (cong ngau nhien), gui request HTTP that
// qua java.net.http.HttpClient co cookie/session that va CSRF token lay tu HTML tra ve - giong trinh duyet.
// Dung CSDL H2 rieng (hotel_st) de du lieu commit that khong anh huong cac lop test khac.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:hotel_st;MODE=MySQL;DB_CLOSE_DELAY=-1")
abstract class StBase {

    private static final AtomicInteger SEQ = new AtomicInteger(500);
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final Pattern CHATBOT_CSRF = Pattern.compile("id=\"chatbotCsrfToken\"\\s+value=\"([^\"]+)\"");

    @LocalServerPort protected int port;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RoomTypeRepository roomTypeRepository;
    @Autowired protected RoomRepository roomRepository;
    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected ComboRepository comboRepository;
    @Autowired protected DiscountCodeRepository discountCodeRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected static int seq() {
        return SEQ.incrementAndGet();
    }

    protected record Res(int status, String body, String location, HttpResponse<String> raw) {
        boolean contains(String s) {
            return body != null && body.contains(s);
        }
    }

    // Mo phong 1 trinh duyet: giu cookie phien, tu lay CSRF token tu trang vua tai
    protected class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10)).build();
        private String csrf;

        private String url(String path) {
            return path.startsWith("http") ? path : "http://localhost:" + port + path;
        }

        Res get(String path) {
            return send(HttpRequest.newBuilder(URI.create(url(path))).GET().build());
        }

        Res post(String path, Map<String, String> form) {
            if (csrf == null) {
                get("/login");
            }
            Map<String, String> all = new LinkedHashMap<>(form);
            all.put("_csrf", csrf);
            String body = all.entrySet().stream()
                    .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                    .collect(Collectors.joining("&"));
            return send(HttpRequest.newBuilder(URI.create(url(path)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        }

        Res postJson(String path, String json, String csrfToken) {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path))).header("Content-Type", "application/json");
            if (csrfToken != null) {
                b.header("X-CSRF-TOKEN", csrfToken);
            }
            return send(b.POST(HttpRequest.BodyPublishers.ofString(json)).build());
        }

        Res follow(Res r) {
            Res cur = r;
            for (int i = 0; i < 5 && cur.status() / 100 == 3 && cur.location() != null; i++) {
                cur = get(cur.location());
            }
            return cur;
        }

        Res loginCustomer(String email, String password) {
            get("/login");
            Res r = post("/login", Map.of("username", email, "password", password));
            get("/");
            return r;
        }

        Res loginAdmin(String email, String password) {
            get("/admin");
            Res r = post("/admin/login", Map.of("username", email, "password", password));
            get("/admin/dashboard");
            return r;
        }

        String chatbotToken(String page) {
            Matcher m = CHATBOT_CSRF.matcher(get(page).body());
            return m.find() ? m.group(1) : null;
        }

        private Res send(HttpRequest req) {
            try {
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                Matcher m = CSRF.matcher(resp.body());
                if (m.find()) {
                    csrf = m.group(1);
                }
                String loc = resp.headers().firstValue("Location").orElse(null);
                if (loc != null && loc.startsWith("http")) {
                    URI u = URI.create(loc);
                    loc = u.getRawPath() + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
                }
                return new Res(resp.statusCode(), resp.body(), loc, resp);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    // ===== Du lieu nen (arrange) - tao truc tiep qua repository, thao tac kiem thu deu qua HTTP =====

    protected User createUser(UserRole role, CustomerType type, String rawPassword) {
        int n = seq();
        return userRepository.save(User.builder().fullName("Khách ST " + n).email("st" + n + "@test.vn")
                .phoneNumber(String.format("07%08d", n)).password(passwordEncoder.encode(rawPassword))
                .role(role).customerType(type).build());
    }

    protected RoomType createType(String name, long price, int guests) {
        return roomTypeRepository.save(RoomType.builder().name(name).basePrice(BigDecimal.valueOf(price)).maxGuests(guests).build());
    }

    protected Room createRoom(RoomType type, long price) {
        return roomRepository.save(Room.builder().roomNumber("ST-" + seq()).roomType(type).price(BigDecimal.valueOf(price))
                .status(RoomStatus.AVAILABLE).build());
    }

    protected Booking createBooking(User customer, Room room, BookingStatus status, int inDays, int nights) {
        return bookingRepository.save(Booking.builder().customer(customer).room(room)
                .checkInDate(LocalDate.now().plusDays(inDays)).checkOutDate(LocalDate.now().plusDays(inDays + nights))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName(customer.getFullName()).guestPhone(customer.getPhoneNumber()).guestEmail(customer.getEmail())
                .totalAmount(room.getPrice().multiply(BigDecimal.valueOf(nights))).discountAmount(BigDecimal.ZERO).status(status).build());
    }

    protected static Map<String, String> form(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
