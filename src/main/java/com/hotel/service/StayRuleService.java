package com.hotel.service;

import com.hotel.entity.PricingSettings;
import com.hotel.entity.RoomType;
import com.hotel.entity.StayRule;
import com.hotel.exception.BusinessException;
import com.hotel.repository.PricingSettingsRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.repository.StayRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

// Luat ban phong: so dem toi thieu theo dip, dong ban theo ngay / loai phong, gioi han dat truoc toi da N ngay
@Service
public class StayRuleService {

    public static final int MAX_MIN_NIGHTS = 30;
    // Tran cung: chi nhan dat truoc toi da 6 thang (moi kenh: khach tu dat, dat doan, doi ngay, dat tai quay).
    // Admin co the dat ngan hon (so ngay) o trang Gia; khong dat dai hon duoc
    public static final int MAX_ADVANCE_MONTHS = 6;
    public static final int MAX_ADVANCE_DAYS = 184;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StayRuleRepository repository;
    private final RoomTypeRepository roomTypeRepository;
    private final PricingService pricingService;
    private final PricingSettingsRepository settingsRepository;

    public StayRuleService(StayRuleRepository repository, RoomTypeRepository roomTypeRepository, PricingService pricingService,
                           PricingSettingsRepository settingsRepository) {
        this.repository = repository;
        this.roomTypeRepository = roomTypeRepository;
        this.pricingService = pricingService;
        this.settingsRepository = settingsRepository;
    }

    // Loi dau tien gap phai (null = dat duoc). Dung de chan dat va de an / bao truoc o trang tim phong
    public Optional<BusinessException> violation(RoomType type, LocalDate checkIn, LocalDate checkOut, LocalDate today) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return Optional.empty();
        }
        Optional<BusinessException> tooFar = advanceViolation(checkIn, today);
        if (tooFar.isPresent()) {
            return tooFar;
        }
        LocalDate lastNight = checkOut.minusDays(1);
        List<StayRule> rules = repository.findOverlapping(checkIn, lastNight).stream().filter(r -> r.appliesTo(type)).toList();
        for (StayRule r : rules) {
            if (r.isClosed()) {
                LocalDate from = checkIn.isAfter(r.getStartDate()) ? checkIn : r.getStartDate();
                return Optional.of(BusinessException.rendered(t -> t.t("err.rule.closed", r.getLocalizedName(),
                        from.format(DAY), r.getEndDate().format(DAY))));
            }
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        Optional<StayRule> strictest = rules.stream()
                .filter(r -> r.getMinNights() != null && r.covers(checkIn))
                .max(java.util.Comparator.comparing(StayRule::getMinNights));
        if (strictest.isPresent() && nights < strictest.get().getMinNights()) {
            StayRule r = strictest.get();
            return Optional.of(BusinessException.rendered(t -> t.t("err.rule.minNights", r.getLocalizedName(), r.getMinNights())));
        }
        return Optional.empty();
    }

    // Ngay nhan phong muon nhat con nhan dat: min(hom nay + 6 thang, hom nay + so ngay admin dat)
    public LocalDate latestCheckIn(LocalDate today) {
        LocalDate cap = today.plusMonths(MAX_ADVANCE_MONTHS);
        int advance = maxAdvanceDays();
        return advance > 0 && today.plusDays(advance).isBefore(cap) ? today.plusDays(advance) : cap;
    }

    public LocalDate latestCheckIn() {
        return latestCheckIn(LocalDate.now());
    }

    private Optional<BusinessException> advanceViolation(LocalDate checkIn, LocalDate today) {
        LocalDate latest = latestCheckIn(today);
        if (checkIn == null || !checkIn.isAfter(latest)) {
            return Optional.empty();
        }
        if (latest.equals(today.plusMonths(MAX_ADVANCE_MONTHS))) {
            return Optional.of(BusinessException.of("err.rule.advanceMonths", MAX_ADVANCE_MONTHS, latest.format(DAY)));
        }
        return Optional.of(BusinessException.of("err.rule.advance", maxAdvanceDays(), latest.format(DAY)));
    }

    // Chi kiem gioi han dat truoc (dat tai quay khong ap so dem toi thieu / dong ban)
    public void checkAdvance(LocalDate checkIn) {
        Optional<BusinessException> v = advanceViolation(checkIn, LocalDate.now());
        if (v.isPresent()) {
            throw v.get();
        }
    }

    public void check(RoomType type, LocalDate checkIn, LocalDate checkOut) {
        Optional<BusinessException> v = violation(type, checkIn, checkOut, LocalDate.now());
        if (v.isPresent()) {
            throw v.get();
        }
    }

    // ===== Quan tri (ADMIN, trang /admin/pricing) =====

    public List<StayRule> all() {
        return repository.findAllByOrderByStartDateDesc();
    }

    @Transactional
    public StayRule create(String name, String nameEn, LocalDate startDate, LocalDate endDate, Long roomTypeId,
                           Integer minNights, boolean stopSell) {
        String text = name == null ? "" : name.trim();
        if (text.isEmpty() || text.length() > 100) {
            throw new BusinessException("Vui lòng nhập tên dịp (tối đa 100 ký tự)");
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BusinessException("Ngày kết thúc phải từ ngày bắt đầu trở đi");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 366) {
            throw new BusinessException("Mỗi luật áp dụng tối đa 1 năm");
        }
        if (minNights != null && (minNights < 2 || minNights > MAX_MIN_NIGHTS)) {
            throw new BusinessException("Số đêm tối thiểu từ 2 đến " + MAX_MIN_NIGHTS);
        }
        if (minNights == null && !stopSell) {
            throw new BusinessException("Chọn số đêm tối thiểu hoặc đóng bán");
        }
        RoomType type = roomTypeId == null ? null
                : roomTypeRepository.findById(roomTypeId).orElseThrow(() -> new BusinessException("Không tìm thấy loại phòng"));
        String en = nameEn == null || nameEn.isBlank() ? null : nameEn.trim();
        return repository.save(StayRule.builder().name(text).nameEn(en != null && en.length() > 100 ? en.substring(0, 100) : en)
                .startDate(startDate).endDate(endDate).roomType(type).minNights(stopSell ? null : minNights).stopSell(stopSell).build());
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(repository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy luật")));
    }

    public int maxAdvanceDays() {
        Integer v = pricingService.getSettings().getMaxAdvanceDays();
        return v == null ? 0 : v;
    }

    // 0 / null = khong gioi han
    @Transactional
    public void updateMaxAdvanceDays(Integer days) {
        if (days != null && (days < 0 || days > MAX_ADVANCE_DAYS)) {
            throw new BusinessException("Số ngày đặt trước từ 0 (theo mặc định 6 tháng) đến " + MAX_ADVANCE_DAYS
                    + " (resort chỉ nhận đặt trước tối đa " + MAX_ADVANCE_MONTHS + " tháng)");
        }
        PricingSettings s = pricingService.getSettings();
        s.setMaxAdvanceDays(days == null || days == 0 ? null : days);
        settingsRepository.save(s);
    }
}
