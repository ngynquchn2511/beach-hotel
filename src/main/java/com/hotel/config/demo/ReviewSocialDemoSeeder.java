package com.hotel.config.demo;

import com.hotel.entity.Review;
import com.hotel.entity.ReviewComment;
import com.hotel.entity.ReviewLike;
import com.hotel.entity.ReviewShare;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.ReviewCommentRepository;
import com.hotel.repository.ReviewLikeRepository;
import com.hotel.repository.ReviewRepository;
import com.hotel.repository.ReviewShareRepository;
import com.hotel.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// Tim / binh luan / chia se demo cho cac danh gia gan day (danh gia co anh / video duoc tuong tac nhieu hon).
// Chi chay khi chua co luot tha tim nao
@Component
@Order(31)
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class ReviewSocialDemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewSocialDemoSeeder.class);

    private static final String[] COMMENTS = {
            "Ảnh đẹp quá, tháng sau nhà mình cũng đi!", "Phòng này view biển thật hả bạn?", "Cảm ơn review chi tiết ạ",
            "Video hoàng hôn xịn thật", "Bạn đặt phòng giá bao nhiêu vậy?", "Đi mùa này có lạnh không bạn?",
            "Nhìn bãi biển mê quá", "Lưu lại để đi trăng mật", "Bữa sáng có ngon không bạn?", "Mình cũng ở đây rồi, công nhận đẹp"};

    private final ReviewRepository reviews;
    private final ReviewLikeRepository likes;
    private final ReviewCommentRepository comments;
    private final ReviewShareRepository shares;
    private final UserRepository users;

    public ReviewSocialDemoSeeder(ReviewRepository reviews, ReviewLikeRepository likes, ReviewCommentRepository comments,
                                  ReviewShareRepository shares, UserRepository users) {
        this.reviews = reviews;
        this.likes = likes;
        this.comments = comments;
        this.shares = shares;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (likes.count() > 0) {
            return;
        }
        List<User> people = users.findAll().stream().filter(u -> u.getRole() == UserRole.CUSTOMER).limit(400).toList();
        if (people.size() < 10) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Review> recent = reviews.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> !r.isHidden() && r.getCreatedAt() != null && r.getCreatedAt().isAfter(now.minusDays(90)))
                .limit(300).toList();
        Random rnd = new Random(20261127L);
        List<ReviewLike> ls = new ArrayList<>();
        List<ReviewComment> cs = new ArrayList<>();
        List<ReviewShare> ss = new ArrayList<>();
        String[] channels = {"link", "facebook", "native", "link"};
        for (Review r : recent) {
            boolean media = !r.getMedia().isEmpty();
            int nLikes = rnd.nextInt(media ? 14 : 5) + (r.getRating() >= 5 ? 1 : 0);
            Set<Long> who = new HashSet<>();
            for (int i = 0; i < nLikes; i++) {
                User u = people.get(rnd.nextInt(people.size()));
                if (!who.add(u.getId()) || u.getId().equals(r.getCustomer().getId())) {
                    continue;
                }
                ls.add(ReviewLike.builder().review(r).user(u).createdAt(after(r, now, rnd)).build());
            }
            int nComments = rnd.nextInt(media ? 4 : 2);
            for (int i = 0; i < nComments; i++) {
                cs.add(ReviewComment.builder().review(r).user(people.get(rnd.nextInt(people.size())))
                        .content(COMMENTS[rnd.nextInt(COMMENTS.length)]).createdAt(after(r, now, rnd)).build());
            }
            int nShares = media ? rnd.nextInt(4) : (rnd.nextInt(4) == 0 ? 1 : 0);
            for (int i = 0; i < nShares; i++) {
                ss.add(ReviewShare.builder().review(r).channel(channels[rnd.nextInt(channels.length)]).createdAt(after(r, now, rnd)).build());
            }
        }
        likes.saveAll(ls);
        comments.saveAll(cs);
        shares.saveAll(ss);
        log.info("Du lieu demo tuong tac danh gia: {} tim, {} binh luan, {} chia se", ls.size(), cs.size(), ss.size());
    }

    // Thoi diem sau khi danh gia dang, truoc hien tai
    private static LocalDateTime after(Review r, LocalDateTime now, Random rnd) {
        long span = java.time.Duration.between(r.getCreatedAt(), now).toMinutes();
        return r.getCreatedAt().plusMinutes(span <= 1 ? 0 : (long) (rnd.nextDouble() * rnd.nextDouble() * span));
    }
}
