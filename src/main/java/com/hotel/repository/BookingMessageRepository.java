package com.hotel.repository;

import com.hotel.entity.BookingMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingMessageRepository extends JpaRepository<BookingMessage, Long> {

    List<BookingMessage> findByBookingIdOrderByCreatedAtAscIdAsc(Long bookingId);

    // Tin khach gui chua ai doc
    long countByFromGuestTrueAndReadAtIsNull();

    long countByBookingIdAndFromGuestTrueAndCreatedAtAfter(Long bookingId, LocalDateTime after);

    // Tin moi nhat moi don (hop tin nhan cua le tan)
    @Query("select m from BookingMessage m join fetch m.booking b join fetch b.room where m.id in "
            + "(select max(x.id) from BookingMessage x group by x.booking.id) order by m.createdAt desc")
    List<BookingMessage> findLatestPerBooking();

    @Query("select m.booking.id, count(m) from BookingMessage m where m.fromGuest = true and m.readAt is null group by m.booking.id")
    List<Object[]> countUnreadPerBooking();

    @Query("select m from BookingMessage m where m.booking.id = :bookingId and m.fromGuest = :fromGuest and m.readAt is null")
    List<BookingMessage> findUnread(@Param("bookingId") Long bookingId, @Param("fromGuest") boolean fromGuest);
}
