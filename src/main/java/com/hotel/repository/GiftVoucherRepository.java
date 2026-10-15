package com.hotel.repository;

import com.hotel.entity.GiftVoucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GiftVoucherRepository extends JpaRepository<GiftVoucher, Long> {

    Optional<GiftVoucher> findByCodeIgnoreCase(String code);

    boolean existsByCode(String code);

    List<GiftVoucher> findAllByOrderByCreatedAtDesc();

    List<GiftVoucher> findByPaidAtBetween(LocalDateTime from, LocalDateTime to);
}
