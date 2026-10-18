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

    // The khach da mua hoac duoc tang (theo email tai khoan)
    @org.springframework.data.jpa.repository.Query("select v from GiftVoucher v where lower(v.purchaserEmail) = lower(:email) "
            + "or lower(v.recipientEmail) = lower(:email) order by v.createdAt desc")
    List<GiftVoucher> findForEmail(@org.springframework.data.repository.query.Param("email") String email);
}
