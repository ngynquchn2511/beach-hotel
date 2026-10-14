package com.hotel.security;

import com.hotel.entity.User;
import com.hotel.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

// Chong do mat khau: sai MAX_FAILED_ATTEMPTS lan lien tiep -> khoa tam LOCK_MINUTES phut (ca cong khach lan cong quan tri).
// Nghe su kien xac thuc cua Spring Security: sai mat khau -> dem them, dang nhap dung -> xoa dem.
// Email khong ton tai thi bo qua (khong tao gi trong DB).
@Service
public class LoginAttemptService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int LOCK_MINUTES = 15;

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;

    public LoginAttemptService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @EventListener
    @Transactional
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        recordFailure(event.getAuthentication().getName(), LocalDateTime.now());
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof CustomUserDetails details) {
            recordSuccess(details.getUser().getEmail());
        }
    }

    @Transactional
    public void recordFailure(String email, LocalDateTime now) {
        Optional<User> found = email == null ? Optional.empty() : userRepository.findByEmail(email.trim());
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        // Het han khoa tam truoc do -> dem lai tu dau
        if (user.getLoginLockedUntil() != null && !user.getLoginLockedUntil().isAfter(now)) {
            user.clearLoginFailures();
        }
        int failures = user.getFailedLoginCount() + 1;
        if (failures >= MAX_FAILED_ATTEMPTS) {
            user.setLoginLockedUntil(now.plusMinutes(LOCK_MINUTES));
            user.setFailedLoginAttempts(0);
            log.warn("Khoa tam tai khoan {} {} phut vi sai mat khau {} lan lien tiep", user.getEmail(), LOCK_MINUTES, failures);
        } else {
            user.setFailedLoginAttempts(failures);
        }
        userRepository.save(user);
    }

    @Transactional
    public void recordSuccess(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getFailedLoginCount() > 0 || user.getLoginLockedUntil() != null) {
                user.clearLoginFailures();
                userRepository.save(user);
            }
        });
    }

    // Trang dang nhap: tai khoan nay dang bi khoa tam (de bao "thu lai sau X phut" thay vi "sai mat khau")
    public Optional<LocalDateTime> temporaryLockUntil(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim())
                .filter(u -> !u.isLocked() && u.isTemporarilyLocked())
                .map(User::getLoginLockedUntil);
    }
}
