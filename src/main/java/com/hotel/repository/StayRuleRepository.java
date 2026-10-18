package com.hotel.repository;

import com.hotel.entity.StayRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StayRuleRepository extends JpaRepository<StayRule, Long> {

    List<StayRule> findAllByOrderByStartDateDesc();

    // Luat co khoang ngay giao voi [from, to]
    @Query("select r from StayRule r where r.startDate <= :to and r.endDate >= :from")
    List<StayRule> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
