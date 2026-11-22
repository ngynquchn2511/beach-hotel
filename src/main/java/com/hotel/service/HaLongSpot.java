package com.hotel.service;

// Diem du lich quanh resort de goi y theo thoi tiet tung ngay (trang Goi y phong cua khach).
// Chu song ngu: spot.<TEN>.name / .desc / .how. mapsQuery = ten dia diem de mo Google Maps chi duong tu resort.
// from / to = khung gio hop di (gio dep nhat trong khung nay duoc goi y); tourKeywords = khop ten tour cua resort (khong dau)
public enum HaLongSpot {
    BAI_CHAY(Kind.BEACH, "bi-water", "/page-images/beach-tran-phu.jpg", "Bãi tắm Bãi Cháy, Hạ Long", 7, 17),
    BAY_CRUISE(Kind.SEA, "bi-tsunami", "/page-images/beach-hon-chong.jpg", "Cảng tàu khách quốc tế Tuần Châu, Hạ Long", 8, 14,
            "du thuyen", "cruise", "vinh"),
    TI_TOP(Kind.SEA, "bi-tree", "/page-images/beach-tour-dao.jpg", "Đảo Ti Tốp, Vịnh Hạ Long", 8, 15, "ti top"),
    SNORKEL(Kind.SEA, "bi-life-preserver", "/page-images/beach-hon-mun.jpg", "Cảng tàu khách quốc tế Tuần Châu, Hạ Long", 7, 14,
            "lan", "kayak", "snorkel", "scuba"),
    BAI_THO(Kind.OUTDOOR, "bi-signpost-split", "/page-images/beach-toan-canh.jpg", "Núi Bài Thơ, Hạ Long", 6, 17),
    SUN_WORLD(Kind.OUTDOOR, "bi-binoculars", "/page-images/beach-hoang-hon.jpg", "Sun World Hạ Long, Bãi Cháy", 14, 19),
    MUSEUM(Kind.INDOOR, "bi-bank", "/page-images/beach-thap-ba.jpg", "Bảo tàng Quảng Ninh, Hạ Long", 8, 17),
    MARKET(Kind.FOOD, "bi-basket", "/page-images/beach-cho-dam.jpg", "Chợ Hạ Long 1, Hạ Long", 7, 11),
    NIGHT_FOOD(Kind.EVENING, "bi-moon-stars", "/page-images/donuong.jpg", "Chợ đêm Bãi Cháy, Hạ Long", 18, 22),
    RESORT_SPA(Kind.RESORT, "bi-flower1", null, null, 9, 21);

    // BEACH = tam bien; SEA = ra vinh bang tau; OUTDOOR = tren bo ngoai troi; INDOOR = trong nha; FOOD = cho / an uong;
    // EVENING = buoi toi ngoai troi; RESORT = trong resort (spa, nha hang)
    public enum Kind { BEACH, SEA, OUTDOOR, INDOOR, FOOD, EVENING, RESORT }

    private final Kind kind;
    private final String icon;
    private final String image;
    private final String mapsQuery;
    private final int from;
    private final int to;
    private final String[] tourKeywords;

    HaLongSpot(Kind kind, String icon, String image, String mapsQuery, int from, int to, String... tourKeywords) {
        this.kind = kind;
        this.icon = icon;
        this.image = image;
        this.mapsQuery = mapsQuery;
        this.from = from;
        this.to = to;
        this.tourKeywords = tourKeywords;
    }

    public Kind getKind() {
        return kind;
    }

    public String getIcon() {
        return icon;
    }

    public String getImage() {
        return image;
    }

    public String getMapsQuery() {
        return mapsQuery;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public String getWindow() {
        return String.format("%02d:00 – %02d:00", from, to);
    }

    String[] tourKeywords() {
        return tourKeywords;
    }

    public boolean isOutdoor() {
        return kind == Kind.BEACH || kind == Kind.SEA || kind == Kind.OUTDOOR || kind == Kind.EVENING;
    }

    // Diem hop thoi tiet ngay d (cang cao cang nen di); < 0 = khong nen di ngay nay
    int fit(WeatherService.Day d) {
        WeatherService.Kind k = d.getKind();
        boolean rough = d.isRough();
        boolean storm = k == WeatherService.Kind.STORM;
        boolean wet = k == WeatherService.Kind.RAIN || k == WeatherService.Kind.DRIZZLE
                || (d.rainChance() != null && d.rainChance() >= 60);
        boolean sunny = k == WeatherService.Kind.CLEAR || k == WeatherService.Kind.PARTLY;
        return switch (kind) {
            case BEACH -> rough || wet || d.maxTemp() < 25 ? -1 : d.isBeachDay() ? 100 : 55;
            case SEA -> rough ? -1
                    : wet ? (this == BAY_CRUISE ? 40 : -1) // hang Sung Sot van tham duoc khi mua nhe
                    : this == SNORKEL && d.maxTemp() < 25 ? -1
                    : sunny ? (this == BAY_CRUISE ? 95 : 90) : 70;
            case OUTDOOR -> storm || wet || (rough && this == SUN_WORLD) ? -1
                    : this == SUN_WORLD && sunny ? 85 : d.maxTemp() <= 30 ? 75 : 50;
            case INDOOR -> wet || rough ? 90 : d.maxTemp() >= 33 ? 60 : 30;
            case FOOD -> wet || rough ? 70 : 55;
            case EVENING -> storm || (wet && k != WeatherService.Kind.DRIZZLE) ? -1 : 60;
            case RESORT -> rough || wet ? 80 : d.minTemp() <= 17 ? 50 : 20;
        };
    }
}
