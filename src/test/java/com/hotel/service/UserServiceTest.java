package com.hotel.service;

import com.hotel.dto.RegisterRequest;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private RegisterRequest validRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setFullName("Nguyen Quoc Hoan");
        r.setEmail("hoan@mail.com");
        r.setPhoneNumber("0901234567");
        r.setPassword("Abc12345");
        r.setConfirmPassword("Abc12345");
        return r;
    }

    @Test
    void registerCustomer_duplicateEmail_throws() {
        RegisterRequest req = validRequest();
        when(userRepository.existsByEmail("hoan@mail.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.registerCustomer(req));
        assertTrue(ex.getMessage().contains("đã được đăng ký"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerCustomer_passwordMismatch_throws() {
        RegisterRequest req = validRequest();
        req.setConfirmPassword("khac-mat-khau");
        when(userRepository.existsByEmail("hoan@mail.com")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.registerCustomer(req));
        assertTrue(ex.getMessage().contains("không khớp"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerCustomer_success_encodesPasswordAndSetsCustomerRole() {
        RegisterRequest req = validRequest();
        when(userRepository.existsByEmail("hoan@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("Abc12345")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerCustomer(req);

        assertEquals("$2a$10$encoded", result.getPassword());
        assertNotEquals("Abc12345", result.getPassword());
        assertEquals(UserRole.CUSTOMER, result.getRole());
        assertEquals("hoan@mail.com", result.getEmail());
    }
}
