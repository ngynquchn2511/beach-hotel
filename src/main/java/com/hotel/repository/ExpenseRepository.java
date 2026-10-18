package com.hotel.repository;

import com.hotel.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // Khoan chi co the cham vao [from, to]: 1 lan trong khoang, hoac hang thang bat dau truoc to va chua ket thuc truoc from
    @Query("select e from Expense e where (e.recurrence = com.hotel.entity.Expense.Recurrence.ONE_OFF and e.expenseDate between :from and :to) "
            + "or (e.recurrence = com.hotel.entity.Expense.Recurrence.MONTHLY and e.startMonth <= :to and (e.endMonth is null or e.endMonth >= :fromMonth)) "
            + "order by e.category, e.description")
    List<Expense> findTouching(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("fromMonth") LocalDate fromMonth);
}
