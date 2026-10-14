package com.hotel.repository;

import com.hotel.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    List<Review> findByBookingIdIn(Collection<Long> bookingIds);

    List<Review> findAllByOrderByCreatedAtDesc();

    // Danh gia dang hien (khong bi admin an) va co noi dung, moi nhat truoc - dung cho trang Gioi thieu
    @Query("select r from Review r where (r.hidden is null or r.hidden = false) and r.comment is not null order by r.createdAt desc")
    List<Review> findVisibleWithComment(Pageable pageable);

    // Danh gia dang hien cua 1 loai phong
    @Query("select r from Review r where r.booking.room.roomType.id = :roomTypeId and (r.hidden is null or r.hidden = false) order by r.createdAt desc")
    List<Review> findVisibleByRoomType(@Param("roomTypeId") Long roomTypeId, Pageable pageable);

    // [diem trung binh, so luot] danh gia dang hien cua 1 loai phong
    @Query("select avg(r.rating), count(r) from Review r where r.booking.room.roomType.id = :roomTypeId and (r.hidden is null or r.hidden = false)")
    List<Object[]> statsByRoomType(@Param("roomTypeId") Long roomTypeId);
}
