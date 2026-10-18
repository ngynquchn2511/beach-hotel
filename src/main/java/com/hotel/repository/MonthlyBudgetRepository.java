package com.hotel.repository;

import com.hotel.entity.MonthlyBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, LocalDate> {
}
