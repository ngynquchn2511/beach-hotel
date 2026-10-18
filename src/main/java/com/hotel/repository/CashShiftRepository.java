package com.hotel.repository;

import com.hotel.entity.CashShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {

    @Query("select s from CashShift s join fetch s.openedBy where s.status = com.hotel.entity.CashShift.Status.OPEN")
    Optional<CashShift> findOpen();

    Optional<CashShift> findFirstByStatusOrderByClosedAtDesc(CashShift.Status status);

    @Query("select s from CashShift s join fetch s.openedBy left join fetch s.closedBy order by s.openedAt desc")
    List<CashShift> findAllForList();

    @Query("select s from CashShift s join fetch s.openedBy left join fetch s.closedBy where s.openedAt between :from and :to order by s.openedAt")
    List<CashShift> findOpenedBetween(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                                      @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);
}
