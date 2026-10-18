package com.hotel.repository;

import com.hotel.entity.AiReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AiReportRepository extends JpaRepository<AiReport, Long> {

    Optional<AiReport> findFirstByReportMonthAndKindOrderByCreatedAtDescIdDesc(LocalDate reportMonth, AiReport.Kind kind);

    List<AiReport> findTop20ByReportMonthAndKindOrderByCreatedAtDescIdDesc(LocalDate reportMonth, AiReport.Kind kind);
}
