package com.hotel.repository;

import com.hotel.entity.DutyShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DutyShiftRepository extends JpaRepository<DutyShift, Long> {

    @Query("select d from DutyShift d join fetch d.staff where d.workDate between :from and :to order by d.workDate, d.shift, d.staff.fullName")
    List<DutyShift> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select d from DutyShift d where d.staff.id = :staffId and d.workDate = :day order by d.shift")
    List<DutyShift> findForStaff(@Param("staffId") Long staffId, @Param("day") LocalDate day);

    boolean existsByStaffIdAndWorkDateAndShift(Long staffId, LocalDate workDate, DutyShift.Shift shift);
}
