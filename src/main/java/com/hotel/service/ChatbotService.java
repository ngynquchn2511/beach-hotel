package com.hotel.service;

import com.hotel.entity.Combo;
import com.hotel.entity.CustomerType;
import com.hotel.entity.DiscountCode;
import com.hotel.entity.DiscountType;
import com.hotel.entity.PricingSettings;
import com.hotel.entity.Room;
import com.hotel.entity.RoomType;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DiscountCodeRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.util.Texts;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Chatbot "Sóng" tu van cho khach tren cac trang khach hang.
// KHONG goi AI/LLM ben ngoai - chi nhan dien tu khoa (khong dau) trong cau hoi roi tra loi
// bang du lieu THAT lay truc tiep tu DB (gia phong, phong trong, combo, cai dat coc...) de dam bao chinh xac.
// Song ngu: hieu tu khoa tieng Viet lan tieng Anh, tra loi theo ngon ngu khach dang chon (cau chu o messages*.properties).
// Quy uoc van ban tra ve (widget hien thi): dong bat dau bang "• " la 1 muc danh sach, **chu** la chu dam.
@Service
public class ChatbotService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final int AVAILABILITY_LOOKAHEAD_DAYS = 7;

    // Thong tin resort dung de tu van - de hang so trong code (khong doc tu application.properties)
    // vi Spring doc file .properties bang ISO-8859-1 mac dinh, lam sai lech chu tieng Viet co dau.
    private static final String HOTEL_NAME = "Sóng Xanh Resort";
    private static final String HOTEL_PHONE = "0338932368";
    private static final String HOTEL_ZALO = "0337196258";
    private static final String HOTEL_EMAIL = "songxanhresort@gmail.com";
    private static final String CHECK_IN_TIME = "14:00";
    private static final String CHECK_OUT_TIME = "12:00";
    private static final String DIRECTIONS_URL = "https://www.google.com/maps/dir/?api=1&destination=Tran+Phu,+Nha+Trang,+Khanh+Hoa";

    // Nut hanh dong hien duoi cau tra loi (link noi bo hoac ben ngoai)
    public record Action(String label, String url) {
    }

    // Cau tra loi day du: noi dung + nut hanh dong + goi y cau hoi tiep theo
    public record Reply(String text, List<Action> actions, List<String> suggestions) {
    }

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final ComboRepository comboRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final PricingService pricingService;

    public ChatbotService(RoomTypeRepository roomTypeRepository, RoomRepository roomRepository,
                          ComboRepository comboRepository, DiscountCodeRepository discountCodeRepository,
                          PricingService pricingService) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.comboRepository = comboRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.pricingService = pricingService;
    }

    public String answer(String rawMessage) {
        return reply(rawMessage).text();
    }

    public Reply reply(String rawMessage) {
        return reply(rawMessage, Texts.VIETNAMESE);
    }

    public Reply reply(String rawMessage, Locale locale) {
        return new Conversation(Texts.supported(locale)).reply(rawMessage);
    }

    // 1 luot tra loi trong 1 ngon ngu
    private final class Conversation {

        private final Locale locale;

        Conversation(Locale locale) {
            this.locale = locale;
        }

        private String t(String code, Object... args) {
            return Texts.get(locale, code, args);
        }

        // Noi dung admin nhap 2 ban: lay ban tieng Anh neu khach chon EN va co nhap
        private String pick(String vietnamese, String english) {
            return Texts.pick(locale, vietnamese, english);
        }

        private Action action(String code, String url) {
            return new Action(t(code), url);
        }

        private String money(BigDecimal amount) {
            NumberFormat nf = NumberFormat.getInstance(locale);
            nf.setMaximumFractionDigits(0);
            return t("chat.money", nf.format(amount));
        }

        private String lines(String... codes) {
            List<String> out = new ArrayList<>();
            for (String code : codes) {
                out.add(t(code));
            }
            return String.join("\n", out);
        }

        Reply reply(String rawMessage) {
            Action rooms = action("chat.act.rooms", "/rooms");
            Action directions = action("chat.act.directions", DIRECTIONS_URL);
            Action zalo = action("chat.act.zalo", "https://zalo.me/" + HOTEL_ZALO);
            Action call = action("chat.act.call", "tel:" + HOTEL_PHONE);
            Action explore = action("chat.act.explore", "/about#kham-pha");
            Action history = action("chat.act.history", "/customer/bookings");

            if (rawMessage == null || rawMessage.isBlank()) {
                return new Reply(t("chat.empty"), List.of(),
                        List.of(t("chat.sg.price"), t("chat.sg.available"), t("chat.sg.directions")));
            }

            String q = normalize(rawMessage);
            List<RoomType> matchedTypes = matchRoomTypes(q);
            List<String> sections = new ArrayList<>();
            Set<Action> actions = new LinkedHashSet<>();
            Set<String> suggestions = new LinkedHashSet<>();

            // Tu khoa khong dau, khong phan biet hoa thuong - moi y gom ca tieng Viet lan tieng Anh
            boolean greet = containsAny(q, "xin chao", "chao ban", "chao song", "hello", " hi ", " alo ", " hey ",
                    "good morning", "good evening") || q.isBlank();
            boolean thanks = containsAny(q, "cam on", "thank", "tks", "cam ta");
            boolean bye = containsAny(q, "tam biet", " bye", "hen gap lai", "see you");
            boolean askPrice = containsAny(q, "gia", "bao nhieu tien", "bao nhieu 1 dem", "mat bao nhieu",
                    "price", "cost", "how much", " rate", " fee");
            boolean askAvailability = containsAny(q, "trong ngay nao", "con trong", "con phong", "het phong", "trong khong",
                    "ngay nao trong", "conphong", "phong trong",
                    "available", "availability", "vacan", "fully booked", "rooms free", "room free", "free room", "any room");
            boolean askDeposit = containsAny(q, "coc", "deposit");
            boolean askPayment = containsAny(q, "thanh toan", "tra tien", "chuyen khoan", "tien mat", "quet qr",
                    "payment", "how can i pay", "how do i pay", "pay by", "cash", "bank transfer", "credit card");
            boolean askFacilities = containsAny(q, "dich vu", "tien ich", "co so vat chat", "tien nghi",
                    "bbq", "ban cong", "ho boi", "be boi", "bai bien", "ghe nam", "lan bien", "san ho", "tour dao", "dua don", "san bay", "bep", "nau an", "thue xe", "xe may", "wifi", "wi fi",
                    "bai do xe", "do xe", "khach san co gi", "khach san phuc vu", "resort co gi", "resort phuc vu", "homestay co gi",
                    "amenit", "facilit", "balcon", "pool", "beach", "snorkel", "diving", "island tour", "airport", "shuttle", "kitchen", "cook", "motorbike", "scooter", "parking",
                    "services");
            boolean askCombo = containsAny(q, "combo", "do an", "an uong", "buffet", "an lau", "mon lau", "nuong",
                    "food", "meal", "hotpot", "barbecue", "dinner", "lau hai san");
            boolean askPromotion = containsAny(q, "uu dai", "khuyen mai", "giam gia", "ma giam", "voucher", "discount",
                    "promo", "coupon", " offer", " deal");
            boolean askDirections = containsAny(q, "chi duong", "duong di", "duong toi", "duong den", "duong len", "di nhu the nao", "di the nao",
                    "tu ha noi", "tu sai gon", "tu hcm", "cam ranh", "tu san bay", "bao xa", "bao lau", "xe khach", "di xe gi",
                    "direction", "get to", "get there", "how far", "from hanoi", "from saigon", "from ho chi minh", "airport", "route", " bus", "how long");
            boolean askAddress = !askDirections && containsAny(q, "dia chi", "o dau", "vi tri", "khach san o", "resort o", "homestay o",
                    "ban do", "address", "where is", "where are", "location", " map");
            boolean askContact = containsAny(q, "so dien thoai", "hotline", "lien he", " sdt ", "dien thoai", "email", "zalo",
                    "goi dien", "phone", "contact", " call");
            boolean askCheckTime = containsAny(q, "gio nhan", "gio tra", "nhan tra phong", "check in", "check out", "checkin",
                    "checkout", "may gio nhan", "may gio tra", "what time", "arrival");
            boolean askWeather = containsAny(q, "thoi tiet", "nang khong", "co bao", "mua mua", "lanh khong", "nhiet do", "mua khong", "suong mu", "mang ao", "tam bien duoc",
                    "weather", " cold", "temperature", " rain", " fog", " storm", " sunny", "swim season", "what to wear", "what to pack");
            boolean askExplore = containsAny(q, "di choi", "tham quan", "dia diem", "song ao", "chup anh",
                    "thap ba", "ponagar", "hon mun", "hon chong", "ra dao", "di dao", "hon tam", "vinpearl", "vinwonders", "lan ngam", "co gi choi", "choi gi",
                    "to do", "nearby", "sightseeing", "visit", "attraction", "island", "tower", "coral", "temple", "what to see");
            boolean askFood = containsAny(q, "dac san", "an gi", "mon ngon", "quan an", "hai san", "bun cha ca", "nem nuong", "banh can",
                    " eat", "local food", "seafood", "speciality", "specialty", "dish", "restaurant");
            boolean askHowToBook = containsAny(q, "cach dat", "dat phong nhu the nao", "dat phong the nao", "lam sao dat",
                    "huong dan dat", "dat the nao", "muon dat phong",
                    "how to book", "how do i book", "how can i book", "make a booking", "want to book", "booking process");
            boolean askCancel = containsAny(q, "huy phong", "huy don", "huy dat", "doi ngay", "hoan coc", "hoan tien",
                    "cancel", "refund", "reschedule", "change date");

            if (greet) {
                sections.add(t("chat.greet", HOTEL_NAME));
                suggestions.add(t("chat.sg.price"));
                suggestions.add(t("chat.sg.available"));
            }
            if (askPrice) {
                sections.add(buildPriceAnswer(matchedTypes));
                actions.add(rooms);
                suggestions.add(t("chat.sg.available"));
                suggestions.add(t("chat.sg.deposit"));
            }
            if (askAvailability) {
                sections.add(buildAvailabilityAnswer(matchedTypes));
                actions.add(rooms);
                suggestions.add(t("chat.sg.howToBook"));
            }
            if (askDeposit) {
                sections.add(buildDepositAnswer());
                suggestions.add(t("chat.sg.payment"));
                suggestions.add(t("chat.sg.cancel"));
            }
            if (askPayment) {
                sections.add(t("chat.payment"));
                suggestions.add(t("chat.sg.deposit"));
            }
            if (askFacilities) {
                sections.add(buildFacilitiesAnswer(matchedTypes));
                suggestions.add(t("chat.sg.combo"));
                suggestions.add(t("chat.sg.explore"));
            }
            if (askCombo) {
                sections.add(buildComboAnswer());
                suggestions.add(t("chat.sg.food"));
            }
            if (askPromotion) {
                sections.add(buildPromotionAnswer(q));
                actions.add(rooms);
            }
            if (askDirections) {
                sections.add(t("chat.directions"));
                actions.add(directions);
                suggestions.add(t("chat.sg.weather"));
            }
            if (askAddress) {
                sections.add(t("chat.address", t("about.contact.addressValue")));
                actions.add(directions);
                suggestions.add(t("chat.sg.directions"));
            }
            if (askContact) {
                sections.add(String.join("\n", t("chat.contact.intro"), t("chat.contact.hotline", HOTEL_PHONE),
                        t("chat.contact.zalo", HOTEL_ZALO), t("chat.contact.email", HOTEL_EMAIL), t("chat.contact.outro")));
                actions.add(zalo);
                actions.add(call);
            }
            if (askCheckTime) {
                sections.add(t("chat.checkTime.times", CHECK_IN_TIME, CHECK_OUT_TIME) + "\n" + t("chat.checkTime.note"));
                suggestions.add(t("chat.sg.howToBook"));
            }
            if (askWeather) {
                sections.add(t("chat.weather"));
                suggestions.add(t("chat.sg.explore"));
            }
            if (askExplore) {
                sections.add(lines("chat.explore.intro", "chat.explore.1", "chat.explore.2", "chat.explore.3",
                        "chat.explore.4", "chat.explore.5", "chat.explore.outro"));
                actions.add(explore);
                suggestions.add(t("chat.sg.food"));
            }
            if (askFood) {
                sections.add(t("chat.food"));
                suggestions.add(t("chat.sg.combo"));
            }
            if (askHowToBook) {
                sections.add(lines("chat.book.intro", "chat.book.1", "chat.book.2", "chat.book.3", "chat.book.4",
                        "chat.book.outro"));
                actions.add(rooms);
                suggestions.add(t("chat.sg.deposit"));
            }
            if (askCancel) {
                sections.add(t("chat.cancel"));
                actions.add(history);
                actions.add(zalo);
            }
            if (thanks) {
                sections.add(t("chat.thanks"));
            }
            if (bye) {
                sections.add(t("chat.bye"));
            }

            if (sections.isEmpty()) {
                if (!matchedTypes.isEmpty()) {
                    // Chi nhac ten loai phong -> tra loi tong quan gia + phong trong cua loai do
                    sections.add(buildPriceAnswer(matchedTypes));
                    sections.add(buildAvailabilityAnswer(matchedTypes));
                    actions.add(rooms);
                } else {
                    sections.add(lines("chat.unknown.intro", "chat.unknown.1", "chat.unknown.2", "chat.unknown.3",
                            "chat.unknown.outro"));
                    actions.add(zalo);
                    suggestions.add(t("chat.sg.price"));
                    suggestions.add(t("chat.sg.deposit"));
                    suggestions.add(t("chat.sg.explore"));
                }
            }

            return new Reply(String.join("\n\n", sections), List.copyOf(actions),
                    suggestions.stream().limit(3).toList());
        }

        private String buildPriceAnswer(List<RoomType> matchedTypes) {
            List<RoomType> types = matchedTypes.isEmpty() ? roomTypeRepository.findAll() : matchedTypes;
            StringBuilder sb = new StringBuilder(t("chat.price.title"));
            boolean any = false;
            for (RoomType rt : types) {
                List<Room> rooms = roomRepository.findByRoomTypeId(rt.getId());
                if (rooms.isEmpty()) {
                    continue;
                }
                any = true;
                BigDecimal min = rooms.stream().map(Room::getPrice).min(BigDecimal::compareTo).orElse(rt.getBasePrice());
                BigDecimal max = rooms.stream().map(Room::getPrice).max(BigDecimal::compareTo).orElse(rt.getBasePrice());
                String range = min.compareTo(max) == 0 ? money(min) : number(min) + " – " + money(max);
                sb.append("\n").append(t("chat.price.line", pick(rt.getName(), rt.getNameEn()), range));
            }
            if (!any) {
                return t("chat.price.none");
            }
            int weekend = settings().getWeekendSurchargePercent();
            if (weekend > 0) {
                sb.append("\n").append(t("chat.price.weekend", weekend));
            }
            sb.append("\n").append(t("chat.price.hint"));
            return sb.toString();
        }

        private String number(BigDecimal amount) {
            NumberFormat nf = NumberFormat.getInstance(locale);
            nf.setMaximumFractionDigits(0);
            return nf.format(amount);
        }

        private String buildAvailabilityAnswer(List<RoomType> matchedTypes) {
            List<RoomType> types = matchedTypes.isEmpty() ? roomTypeRepository.findAll() : matchedTypes;
            LocalDate today = LocalDate.now();
            StringBuilder sb = new StringBuilder(t("chat.avail.title", AVAILABILITY_LOOKAHEAD_DAYS));
            for (RoomType rt : types) {
                List<String> freeDays = new ArrayList<>();
                for (int i = 0; i < AVAILABILITY_LOOKAHEAD_DAYS; i++) {
                    LocalDate checkIn = today.plusDays(i);
                    int freeCount = roomRepository.findAvailableRooms(checkIn, checkIn.plusDays(1), rt.getId(), null).size();
                    if (freeCount > 0) {
                        freeDays.add(t("chat.avail.day", checkIn.format(DATE_FMT), freeCount));
                    }
                }
                sb.append("\n").append(t("chat.avail.line", pick(rt.getName(), rt.getNameEn()),
                        freeDays.isEmpty() ? t("chat.avail.full") : String.join(", ", freeDays)));
            }
            sb.append("\n").append(t("chat.avail.hint"));
            return sb.toString();
        }

        // Lay dung cai dat dat coc hien tai (admin chinh o trang "Gia & dat coc")
        private String buildDepositAnswer() {
            PricingSettings s = settings();
            if (s.getDepositPercent() <= 0) {
                return t("chat.deposit.none");
            }
            return t("chat.deposit.rule", s.getDepositPercent(), s.getDepositDeadlineHours()) + "\n" + t("chat.deposit.how");
        }

        private String buildFacilitiesAnswer(List<RoomType> matchedTypes) {
            StringBuilder sb = new StringBuilder(lines("chat.fac.intro", "chat.fac.1", "chat.fac.2", "chat.fac.3",
                    "chat.fac.4", "chat.fac.5", "chat.fac.6"));
            for (RoomType rt : matchedTypes) {
                String amenities = pick(rt.getAmenities(), rt.getAmenitiesEn());
                if (amenities != null && !amenities.isBlank()) {
                    sb.append("\n").append(t("chat.fac.room", pick(rt.getName(), rt.getNameEn()), amenities));
                }
            }
            return sb.toString();
        }

        private String buildComboAnswer() {
            List<Combo> combos = comboRepository.findByActiveTrue();
            if (combos.isEmpty()) {
                return t("chat.combo.none");
            }
            StringBuilder sb = new StringBuilder(t("chat.combo.title"));
            for (Combo c : combos) {
                sb.append("\n").append(t("chat.combo.line", pick(c.getName(), c.getNameEn()), money(c.getPrice())));
                String description = pick(c.getDescription(), c.getDescriptionEn());
                if (description != null && !description.isBlank()) {
                    sb.append(" (").append(description).append(")");
                }
            }
            sb.append("\n").append(t("chat.combo.hint"));
            return sb.toString();
        }

        // Hoi "nguoi moi/khach moi/lan dau" -> chi tra loi ma ap dung cho CustomerType.NEW hoac ma dung chung
        private String buildPromotionAnswer(String normalizedQuestion) {
            boolean askingForNew = containsAny(normalizedQuestion, "nguoi moi", "khach moi", "lan dau", "moi dang ky",
                    "khach hang moi", "new customer", "new guest", "first time");
            List<DiscountCode> relevant = discountCodeRepository.findByActiveTrue().stream()
                    .filter(c -> !askingForNew || c.getApplicableCustomerType() == null
                            || c.getApplicableCustomerType() == CustomerType.NEW)
                    .toList();

            if (relevant.isEmpty()) {
                return t(askingForNew ? "chat.promo.noneNew" : "chat.promo.none");
            }

            StringBuilder sb = new StringBuilder(t(askingForNew ? "chat.promo.titleNew" : "chat.promo.title"));
            for (DiscountCode c : relevant) {
                String amount = c.getDiscountType() == DiscountType.PERCENTAGE
                        ? c.getDiscountValue().stripTrailingZeros().toPlainString() + "%"
                        : money(c.getDiscountValue());
                sb.append("\n").append(t("chat.promo.line", c.getCode(), amount));
                if (c.getApplicableCustomerType() != null) {
                    String type = t("enum.CustomerType." + c.getApplicableCustomerType().name()).toLowerCase(locale);
                    sb.append(" ").append(t("chat.promo.for", type));
                }
                String description = pick(c.getDescription(), c.getDescriptionEn());
                if (description != null && !description.isBlank()) {
                    sb.append(" – ").append(description);
                }
            }
            sb.append("\n").append(t("chat.promo.hint"));
            return sb.toString();
        }
    }

    // Khop ten loai phong (VD "Phong VIP") xuat hien trong cau hoi, khong phan biet dau/hoa-thuong
    private List<RoomType> matchRoomTypes(String normalizedQuestion) {
        List<RoomType> matched = new ArrayList<>();
        for (RoomType rt : roomTypeRepository.findAll()) {
            // Khop ca ten tieng Viet lan ten tieng Anh (neu admin co nhap)
            if (nameMatches(normalizedQuestion, rt.getName())
                    || (rt.getNameEn() != null && nameMatches(normalizedQuestion, rt.getNameEn()))) {
                matched.add(rt);
            }
        }
        return matched;
    }

    private boolean nameMatches(String normalizedQuestion, String name) {
        String normName = normalize(name);
        if (normalizedQuestion.contains(normName)) {
            return true;
        }
        for (String word : normName.trim().split("\\s+")) {
            if (word.length() >= 3 && !word.equals("phong") && !word.equals("room")
                    && normalizedQuestion.contains(" " + word + " ")) {
                return true;
            }
        }
        return false;
    }

    private PricingSettings settings() {
        PricingSettings s = pricingService.getSettings();
        return s != null ? s : PricingSettings.defaults();
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    // Bo dau tieng Viet + ve chu thuong + bo dau cau de so khop tu khoa khong phu thuoc dau/hoa-thuong
    private String normalize(String s) {
        String temp = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        temp = temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        temp = temp.replace('đ', 'd').replace('Đ', 'd');
        temp = temp.replaceAll("[^a-z0-9]+", " ").trim();
        return " " + temp + " ";
    }
}
