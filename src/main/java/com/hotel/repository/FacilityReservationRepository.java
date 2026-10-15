package com.hotel.repository;

import com.hotel.entity.FacilityReservation;
import com.hotel.entity.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface FacilityReservationRepository extends JpaRepository<FacilityReservation, Long> {

    List<FacilityReservation> findByBookingIdOrderByReservedDateAscReservedTimeAsc(Long bookingId);

    // So luot spa / so khach nha hang da giu o 1 khung gio (chua huy)
    @Query("select count(r) from FacilityReservation r where r.kind = :kind and r.reservedDate = :date "
            + "and r.reservedTime = :time and r.status <> com.hotel.entity.ServiceStatus.CANCELLED")
    long countActive(@Param("kind") FacilityReservation.Kind kind, @Param("date") LocalDate date, @Param("time") LocalTime time);

    @Query("select coalesce(sum(r.people), 0) from FacilityReservation r where r.kind = :kind and r.reservedDate = :date "
            + "and r.reservedTime = :time and r.status <> com.hotel.entity.ServiceStatus.CANCELLED")
    long sumPeople(@Param("kind") FacilityReservation.Kind kind, @Param("date") LocalDate date, @Param("time") LocalTime time);

    @Query("select r from FacilityReservation r join fetch r.booking b join fetch b.room where r.reservedDate >= :from "
            + "and (:status is null or r.status = :status) order by r.reservedDate, r.reservedTime")
    List<FacilityReservation> findForStaff(@Param("from") LocalDate from, @Param("status") ServiceStatus status);

    long countByStatus(ServiceStatus status);
}
