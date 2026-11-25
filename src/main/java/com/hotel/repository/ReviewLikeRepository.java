package com.hotel.repository;

import com.hotel.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    Optional<ReviewLike> findByReviewIdAndUserId(Long reviewId, Long userId);

    long countByReviewId(Long reviewId);

    @Query("select l.review.id, count(l) from ReviewLike l where l.review.id in :ids group by l.review.id")
    List<Object[]> countByReviewIds(@Param("ids") Collection<Long> ids);

    @Query("select l.review.id from ReviewLike l where l.user.id = :userId and l.review.id in :ids")
    List<Long> likedAmong(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    @Query("select l.review.id, count(l) from ReviewLike l group by l.review.id")
    List<Object[]> countAll();

    List<ReviewLike> findByCreatedAtGreaterThanEqual(LocalDateTime from);
}
