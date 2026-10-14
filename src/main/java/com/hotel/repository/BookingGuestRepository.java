package com.hotel.repository;

import com.hotel.entity.BookingGuest;
import com.hotel.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingGuestRepository extends JpaRepository<BookingGuest, Long> {

    List<BookingGuest> findByBookingIdOrderByIdAsc(Long bookingId);

    long countByBookingId(Long bookingId);

    // Khach cua cac don co thoi gian o giao voi [from, to] (tinh theo ngay nhan/tra phong), dung cho trang khai bao luu tru
    @Query("SELECT g FROM BookingGuest g JOIN FETCH g.booking b JOIN FETCH b.room "
            + "WHERE b.status IN :statuses AND b.checkInDate <= :to AND b.checkOutDate > :from "
            + "ORDER BY b.checkInDate, b.id, g.id")
    List<BookingGuest> findStaying(LocalDate from, LocalDate to, Collection<BookingStatus> statuses);
}
