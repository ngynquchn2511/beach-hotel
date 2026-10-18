package com.hotel.service;

import com.hotel.entity.SpaPackage;
import com.hotel.exception.BusinessException;
import com.hotel.repository.SpaPackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// Quan ly goi spa (ADMIN): them, sua ten / thoi luong / gia, an hoac mo lai goi
@Service
public class SpaPackageService {

    public static final int MIN_MINUTES = 15;
    public static final int MAX_MINUTES = 240;
    public static final BigDecimal MAX_PRICE = new BigDecimal("20000000");

    private final SpaPackageRepository repository;

    public SpaPackageService(SpaPackageRepository repository) {
        this.repository = repository;
    }

    // 4 goi mac dinh khi bang con trong (truoc day co dinh trong code)
    @Transactional
    public void ensureDefaults() {
        if (repository.count() > 0) {
            return;
        }
        save(null, "Massage chân thảo dược", "Herbal foot massage", 60, BigDecimal.valueOf(350_000));
        save(null, "Massage body tinh dầu", "Aromatherapy body massage", 90, BigDecimal.valueOf(550_000));
        save(null, "Massage đá nóng", "Hot stone massage", 90, BigDecimal.valueOf(750_000));
        save(null, "Chăm sóc da mặt", "Facial care", 60, BigDecimal.valueOf(450_000));
    }

    public List<SpaPackage> active() {
        return repository.findByActiveTrueOrderByPriceAscNameAsc();
    }

    public List<SpaPackage> all() {
        return repository.findAllByOrderByActiveDescPriceAscNameAsc();
    }

    public SpaPackage find(Long id) {
        if (id == null) {
            throw BusinessException.of("err.svc.required");
        }
        return repository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy gói spa"));
    }

    @Transactional
    public SpaPackage create(String name, String nameEn, Integer minutes, BigDecimal price) {
        return save(null, name, nameEn, minutes, price);
    }

    @Transactional
    public SpaPackage update(Long id, String name, String nameEn, Integer minutes, BigDecimal price) {
        return save(find(id), name, nameEn, minutes, price);
    }

    @Transactional
    public SpaPackage toggle(Long id) {
        SpaPackage p = find(id);
        p.setActive(!p.isOpen());
        return repository.save(p);
    }

    private SpaPackage save(SpaPackage target, String name, String nameEn, Integer minutes, BigDecimal price) {
        String text = name == null ? "" : name.trim();
        if (text.isEmpty() || text.length() > 100) {
            throw new BusinessException("Vui lòng nhập tên gói (tối đa 100 ký tự)");
        }
        String en = nameEn == null || nameEn.isBlank() ? null : nameEn.trim();
        if (en != null && en.length() > 100) {
            throw new BusinessException("Tên tiếng Anh tối đa 100 ký tự");
        }
        if (minutes == null || minutes < MIN_MINUTES || minutes > MAX_MINUTES || minutes % 15 != 0) {
            throw new BusinessException("Thời lượng từ " + MIN_MINUTES + " đến " + MAX_MINUTES + " phút, bội số của 15");
        }
        if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new BusinessException("Giá phải lớn hơn 0 và không quá 20.000.000 VND");
        }
        SpaPackage p = target != null ? target : SpaPackage.builder().active(true).build();
        p.setName(text);
        p.setNameEn(en);
        p.setMinutes(minutes);
        p.setPrice(price.setScale(0, RoundingMode.HALF_UP));
        return repository.save(p);
    }
}
