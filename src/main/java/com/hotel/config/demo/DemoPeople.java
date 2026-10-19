package com.hotel.config.demo;

import com.hotel.entity.Gender;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// Sinh nguoi gia lap trong nhu that: ho ten Viet theo ty le ho pho bien, SDT dung dau so nha mang, CCCD dung ma tinh,
// dia chi theo tinh thanh khach hay toi Ha Long. Email dung ten mien du phong example.com/.net/.org (RFC 2606)
// de he thong co gui mail (nhac lich, moi danh gia) cung khong toi hop thu cua nguoi that nao.
public final class DemoPeople {

    public record Person(String fullName, Gender gender, LocalDate birthDate, String phone, String email, String address,
                         String idNumber, boolean foreigner, String nationality) {
        public String firstName() {
            String[] p = fullName.split(" ");
            return p[p.length - 1];
        }
    }

    private static final String[] SURNAMES = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ",
            "Hồ", "Ngô", "Dương", "Lý", "Đinh", "Trịnh", "Đào", "Mai", "Lương", "Tạ", "Cao", "Lâm", "Nghiêm", "Phùng", "Chu", "Kiều"};
    private static final int[] SURNAME_WEIGHTS = {310, 110, 95, 70, 50, 30, 45, 40, 25, 22, 20, 16, 13, 13, 11, 6, 8, 7, 7, 6, 5, 4, 5, 4, 2, 3, 3, 2};

    private static final String[] MALE_MIDDLE = {"Văn", "Đức", "Minh", "Quốc", "Hữu", "Thành", "Tuấn", "Hoàng", "Xuân", "Công", "Gia", "Đình",
            "Trọng", "Anh", "Ngọc", "Quang", "Thanh", "Việt", "Duy", "Mạnh", "Bá", "Tiến", "Hải", "Trung"};
    private static final String[] MALE_GIVEN = {"Hùng", "Dũng", "Tuấn", "Nam", "Long", "Hải", "Sơn", "Phong", "Minh", "Khoa", "Đức", "Huy",
            "Quân", "Thắng", "Trung", "Hiếu", "Phúc", "Bình", "Tùng", "Kiên", "Cường", "Hoàng", "Vinh", "Toàn", "Khánh", "Thịnh", "Đạt",
            "Lâm", "Hưng", "An", "Bảo", "Nghĩa", "Tài", "Lộc", "Quang", "Thái", "Hòa", "Tiến", "Duy", "Khôi"};
    private static final String[] FEMALE_MIDDLE = {"Thị", "Thu", "Ngọc", "Thanh", "Minh", "Phương", "Hồng", "Kim", "Mai", "Khánh", "Diệu",
            "Bảo", "Thùy", "Hải", "Quỳnh", "Lan", "Hoài", "Thảo", "Mỹ", "Bích"};
    private static final String[] FEMALE_GIVEN = {"Hương", "Lan", "Hoa", "Trang", "Linh", "Hằng", "Ngọc", "Thảo", "Mai", "Huyền", "Nhung",
            "Yến", "Phương", "Hà", "Anh", "Vân", "Thủy", "Dung", "Nga", "Hạnh", "Loan", "Quyên", "Trâm", "Vy", "Chi", "Giang", "Hiền",
            "Oanh", "Tâm", "My", "Nhi", "Uyên", "Thư", "Hồng", "Liên", "Ly"};

    // Khach nuoc ngoai hay gap o Ha Long: Han, Trung, Phap, Anh, My, Uc, Duc, Nhat
    private static final String[][] FOREIGNERS = {
            {"Kim Min-jun", "M", "Hàn Quốc"}, {"Lee Ji-eun", "F", "Hàn Quốc"}, {"Park Seo-yeon", "F", "Hàn Quốc"}, {"Choi Woo-sik", "M", "Hàn Quốc"},
            {"Jung Ha-neul", "F", "Hàn Quốc"}, {"Kang Dong-hyun", "M", "Hàn Quốc"}, {"Yoon So-hee", "F", "Hàn Quốc"}, {"Han Jae-won", "M", "Hàn Quốc"},
            {"Wang Lei", "M", "Trung Quốc"}, {"Chen Jing", "F", "Trung Quốc"}, {"Liu Yang", "M", "Trung Quốc"}, {"Zhang Wei", "M", "Trung Quốc"},
            {"Li Na", "F", "Trung Quốc"}, {"Sophie Dubois", "F", "Pháp"}, {"Nicolas Martin", "M", "Pháp"}, {"Camille Laurent", "F", "Pháp"},
            {"James Smith", "M", "Anh"}, {"Emma Wilson", "F", "Anh"}, {"Oliver Brown", "M", "Anh"}, {"Olivia Taylor", "F", "Mỹ"},
            {"Michael Johnson", "M", "Mỹ"}, {"Sarah Miller", "F", "Mỹ"}, {"Jack Thompson", "M", "Úc"}, {"Chloe Anderson", "F", "Úc"},
            {"Thomas Müller", "M", "Đức"}, {"Lena Schmidt", "F", "Đức"}, {"Tanaka Yuki", "F", "Nhật Bản"}, {"Sato Haruto", "M", "Nhật Bản"},
            {"Suzuki Aoi", "F", "Nhật Bản"}, {"Anna Kowalska", "F", "Ba Lan"}, {"Lucas Silva", "M", "Brazil"}, {"Mia Jensen", "F", "Đan Mạch"}};

    // Tinh -> {ma tinh tren CCCD, quan/huyen..., duong...}
    private static final String[][][] CITIES = {
            {{"Hà Nội", "001", "350"}, {"Đống Đa", "Cầu Giấy", "Thanh Xuân", "Hai Bà Trưng", "Ba Đình", "Hoàng Mai", "Long Biên", "Nam Từ Liêm", "Tây Hồ", "Hà Đông"},
                    {"Nguyễn Chí Thanh", "Trần Duy Hưng", "Láng Hạ", "Xuân Thủy", "Nguyễn Trãi", "Kim Mã", "Giải Phóng", "Lạc Long Quân", "Tô Hiến Thành", "Nguyễn Văn Cừ", "Hoàng Quốc Việt", "Lê Văn Lương"}},
            {{"Hải Phòng", "031", "120"}, {"Lê Chân", "Ngô Quyền", "Hồng Bàng", "Hải An", "Kiến An", "Dương Kinh"},
                    {"Lạch Tray", "Văn Cao", "Tô Hiệu", "Lê Hồng Phong", "Trần Nguyên Hãn", "Điện Biên Phủ", "Cát Bi"}},
            {{"Quảng Ninh", "022", "110"}, {"TP. Hạ Long", "TP. Cẩm Phả", "TP. Uông Bí", "TX. Quảng Yên", "TP. Móng Cái"},
                    {"Trần Hưng Đạo", "Lê Thánh Tông", "Cao Xanh", "Nguyễn Văn Cừ", "Hạ Long", "Bến Đoan"}},
            {{"Bắc Ninh", "027", "45"}, {"TP. Bắc Ninh", "TP. Từ Sơn", "Yên Phong", "Quế Võ"}, {"Ngô Gia Tự", "Lý Thái Tổ", "Nguyễn Gia Thiều", "Trần Hưng Đạo"}},
            {{"Hải Dương", "030", "45"}, {"TP. Hải Dương", "TP. Chí Linh", "Kinh Môn"}, {"Trần Hưng Đạo", "Nguyễn Lương Bằng", "Thanh Niên"}},
            {{"Hưng Yên", "033", "25"}, {"TP. Hưng Yên", "Văn Lâm", "Mỹ Hào"}, {"Điện Biên", "Nguyễn Văn Linh", "Bạch Đằng"}},
            {{"Thái Bình", "034", "22"}, {"TP. Thái Bình", "Vũ Thư", "Tiền Hải"}, {"Lý Bôn", "Trần Thái Tông", "Hai Bà Trưng"}},
            {{"Nam Định", "036", "20"}, {"TP. Nam Định", "Hải Hậu", "Giao Thủy"}, {"Trần Hưng Đạo", "Hùng Vương", "Văn Cao"}},
            {{"Thái Nguyên", "019", "20"}, {"TP. Thái Nguyên", "TP. Sông Công", "Phổ Yên"}, {"Hoàng Văn Thụ", "Lương Ngọc Quyến", "Quang Trung"}},
            {{"Vĩnh Phúc", "026", "18"}, {"TP. Vĩnh Yên", "TP. Phúc Yên"}, {"Mê Linh", "Nguyễn Tất Thành", "Kim Ngọc"}},
            {{"Hồ Chí Minh", "079", "60"}, {"Quận 1", "Quận 3", "Quận 7", "Bình Thạnh", "Phú Nhuận", "TP. Thủ Đức", "Tân Bình"},
                    {"Nguyễn Đình Chiểu", "Điện Biên Phủ", "Nguyễn Văn Linh", "Phan Xích Long", "Võ Văn Tần", "Cộng Hòa", "Xa Lộ Hà Nội"}},
            {{"Đà Nẵng", "048", "15"}, {"Hải Châu", "Sơn Trà", "Thanh Khê"}, {"Nguyễn Văn Linh", "Võ Nguyên Giáp", "Lê Duẩn"}},
            {{"Nghệ An", "040", "15"}, {"TP. Vinh", "Cửa Lò"}, {"Lê Lợi", "Quang Trung", "Nguyễn Du"}},
            {{"Thanh Hóa", "038", "15"}, {"TP. Thanh Hóa", "TP. Sầm Sơn"}, {"Lê Hoàn", "Bà Triệu", "Trần Phú"}}};

    private static final String[] VIETTEL = {"096", "097", "098", "086", "032", "033", "034", "035", "036", "037", "038", "039"};
    private static final String[] VINA = {"091", "094", "088", "083", "084", "085", "081", "082"};
    private static final String[] MOBI = {"090", "093", "089", "070", "079", "077", "076", "078"};
    private static final String[] DOMAINS = {"example.com", "example.net", "example.org"};

    private final Random rnd;
    private final Set<String> usedEmails = new HashSet<>();
    private final Set<String> usedPhones = new HashSet<>();
    private final Set<String> usedIds = new HashSet<>();

    public DemoPeople(Random rnd) {
        this.rnd = rnd;
    }

    public <T> T pick(T[] a) {
        return a[rnd.nextInt(a.length)];
    }

    public <T> T pick(List<T> a) {
        return a.get(rnd.nextInt(a.size()));
    }

    private String surname() {
        int total = 0;
        for (int w : SURNAME_WEIGHTS) {
            total += w;
        }
        int r = rnd.nextInt(total);
        for (int i = 0; i < SURNAMES.length; i++) {
            r -= SURNAME_WEIGHTS[i];
            if (r < 0) {
                return SURNAMES[i];
            }
        }
        return SURNAMES[0];
    }

    public String vietName(Gender g) {
        return vietName(g, surname());
    }

    public String vietName(Gender g, String surname) {
        boolean male = g == Gender.MALE;
        String middle = pick(male ? MALE_MIDDLE : FEMALE_MIDDLE);
        String given = pick(male ? MALE_GIVEN : FEMALE_GIVEN);
        // ~15% ten 4 chu (VD "Nguyễn Thị Thu Hằng")
        if (rnd.nextInt(100) < 15) {
            String m2 = pick(male ? MALE_MIDDLE : FEMALE_MIDDLE);
            if (!m2.equals(middle) && !m2.equals(given)) {
                middle = middle + " " + m2;
            }
        }
        if (middle.endsWith(given)) {
            given = pick(male ? MALE_GIVEN : FEMALE_GIVEN);
        }
        return surname + " " + middle + " " + given;
    }

    public static String ascii(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace("đ", "d").replace("Đ", "D").replace("ü", "u").replace("-", "");
    }

    public String email(String fullName, int birthYear) {
        String[] p = ascii(fullName).toLowerCase().split(" ");
        String given = p[p.length - 1];
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < p.length - 1; i++) {
            initials.append(p[i].charAt(0));
        }
        String base = switch (rnd.nextInt(5)) {
            case 0 -> given + initials + (birthYear % 100 < 10 ? "0" : "") + (birthYear % 100);
            case 1 -> String.join(".", p);
            case 2 -> given + "." + p[0] + birthYear;
            case 3 -> p[0] + given + (rnd.nextInt(90) + 10);
            default -> given + initials;
        };
        String domain = pick(DOMAINS);
        String email = base + "@" + domain;
        int i = 2;
        while (!usedEmails.add(email)) {
            email = base + i++ + "@" + domain;
        }
        return email;
    }

    public String phone() {
        int r = rnd.nextInt(100);
        String prefix = pick(r < 50 ? VIETTEL : r < 78 ? VINA : MOBI);
        String phone;
        do {
            phone = prefix + String.format("%07d", rnd.nextInt(10_000_000));
        } while (!usedPhones.add(phone));
        return phone;
    }

    private String[][] city() {
        int total = 0;
        for (String[][] c : CITIES) {
            total += Integer.parseInt(c[0][2]);
        }
        int r = rnd.nextInt(total);
        for (String[][] c : CITIES) {
            r -= Integer.parseInt(c[0][2]);
            if (r < 0) {
                return c;
            }
        }
        return CITIES[0];
    }

    private String address(String[][] c) {
        String street = pick(c[2]);
        String no = switch (rnd.nextInt(3)) {
            case 0 -> "Số " + (rnd.nextInt(220) + 1) + " ";
            case 1 -> "Số " + (rnd.nextInt(60) + 1) + " ngõ " + (rnd.nextInt(300) + 1) + " ";
            default -> "Số " + (rnd.nextInt(40) + 1) + " ngách " + (rnd.nextInt(80) + 1) + "/" + (rnd.nextInt(200) + 1) + " ";
        };
        return no + street + ", " + pick(c[1]) + ", " + c[0][0];
    }

    // CCCD 12 so: ma tinh (3) + gioi tinh/the ky (1) + 2 so cuoi nam sinh + 6 so ngau nhien
    public String cccd(String provinceCode, Gender g, int birthYear) {
        int centuryGender = (birthYear >= 2000 ? 2 : 0) + (g == Gender.FEMALE ? 1 : 0);
        String id;
        do {
            id = provinceCode + centuryGender + String.format("%02d", birthYear % 100) + String.format("%06d", rnd.nextInt(1_000_000));
        } while (!usedIds.add(id));
        return id;
    }

    public String passport(String nationality) {
        String prefix = switch (nationality) {
            case "Hàn Quốc" -> "M";
            case "Trung Quốc" -> "E";
            case "Nhật Bản" -> "TK";
            case "Mỹ" -> "5";
            default -> "P";
        };
        String id;
        do {
            id = prefix + String.format("%08d", rnd.nextInt(100_000_000));
        } while (!usedIds.add(id));
        return id;
    }

    public LocalDate birthDate(int minAge, int maxAge, LocalDate today) {
        int age = minAge + rnd.nextInt(maxAge - minAge + 1);
        return today.minusYears(age).minusDays(rnd.nextInt(365));
    }

    public Person person(LocalDate today, boolean foreigner) {
        if (foreigner) {
            String[] f = pick(FOREIGNERS);
            Gender g = f[1].equals("M") ? Gender.MALE : Gender.FEMALE;
            LocalDate dob = birthDate(24, 62, today);
            return new Person(f[0], g, dob, rnd.nextInt(100) < 40 ? phone() : "09" + String.format("%08d", rnd.nextInt(100_000_000)),
                    email(f[0], dob.getYear()), null, passport(f[2]), true, f[2]);
        }
        Gender g = rnd.nextBoolean() ? Gender.MALE : Gender.FEMALE;
        String name = vietName(g);
        LocalDate dob = birthDate(23, 66, today);
        String[][] c = city();
        return new Person(name, g, dob, phone(), email(name, dob.getYear()), address(c), cccd(c[0][1], g, dob.getYear()), false, "Việt Nam");
    }

    // Nguoi di cung: vo/chong (khac gioi), con nho (giay khai sinh)
    public Person companion(Person main, LocalDate today, boolean child) {
        if (main.foreigner()) {
            Gender g = child ? (rnd.nextBoolean() ? Gender.MALE : Gender.FEMALE) : (main.gender() == Gender.MALE ? Gender.FEMALE : Gender.MALE);
            // Nguoi di cung cung quoc tich; uu tien dung gioi tinh, khong trung ten nguoi dat
            List<String[]> same = java.util.Arrays.stream(FOREIGNERS)
                    .filter(f -> f[2].equals(main.nationality()) && !f[0].equals(main.fullName())).toList();
            List<String[]> sameGender = same.stream().filter(f -> f[1].equals(g == Gender.MALE ? "M" : "F")).toList();
            String[] f = !sameGender.isEmpty() ? pick(sameGender) : !same.isEmpty() ? pick(same) : new String[]{main.fullName(), "", main.nationality()};
            String name = f[0];
            if (child) {
                // Con lay ho cua bo / me: ho dung dau voi nguoi chau A, dung cuoi voi nguoi phuong Tay
                boolean asian = List.of("Hàn Quốc", "Trung Quốc", "Nhật Bản").contains(main.nationality());
                String[] mp = main.fullName().split(" ");
                String[] fp = f[0].split(" ");
                name = asian ? mp[0] + " " + fp[fp.length - 1] : fp[0] + " " + mp[mp.length - 1];
            }
            LocalDate dob = child ? birthDate(3, 12, today) : birthDate(24, 60, today);
            return new Person(name, g, dob, null, null, null, passport(main.nationality()), true, main.nationality());
        }
        String surname = main.fullName().split(" ")[0];
        if (child) {
            Gender g = rnd.nextBoolean() ? Gender.MALE : Gender.FEMALE;
            LocalDate dob = birthDate(2, 13, today);
            return new Person(vietName(g, main.gender() == Gender.MALE ? surname : surname()), g, dob, null, null, main.address(),
                    "0" + String.format("%02d", rnd.nextInt(99)) + "/" + dob.getYear() + "/" + String.format("%04d", rnd.nextInt(10_000)), false, "Việt Nam");
        }
        Gender g = main.gender() == Gender.MALE ? Gender.FEMALE : Gender.MALE;
        String name = vietName(g);
        int age = Math.max(22, main.birthDate().until(today).getYears() + rnd.nextInt(7) - 3);
        LocalDate dob = today.minusYears(age).minusDays(rnd.nextInt(365));
        return new Person(name, g, dob, null, null, main.address(), cccd(main.idNumber().substring(0, 3), g, dob.getYear()), false, "Việt Nam");
    }
}
