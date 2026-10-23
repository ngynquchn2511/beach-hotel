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
                        // Moi vai tro cong quan tri: trang chu theo vai tro + nhap ma OTP
                        .requestMatchers("/admin/home", "/admin/otp", "/admin/otp/**")
                                .hasAnyRole("ADMIN", "STAFF", "HOUSEKEEPER", "ACCOUNTANT")
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
                        // Dong bo OTA: le tan xem / dong bo / ghi nhan don; them, xoa, bat tat link chi ADMIN
                        .requestMatchers(HttpMethod.POST, "/admin/ota/feeds", "/admin/ota/feeds/*/toggle", "/admin/ota/feeds/*/delete").hasRole("ADMIN")
                        // Le tan giao mon duoc, sua thuc don chi ADMIN
                        .requestMatchers("/admin/room-service/menu", "/admin/room-service/menu/**").hasRole("ADMIN")
                        .requestMatchers("/admin/spa-packages", "/admin/spa-packages/**").hasRole("ADMIN")
                        .requestMatchers("/admin/quick-replies", "/admin/quick-replies/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/admin/upgrades/settings").hasRole("ADMIN")
                        // Cac chuc nang con lai (don dat phong, check-in/out, trang thai phong, doanh thu...) - ca STAFF va ADMIN
                        // Buong phong: don phong, bao hong / bao tri, do that lac
                        // Phan cong / kiem phong / cai dat: le tan, quan ly
                        .requestMatchers("/admin/housekeeping/*/assign", "/admin/housekeeping/*/inspect/**",
                                "/admin/housekeeping/settings", "/admin/housekeeping/stats").hasAnyRole("ADMIN", "STAFF")
                        // Thong bao tuc thoi: le tan va buong phong (moi vai tro chi nhan thong bao cua minh)
                        .requestMatchers("/admin/notifications/**").hasAnyRole("ADMIN", "STAFF", "HOUSEKEEPER")
                        .requestMatchers("/admin/housekeeping", "/admin/housekeeping/**", "/admin/maintenance", "/admin/maintenance/**",
                                "/admin/lost-found", "/admin/lost-found/**").hasAnyRole("ADMIN", "STAFF", "HOUSEKEEPER")
                        // Ke toan: hoa don cong ty (ghi so HDDT), xem doanh thu / so quy ca / the qua tang / hoa don don phong
                        .requestMatchers("/admin/company-invoices", "/admin/company-invoices/**").hasAnyRole("ADMIN", "STAFF", "ACCOUNTANT")
                        // Bao cao cuoi ngay: le tan / ke toan xem, gui email ngay chi ADMIN
                        .requestMatchers(HttpMethod.POST, "/admin/reports/daily/send").hasRole("ADMIN")
                        .requestMatchers("/admin/reports/daily").hasAnyRole("ADMIN", "STAFF", "ACCOUNTANT")
                        .requestMatchers("/admin/reports/cancellations", "/admin/reports/cancellations/**").hasAnyRole("ADMIN", "STAFF", "ACCOUNTANT")
                        // Bao cao ke toan: so quy thang, doi soat VNPay
                        .requestMatchers("/admin/accounting", "/admin/accounting/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers(HttpMethod.GET, "/admin/dashboard", "/admin/dashboard/export", "/admin/forecast",
                                "/admin/shifts", "/admin/shifts/*", "/admin/gift-vouchers",
                                "/admin/bookings/*/invoice", "/admin/bookings/*/invoice.pdf").hasAnyRole("ADMIN", "STAFF", "ACCOUNTANT")
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
                        .requestMatchers("/", "/home", "/about", "/services", "/rooms", "/rooms/**", "/login", "/register",
                                "/forgot-password", "/reset-password", "/verify-email", "/resend-verification", "/robots.txt", "/sitemap.xml",
                                "/css/**", "/js/**", "/images/**", "/uploads/**", "/page-images/**").permitAll()
                        // Chatbot tu van - bat buoc dang nhap (khach chua dang nhap thay nut "Dang nhap" trong khung chat)
                        .requestMatchers("/api/chatbot/**").authenticated()
                        // Form "Gui yeu cau tu van" (lien he) - bat buoc dang nhap; GET /contact = dang nhap roi quay lai form
                        .requestMatchers("/contact").authenticated()
                        // Khach vang lai chi duoc XEM / TIM phong (+ dang ky o tren). Moi chuc nang
                        // con lai cua khach (chatbot, lien he, dat phong, xem / huy / coc / doi ngay don, dich vu them, nhan tin, danh gia,
                        // "bao toi khi phong trong", mua the qua tang...) bat buoc dang nhap tai khoan khach hang.
                        // Chua dang nhap -> chuyen ve /login, dang nhap xong quay lai dung trang dang mo (CustomerPortalSuccessHandler)
                        .requestMatchers("/customer/rooms", "/customer/rooms/*").permitAll()
                        .requestMatchers("/gift-vouchers", "/gift-vouchers/**").hasRole("CUSTOMER")
                        // Lich .ics cho Google Calendar / Booking.com - kiem tra ma bi mat ?key= trong IcalController
                        .requestMatchers(HttpMethod.GET, "/ical/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/payment/vnpay/return", "/payment/vnpay/ipn").permitAll()
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
