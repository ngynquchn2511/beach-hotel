package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.CustomerType;
import com.hotel.entity.PointTransaction;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.repository.PointTransactionRepository;
import com.hotel.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// Diem thuong thanh vien:
// - Tich: tra phong xong duoc 1 diem / 10.000d tong don, nhan he so theo hang (khach quen x1.2, VIP x1.5)
// - Dung: luc dat phong (da dang nhap), 1 diem = 1.000d, toi thieu 50 diem, tru toi da 50% tien don
// - Don da dung diem bi huy -> tra lai diem
@Service
public class LoyaltyService {

    public static final int VND_PER_EARNED_POINT = 10_000;
    public static final int VND_PER_REDEEMED_POINT = 1_000;
    public static final int MIN_REDEEM_POINTS = 50;
    public static final int MAX_REDEEM_PERCENT = 50;

    private final UserRepository userRepository;
    private final PointTransactionRepository transactionRepository;

    public LoyaltyService(UserRepository userRepository, PointTransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    public static double earnMultiplier(CustomerType tier) {
        if (tier == null) {
            return 1.0;
        }
        return switch (tier) {
            case NEW -> 1.0;
            case REGULAR -> 1.2;
            case VIP -> 1.5;
        };
    }

    public static int pointsFor(BigDecimal amount, CustomerType tier) {
        if (amount == null || amount.signum() <= 0) {
            return 0;
        }
        // Moi tron 10.000d duoc 1 diem goc, roi nhan he so hang (lam tron xuong)
        long base = amount.divide(BigDecimal.valueOf(VND_PER_EARNED_POINT), 0, RoundingMode.DOWN).longValue();
        return (int) Math.floor(base * earnMultiplier(tier));
    }

    // So diem toi da dung duoc cho don co tien (sau ma giam gia) = amount; 0 = chua du diem toi thieu
    public static int maxRedeemable(User user, BigDecimal amount) {
        if (user == null || amount == null || user.getPointsBalance() < MIN_REDEEM_POINTS) {
            return 0;
        }
        int cap = amount.multiply(BigDecimal.valueOf(MAX_REDEEM_PERCENT))
                .divide(BigDecimal.valueOf(100L * VND_PER_REDEEMED_POINT), 0, RoundingMode.DOWN).intValue();
        int max = Math.min(user.getPointsBalance(), cap);
        return max >= MIN_REDEEM_POINTS ? max : 0;
    }

    // So du moi nhat trong DB (doi tuong trong phien dang nhap co the da cu)
    public int currentBalance(User user) {
        return userRepository.findById(user.getId()).map(User::getPointsBalance).orElse(0);
    }

    // Kiem tra so diem khach muon dung -> so tien duoc tru (chua tru diem, goi recordRedeem sau khi luu don)
    public BigDecimal redeemDiscount(User user, int points, BigDecimal amount) {
        if (points <= 0) {
            return BigDecimal.ZERO;
        }
        // Doc so du moi nhat (doi tuong khach trong phien dang nhap co the da cu)
        user = userRepository.findById(user.getId()).orElse(user);
        if (points < MIN_REDEEM_POINTS) {
            throw BusinessException.of("points.error.min", MIN_REDEEM_POINTS);
        }
        if (points > user.getPointsBalance()) {
            throw BusinessException.of("points.error.balance", user.getPointsBalance());
        }
        int max = maxRedeemable(user, amount);
        if (points > max) {
            throw BusinessException.of("points.error.cap", MAX_REDEEM_PERCENT, max);
        }
        return BigDecimal.valueOf((long) points * VND_PER_REDEEMED_POINT);
    }

    // Don vua luu co dung diem -> tru diem khach + ghi lich su
    @Transactional
    public void recordRedeem(Booking booking) {
        if (booking.getPointsUsed() == null || booking.getPointsUsed() <= 0) {
            return;
        }
        change(booking.getCustomer(), booking, PointTransaction.Type.REDEEM, -booking.getPointsUsed());
    }

    // Tra phong xong -> cong diem theo tong don va hang hien tai cua khach (truoc khi xet len hang)
    @Transactional
    public int earn(Booking booking) {
        User customer = booking.getCustomer();
        if (customer == null || booking.getPointsEarned() != null || isWalkInPlaceholder(customer)) {
            return 0;
        }
        int points = pointsFor(booking.getTotalAmount(), customer.getCustomerType());
        booking.setPointsEarned(points);
        if (points > 0) {
            change(customer, booking, PointTransaction.Type.EARN, points);
        }
        return points;
    }

    // Don da dung diem bi huy -> tra lai diem (goi 1 lan khi don chuyen sang CANCELLED)
    @Transactional
    public void refundUsed(Booking booking) {
        if (booking.getPointsUsed() == null || booking.getPointsUsed() <= 0) {
            return;
        }
        change(booking.getCustomer(), booking, PointTransaction.Type.REFUND, booking.getPointsUsed());
    }

    @Transactional(readOnly = true)
    public List<PointTransaction> fullHistory(Long userId) {
        return transactionRepository.findRecent(userId, org.springframework.data.domain.Pageable.unpaged());
    }

    public List<PointTransaction> history(Long userId, int limit) {
        return transactionRepository.findRecent(userId, PageRequest.of(0, limit));
    }

    private void change(User user, Booking booking, PointTransaction.Type type, int delta) {
        // Luon sua tren ban moi nhat trong DB - khong luu doi tuong cu tu phien dang nhap (de mat thay doi khac)
        user = userRepository.findById(user.getId()).orElse(user);
        int balance = Math.max(0, user.getPointsBalance() + delta);
        user.setLoyaltyPoints(balance);
        userRepository.save(user);
        transactionRepository.save(PointTransaction.builder().user(user).booking(booking).type(type)
                .points(delta).balanceAfter(balance).build());
    }

    // ===== Thuong viet danh gia =====
    // Chi cham sao 5 diem; co nhan xet >= 20 ky tu 20 diem; moi anh +10 (toi da 3 anh); co video +30. Toi da 80 diem / danh gia
    public static final int REVIEW_STARS_POINTS = 5;
    public static final int REVIEW_TEXT_POINTS = 20;
    public static final int REVIEW_TEXT_MIN_CHARS = 20;
    public static final int REVIEW_PHOTO_POINTS = 10;
    public static final int REVIEW_MAX_PHOTOS = 3;
    public static final int REVIEW_VIDEO_POINTS = 30;

    public static int reviewPoints(String comment, long photos, long videos) {
        String text = comment == null ? "" : comment.trim();
        int points = text.codePointCount(0, text.length()) >= REVIEW_TEXT_MIN_CHARS ? REVIEW_TEXT_POINTS : REVIEW_STARS_POINTS;
        points += (int) Math.min(photos, REVIEW_MAX_PHOTOS) * REVIEW_PHOTO_POINTS;
        if (videos > 0) {
            points += REVIEW_VIDEO_POINTS;
        }
        return points;
    }

    public static int reviewPoints(com.hotel.entity.Review review) {
        long photos = review.getMedia().stream().filter(m -> !m.isVideo()).count();
        long videos = review.getMedia().stream().filter(com.hotel.entity.ReviewMedia::isVideo).count();
        return reviewPoints(review.getComment(), photos, videos);
    }

    // Cong diem cho danh gia vua viet (hoac vua duoc hien lai). Tra ve so diem da cong (0 = khong cong)
    @Transactional
    public int rewardReview(com.hotel.entity.Review review) {
        User customer = review.getCustomer();
        if (customer == null || isWalkInPlaceholder(customer) || review.isHidden()
                || (review.getRewardPoints() != null && review.getRewardPoints() > 0)) {
            return 0;
        }
        int points = reviewPoints(review);
        review.setRewardPoints(points);
        change(customer, review.getBooking(), PointTransaction.Type.REVIEW, points);
        return points;
    }

    // Danh gia bi an -> thu hoi diem da cong (so du khong am)
    @Transactional
    public int revokeReview(com.hotel.entity.Review review) {
        Integer points = review.getRewardPoints();
        if (points == null || points <= 0 || review.getCustomer() == null) {
            return 0;
        }
        review.setRewardPoints(0);
        change(review.getCustomer(), review.getBooking(), PointTransaction.Type.REVOKE, -points);
        return points;
    }

    // Tai khoan tam le tan tao cho khach tai quay (email @khachvanglai.local) - khong ai dang nhap de dung diem
    private static boolean isWalkInPlaceholder(User user) {
        return user.getEmail() != null && user.getEmail().endsWith("@khachvanglai.local");
    }
}
