package com.hotel.service;

import com.hotel.entity.Review;
import com.hotel.entity.ReviewComment;
import com.hotel.entity.ReviewLike;
import com.hotel.entity.ReviewShare;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ReviewCommentRepository;
import com.hotel.repository.ReviewLikeRepository;
import com.hotel.repository.ReviewRepository;
import com.hotel.repository.ReviewShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Tuong tac voi danh gia cua khach: tha tim, binh luan, chia se (dem luot) + thong ke cho quan tri
@Service
public class ReviewSocialService {

    public static final int COMMENT_MAX = 500;
    // Chong spam: toi da 10 binh luan / nguoi / gio
    static final int COMMENTS_PER_HOUR = 10;
    private static final Set<String> CHANNELS = Set.of("link", "facebook", "zalo", "native");

    // Tuong tac cua 1 danh gia voi nguoi dang xem
    public record Stats(long likes, long comments, boolean liked) {
    }

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository likeRepository;
    private final ReviewCommentRepository commentRepository;
    private final ReviewShareRepository shareRepository;

    public ReviewSocialService(ReviewRepository reviewRepository, ReviewLikeRepository likeRepository,
                               ReviewCommentRepository commentRepository, ReviewShareRepository shareRepository) {
        this.reviewRepository = reviewRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.shareRepository = shareRepository;
    }

    // Danh gia dang hien (an thi coi nhu khong ton tai voi khach)
    public Review visibleReview(Long id) {
        Review r = reviewRepository.findById(id).orElseThrow(() -> BusinessException.of("err.reviewNotFound"));
        if (r.isHidden()) {
            throw BusinessException.of("err.reviewNotFound");
        }
        return r;
    }

