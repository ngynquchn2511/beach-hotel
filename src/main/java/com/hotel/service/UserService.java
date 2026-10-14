package com.hotel.service;

import com.hotel.dto.ChangePasswordRequest;
import com.hotel.dto.ProfileUpdateRequest;
import com.hotel.dto.RegisterRequest;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class UserService {

    // Lien ket dat lai mat khau chi co hieu luc trong 30 phut
    public static final int RESET_TOKEN_VALID_MINUTES = 30;
    // Link kich hoat tai khoan (xac thuc email) co hieu luc 48 gio
    public static final int VERIFY_TOKEN_VALID_HOURS = 48;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerCustomer(RegisterRequest request) {
        // Business rule: email không được trùng
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BusinessException.of("err.emailTaken");
        }

        // Business rule: mật khẩu xác nhận phải giống mật khẩu
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw BusinessException.of("err.passwordMismatch");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.CUSTOMER)
                // Chua dang nhap duoc cho toi khi bam link kich hoat gui toi email nay
                .emailVerified(false)
                .emailVerifyToken(newToken())
                .emailVerifyExpiry(LocalDateTime.now().plusHours(VERIFY_TOKEN_VALID_HOURS))
                .build();

        return userRepository.save(user);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Link gui cho tai khoan khach CHUA KICH HOAT co email nay:
    // - tu dang ky ma chua bam link (co emailVerifyToken) -> link kich hoat moi (setupPassword = false)
    // - tu tao khi dat phong khong dang nhap (chua tung dat mat khau) -> link DAT MAT KHAU (setupPassword = true).
    //   Khong bao gio nhan mat khau tu form dang ky cho tai khoan da co: mat khau chi do chu email dat qua link.
    public record PendingLink(User user, boolean setupPassword) {
    }

    @Transactional
    public Optional<PendingLink> renewPendingLink(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim())
                .filter(u -> u.getRole() == UserRole.CUSTOMER && !u.isEmailVerified())
                .map(u -> {
                    if (u.getEmailVerifyToken() != null) {
                        u.setEmailVerifyToken(newToken());
                        u.setEmailVerifyExpiry(LocalDateTime.now().plusHours(VERIFY_TOKEN_VALID_HOURS));
                        return new PendingLink(userRepository.save(u), false);
                    }
                    u.setResetToken(newToken());
                    u.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALID_MINUTES));
                    return new PendingLink(userRepository.save(u), true);
                });
    }

    // Bam link kich hoat: dung token va con han -> tai khoan dung duoc. Tra ve false neu link sai / het han
    @Transactional
    public boolean verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return userRepository.findByEmailVerifyToken(token)
                .filter(u -> u.getEmailVerifyExpiry() != null && u.getEmailVerifyExpiry().isAfter(LocalDateTime.now()))
                .map(u -> {
                    u.markEmailVerified();
                    userRepository.save(u);
                    return true;
                })
                .orElse(false);
    }

    // Tao token dat lai mat khau cho tai khoan khach hang co email nay.
    // Tra ve Optional.empty() neu email chua dang ky tai khoan khach hang.
    @Transactional
    public Optional<User> createPasswordResetToken(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim())
                .filter(user -> user.getRole() == UserRole.CUSTOMER)
                .map(user -> {
                    byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    user.setResetToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
                    user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALID_MINUTES));
                    return userRepository.save(user);
                });
    }

    public boolean isResetTokenValid(String token) {
        return findValidResetUser(token).isPresent();
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        User user = findValidResetUser(token).orElseThrow(() ->
                BusinessException.of("err.resetLinkInvalid"));

        if (!newPassword.equals(confirmPassword)) {
            throw BusinessException.of("err.passwordMismatch");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        // Token chi dung duoc 1 lan
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        // Chu email da xac minh qua link -> mo khoa tam do sai mat khau (khoa cua admin van giu),
        // tai khoan chua kich hoat (tu dang ky / tu tao khi dat phong) cung duoc kich hoat luon
        user.clearLoginFailures();
        user.markEmailVerified();
        userRepository.save(user);
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.of("err.accountNotFound"));
    }

    // Khach tu sua ho ten / so dien thoai. So dien thoai dung de tim khach khi dat phong tai quay nen khong duoc trung
    @Transactional
    public User updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = findById(userId);
        String phone = request.getPhoneNumber().trim();
        userRepository.findByPhoneNumber(phone)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> {
                    throw BusinessException.of("err.phoneTaken");
                });
        user.setFullName(request.getFullName().trim());
        user.setPhoneNumber(phone);
        return userRepository.save(user);
    }

    // Doi mat khau khi dang dang nhap - bat buoc nhap dung mat khau hien tai
    @Transactional
    public User changePassword(Long userId, ChangePasswordRequest request) {
        User user = findById(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw BusinessException.of("err.wrongPassword");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw BusinessException.of("err.passwordMismatch");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw BusinessException.of("err.samePassword");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        return userRepository.save(user);
    }

    private Optional<User> findValidResetUser(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByResetToken(token)
                .filter(user -> user.getResetTokenExpiry() != null
                        && user.getResetTokenExpiry().isAfter(LocalDateTime.now()));
    }
}
