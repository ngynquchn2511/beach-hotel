package com.hotel.security;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_found_returnsCustomUserDetails() {
        User user = User.builder().email("a@mail.com").password("hash").role(UserRole.CUSTOMER).build();
        when(userRepository.findByEmail("a@mail.com")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("a@mail.com");

        assertInstanceOf(CustomUserDetails.class, result);
        assertEquals("a@mail.com", result.getUsername());
    }

    @Test
    void loadUserByUsername_notFound_throws() {
        when(userRepository.findByEmail("notfound@mail.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("notfound@mail.com"));
    }
}
