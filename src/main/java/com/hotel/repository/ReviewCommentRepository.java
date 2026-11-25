package com.hotel.repository;

import com.hotel.entity.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    List<ReviewComment> findByReviewIdOrderByCreatedAtAsc(Long reviewId);

    List<ReviewComment> findByReviewIdInOrderByCreatedAtAsc(Collection<Long> reviewIds);

    // Dem binh luan dang hien
    @Query("select c.review.id, count(c) from ReviewComment c where c.review.id in :ids and (c.hidden is null or c.hidden = false) group by c.review.id")
    List<Object[]> countVisibleByReviewIds(@Param("ids") Collection<Long> ids);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    List<ReviewComment> findByCreatedAtGreaterThanEqual(LocalDateTime from);

    List<ReviewComment> findTop20ByOrderByCreatedAtDesc();
}
