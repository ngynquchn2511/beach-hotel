package com.hotel.config;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Tao san tai khoan ADMIN va STAFF, chi tao neu chua ton tai.
// Mat khau lay tu cau hinh: o dev/test mac dinh admin123/staff123, o profile prod bat buoc dat
// bien moi truong ADMIN_PASSWORD/STAFF_PASSWORD (thieu thi ung dung khong khoi dong).
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@hotel.com}")
    private String adminEmail;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.staff.email:staff@hotel.com}")
    private String staffEmail;

    @Value("${app.staff.password:staff123}")
    private String staffPassword;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createIfMissing(adminEmail, "Quản trị viên", "0900000001", adminPassword, UserRole.ADMIN);
        createIfMissing(staffEmail, "Nhân viên lễ tân", "0900000002", staffPassword, UserRole.STAFF);
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
