package com.hotel.repository;

import com.hotel.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    @Query("select p from Payslip p join fetch p.staff where p.payMonth = :month")
    List<Payslip> findForMonth(@Param("month") LocalDate month);

    Optional<Payslip> findByStaffIdAndPayMonth(Long staffId, LocalDate payMonth);
}
