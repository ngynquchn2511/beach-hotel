package com.hotel.service;

import com.hotel.dto.StaffAccountRequest;
import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Quan ly tai khoan noi bo (nhan vien STAFF va quan tri vien ADMIN) - chi ADMIN duoc dung
@Service
public class StaffAccountService {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findAllStaffAccounts() {
        List<User> all = new ArrayList<>(userRepository.findByRoleOrderByCreatedAtDesc(UserRole.ADMIN));
        all.addAll(userRepository.findByRoleOrderByCreatedAtDesc(UserRole.STAFF));
        all.sort(Comparator.comparing(User::getCreatedAt).reversed());
        return all;
    }

    public User findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
        if (user.getRole() == UserRole.CUSTOMER) {
            throw new BusinessException("Đây là tài khoản khách hàng, không thuộc danh sách nhân viên");
        }
        return user;
    }

    @Transactional
    public User create(StaffAccountRequest request) {
        validateRole(request.getRole());
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email này đã được sử dụng cho một tài khoản khác");
        }
        validatePassword(request.getPassword());
        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .phoneNumber(request.getPhoneNumber().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .customerType(CustomerType.NEW)
                .locked(false)
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public User update(Long id, StaffAccountRequest request, Long currentUserId) {
        validateRole(request.getRole());
        User user = findById(id);
        String email = request.getEmail().trim().toLowerCase();
        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmail(email)) {
            throw new BusinessException("Email này đã được sử dụng cho một tài khoản khác");
        }
        // Khong cho tu ha quyen chinh minh -> tranh truong hop he thong khong con ADMIN nao
        if (user.getId().equals(currentUserId) && request.getRole() != UserRole.ADMIN) {
            throw new BusinessException("Bạn không thể tự hạ quyền tài khoản của chính mình");
        }
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPhoneNumber(request.getPhoneNumber().trim());
        user.setRole(request.getRole());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            validatePassword(request.getPassword());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return userRepository.save(user);
    }

    @Transactional
    public void toggleLocked(Long id, Long currentUserId) {
        User user = findById(id);
        if (user.getId().equals(currentUserId)) {
            throw new BusinessException("Bạn không thể tự khóa tài khoản của chính mình");
        }
        user.setLocked(!user.isLocked());
        userRepository.save(user);
    }

    private void validateRole(UserRole role) {
        if (role != UserRole.STAFF && role != UserRole.ADMIN) {
            throw new BusinessException("Vai trò chỉ được là Nhân viên hoặc Quản trị viên");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự");
        }
    }
}
