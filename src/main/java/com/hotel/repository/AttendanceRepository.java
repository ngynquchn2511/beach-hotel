package com.hotel.repository;

import com.hotel.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByStaffIdAndWorkDate(Long staffId, LocalDate workDate);

    @Query("select a from Attendance a join fetch a.staff where a.workDate between :from and :to order by a.workDate desc, a.checkInAt desc")
    List<Attendance> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select a from Attendance a where a.staff.id = :staffId and a.workDate between :from and :to order by a.workDate desc")
    List<Attendance> findForStaff(@Param("staffId") Long staffId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
