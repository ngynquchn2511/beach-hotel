package com.hotel.repository;

import com.hotel.entity.PointTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    // Moi nhat len dau, nap san don (neu co) de hien "Don #..."
    @Query("select t from PointTransaction t left join fetch t.booking where t.user.id = :userId order by t.createdAt desc, t.id desc")
    List<PointTransaction> findRecent(@Param("userId") Long userId, Pageable pageable);
}
