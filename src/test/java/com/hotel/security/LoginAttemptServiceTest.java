package com.hotel.security;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Dem sai mat khau va khoa tam: 5 lan lien tiep -> khoa 15 phut, het han thi dem lai, dang nhap dung thi xoa dem
class LoginAttemptServiceTest {

    private UserRepository userRepository;
    private LoginAttemptService service;
    private User user;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 10, 0);

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new LoginAttemptService(userRepository);
        user = User.builder().id(1L).email("khach@mail.com").role(UserRole.CUSTOMER).build();
        when(userRepository.findByEmail("khach@mail.com")).thenReturn(Optional.of(user));
    }

    @Test
    void fifthFailure_locksForFifteenMinutes_andResetsCounter() {
        for (int i = 1; i < LoginAttemptService.MAX_FAILED_ATTEMPTS; i++) {
            service.recordFailure("khach@mail.com", NOW);
            assertThat(user.getFailedLoginCount()).isEqualTo(i);
            assertThat(user.getLoginLockedUntil()).isNull();
        }

        service.recordFailure("khach@mail.com", NOW);

        assertThat(user.getLoginLockedUntil()).isEqualTo(NOW.plusMinutes(LoginAttemptService.LOCK_MINUTES));
        assertThat(user.getFailedLoginCount()).isZero();
    }

    @Test
    void failureAfterLockExpired_startsCountingAgain() {
        user.setLoginLockedUntil(NOW.minusMinutes(1));

        service.recordFailure("khach@mail.com", NOW);

        assertThat(user.getLoginLockedUntil()).isNull();
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    void unknownEmail_ignored() {
        when(userRepository.findByEmail("khong-co@mail.com")).thenReturn(Optional.empty());

        service.recordFailure("khong-co@mail.com", NOW);
        service.recordFailure(null, NOW);

        verify(userRepository, never()).save(any());
    }

    @Test
    void success_clearsCounterAndLock_onlySavesWhenNeeded() {
        service.recordSuccess("khach@mail.com");
        verify(userRepository, never()).save(any());

        user.setFailedLoginAttempts(3);
        service.recordSuccess("khach@mail.com");
        assertThat(user.getFailedLoginCount()).isZero();
        verify(userRepository).save(user);
    }

    @Test
    void temporaryLockUntil_onlyForTemporaryNotAdminLock() {
        assertThat(service.temporaryLockUntil("khach@mail.com")).isEmpty();

        LocalDateTime until = LocalDateTime.now().plusMinutes(10);
        user.setLoginLockedUntil(until);
        assertThat(service.temporaryLockUntil("khach@mail.com")).contains(until);

        // Bi admin khoa -> bao "lien he quan tri" chu khong phai "thu lai sau"
        user.setLocked(true);
        assertThat(service.temporaryLockUntil("khach@mail.com")).isEmpty();
        assertThat(service.temporaryLockUntil(" ")).isEmpty();
    }

    @Test
    void adminToggle_unlocksTemporaryLockFirst_thenNormalToggleClearsCounter() {
        user.setLoginLockedUntil(LocalDateTime.now().plusMinutes(10));
        user.setFailedLoginAttempts(2);

        user.toggleLockByAdmin();
        assertThat(user.isTemporarilyLocked()).isFalse();
        assertThat(user.isLocked()).isFalse();

        user.toggleLockByAdmin();
        assertThat(user.isLocked()).isTrue();

        user.setFailedLoginAttempts(4);
        user.toggleLockByAdmin();
        assertThat(user.isLocked()).isFalse();
        assertThat(user.getFailedLoginCount()).isZero();
    }
}
