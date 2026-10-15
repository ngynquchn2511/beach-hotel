package com.hotel.repository;

import com.hotel.entity.PromotionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionRuleRepository extends JpaRepository<PromotionRule, Long> {

    List<PromotionRule> findByActiveTrueOrderByDiscountPercentDesc();

    List<PromotionRule> findAllByOrderByActiveDescDiscountPercentDesc();
}