    @Transactional
    public Stats toggleLike(Long reviewId, User user) {
        Review r = visibleReview(reviewId);
        var existing = likeRepository.findByReviewIdAndUserId(reviewId, user.getId());
        boolean liked;
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            liked = false;
        } else {
            likeRepository.save(ReviewLike.builder().review(r).user(user).createdAt(LocalDateTime.now()).build());
            liked = true;
        }
        likeRepository.flush();
        return new Stats(likeRepository.countByReviewId(reviewId), countComments(List.of(reviewId)).getOrDefault(reviewId, 0L), liked);
    }

    @Transactional
    public ReviewComment comment(Long reviewId, User user, String content) {
        Review r = visibleReview(reviewId);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw BusinessException.of("err.commentEmpty");
        }
        if (text.length() > COMMENT_MAX) {
            throw BusinessException.of("err.commentTooLong", COMMENT_MAX);
        }
        if (commentRepository.countByUserIdAndCreatedAtAfter(user.getId(), LocalDateTime.now().minusHours(1)) >= COMMENTS_PER_HOUR) {
            throw BusinessException.of("err.commentTooMany");
        }
        return commentRepository.save(ReviewComment.builder().review(r).user(user).content(text)
                .createdAt(LocalDateTime.now()).build());
    }

    // Nguoi viet tu xoa binh luan cua minh
    @Transactional
    public Long deleteOwnComment(Long commentId, User user) {
        ReviewComment c = commentRepository.findById(commentId).orElseThrow(() -> BusinessException.of("err.commentNotFound"));
        if (user == null || c.getUser() == null || !c.getUser().getId().equals(user.getId())) {
            throw BusinessException.of("err.commentNotFound");
        }
        Long reviewId = c.getReview().getId();
        commentRepository.delete(c);
        return reviewId;
    }

    // Quan tri an / hien binh luan
    @Transactional
    public ReviewComment toggleCommentHidden(Long commentId) {
        ReviewComment c = commentRepository.findById(commentId).orElseThrow(() -> BusinessException.of("err.commentNotFound"));
        c.setHidden(!c.isHidden());
        return commentRepository.save(c);
    }

    @Transactional
    public void share(Long reviewId, String channel) {
        Review r = visibleReview(reviewId);
        shareRepository.save(ReviewShare.builder().review(r).channel(channel != null && CHANNELS.contains(channel) ? channel : "link")
                .createdAt(LocalDateTime.now()).build());
    }

    public List<ReviewComment> visibleComments(Long reviewId) {
        return commentRepository.findByReviewIdOrderByCreatedAtAsc(reviewId).stream().filter(c -> !c.isHidden()).toList();
    }

    // Moi binh luan (ca da an) cua cac danh gia - cho trang quan tri
    public Map<Long, List<ReviewComment>> allComments(Collection<Long> reviewIds) {
        Map<Long, List<ReviewComment>> out = new HashMap<>();
        if (reviewIds.isEmpty()) {
            return out;
        }
        commentRepository.findByReviewIdInOrderByCreatedAtAsc(reviewIds)
                .forEach(c -> out.computeIfAbsent(c.getReview().getId(), k -> new ArrayList<>()).add(c));
        return out;
    }

    // Tuong tac cua nhieu danh gia 1 lan (tranh N+1) - user null = khach chua dang nhap
    public Map<Long, Stats> stats(Collection<Review> reviews, User viewer) {
        List<Long> ids = reviews.stream().map(Review::getId).distinct().toList();
        Map<Long, Stats> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Map<Long, Long> likes = toMap(likeRepository.countByReviewIds(ids));
        Map<Long, Long> comments = countComments(ids);
        Set<Long> liked = viewer == null ? Set.of() : new HashSet<>(likeRepository.likedAmong(viewer.getId(), ids));
        for (Long id : ids) {
            out.put(id, new Stats(likes.getOrDefault(id, 0L), comments.getOrDefault(id, 0L), liked.contains(id)));
        }
        return out;
    }

    public Stats stats(Review review, User viewer) {
        return stats(List.of(review), viewer).get(review.getId());
    }

    private Map<Long, Long> countComments(List<Long> ids) {
        return toMap(commentRepository.countVisibleByReviewIds(ids));
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        return m;
    }

    // ===== Thong ke cho quan tri =====

    public record Engagement(int days, long likes, long comments, long shares, List<String> labels,
                             List<Long> dayLikes, List<Long> dayComments, List<Long> dayShares,
                             Map<String, Long> shareChannels, List<Top> top, List<ReviewComment> latestComments) {
    }

    public record Top(Review review, long likes, long comments, long shares) {
        public long total() {
            return likes + comments * 2 + shares * 3;
        }
    }

    @Transactional(readOnly = true)
    public Engagement engagement(int days, LocalDate today) {
        LocalDate from = today.minusDays(days - 1L);
        LocalDateTime start = from.atStartOfDay();
        Map<LocalDate, long[]> perDay = new HashMap<>();
        var likes = likeRepository.findByCreatedAtGreaterThanEqual(start);
        var comments = commentRepository.findByCreatedAtGreaterThanEqual(start);
        var shares = shareRepository.findByCreatedAtGreaterThanEqual(start);
        likes.forEach(l -> perDay.computeIfAbsent(l.getCreatedAt().toLocalDate(), k -> new long[3])[0]++);
        comments.forEach(c -> perDay.computeIfAbsent(c.getCreatedAt().toLocalDate(), k -> new long[3])[1]++);
        shares.forEach(s -> perDay.computeIfAbsent(s.getCreatedAt().toLocalDate(), k -> new long[3])[2]++);
        List<String> labels = new ArrayList<>();
        List<Long> dl = new ArrayList<>();
        List<Long> dc = new ArrayList<>();
        List<Long> ds = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            long[] a = perDay.getOrDefault(d, new long[3]);
            labels.add(d.format(fmt));
            dl.add(a[0]);
            dc.add(a[1]);
            ds.add(a[2]);
        }
        Map<String, Long> channels = new HashMap<>();
        shares.forEach(s -> channels.merge(s.getChannel() == null ? "link" : s.getChannel(), 1L, Long::sum));

        // Danh gia duoc tuong tac nhieu nhat (moi thoi gian)
        Map<Long, long[]> all = new HashMap<>();
        likeRepository.countAll().forEach(r -> all.computeIfAbsent(((Number) r[0]).longValue(), k -> new long[3])[0] = ((Number) r[1]).longValue());
        commentRepository.findAll().forEach(c -> all.computeIfAbsent(c.getReview().getId(), k -> new long[3])[1]++);
        shareRepository.countAll().forEach(r -> all.computeIfAbsent(((Number) r[0]).longValue(), k -> new long[3])[2] = ((Number) r[1]).longValue());
        List<Long> topIds = all.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Long, long[]> e) -> e.getValue()[0] + e.getValue()[1] * 2 + e.getValue()[2] * 3).reversed())
                .limit(5).map(Map.Entry::getKey).toList();
        Map<Long, Review> reviews = new HashMap<>();
        reviewRepository.findAllById(topIds).forEach(r -> reviews.put(r.getId(), r));
        List<Top> top = topIds.stream().filter(reviews::containsKey)
                .map(id -> new Top(reviews.get(id), all.get(id)[0], all.get(id)[1], all.get(id)[2])).toList();

        return new Engagement(days, likes.size(), comments.size(), shares.size(), labels, dl, dc, ds, channels, top,
                commentRepository.findTop20ByOrderByCreatedAtDesc());
    }
}
