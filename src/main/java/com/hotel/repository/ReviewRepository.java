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

    // ===== Trang "Khach noi gi" (/reviews): media 0 = tat ca, 1 = co anh / video, 2 = co video; rating null = moi so sao =====
    String WALL_WHERE = "(r.hidden is null or r.hidden = false) and (:rating is null or r.rating = :rating)"
            + " and (:media = 0 or (:media = 1 and exists (select m.id from ReviewMedia m where m.review = r))"
            + " or (:media = 2 and exists (select m.id from ReviewMedia m where m.review = r and m.mediaType = com.hotel.entity.MediaType.VIDEO)))";

    @Query(value = "select r from Review r where " + WALL_WHERE + " order by r.createdAt desc",
            countQuery = "select count(r) from Review r where " + WALL_WHERE)
    org.springframework.data.domain.Page<Review> wallNewest(@Param("rating") Integer rating, @Param("media") int media, Pageable pageable);

    // Duoc tha tim nhieu nhat truoc
    @Query(value = "select r from Review r where " + WALL_WHERE
            + " order by (select count(l.id) from ReviewLike l where l.review = r) desc, r.createdAt desc",
            countQuery = "select count(r) from Review r where " + WALL_WHERE)
    org.springframework.data.domain.Page<Review> wallTop(@Param("rating") Integer rating, @Param("media") int media, Pageable pageable);
}
