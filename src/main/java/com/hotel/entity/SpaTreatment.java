package com.hotel.entity;

import java.math.BigDecimal;

// Goi spa ban kem don phong (gia / nguoi)
public enum SpaTreatment {
    FOOT_MASSAGE("Massage chân thảo dược", "Herbal foot massage", 60, 350_000),
    BODY_MASSAGE("Massage body tinh dầu", "Aromatherapy body massage", 90, 550_000),
    HOT_STONE("Massage đá nóng", "Hot stone massage", 90, 750_000),
    FACIAL("Chăm sóc da mặt", "Facial care", 60, 450_000);

    private final String name;
    private final String nameEn;
    private final int minutes;
    private final BigDecimal price;

    SpaTreatment(String name, String nameEn, int minutes, long price) {
        this.name = name;
        this.nameEn = nameEn;
        this.minutes = minutes;
        this.price = BigDecimal.valueOf(price);
    }

    public String getName() {
        return name;
    }

    public String getLocalizedName() {
        return com.hotel.util.Texts.pick(name, nameEn);
    }

    public int getMinutes() {
        return minutes;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
