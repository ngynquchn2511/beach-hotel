package com.hotel.repository;

import com.hotel.entity.AirportTransfer;
import com.hotel.entity.ServiceStatus;
import com.hotel.entity.TransferDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AirportTransferRepository extends JpaRepository<AirportTransfer, Long> {

    List<AirportTransfer> findByBookingIdOrderByPickupAtAsc(Long bookingId);

    boolean existsByBookingIdAndDirectionAndStatusNot(Long bookingId, TransferDirection direction, ServiceStatus status);

    @Query("SELECT a FROM AirportTransfer a JOIN FETCH a.booking b JOIN FETCH b.room "
            + "WHERE a.pickupAt >= :from AND (:status IS NULL OR a.status = :status) ORDER BY a.pickupAt, a.id")
    List<AirportTransfer> findForStaff(@Param("from") LocalDateTime from, @Param("status") ServiceStatus status);

    @Query("SELECT COUNT(a) FROM AirportTransfer a WHERE a.status = com.hotel.entity.ServiceStatus.PENDING "
            + "AND a.booking.status <> com.hotel.entity.BookingStatus.CANCELLED")
    long countPending();
}
