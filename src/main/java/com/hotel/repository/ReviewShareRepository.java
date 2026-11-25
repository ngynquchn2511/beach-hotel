package com.hotel.repository;

import com.hotel.entity.ReviewShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewShareRepository extends JpaRepository<ReviewShare, Long> {

    List<ReviewShare> findByCreatedAtGreaterThanEqual(LocalDateTime from);

    long countByReviewId(Long reviewId);

    @Query("select s.review.id, count(s) from ReviewShare s group by s.review.id")
    List<Object[]> countAll();
}
