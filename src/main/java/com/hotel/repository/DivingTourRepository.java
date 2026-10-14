package com.hotel.repository;

import com.hotel.entity.DivingTour;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DivingTourRepository extends JpaRepository<DivingTour, Long> {

    List<DivingTour> findByActiveTrueOrderByPricePerPersonAsc();

    List<DivingTour> findAllByOrderByIdAsc();

    // Khoa dong tour truoc khi dem cho con trong + luu don: 2 khach dat cung chuyen cung luc phai xep hang,
    // tranh ban qua so cho (giong cach khoa dong phong khi dat phong)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT t FROM DivingTour t WHERE t.id = :id")
    Optional<DivingTour> findByIdForUpdate(@Param("id") Long id);
}
