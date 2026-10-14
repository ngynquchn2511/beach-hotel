package com.hotel.repository;

import com.hotel.entity.SpecialRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface SpecialRateRepository extends JpaRepository<SpecialRate, Long> {

    List<SpecialRate> findAllByOrderByStartDateDesc();

    // Cac giai doan co giao voi khoang dem [from, to] (tinh ca 2 dau)
    @Query("SELECT s FROM SpecialRate s WHERE s.startDate <= :to AND s.endDate >= :from")
    List<SpecialRate> findOverlapping(LocalDate from, LocalDate to);
}
