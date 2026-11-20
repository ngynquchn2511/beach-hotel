package com.hotel.repository;

import com.hotel.entity.ReviewReplyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewReplyRuleRepository extends JpaRepository<ReviewReplyRule, Long> {

    List<ReviewReplyRule> findAllByOrderBySortOrderAscIdAsc();
}
