package com.hotel.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class UserEntityTest {

    // onCreate() la @PrePersist, duoc goi boi JPA provider luc luu - goi truc tiep qua reflection de kiem thu don vi
    private void invokeOnCreate(User user) throws Exception {
        Method m = User.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(user);
    }

    @Test
    void onCreate_setsCreatedAt() throws Exception {
        User user = User.builder().email("a@mail.com").role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build();
        assertNull(user.getCreatedAt());

        invokeOnCreate(user);

        assertNotNull(user.getCreatedAt());
    }

    @Test
    void onCreate_defaultsCustomerTypeToNewWhenNull() throws Exception {
        User user = User.builder().email("a@mail.com").role(UserRole.CUSTOMER).build();
        user.setCustomerType(null);

        invokeOnCreate(user);

        assertEquals(CustomerType.NEW, user.getCustomerType());
    }

    @Test
    void onCreate_keepsExplicitCustomerType() throws Exception {
        User user = User.builder().email("a@mail.com").role(UserRole.CUSTOMER).customerType(CustomerType.VIP).build();

        invokeOnCreate(user);

        assertEquals(CustomerType.VIP, user.getCustomerType());
    }
}
