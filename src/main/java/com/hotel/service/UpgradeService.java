package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.PricingSettings;
import com.hotel.entity.UpgradeOffer;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.PricingSettingsRepository;
import com.hotel.repository.UpgradeOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Ban nang hang phong: goi y toi da 2 loai phong cao hon con trong dung cac dem cua don, gia = chenh lech tien phong
// tru uu dai %. Khach dong y tren trang don -> doi phong (kiem tra lai phong trong) va cong tien vao don
@Service
public class UpgradeService {

    public static final int MAX_OFFERS = 2;
    public static final int MAX_DISCOUNT_PERCENT = 70;
    // Gui tu dong truoc ngay nhan phong bao nhieu ngay
    public static final int AUTO_DAYS_BEFORE = 2;

    private final UpgradeOfferRepository repository;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final PricingService pricingService;
    private final PricingSettingsRepository settingsRepository;
    private final EmailService emailService;
    private final StaffNotificationService notifier;

    public UpgradeService(UpgradeOfferRepository repository, BookingService bookingService, BookingRepository bookingRepository,
                          PricingService pricingService, PricingSettingsRepository settingsRepository, EmailService emailService,
                          StaffNotificationService notifier) {
        this.repository = repository;
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.pricingService = pricingService;
        this.settingsRepository = settingsRepository;
        this.emailService = emailService;
        this.notifier = notifier;
    }

    public record Candidate(BookingService.RoomMoveOption option, BigDecimal price) {
    }

    // Don con chua nhan phong (cho / da xac nhan), gia khong hoan tien van nang hang duoc
    public boolean canOffer(Booking booking) {
        return (booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.CONFIRMED)
                && LocalDateTime.of(booking.getCheckInDate(), booking.getCheckInTime()).isAfter(LocalDateTime.now());
    }

    // Loai phong gia cao hon loai hien tai, moi loai lay phong re nhat con trong; sap theo gia tang dan
    public List<Candidate> candidates(Booking booking) {
        if (!canOffer(booking)) {
            return List.of();
        }
        BigDecimal currentTypePrice = booking.getRoom().getRoomType().getBasePrice();
        Map<Long, BookingService.RoomMoveOption> bestPerType = new LinkedHashMap<>();
        for (BookingService.RoomMoveOption o : bookingService.roomMoveOptions(booking.getId())) {
            BigDecimal typePrice = o.room().getRoomType().getBasePrice();
            if (o.difference().signum() <= 0 || typePrice == null || currentTypePrice == null || typePrice.compareTo(currentTypePrice) <= 0) {
                continue;
            }
            bestPerType.merge(o.room().getRoomType().getId(), o,
                    (a, b) -> a.difference().compareTo(b.difference()) <= 0 ? a : b);
        }
        int percent = discountPercent();
        return bestPerType.values().stream()
                .sorted(Comparator.comparing(BookingService.RoomMoveOption::difference))
                .limit(MAX_OFFERS)
                .map(o -> new Candidate(o, price(o.difference(), percent)))
                .toList();
    }

