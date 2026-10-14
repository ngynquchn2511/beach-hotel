package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.Review;
import com.hotel.entity.ReviewMedia;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ReviewRepository reviewRepository;
    private final BookingService bookingService;
    private final FileStorageService fileStorageService;

    // Toi da so anh + video dinh kem moi danh gia
    public static final int MAX_MEDIA_PER_REVIEW = 5;

    public ReviewService(ReviewRepository reviewRepository, BookingService bookingService,
                         FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
        this.reviewRepository = reviewRepository;
        this.bookingService = bookingService;
    }

    public Optional<Review> findByBookingId(Long bookingId) {
        return reviewRepository.findByBookingId(bookingId);
    }

    public Map<Long, Review> findByBookings(Collection<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = bookings.stream().map(Booking::getId).toList();
        return reviewRepository.findByBookingIdIn(ids).stream()
                .collect(Collectors.toMap(r -> r.getBooking().getId(), Function.identity()));
    }

    // Chi chu don, don da tra phong (CHECKED_OUT) va chua danh gia moi duoc viet danh gia
    public boolean canReview(Booking booking, Long customerId) {
        return customerId != null
                && booking.getCustomer().getId().equals(customerId)
                && booking.getStatus() == BookingStatus.CHECKED_OUT
                && !reviewRepository.existsByBookingId(booking.getId());
    }

    @Transactional
    public Review createReview(Long bookingId, Long customerId, Integer rating, String comment) {
        return createReview(bookingId, customerId, rating, comment, List.of());
    }

    // Danh gia kem anh / video (tong cong toi da MAX_MEDIA_PER_REVIEW tep). Kiem tra het dieu kien truoc roi moi
    // luu tep; luu loi giua chung thi xoa cac tep da luu de khong de rac tren o dia
    @Transactional
    public Review createReview(Long bookingId, Long customerId, Integer rating, String comment,
                               List<MultipartFile> files) {
        List<MultipartFile> uploads = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (uploads.size() > MAX_MEDIA_PER_REVIEW) {
            throw BusinessException.of("err.mediaTooMany", MAX_MEDIA_PER_REVIEW);
        }
        Booking booking = bookingService.findByIdForCustomer(bookingId, customerId);
        if (booking.getStatus() != BookingStatus.CHECKED_OUT) {
            throw BusinessException.of("err.reviewNotCheckedOut");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw BusinessException.of("err.reviewDuplicate");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw BusinessException.of("err.reviewRating");
        }
        String text = comment == null ? null : comment.trim();
        if (text != null && text.length() > MAX_COMMENT_LENGTH) {
            throw BusinessException.of("err.reviewTooLong", MAX_COMMENT_LENGTH);
        }
        Review review = Review.builder()
                .booking(booking)
                .customer(booking.getCustomer())
                .rating(rating)
                .comment(text == null || text.isEmpty() ? null : text)
                .build();
        List<String> saved = new ArrayList<>();
        try {
            for (int i = 0; i < uploads.size(); i++) {
                FileStorageService.StoredMedia stored = fileStorageService.storeReviewMedia(uploads.get(i));
                saved.add(stored.url());
                review.getMedia().add(ReviewMedia.builder().review(review).url(stored.url())
                        .mediaType(stored.type()).sortOrder(i).build());
            }
        } catch (RuntimeException ex) {
            saved.forEach(fileStorageService::delete);
            throw ex;
        }
        return reviewRepository.save(review);
    }

    // Danh gia hien thi o trang Gioi thieu (bo qua danh gia admin da an)
    public List<Review> findLatestPublicReviews() {
        return reviewRepository.findVisibleWithComment(PageRequest.of(0, 12));
    }

    // Danh gia moi nhat cua loai phong, hien o trang chi tiet phong
    public List<Review> findLatestForRoomType(Long roomTypeId, int limit) {
        return reviewRepository.findVisibleByRoomType(roomTypeId, PageRequest.of(0, limit));
    }

    // Diem trung binh (lam tron 1 chu so) va so luot danh gia dang hien cua loai phong
    public RatingStats statsForRoomType(Long roomTypeId) {
        List<Object[]> rows = reviewRepository.statsByRoomType(roomTypeId);
        if (rows.isEmpty() || rows.get(0)[0] == null) {
            return new RatingStats(0, 0);
        }
        double avg = ((Number) rows.get(0)[0]).doubleValue();
        long count = ((Number) rows.get(0)[1]).longValue();
        return new RatingStats(Math.round(avg * 10) / 10.0, count);
    }

    public record RatingStats(double average, long count) {
        // So sao day du de ve (VD 4.3 -> 4 sao dac + 1 sao rong)
        public int fullStars() {
            return (int) Math.round(average);
        }
    }

    // ===== Quan ly cua admin =====

    // Loc danh sach cho admin: theo so sao, trang thai an/hien, tu khoa (ten khach, email, noi dung, ma don)
    public List<Review> findAllForAdmin(Integer rating, Boolean hidden, String keyword) {
        String q = keyword == null ? "" : keyword.trim().toLowerCase();
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> rating == null || r.getRating().equals(rating))
                .filter(r -> hidden == null || r.isHidden() == hidden)
                .filter(r -> q.isEmpty()
                        || contains(r.getCustomer().getFullName(), q)
                        || contains(r.getCustomer().getEmail(), q)
                        || contains(r.getComment(), q)
                        || ("#" + r.getBooking().getId()).equals(q) || String.valueOf(r.getBooking().getId()).equals(q))
                .toList();
    }

    @Transactional
    public Review toggleHidden(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đánh giá"));
        review.setHidden(!review.isHidden());
        return reviewRepository.save(review);
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }
}
