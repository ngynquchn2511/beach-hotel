package com.hotel.config.demo;

import com.hotel.entity.MediaType;
import com.hotel.entity.Review;
import com.hotel.entity.ReviewMedia;
import com.hotel.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Anh demo cho danh gia (lay anh san co trong /page-images: bai bien + phong) de trang "Khach noi gi"
// va trinh xem anh co noi dung. Khoang 1/4 danh gia 4-5 sao trong 120 ngay gan day co 1-4 anh.
// Chi chay khi chua co anh / video danh gia nao. Video demo khong co (can khach tai len that)
@Component
@Order(29)
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class ReviewMediaDemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewMediaDemoSeeder.class);

    private static final String[] BEACH = {"beach-bo-bien", "beach-cho-dam", "beach-hoang-hon", "beach-hon-chong", "beach-hon-mun",
            "beach-thap-ba", "beach-toan-canh", "beach-tour-dao", "beach-tran-phu", "donuong"};
    private static final String[] ROOMS = {"bungalow", "deluxe", "family", "family-garden", "standard"};

    private final ReviewRepository reviews;

    public ReviewMediaDemoSeeder(ReviewRepository reviews) {
        this.reviews = reviews;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Review> all = reviews.findAllByOrderByCreatedAtDesc();
        if (all.stream().anyMatch(r -> !r.getMedia().isEmpty())) {
            return;
        }
        java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(120);
        Random rnd = new Random(20261128L);
        List<Review> changed = new ArrayList<>();
        int photos = 0;
        for (Review r : all) {
            if (r.isHidden() || r.getRating() < 4 || r.getCreatedAt() == null || r.getCreatedAt().isBefore(since) || rnd.nextInt(4) != 0) {
                continue;
            }
            int n = 1 + rnd.nextInt(4);
            for (int i = 0; i < n; i++) {
                String url = rnd.nextInt(3) == 0
                        ? "/page-images/rooms/" + ROOMS[rnd.nextInt(ROOMS.length)] + "-" + (1 + rnd.nextInt(3)) + ".jpg"
                        : "/page-images/" + BEACH[rnd.nextInt(BEACH.length)] + ".jpg";
                r.getMedia().add(ReviewMedia.builder().review(r).url(url).mediaType(MediaType.IMAGE).sortOrder(i).build());
                photos++;
            }
            changed.add(r);
        }
        reviews.saveAll(changed);
        log.info("Du lieu demo anh danh gia: {} anh cho {} danh gia", photos, changed.size());
    }
}