    // Lam tron len hang nghin
    static BigDecimal price(BigDecimal difference, int percent) {
        BigDecimal p = difference.multiply(BigDecimal.valueOf(100L - percent)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return p.divide(BigDecimal.valueOf(1000), 0, RoundingMode.CEILING).multiply(BigDecimal.valueOf(1000));
    }

    public List<UpgradeOffer> offersOf(Long bookingId) {
        return repository.findByBooking(bookingId);
    }

    public List<UpgradeOffer> openOffersOf(Long bookingId) {
        LocalDateTime now = LocalDateTime.now();
        return offersOf(bookingId).stream().filter(o -> o.isOpen(now)).toList();
    }

    // Tao loi moi moi (huy loi moi cu con mo) va gui email cho khach
    @Transactional
    public List<UpgradeOffer> createOffers(Long bookingId, String createdBy) {
        Booking booking = bookingService.findById(bookingId);
        if (!canOffer(booking)) {
            throw new BusinessException("Chỉ mời nâng hạng cho đơn chưa nhận phòng");
        }
        List<Candidate> candidates = candidates(booking);
        if (candidates.isEmpty()) {
            throw new BusinessException("Không còn loại phòng cao hơn trống cho các đêm của đơn này");
        }
        for (UpgradeOffer old : repository.findByBooking(bookingId)) {
            if (old.getStatus() == UpgradeOffer.Status.OFFERED) {
                old.setStatus(UpgradeOffer.Status.EXPIRED);
                repository.save(old);
            }
        }
        LocalDateTime expires = LocalDateTime.of(booking.getCheckInDate(), booking.getCheckInTime());
        List<UpgradeOffer> offers = new ArrayList<>();
        for (Candidate c : candidates) {
            offers.add(repository.save(UpgradeOffer.builder().booking(booking).room(c.option().room())
                    .fullDifference(c.option().difference()).price(c.price()).status(UpgradeOffer.Status.OFFERED)
                    .expiresAt(expires).createdBy(createdBy).build()));
        }
        emailService.sendUpgradeOffer(booking, offers);
        return offers;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking accept(Booking booking, Long offerId) {
        UpgradeOffer offer = find(booking, offerId);
        if (!offer.isOpen(LocalDateTime.now())) {
            throw BusinessException.of("upgrade.error.closed");
        }
        if (!canOffer(booking)) {
            throw BusinessException.of("upgrade.error.closed");
        }
        String oldRoom = booking.getRoom().getRoomNumber();
        Booking moved;
        try {
            moved = bookingService.moveRoom(booking.getId(), offer.getRoom().getId(), false,
                    "Khách nhận nâng hạng lên " + offer.getRoom().getRoomType().getName());
        } catch (BusinessException ex) {
            offer.setStatus(UpgradeOffer.Status.EXPIRED);
            repository.save(offer);
            throw BusinessException.of("upgrade.error.taken");
        }
        // moveRoom da cong du chenh lech theo bang gia -> tru phan uu dai cua loi moi
        BigDecimal saving = offer.getSaving();
        if (saving.signum() > 0) {
            moved.setRoomAmount(moved.getEffectiveRoomAmount().subtract(saving));
            moved.setTotalAmount(moved.getTotalAmount().subtract(saving).max(BigDecimal.ZERO));
            moved = bookingRepository.save(moved);
        }
        LocalDateTime now = LocalDateTime.now();
        for (UpgradeOffer o : repository.findByBooking(booking.getId())) {
            if (o.getStatus() == UpgradeOffer.Status.OFFERED) {
                o.setStatus(o.getId().equals(offer.getId()) ? UpgradeOffer.Status.ACCEPTED : UpgradeOffer.Status.EXPIRED);
                o.setRespondedAt(now);
                repository.save(o);
            }
        }
        notifier.notify("upgrade", StaffNotificationService.Level.SUCCESS,
                "Khách nhận nâng hạng đơn #" + booking.getId(),
                "Phòng " + oldRoom + " → " + offer.getRoom().getRoomNumber() + " (" + offer.getRoom().getRoomType().getName()
                        + ") · +" + com.hotel.util.Money.format(offer.getPrice()) + " VND", "/admin/bookings/" + booking.getId());
        return moved;
    }

    @Transactional
    public void decline(Booking booking, Long offerId) {
        UpgradeOffer offer = find(booking, offerId);
        LocalDateTime now = LocalDateTime.now();
        for (UpgradeOffer o : repository.findByBooking(booking.getId())) {
            if (o.getStatus() == UpgradeOffer.Status.OFFERED) {
                o.setStatus(UpgradeOffer.Status.DECLINED);
                o.setRespondedAt(now);
                repository.save(o);
            }
        }
        if (offer.getStatus() != UpgradeOffer.Status.DECLINED) {
            throw BusinessException.of("upgrade.error.closed");
        }
    }

    // Moi ngay: don da xac nhan nhan phong sau AUTO_DAYS_BEFORE ngay, chua tung duoc moi -> gui loi moi
    @Transactional
    public int sendAutoOffers(LocalDate today) {
        if (!autoEnabled()) {
            return 0;
        }
        LocalDate arrival = today.plusDays(AUTO_DAYS_BEFORE);
        int sent = 0;
        for (Booking b : bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CONFIRMED)) {
            if (!b.getCheckInDate().equals(arrival) || repository.existsByBookingId(b.getId())) {
                continue;
            }
            if (candidates(b).isEmpty()) {
                continue;
            }
            createOffers(b.getId(), "Tự động");
            sent++;
        }
        return sent;
    }

    // ===== Cai dat + thong ke (ADMIN) =====

    public int discountPercent() {
        Integer p = pricingService.getSettings().getUpgradeDiscountPercent();
        return p == null ? 0 : p;
    }

    public boolean autoEnabled() {
        return Boolean.TRUE.equals(pricingService.getSettings().getUpgradeAutoOffer());
    }

    @Transactional
    public void updateSettings(Integer percent, boolean auto) {
        if (percent == null || percent < 0 || percent > MAX_DISCOUNT_PERCENT) {
            throw new BusinessException("Ưu đãi nâng hạng từ 0 đến " + MAX_DISCOUNT_PERCENT + "% phần chênh lệch");
        }
        PricingSettings s = pricingService.getSettings();
        s.setUpgradeDiscountPercent(percent == 0 ? null : percent);
        s.setUpgradeAutoOffer(auto ? true : null);
        settingsRepository.save(s);
    }

    public List<UpgradeOffer> all() {
        return repository.findAllForList();
    }

    private UpgradeOffer find(Booking booking, Long offerId) {
        UpgradeOffer offer = repository.findById(offerId).orElseThrow(() -> BusinessException.of("upgrade.error.closed"));
        if (!offer.getBooking().getId().equals(booking.getId())) {
            throw BusinessException.of("upgrade.error.closed");
        }
        return offer;
    }
}
