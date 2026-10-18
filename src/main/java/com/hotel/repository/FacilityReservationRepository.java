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

    // Luot spa chua huy trong ngay (kem goi de biet thoi luong)
    @Query("select r from FacilityReservation r join fetch r.spaPackage where r.kind = com.hotel.entity.FacilityReservation.Kind.SPA "
            + "and r.reservedDate = :date and r.status <> com.hotel.entity.ServiceStatus.CANCELLED")
    List<FacilityReservation> findActiveSpaOn(@Param("date") LocalDate date);

    // So khach nha hang da giu o 1 khung gio (chua huy)
    @Query("select coalesce(sum(r.people), 0) from FacilityReservation r where r.kind = :kind and r.reservedDate = :date "
            + "and r.reservedTime = :time and r.status <> com.hotel.entity.ServiceStatus.CANCELLED")
    long sumPeople(@Param("kind") FacilityReservation.Kind kind, @Param("date") LocalDate date, @Param("time") LocalTime time);

    @Query("select r from FacilityReservation r join fetch r.booking b join fetch b.room left join fetch r.spaPackage where r.reservedDate >= :from "
            + "and (:status is null or r.status = :status) order by r.reservedDate, r.reservedTime")
    List<FacilityReservation> findForStaff(@Param("from") LocalDate from, @Param("status") ServiceStatus status);

    long countByStatus(ServiceStatus status);

    // Diem trung binh + so luot danh gia theo goi spa: [spaPackageId, avg, count]
    @Query("select r.spaPackage.id, avg(r.rating), count(r) from FacilityReservation r where r.rating is not null "
            + "and r.kind = com.hotel.entity.FacilityReservation.Kind.SPA group by r.spaPackage.id")
    List<Object[]> spaRatingStats();

    // [avg, count] cua nha hang
    @Query("select avg(r.rating), count(r) from FacilityReservation r where r.rating is not null "
            + "and r.kind = com.hotel.entity.FacilityReservation.Kind.RESTAURANT")
    List<Object[]> restaurantRatingStats();

    @Query("select r from FacilityReservation r join fetch r.booking b join fetch b.room left join fetch r.spaPackage "
            + "where r.rating is not null order by r.ratedAt desc")
    List<FacilityReservation> findRated(org.springframework.data.domain.Pageable pageable);
}
