package com.hotel.repository;

import com.hotel.entity.ServiceStatus;
import com.hotel.entity.TourBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TourBookingRepository extends JpaRepository<TourBooking, Long> {

    List<TourBooking> findByBookingIdOrderByTourDateAscIdAsc(Long bookingId);

    // So cho da ban cua 1 chuyen: bo qua don dich vu da huy va don dich vu thuoc don phong da huy
    @Query("SELECT COALESCE(SUM(t.participants), 0) FROM TourBooking t WHERE t.tour.id = :tourId AND t.tourDate = :date "
            + "AND t.status <> com.hotel.entity.ServiceStatus.CANCELLED "
            + "AND t.booking.status <> com.hotel.entity.BookingStatus.CANCELLED")
    long sumBookedParticipants(@Param("tourId") Long tourId, @Param("date") LocalDate date);

    @Query("SELECT t FROM TourBooking t JOIN FETCH t.booking b JOIN FETCH t.tour JOIN FETCH b.room "
            + "WHERE t.tourDate >= :from AND (:status IS NULL OR t.status = :status) ORDER BY t.tourDate, t.id")
    List<TourBooking> findForStaff(@Param("from") LocalDate from, @Param("status") ServiceStatus status);

    @Query("SELECT COUNT(t) FROM TourBooking t WHERE t.status = com.hotel.entity.ServiceStatus.PENDING "
            + "AND t.booking.status <> com.hotel.entity.BookingStatus.CANCELLED")
    long countPending();

    boolean existsByTourId(Long tourId);
}
