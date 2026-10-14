package com.hotel.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BookingEntityTest {

    private void invokeOnCreate(Booking booking) throws Exception {
        Method m = Booking.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(booking);
    }

    @Test
    void builder_defaultsNewBookingToTrue() {
        Booking booking = Booking.builder().totalAmount(BigDecimal.ZERO).build();
        assertTrue(booking.isNewBooking());
    }

    @Test
    void builder_defaultsCheckInTimeChangedToFalse() {
        Booking booking = Booking.builder().totalAmount(BigDecimal.ZERO).build();
        assertFalse(booking.isCheckInTimeChanged());
    }

    @Test
    void onCreate_setsCreatedAt() throws Exception {
        Booking booking = Booking.builder().totalAmount(BigDecimal.ZERO).build();
        assertNull(booking.getCreatedAt());

        invokeOnCreate(booking);

        assertNotNull(booking.getCreatedAt());
    }

    @Test
    void onCreate_defaultsStatusToPendingWhenNull() throws Exception {
        Booking booking = Booking.builder().totalAmount(BigDecimal.ZERO).build();
        assertNull(booking.getStatus());

        invokeOnCreate(booking);

        assertEquals(BookingStatus.PENDING, booking.getStatus());
    }

    @Test
    void onCreate_keepsExplicitStatus() throws Exception {
        Booking booking = Booking.builder().totalAmount(BigDecimal.ZERO).status(BookingStatus.CONFIRMED).build();

        invokeOnCreate(booking);

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }
}
