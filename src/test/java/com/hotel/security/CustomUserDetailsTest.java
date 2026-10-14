package com.hotel.security;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomUserDetailsTest {

    @Test
    void getAuthorities_customerRole_returnsRoleCustomer() {
        User user = User.builder().email("a@mail.com").password("hash").role(UserRole.CUSTOMER).build();
        CustomUserDetails details = new CustomUserDetails(user);

        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER")));
    }

    @Test
    void getAuthorities_adminRole_returnsRoleAdmin() {
        User user = User.builder().email("admin@mail.com").password("hash").role(UserRole.ADMIN).build();
        CustomUserDetails details = new CustomUserDetails(user);

        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void getUsername_returnsEmail() {
        User user = User.builder().email("x@mail.com").password("hash").role(UserRole.STAFF).build();
        CustomUserDetails details = new CustomUserDetails(user);

        assertEquals("x@mail.com", details.getUsername());
    }

    @Test
    void accountFlags_allTrue() {
        User user = User.builder().email("x@mail.com").password("hash").role(UserRole.STAFF).build();
        CustomUserDetails details = new CustomUserDetails(user);

        assertTrue(details.isAccountNonExpired());
        assertTrue(details.isAccountNonLocked());
        assertTrue(details.isCredentialsNonExpired());
        assertTrue(details.isEnabled());
    }
}
