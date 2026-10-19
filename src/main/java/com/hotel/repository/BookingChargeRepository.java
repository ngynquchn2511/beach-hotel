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

    // Phu phi phat sinh trong [from, to) kem don + phong + hang phong
    @Query("select c from BookingCharge c join fetch c.booking b join fetch b.room r join fetch r.roomType "
            + "where c.createdAt >= :from and c.createdAt < :to")
    List<BookingCharge> findCreatedBetween(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                                           @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);

    List<BookingCharge> findByBookingIdIn(java.util.Collection<Long> bookingIds);
}
