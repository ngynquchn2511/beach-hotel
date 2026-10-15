package com.hotel.config;

import com.hotel.security.AdminOtpFilter;
import com.hotel.security.AdminOtpService;
import com.hotel.security.AdminPortalSuccessHandler;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.CustomerPortalSuccessHandler;
import com.hotel.security.LoginAttemptService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final LoginAttemptService loginAttemptService;
    private final AdminOtpService adminOtpService;

    public SecurityConfig(CustomUserDetailsService userDetailsService, LoginAttemptService loginAttemptService,
                          AdminOtpService adminOtpService) {
        this.adminOtpService = adminOtpService;
        this.userDetailsService = userDetailsService;
        this.loginAttemptService = loginAttemptService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Spring Security 6 chi tao CsrfToken THUC SU (va phien HTTP di kem) khi co noi nao do GOI
    // csrfToken.getToken() lan dau (lazy, de chong BREACH). Fragment chatbot-widget doc "_csrf.token"
    // o GAN CUOI trang (sau rat nhieu CSS/JS nhung) - voi trang du lon (rooms.html, customer/rooms.html),
    // luc Thymeleaf render toi do thi Tomcat da flush/commit response roi, nen khong the tao session moi
    // nua -> IllegalStateException "Cannot create a session after the response has been committed",
    // ung dung bi cat response giua chung (thieu han phan footer). Filter nay ep "cham" vao token ngay
    // tu dau request (truoc khi co byte nao duoc ghi ra), dung theo khuyen nghi chinh thuc cua Spring
    // Security cho tinh huong nay.
    @Bean
    public OncePerRequestFilter csrfTokenEagerLoadFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (csrfToken != null) {
                    csrfToken.getToken();
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    // Dang khoa tam vi sai mat khau nhieu lan (ke ca lan sai vua cham nguong) -> "?locked=temp&minutes=N";
    // bi admin khoa -> "?locked=true"; chua kich hoat email -> "?unverified=true"; con lai (sai email/mat khau) -> "?error=true"
    private AuthenticationFailureHandler loginFailureHandler(String loginPage) {
        return (request, response, exception) -> {
            String query = loginAttemptService.temporaryLockUntil(request.getParameter("username"))
                    .map(until -> "?locked=temp&minutes="
                            + Math.max(1, (Duration.between(LocalDateTime.now(), until).toSeconds() + 59) / 60))
                    .orElse(exception instanceof LockedException ? "?locked=true"
                            : exception instanceof DisabledException ? "?unverified=true" : "?error=true");
            response.sendRedirect(request.getContextPath() + loginPage + query);
        };
    }

    // Cong danh cho STAFF va ADMIN - trang dang nhap tai /admin
    // Phai dat @Order(1) de duoc xet truoc chain con lai (pham vi hep hon: /admin/**, /staff/**)
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterAfter(csrfTokenEagerLoadFilter(), CsrfFilter.class)
                .securityMatcher("/admin", "/admin/**", "/staff/**")
                // Xac thuc 2 buoc: phien chua nhap ma OTP chi vao duoc trang nhap ma / dang xuat
                .addFilterAfter(new AdminOtpFilter(), org.springframework.security.web.access.ExceptionTranslationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // /admin la diem vao duy nhat hien trang dang nhap quan tri.
                        // /admin/login chi la endpoint POST do Spring Security xu ly.
                        .requestMatchers("/admin", "/admin/login").permitAll()
                        // Quan ly tai khoan nhan vien va xem nhat ky thao tac - chi danh rieng cho ADMIN
                        .requestMatchers("/admin/staff-accounts", "/admin/staff-accounts/**",
                                "/admin/audit-logs", "/admin/audit-logs/**").hasRole("ADMIN")
                        // Tao/xoa danh muc (loai phong, phong, combo) - chi ADMIN; STAFF chi xem/sua thong tin
                        .requestMatchers("/admin/room-types/new", "/admin/room-types/*/delete",
                                "/admin/rooms/new", "/admin/rooms/*/delete",
                                "/admin/combos/new", "/admin/combos/*/delete",
                                "/admin/diving-tours/new", "/admin/diving-tours/*/delete").hasRole("ADMIN")
                        // Bang gia xe dua don san bay - chi ADMIN duoc doi gia
                        .requestMatchers("/admin/transfer-rates", "/admin/transfer-rates/**").hasRole("ADMIN")
                        // Ma giam gia: STAFF chi duoc xem danh sach, moi thao tac thay doi danh cho ADMIN
                        .requestMatchers(HttpMethod.POST, "/admin/discount-codes/**").hasRole("ADMIN")
                        .requestMatchers("/admin/discount-codes/new", "/admin/discount-codes/*/edit").hasRole("ADMIN")
                        // Doi loai khach hang (anh huong ma giam gia) va khoa tai khoan khach - chi ADMIN
                        .requestMatchers("/admin/customers/*/update-type", "/admin/customers/*/toggle-lock").hasRole("ADMIN")
                        // Gia cuoi tuan / ngay le va chinh sach dat coc - chi ADMIN duoc doi gia
                        .requestMatchers("/admin/pricing", "/admin/pricing/**").hasRole("ADMIN")
                        // Link lich iCal lo ra la ai cung xem duoc lich phong -> chi ADMIN lay link / doi ma
                        .requestMatchers("/admin/ical", "/admin/ical/**").hasRole("ADMIN")
                        // Le tan giao mon duoc, sua thuc don chi ADMIN
                        .requestMatchers("/admin/room-service/menu", "/admin/room-service/menu/**").hasRole("ADMIN")
                        // Cac chuc nang con lai (don dat phong, check-in/out, trang thai phong, doanh thu...) - ca STAFF va ADMIN
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/admin")
                        .loginProcessingUrl("/admin/login")
                        .successHandler(new AdminPortalSuccessHandler(adminOtpService))
                        .failureHandler(loginFailureHandler("/admin"))
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin?logout=true")
                        .permitAll()
                )
                .userDetailsService(userDetailsService);

        return http.build();
    }

    // Cong danh cho CUSTOMER (va khach vang lai) - dang nhap tai /login
    // @Order(2) - chi xu ly cac request khong khop chain o tren
    @Bean
    @Order(2)
    public SecurityFilterChain customerFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterAfter(csrfTokenEagerLoadFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Mua the qua tang khong can dang nhap (trang thanh toan kiem tra ?token=)
                        .requestMatchers("/gift-vouchers", "/gift-vouchers/**").permitAll()
                        .requestMatchers("/", "/home", "/about", "/services", "/rooms", "/rooms/**", "/login", "/register",
                                "/forgot-password", "/reset-password", "/verify-email", "/contact", "/resend-verification", "/robots.txt", "/sitemap.xml",
                                "/css/**", "/js/**", "/images/**", "/uploads/**", "/page-images/**").permitAll()
                        // Chatbot tu van - khach chua dang nhap cung phai hoi duoc
                        .requestMatchers("/api/chatbot/**").permitAll()
                        // Tim phong, xem chi tiet phong, dat phong, kiem tra ma giam gia, xem lai 1 don vua dat
                        // KHONG can dang nhap - khach nhap ho ten/email/SDT ngay luc dat phong
                        .requestMatchers("/customer/rooms", "/customer/rooms/*",
                                "/customer/bookings/new", "/customer/bookings/check-discount",
                                "/customer/bookings/*").permitAll()
                        // Dat / huy dich vu them (tour lan, xe dua don) tren trang don - khach vang lai dung ?token=
                        // (kiem tra quyen trong BookingService.findForViewing giong trang xem don)
                        .requestMatchers("/customer/bookings/*/tours", "/customer/bookings/*/tours/*/cancel",
                                "/customer/bookings/*/transfers", "/customer/bookings/*/transfers/*/cancel").permitAll()
                        // Viet danh gia tu link trong email moi danh gia (?token=) - kiem tra quyen giong tren
                        .requestMatchers(HttpMethod.POST, "/customer/bookings/*/review").permitAll()
                        // Khach vang lai tu doi ngay / gio / combo / huy don bang link co ?token= (khong co tai khoan de dang nhap) -
                        // controller kiem tra chu don hoac dung ma truy cap truoc khi lam gi
                        .requestMatchers(HttpMethod.POST, "/customer/bookings/*/cancel", "/customer/bookings/*/change-dates",
                                "/customer/bookings/*/edit-time", "/customer/bookings/*/edit-combo").permitAll()
                        .requestMatchers(HttpMethod.GET, "/customer/bookings/*/change-dates/quote").permitAll()
                        // Dat coc qua VNPay: nut tren trang don (kiem tra chu don / ma truy cap nhu tren);
                        // trang tra ve + IPN do VNPay goi, an toan nho kiem tra chu ky HMAC
                        .requestMatchers(HttpMethod.POST, "/customer/bookings/*/vnpay").permitAll()
                        // Khach tu khai bao luu tru truoc khi den (check-in online) - kiem tra chu don / ma truy cap nhu tren
                        .requestMatchers(HttpMethod.POST, "/customer/bookings/*/guests", "/customer/bookings/*/guests/*/delete").permitAll()
                        // Xin nhan phong som / tra phong muon (kiem tra chu don / ma truy cap nhu tren)
                        .requestMatchers(HttpMethod.POST, "/customer/bookings/*/stay-time", "/customer/bookings/*/stay-time/withdraw").permitAll()
                        // Khach dang o goi mon len phong (kiem tra chu don / ma truy cap nhu tren)
                        .requestMatchers(HttpMethod.POST, "/customer/bookings/*/room-service", "/customer/bookings/*/room-service/*/cancel").permitAll()
                        // Thong tin xuat hoa don cong ty tren trang don (kiem tra chu don / ma truy cap)
                        .requestMatchers(HttpMethod.POST, "/customer/bookings/*/company-invoice").permitAll()
                        // Dat nhieu phong 1 lan (khach vang lai dung ?token= cua nhom)
                        .requestMatchers("/customer/bookings/group/new", "/customer/bookings/group/*",
                                "/customer/bookings/group/*/cancel").permitAll()
                        // "Bao toi khi phong trong lai" o trang chi tiet phong - khach chua dang nhap cung dung duoc
                        .requestMatchers(HttpMethod.POST, "/customer/rooms/*/alerts").permitAll()
                        // Lich .ics cho Google Calendar / Booking.com - kiem tra ma bi mat ?key= trong IcalController
                        .requestMatchers(HttpMethod.GET, "/ical/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/payment/vnpay/return", "/payment/vnpay/ipn").permitAll()
                        // Con lai (lich su dat phong, tai khoan, hoa don...) can dang nhap
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(new CustomerPortalSuccessHandler())
                        .failureHandler(loginFailureHandler("/login"))
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                )
                .userDetailsService(userDetailsService);

        return http.build();
    }
}
