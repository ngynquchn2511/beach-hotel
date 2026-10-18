package com.hotel.repository;

import com.hotel.entity.HousekeepingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HousekeepingLogRepository extends JpaRepository<HousekeepingLog, Long> {

    @Query("select l from HousekeepingLog l join fetch l.room left join fetch l.cleaner "
            + "where l.finishedAt >= :from and l.finishedAt < :to order by l.finishedAt desc")
    List<HousekeepingLog> findBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
