package com.hotel.config;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.UserRepository;
import com.hotel.service.SpaPackageService;
import com.hotel.service.TransferRateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Tao san tai khoan ADMIN va STAFF (chi tao neu chua ton tai) va bang gia dua don san bay mac dinh.
// Mat khau lay tu cau hinh: o dev/test mac dinh admin123/staff123, o profile prod bat buoc dat
// bien moi truong ADMIN_PASSWORD/STAFF_PASSWORD (thieu thi ung dung khong khoi dong).
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransferRateService transferRateService;
    private final SpaPackageService spaPackageService;

    @Value("${app.admin.email:admin@hotel.com}")
    private String adminEmail;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.staff.email:staff@hotel.com}")
    private String staffEmail;

    @Value("${app.staff.password:staff123}")
    private String staffPassword;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           TransferRateService transferRateService, SpaPackageService spaPackageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.transferRateService = transferRateService;
        this.spaPackageService = spaPackageService;
    }

    @Override
    public void run(String... args) {
        createIfMissing(adminEmail, "Quản trị viên", "0900000001", adminPassword, UserRole.ADMIN);
        createIfMissing(staffEmail, "Nhân viên lễ tân", "0900000002", staffPassword, UserRole.STAFF);
        // Bang gia xe dua don san bay mac dinh (chi tao o con thieu) - admin sua lai o trang "Giá đưa đón"
        transferRateService.ensureDefaults();
        // 4 goi spa mac dinh khi chua co goi nao - admin sua o trang "Goi spa"
        spaPackageService.ensureDefaults();
    }

    private void createIfMissing(String email, String fullName, String phone, String rawPassword, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .phoneNumber(phone)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build();
        userRepository.save(user);
    }
}
