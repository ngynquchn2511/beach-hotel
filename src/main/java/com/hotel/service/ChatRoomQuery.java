package com.hotel.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Doc cau khach nhan tro ly "Song" de tim phong: dac diem (view bien, vuon, ho boi, gia dinh...), ngay o
// ("25 - 27", "25/12 den 27/12", "ngay mai 2 dem", "cuoi tuan nay"), so nguoi, ngan sach ("duoi 1 trieu").
// VD "toi muon dat 1 phong ngay 25 - 27 view bien cho 2 nguoi" -> SEA, 25 -> 27 (thang nay / thang sau), 2 khach
public record ChatRoomQuery(Set<Feature> features, LocalDate checkIn, LocalDate checkOut, Integer guests,
                            BigDecimal maxPrice, boolean bookingIntent, boolean cheapest) {

    public static final int MAX_NIGHTS = 30;

    // q: tu khoa trong cau khach (khong dau); room: tu khoa trong ten / tien nghi / mo ta loai phong (khong dau)
    public enum Feature {
        SEA(List.of("view bien", "huong bien", "ngam bien", "nhin bien", "nhin ra bien", "sat bien", "gan bien", "canh bien",
                "bo bien", "sea view", "seaview", "ocean", "beachfront", "by the sea", "near the beach"),
                List.of("bien", "sea", "ocean", "beach")),
        BAY(List.of("view vinh", "huong vinh", "ngam vinh", "toan canh", "panorama", "bay view"),
                List.of("vinh", "toan canh", "panoram", "bay")),
        ISLAND(List.of("view dao", "huong dao", "ngam dao", "island view"), List.of("dao", "island")),
        GARDEN(List.of("view vuon", "huong vuon", "vuon", "garden", "cay xanh", "yen tinh"), List.of("vuon", "garden", "san hien")),
        POOL(List.of("ho boi", "be boi", "pool", "swimming"), List.of("ho boi", "pool")),
        CITY(List.of("huong pho", "view pho", "city view"), List.of("pho", "city")),
        SUNSET(List.of("hoang hon", "sunset"), List.of("hoang hon", "sunset", "bien")),
        FAMILY(List.of("gia dinh", "family", "tre em", "tre con", "con nho", "ca nha"), List.of("gia dinh", "family", "2 phong ngu")),
        COUPLE(List.of("cap doi", "trang mat", "hai vo chong", "2 vo chong", "honeymoon", "couple", "lang man", "romantic", "nguoi yeu"),
                List.of("bon tam", "suite", "be ngam", "sat bien", "hoang hon")),
        BATHTUB(List.of("bon tam", "bathtub", "be ngam", "jacuzzi"), List.of("bon tam", "bathtub", "be ngam")),
        BALCONY(List.of("ban cong", "balcony", "san hien", "terrace", "san thuong"), List.of("ban cong", "balcony", "san hien", "san thuong")),
        LUXURY(List.of("sang trong", "cao cap", "luxury", "xin nhat", "dep nhat", "tot nhat", "best room"),
                List.of("suite", "penthouse", "premier", "bungalow", "quan gia"));

        private final List<String> askWords;
        private final List<String> roomWords;

        Feature(List<String> askWords, List<String> roomWords) {
            this.askWords = askWords;
            this.roomWords = roomWords;
        }

        public boolean matchesRoom(String normalizedRoomText, int maxGuests) {
            if (this == FAMILY && maxGuests >= 4) {
                return true;
            }
            return roomWords.stream().anyMatch(w -> normalizedRoomText.contains(" " + w + " ") || normalizedRoomText.contains(" " + w));
        }
    }

    private static final List<String> BOOKING_WORDS = List.of("dat phong", "muon dat", "can dat", "dat 1 phong", "dat mot phong",
            "book", "tim phong", "can phong", "can 1 phong", "thue phong", "phong nao", "con phong", "phong trong", "muon o", "o lai",
            "muon phong", "co phong", "need a room", "looking for a room", "want a room");
    private static final List<String> CHEAP_WORDS = List.of("gia re", "re nhat", "re ", "tiet kiem", "binh dan", "cheap", "budget",
            "affordable");

    // Ngay - ngay (thang): "25 - 27", "25/12 - 27/12", "tu 25 den 27 thang 12", "25-27/12"
    private static final String DAY = "(\\d{1,2})(?:\\s*[/.]\\s*(\\d{1,2}))?(?:\\s*[/.]\\s*(\\d{4}))?";
    private static final Pattern RANGE = Pattern.compile(
            "(?:\\b(?:ngay|tu|from|on|date))?\\s*" + DAY + "\\s*(?:-|–|—|~|den|toi|to|->|=>)\\s*(?:ngay\\s*)?" + DAY
                    + "(?:\\s*thang\\s*(\\d{1,2}))?(?!\\s*(?:nguoi|khach|dem|ngay dem|tuoi|phong|trieu|tr\\b|k\\b|nghin|ngan|adult|guest|people|night|room|%))");
    // 1 ngay: "ngay 25", "ngay 25/12", "25/12" (+ "2 dem" / "3 ngay 2 dem")
    private static final Pattern SINGLE = Pattern.compile("(?:\\b(?:ngay|tu|from|on)\\s*(\\d{1,2})(?:\\s*[/.]\\s*(\\d{1,2}))?|(\\d{1,2})\\s*/\\s*(\\d{1,2}))(?:\\s*thang\\s*(\\d{1,2}))?");
    private static final Pattern NIGHTS = Pattern.compile("(\\d{1,2})\\s*(?:dem|night)");
    private static final Pattern GUESTS = Pattern.compile("(\\d{1,2})\\s*(?:nguoi|khach|nguoi lon|adult|adults|guest|guests|people|pax|nguoi\\b)");
    private static final Pattern KIDS = Pattern.compile("(\\d{1,2})\\s*(?:tre|be|chau|con nho|kid|kids|child|children)");
    private static final Pattern BUDGET = Pattern.compile("(?:duoi|toi da|khong qua|it hon|under|max|below|less than|tam)\\s*(\\d+(?:[.,]\\d+)*)\\s*(trieu|tr|m|k|nghin|ngan)?");

    public static ChatRoomQuery parse(String raw, LocalDate today) {
        String text = raw == null ? "" : raw;
        String q = " " + normalize(text) + " ";
        // Giu dau / va . cho ngay thang
        String d = " " + stripMarks(text.toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9/.\\-–—~>=]+", " ").replaceAll("\\s+", " ").trim() + " ";

        Set<Feature> features = new LinkedHashSet<>();
        for (Feature f : Feature.values()) {
            if (f.askWords.stream().anyMatch(w -> q.contains(" " + w + " ") || q.contains(" " + w))) {
                features.add(f);
            }
        }

        LocalDate in = null;
        LocalDate out = null;
        Matcher m = RANGE.matcher(d);
        while (m.find()) {
            Integer d1 = num(m.group(1));
            Integer m1 = num(m.group(2));
            Integer y1 = num(m.group(3));
            Integer d2 = num(m.group(4));
            Integer m2 = num(m.group(5));
            Integer y2 = num(m.group(6));
            Integer mThang = num(m.group(7));
            if (m2 == null) {
                m2 = mThang;
            }
            if (m1 == null && m2 != null && d1 <= d2) {
                m1 = m2;
            }
            LocalDate a = resolve(d1, m1, y1, today);
            if (a == null) {
                continue;
            }
            LocalDate b;
            if (m2 != null) {
                b = resolveFrom(d2, m2, y2, a);
            } else {
                b = d2 > a.getDayOfMonth() ? safeDate(a.getYear(), a.getMonthValue(), d2)
                        : safeDate(a.plusMonths(1).getYear(), a.plusMonths(1).getMonthValue(), d2);
            }
            if (b != null && b.isAfter(a) && !b.isAfter(a.plusDays(MAX_NIGHTS))) {
                in = a;
                out = b;
                break;
            }
        }
        Integer nights = null;
        Matcher nm = NIGHTS.matcher(q);
        if (nm.find()) {
            int n = Integer.parseInt(nm.group(1));
            if (n >= 1 && n <= MAX_NIGHTS) {
                nights = n;
            }
        }
        if (in == null) {
            Matcher s = SINGLE.matcher(d);
            while (s.find()) {
                Integer day = num(s.group(1) != null ? s.group(1) : s.group(3));
                Integer mon = num(s.group(1) != null ? s.group(2) : s.group(4));
                if (mon == null) {
                    mon = num(s.group(5));
                }
                // "ngay 2 dem" khong phai ngay
                String after = d.substring(s.end()).trim();
                if (after.startsWith("dem") || after.startsWith("nguoi") || after.startsWith("khach") || after.startsWith("night")) {
                    continue;
                }
                LocalDate a = resolve(day, mon, null, today);
                if (a != null) {
                    in = a;
                    break;
                }
            }
        }
        if (in == null) {
            if (q.contains(" hom nay ") || q.contains(" toi nay ") || q.contains(" dem nay ") || q.contains(" today ") || q.contains(" tonight ")) {
                in = today;
            } else if (q.contains(" ngay mai ") || q.contains(" toi mai ") || q.contains(" mai ") && containsAny(q, BOOKING_WORDS)
                    || q.contains(" tomorrow ")) {
                in = today.plusDays(1);
            } else if (q.contains(" ngay kia ") || q.contains(" ngay mot ")) {
                in = today.plusDays(2);
            } else if (q.contains(" cuoi tuan ") || q.contains(" weekend ")) {
                in = today.getDayOfWeek() == DayOfWeek.SATURDAY ? today : today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
                if (q.contains(" tuan sau ") || q.contains(" next weekend ")) {
                    in = in.plusWeeks(1);
                }
            }
        }
        if (in != null && out == null) {
            out = in.plusDays(nights != null ? nights : 1);
        }

        Integer guests = null;
        Matcher g = GUESTS.matcher(q);
        if (g.find()) {
            int n = Integer.parseInt(g.group(1));
            if (n >= 1 && n <= 60) {
                guests = n;
            }
        }
        Matcher k = KIDS.matcher(q);
        if (k.find()) {
            int kids = Integer.parseInt(k.group(1));
            if (kids >= 1 && kids <= 10) {
                guests = (guests == null ? 2 : guests) + kids;
            }
        }
        if (guests == null && (q.contains(" 2 vo chong ") || q.contains(" hai vo chong ") || q.contains(" cap doi ") || q.contains(" couple "))) {
            guests = 2;
        }

        BigDecimal maxPrice = null;
        String money = " " + stripMarks(text.toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9.,]+", " ") + " ";
        Matcher b = BUDGET.matcher(money);
        if (b.find()) {
            try {
                String n = b.group(1);
                n = n.matches("\\d{1,3}([.,]\\d{3})+") ? n.replaceAll("[.,]", "") : n.replace(',', '.');
                BigDecimal v = new BigDecimal(n);
                String unit = b.group(2);
                if (unit == null) {
                    unit = v.compareTo(BigDecimal.valueOf(100)) < 0 ? "trieu" : "";
                }
                switch (unit) {
                    case "trieu", "tr", "m" -> v = v.multiply(BigDecimal.valueOf(1_000_000));
                    case "k", "nghin", "ngan" -> v = v.multiply(BigDecimal.valueOf(1_000));
                    default -> { }
                }
                if (v.compareTo(BigDecimal.valueOf(100_000)) >= 0) {
                    maxPrice = v;
                }
            } catch (NumberFormatException ignored) {
                // bo qua
            }
        }
        boolean cheapest = containsAny(q, CHEAP_WORDS);
        return new ChatRoomQuery(features, in, out, guests, maxPrice, containsAny(q, BOOKING_WORDS), cheapest);
    }

    // Co du dieu kien de Song tim phong thay vi tra loi chung chung
    public boolean hasCriteria() {
        return !features.isEmpty() || checkIn != null || maxPrice != null || cheapest;
    }

    public boolean hasDates() {
        return checkIn != null && checkOut != null;
    }

    public int nights() {
        return hasDates() ? (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut) : 0;
    }

    public List<Feature> featureList() {
        return new ArrayList<>(features);
    }

    // Ngay khong co thang: tu hom nay tro di (ngay da qua trong thang -> thang sau); co thang ma da qua -> nam sau
    private static LocalDate resolve(Integer day, Integer month, Integer year, LocalDate today) {
        if (day == null || day < 1 || day > 31) {
            return null;
        }
        if (month != null && (month < 1 || month > 12)) {
            return null;
        }
        if (year != null) {
            return month == null ? null : safeDate(year, month, day);
        }
        if (month == null) {
            LocalDate c = day >= today.getDayOfMonth() ? safeDate(today.getYear(), today.getMonthValue(), day) : null;
            if (c == null) {
                LocalDate next = today.plusMonths(1);
                c = safeDate(next.getYear(), next.getMonthValue(), day);
            }
            return c;
        }
        LocalDate c = safeDate(today.getYear(), month, day);
        if (c != null && c.isBefore(today)) {
            c = safeDate(today.getYear() + 1, month, day);
        }
        return c;
    }

    private static LocalDate resolveFrom(int day, int month, Integer year, LocalDate from) {
        if (month < 1 || month > 12) {
            return null;
        }
        int y = year != null ? year : from.getYear();
        LocalDate c = safeDate(y, month, day);
        if (c != null && year == null && !c.isAfter(from)) {
            c = safeDate(y + 1, month, day);
        }
        return c;
    }

    private static LocalDate safeDate(int y, int m, int d) {
        try {
            return LocalDate.of(y, m, d);
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    private static Integer num(String s) {
        return s == null ? null : Integer.valueOf(s);
    }

    private static boolean containsAny(String q, List<String> words) {
        return words.stream().anyMatch(w -> q.contains(" " + w));
    }

    static String stripMarks(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'd');
    }

    public static String normalize(String s) {
        return stripMarks(s == null ? "" : s.toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
