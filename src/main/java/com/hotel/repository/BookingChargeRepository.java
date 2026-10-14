package com.hotel.repository;

import com.hotel.entity.BookingCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BookingChargeRepository extends JpaRepository<BookingCharge, Long> {

    List<BookingCharge> findByBookingIdOrderByCreatedAtAsc(Long bookingId);

    // Tong phu phi cua tung don - moi phan tu la [bookingId (Long), tong tien (BigDecimal)]
    @Query("SELECT c.booking.id, SUM(c.amount) FROM BookingCharge c GROUP BY c.booking.id")
    List<Object[]> sumAmountGroupByBooking();
}
