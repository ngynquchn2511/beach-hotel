package com.hotel.repository;

import com.hotel.entity.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

    Optional<DiscountCode> findByCodeAndActiveTrue(String code);

    boolean existsByCode(String code);

    List<DiscountCode> findByActiveTrue();
}
