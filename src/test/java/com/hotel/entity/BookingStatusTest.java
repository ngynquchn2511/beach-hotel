package com.hotel.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BookingStatusTest {

    @Test
    void getVietnameseLabel_returnsCorrectLabelForEachStatus() {
        assertEquals("Chờ xác nhận", BookingStatus.PENDING.getVietnameseLabel());
        assertEquals("Đã xác nhận", BookingStatus.CONFIRMED.getVietnameseLabel());
        assertEquals("Đã nhận phòng", BookingStatus.CHECKED_IN.getVietnameseLabel());
        assertEquals("Đã trả phòng", BookingStatus.CHECKED_OUT.getVietnameseLabel());
        assertEquals("Đã hủy", BookingStatus.CANCELLED.getVietnameseLabel());
    }

    @Test
    void getBadgeClass_returnsDistinctClassForEachStatus() {
        long distinctCount = java.util.Arrays.stream(BookingStatus.values())
                .map(BookingStatus::getBadgeClass)
                .distinct()
                .count();
        assertEquals(BookingStatus.values().length, distinctCount, "Mỗi trạng thái phải có 1 class badge riêng để dễ phân biệt");
    }

    @Test
    void getBadgeClass_pendingIsWarning() {
        assertEquals("bg-warning text-dark", BookingStatus.PENDING.getBadgeClass());
    }

    @Test
    void getBadgeClass_cancelledIsDanger() {
        assertEquals("bg-danger", BookingStatus.CANCELLED.getBadgeClass());
    }
}
