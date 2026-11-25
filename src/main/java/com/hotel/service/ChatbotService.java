package com.hotel.service;

import com.hotel.util.Money;
import com.hotel.entity.Airport;
import com.hotel.entity.Combo;
import com.hotel.entity.CustomerType;
import com.hotel.entity.DiscountCode;
import com.hotel.entity.DiscountType;
import com.hotel.entity.DivingTour;
import com.hotel.entity.PricingSettings;
import com.hotel.entity.Room;
import com.hotel.entity.RoomType;
import com.hotel.entity.TransferRate;
import com.hotel.entity.VehicleType;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DivingTourRepository;
import com.hotel.repository.TransferRateRepository;
import com.hotel.repository.DiscountCodeRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.util.Texts;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Chatbot "Sóng" tu van cho khach tren cac trang khach hang.
// KHONG goi AI/LLM ben ngoai - chi nhan dien tu khoa (khong dau) trong cau hoi roi tra loi
// bang du lieu THAT lay truc tiep tu DB (gia phong, phong trong, combo, cai dat coc...) de dam bao chinh xac.
// Song ngu: hieu tu khoa tieng Viet lan tieng Anh, tra loi theo ngon ngu khach dang chon (cau chu o messages*.properties).
// Quy uoc van ban tra ve (widget hien thi): dong bat dau bang "• " la 1 muc danh sach, **chu** la chu dam.
@Service
public class ChatbotService {

    // Chi gioi thieu ma con dung duoc (trong thoi han, con luot)
    private DiscountCodeService discountCodeService;

    @org.springframework.beans.factory.annotation.Autowired
    void setDiscountCodeService(DiscountCodeService discountCodeService) {
        this.discountCodeService = discountCodeService;
    }

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
    private static final String DIRECTIONS_URL = "https://www.google.com/maps/dir/?api=1&destination=Bai+Chay,+Ha+Long,+Quang+Ninh";

    // Nut hanh dong hien duoi cau tra loi (link noi bo hoac ben ngoai)
    public record Action(String label, String url) {
    }

    // Cau tra loi day du: noi dung + nut hanh dong + goi y cau hoi tiep theo
    // The phong Song goi y: bam vao mo trang phong voi ngay / so khach dien san
    public record RoomCard(Long roomId, String title, String subtitle, String image, String price, String detail,
                           List<String> badges, String url) {
    }

    // topics: ma chu de Song nhan ra (rong = khong hieu), roomTypes: ten loai phong khach nhac toi - dung de thong ke
    // cards: phong phu hop khi khach tim phong theo nhu cau (view, ngay, so nguoi, ngan sach)
    public record Reply(String text, List<Action> actions, List<String> suggestions, List<String> topics,
                        List<String> roomTypes, List<RoomCard> cards) {

        public Reply(String text, List<Action> actions, List<String> suggestions) {
            this(text, actions, suggestions, List.of(), List.of(), List.of());
        }

        public Reply(String text, List<Action> actions, List<String> suggestions, List<String> topics, List<String> roomTypes) {
            this(text, actions, suggestions, topics, roomTypes, List.of());
        }

        public boolean understood() {
            return !topics.isEmpty();
        }
    }

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final ComboRepository comboRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final PricingService pricingService;
    private final DivingTourRepository divingTourRepository;
    private final TransferRateRepository transferRateRepository;

    public ChatbotService(RoomTypeRepository roomTypeRepository, RoomRepository roomRepository,
                          ComboRepository comboRepository, DiscountCodeRepository discountCodeRepository,
                          PricingService pricingService, DivingTourRepository divingTourRepository,
                          TransferRateRepository transferRateRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.comboRepository = comboRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.pricingService = pricingService;
        this.divingTourRepository = divingTourRepository;
        this.transferRateRepository = transferRateRepository;
    }

    private WeatherService weatherService;

    @org.springframework.beans.factory.annotation.Autowired
    void setWeatherService(WeatherService weatherService) {
        this.weatherService = weatherService;
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

        // Du bao that 3 ngay toi (null neu khong lay duoc)
        private String liveWeather() {
            if (weatherService == null) {
                return null;
            }
            var days = weatherService.next(3);
            if (days.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder(t("chat.weather.live"));
            for (var d : days) {
                sb.append("\n- ").append(t("chat.weather.day", d.date().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")),
                        t("weather.kind." + d.getKind().name()), String.valueOf(d.minTemp()), String.valueOf(d.maxTemp()),
                        d.rainChance() == null ? "?" : String.valueOf(d.rainChance())));
            }
            return sb.toString();
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
            return t("chat.money", Money.format(amount));
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
                    "bbq", "ban cong", "ho boi", "be boi", "bai bien", "ghe nam", "bep", "nau an", "thue xe", "xe may", "wifi", "wi fi",
                    "bai do xe", "do xe", "khach san co gi", "khach san phuc vu", "resort co gi", "resort phuc vu", "homestay co gi",
                    "amenit", "facilit", "balcon", "pool", "beach", "kitchen", "cook", "motorbike", "scooter", "parking",
                    "services");
            boolean askCombo = containsAny(q, "combo", "do an", "an uong", "buffet", "an lau", "mon lau", "nuong",
                    "food", "meal", "hotpot", "barbecue", "dinner", "lau hai san");
            boolean askPromotion = containsAny(q, "uu dai", "khuyen mai", "giam gia", "ma giam", "voucher", "discount",
                    "promo", "coupon", " offer", " deal");
            boolean askDirections = containsAny(q, "chi duong", "duong di", "duong toi", "duong den", "duong len", "di nhu the nao", "di the nao",
                    "tu ha noi", "tu sai gon", "tu hcm", "cao toc", "bao xa", "bao lau", "xe khach", "di xe gi",
                    "direction", "get to", "get there", "how far", "from hanoi", "from saigon", "from ho chi minh", "expressway", "route", " bus", "how long");
            boolean askAddress = !askDirections && containsAny(q, "dia chi", "o dau", "vi tri", "khach san o", "resort o", "homestay o",
                    "ban do", "address", "where is", "where are", "location", " map");
            boolean askContact = containsAny(q, "so dien thoai", "hotline", "lien he", " sdt ", "dien thoai", "email", "zalo",
                    "goi dien", "phone", "contact", " call");
            boolean askCheckTime = containsAny(q, "gio nhan", "gio tra", "nhan tra phong", "check in", "check out", "checkin",
                    "checkout", "may gio nhan", "may gio tra", "what time", "arrival");
            boolean askWeather = containsAny(q, "thoi tiet", "nang khong", "co bao", "mua mua", "lanh khong", "nhiet do", "mua khong", "suong mu", "mang ao", "tam bien duoc",
                    "weather", " cold", "temperature", " rain", " fog", " storm", " sunny", "swim season", "what to wear", "what to pack");
            boolean askExplore = containsAny(q, "di choi", "tham quan", "dia diem", "song ao", "chup anh",
                    "sung sot", "ti top", "titop", "trong mai", "du thuyen", "bai chay", "sun world", "ra dao", "di dao", "co gi choi", "choi gi",
                    "to do", "nearby", "sightseeing", "visit", "attraction", "island", "cruise", "cave", "what to see");
            boolean askFood = containsAny(q, "dac san", "an gi", "mon ngon", "quan an", "hai san", "cha muc", "sa sung", "ngan",
                    " eat", "local food", "seafood", "speciality", "specialty", "dish", "restaurant");
            // Dich vu them: tour lan bien va xe dua don san bay (tra loi bang du lieu that trong DB)
            boolean askTour = containsAny(q, "tour lan", "lan bien", "lan ong tho", "lan binh", "lan ngam", "san ho", "kayak",
                    "snorkel", "diving", "dive", "scuba", "coral");
            boolean askSuggestRoom = containsAny(q, "goi y phong", "tu van phong", "phong nao hop", "phong nao phu hop", "nen chon phong",
                    "nen dat phong nao", "chon phong nao", "phong nao tot", "recommend a room", "which room", "suggest a room", "best room for");
            boolean askTransfer = containsAny(q, "dua don", "don san bay", "tien san bay", "xe san bay", "ra san bay",
                    "tu san bay", "xe don", "van don", "noi bai", "cat bi",
                    "airport", "transfer", "shuttle", "pick up", "pickup", "drop off", "dropoff");
            boolean askHowToBook = containsAny(q, "cach dat", "dat phong nhu the nao", "dat phong the nao", "lam sao dat",
                    "huong dan dat", "dat the nao", "muon dat phong",
                    "how to book", "how do i book", "how can i book", "make a booking", "want to book", "booking process");
            boolean askCancel = containsAny(q, "huy phong", "huy don", "huy dat", "doi ngay", "hoan coc", "hoan tien",
                    "cancel", "refund", "reschedule", "change date");

            // Tim phong theo nhu cau: co dac diem / ngay / ngan sach, hoac muon dat phong cho N nguoi
            ChatRoomQuery rq = ChatRoomQuery.parse(rawMessage, LocalDate.now());
            boolean find = rq.hasCriteria() || rq.guests() != null && (rq.bookingIntent() || askAvailability || askSuggestRoom);
            List<RoomCard> cards = List.of();
            List<String> shownTypes = List.of();
            if (find) {
                Finder f = findRooms(rq, matchedTypes);
                sections.add(f.text());
                cards = f.cards();
                shownTypes = f.typeNames();
                actions.addAll(f.actions());
                suggestions.addAll(f.suggestions());
            }

            if (greet) {
                sections.add(t("chat.greet", HOTEL_NAME));
                suggestions.add(t("chat.sg.price"));
                suggestions.add(t("chat.sg.available"));
            }
            if (askPrice && !find) {
                sections.add(buildPriceAnswer(matchedTypes));
                actions.add(rooms);
                suggestions.add(t("chat.sg.available"));
                suggestions.add(t("chat.sg.deposit"));
            }
            if (askAvailability && !find) {
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
            if (askSuggestRoom && find) {
                actions.add(action("chat.btn.suggestRoom", "/rooms/suggest"));
            }
            if (askSuggestRoom && !find) {
                sections.add(t("chat.suggestRoom"));
                actions.add(action("chat.btn.suggestRoom", "/rooms/suggest"));
            }
            if (askWeather) {
                String live = liveWeather();
                sections.add(live != null ? live + "\n\n" + t("chat.weather") : t("chat.weather"));
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
            if (askTour) {
                sections.add(buildTourAnswer());
                actions.add(action("chat.act.services", "/services#tour-lan"));
                suggestions.add(t("chat.sg.transfer"));
            }
            if (askTransfer) {
                sections.add(buildTransferAnswer());
                actions.add(action("chat.act.services", "/services#dua-don"));
                suggestions.add(t("chat.sg.tour"));
            }
            if (askHowToBook) {
                sections.add(lines("chat.book.intro", "chat.book.1", "chat.book.2", "chat.book.3", "chat.book.4",
                        "chat.book.outro"));
                actions.add(rooms);
                suggestions.add(t("chat.sg.deposit"));
            }
            if (askCancel) {
                sections.add(t("chat.cancel"));
                // Chi noi ve hoan coc khi resort dang yeu cau dat coc
                PricingSettings policy = settings();
                if (policy.getDepositPercent() > 0) {
                    sections.add(t("chat.cancelPolicy", policy.getRefundFullHours(), policy.getRefundPartialHours(),
                            policy.getRefundPartialPercent()));
                }
                actions.add(history);
                actions.add(zalo);
            }
            if (thanks) {
                sections.add(t("chat.thanks"));
            }
            if (bye) {
                sections.add(t("chat.bye"));
            }

            List<String> topics = new ArrayList<>();
            Object[][] flags = {{greet, "GREET"}, {askPrice, "PRICE"}, {askAvailability, "AVAILABILITY"}, {askDeposit, "DEPOSIT"},
                    {askPayment, "PAYMENT"}, {askFacilities, "FACILITIES"}, {askCombo, "COMBO"}, {askPromotion, "PROMOTION"},
                    {askDirections, "DIRECTIONS"}, {askAddress, "ADDRESS"}, {askContact, "CONTACT"}, {askCheckTime, "CHECK_TIME"},
                    {askSuggestRoom, "SUGGEST_ROOM"}, {askWeather, "WEATHER"}, {askExplore, "EXPLORE"}, {askFood, "FOOD"},
                    {askTour, "TOUR"}, {askTransfer, "TRANSFER"}, {askHowToBook, "HOW_TO_BOOK"}, {askCancel, "CANCEL"},
                    {thanks, "THANKS"}, {bye, "BYE"}};
            for (Object[] f : flags) {
                if ((Boolean) f[0]) {
                    topics.add((String) f[1]);
                }
            }
            if (find) {
                topics.add(0, "FIND_ROOM");
            }
            if (topics.isEmpty() && !matchedTypes.isEmpty()) {
                topics.add("ROOM_TYPE");
            }
            List<String> typeNames = find && !shownTypes.isEmpty() ? shownTypes : matchedTypes.stream().map(RoomType::getName).toList();

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
                    suggestions.stream().limit(3).toList(), List.copyOf(topics), typeNames, cards);
        }

        // ===== Tim phong theo nhu cau =====

        private record Candidate(RoomType type, Room room, int score, BigDecimal total, int free) {
        }

        private String featureLabel(ChatRoomQuery.Feature f) {
            return t("chat.feat." + f.name());
        }

        private String roomText(RoomType rt) {
            // Chi ten + tien nghi (mo ta hay nhac "cach bien 5 phut" lam khop nham view bien)
            return " " + ChatRoomQuery.normalize(String.join(" ", nz(rt.getName()), nz(rt.getNameEn()), nz(rt.getAmenities()),
                    nz(rt.getAmenitiesEn()))) + " ";
        }

        private String dm(LocalDate d) {
            return d.format(DATE_FMT);
        }

        private String criteriaText(ChatRoomQuery rq, Integer guests) {
            List<String> parts = new ArrayList<>();
            if (rq.hasDates()) {
                parts.add(t("chat.find.dates", dm(rq.checkIn()), dm(rq.checkOut()), rq.nights()));
            }
            if (guests != null) {
                parts.add(t("chat.find.guests", guests));
            }
            rq.features().forEach(f -> parts.add(featureLabel(f)));
            if (rq.maxPrice() != null) {
                parts.add(t("chat.find.budget", money(rq.maxPrice())));
            }
            if (rq.cheapest() && rq.maxPrice() == null) {
                parts.add(t("chat.find.cheap"));
            }
            return parts.isEmpty() ? "" : String.join(" · ", parts);
        }

        // Phong re nhat cua loai (co ngay: chi phong con trong ca ky o)
        private Candidate candidate(RoomType rt, ChatRoomQuery rq, LocalDate in, LocalDate out, Integer guests, int score) {
            List<Room> rooms = in != null
                    ? roomRepository.findAvailableRooms(in, out, rt.getId(), guests)
                    : roomRepository.findByRoomTypeId(rt.getId()).stream()
                    .filter(r -> r.getStatus() != com.hotel.entity.RoomStatus.MAINTENANCE).toList();
            Room best = rooms.stream().filter(r -> r.getPrice() != null)
                    .min(java.util.Comparator.comparing(Room::getPrice)).orElse(null);
            if (best == null) {
                return null;
            }
            BigDecimal total = null;
            if (in != null) {
                total = pricingService.nightlyPrices(best.getPrice(), in, out).stream()
                        .map(PricingService.NightPrice::price).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            return new Candidate(rt, best, score, total, rooms.size());
        }

        private RoomCard card(Candidate c, ChatRoomQuery rq, LocalDate in, LocalDate out, Integer guests) {
            RoomType rt = c.type();
            String text = roomText(rt);
            List<String> badges = new ArrayList<>();
            for (ChatRoomQuery.Feature f : rq.features()) {
                if (f.matchesRoom(text, rt.getMaxGuests() == null ? 0 : rt.getMaxGuests())) {
                    badges.add(featureLabel(f));
                }
            }
            if (badges.isEmpty()) {
                String amen = pick(rt.getAmenities(), rt.getAmenitiesEn());
                if (amen != null) {
                    java.util.Arrays.stream(amen.split(",")).map(String::trim).filter(x -> !x.isEmpty()).limit(2).forEach(badges::add);
                }
            }
            String price = in != null ? t("chat.card.night", money(c.room().getPrice())) : t("chat.card.from", money(c.room().getPrice()));
            String detail = in != null
                    ? t("chat.card.total", (int) java.time.temporal.ChronoUnit.DAYS.between(in, out), money(c.total())) + " · " + t("chat.card.left", c.free())
                    : amenitiesShort(rt);
            StringBuilder url = new StringBuilder("/customer/rooms/").append(c.room().getId());
            List<String> params = new ArrayList<>();
            if (in != null) {
                params.add("checkIn=" + in);
                params.add("checkOut=" + out);
            }
            if (guests != null) {
                params.add("guests=" + guests);
            }
            if (!params.isEmpty()) {
                url.append('?').append(String.join("&", params));
            }
            String subtitle = t("chat.card.sub", c.room().getRoomNumber(), rt.getMaxGuests());
            return new RoomCard(c.room().getId(), pick(rt.getName(), rt.getNameEn()), subtitle, c.room().getCoverImageUrl(), price, detail,
                    badges.stream().limit(3).toList(), url.toString());
        }

        private String amenitiesShort(RoomType rt) {
            String amen = pick(rt.getAmenities(), rt.getAmenitiesEn());
            if (amen == null || amen.isBlank()) {
                return t("chat.card.guests", rt.getMaxGuests());
            }
            return java.util.Arrays.stream(amen.split(",")).map(String::trim).filter(x -> !x.isEmpty()).limit(3)
                    .collect(java.util.stream.Collectors.joining(" · "));
        }

        private record Finder(String text, List<RoomCard> cards, List<String> typeNames, List<Action> actions, List<String> suggestions) {
        }

        private Finder findRooms(ChatRoomQuery rq, List<RoomType> named) {
            LocalDate in = rq.checkIn();
            LocalDate out = rq.checkOut();
            Integer guests = rq.guests();
            List<RoomType> all = roomTypeRepository.findAll();
            int maxCap = all.stream().map(RoomType::getMaxGuests).filter(java.util.Objects::nonNull).max(Integer::compare).orElse(0);
            List<String> notes = new ArrayList<>();
            List<String> sugg = new ArrayList<>();
            List<Action> acts = new ArrayList<>();
            Integer capGuests = guests;
            if (guests != null && maxCap > 0 && guests > maxCap) {
                notes.add(t("chat.find.tooMany", maxCap, guests));
                capGuests = null;
            }
            final Integer cap = capGuests;
            // Diem: so dac diem khop; khach goi ten loai phong thi chi xet loai do
            List<RoomType> pool = (named.isEmpty() ? all : named).stream()
                    .filter(rt -> cap == null || rt.getMaxGuests() == null || rt.getMaxGuests() >= cap).toList();
            java.util.Map<RoomType, Integer> score = new java.util.HashMap<>();
            for (RoomType rt : pool) {
                String text = roomText(rt);
                int sc = 0;
                for (ChatRoomQuery.Feature f : rq.features()) {
                    if (f.matchesRoom(text, rt.getMaxGuests() == null ? 0 : rt.getMaxGuests())) {
                        sc++;
                    }
                }
                score.put(rt, sc);
            }
            List<RoomType> matching = pool.stream().filter(rt -> rq.features().isEmpty() || score.get(rt) > 0).toList();
            String crit = criteriaText(rq, cap);
            if (matching.isEmpty() && !rq.features().isEmpty()) {
                notes.add(t("chat.find.noMatch", rq.features().stream().map(this::featureLabel).collect(java.util.stream.Collectors.joining(", "))));
                matching = pool;
            }
            java.util.Comparator<Candidate> order = java.util.Comparator.comparingInt((Candidate c) -> -c.score())
                    .thenComparing(c -> c.room().getPrice());
            List<Candidate> found = new ArrayList<>();
            for (RoomType rt : matching) {
                Candidate c = candidate(rt, rq, in, out, cap, score.getOrDefault(rt, 0));
                if (c != null) {
                    found.add(c);
                }
            }
            List<Candidate> inBudget = rq.maxPrice() == null ? found
                    : found.stream().filter(c -> c.room().getPrice().compareTo(rq.maxPrice()) <= 0).toList();
            if (rq.maxPrice() != null && inBudget.isEmpty() && !found.isEmpty()) {
                notes.add(t("chat.find.overBudget", money(rq.maxPrice())));
                order = java.util.Comparator.comparing((Candidate c) -> c.room().getPrice());
                inBudget = found;
            } else if (rq.cheapest() && rq.features().isEmpty()) {
                order = java.util.Comparator.comparing((Candidate c) -> c.room().getPrice());
            }
            List<Candidate> top = inBudget.stream().sorted(order).limit(4).toList();

            StringBuilder text = new StringBuilder();
            if (!top.isEmpty()) {
                text.append(in != null ? t("chat.find.found", top.size()) : t("chat.find.foundNoDates", top.size()));
            } else if (in != null) {
                // Dung y nhung kin ngay: goi y phong khac con trong + ngay gan nhat loai hop nhat con trong
                notes.add(t("chat.find.full", crit));
                RoomType bestType = matching.stream().max(java.util.Comparator.comparingInt(rt -> score.getOrDefault(rt, 0))).orElse(null);
                if (bestType != null) {
                    int nights = rq.nights();
                    for (int shift = 1; shift <= 14; shift++) {
                        LocalDate a = in.plusDays(shift);
                        Candidate c = candidate(bestType, rq, a, a.plusDays(nights), cap, score.getOrDefault(bestType, 0));
                        if (c != null) {
                            notes.add(t("chat.find.nextFree", pick(bestType.getName(), bestType.getNameEn()), dm(a), dm(a.plusDays(nights))));
                            top = List.of(c);
                            in = a;
                            out = a.plusDays(nights);
                            break;
                        }
                    }
                }
                if (top.isEmpty()) {
                    List<Candidate> others = new ArrayList<>();
                    for (RoomType rt : pool) {
                        if (!matching.contains(rt)) {
                            Candidate c = candidate(rt, rq, rq.checkIn(), rq.checkOut(), cap, 0);
                            if (c != null) {
                                others.add(c);
                            }
                        }
                    }
                    if (!others.isEmpty()) {
                        notes.add(t("chat.find.alternatives"));
                        top = others.stream().sorted(java.util.Comparator.comparing((Candidate c) -> c.room().getPrice())).limit(3).toList();
                        in = rq.checkIn();
                        out = rq.checkOut();
                    } else {
                        notes.add(t("chat.find.none"));
                        acts.add(action("chat.act.zalo", "https://zalo.me/" + HOTEL_ZALO));
                    }
                }
            } else {
                notes.add(t("chat.find.none"));
            }
            final LocalDate cin = in;
            final LocalDate cout = out;
            List<RoomCard> cards = top.stream().map(c -> card(c, rq, cin, cout, cap)).toList();
            List<String> parts = new ArrayList<>();
            if (!crit.isEmpty()) {
                parts.add(t("chat.find.criteria", crit));
            }
            parts.addAll(notes);
            if (text.length() > 0) {
                parts.add(text.toString());
            }
            if (!cards.isEmpty()) {
                parts.add(t("chat.find.tapHint"));
            }
            if (!rq.hasDates()) {
                parts.add(t("chat.find.askDates"));
                LocalDate sat = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY));
                String feat = rq.features().isEmpty() ? t("chat.find.sgRoom") : featureLabel(rq.features().iterator().next());
                sugg.add(t("chat.find.sgDates", feat, sat.getDayOfMonth() + "/" + sat.getMonthValue(),
                        sat.plusDays(1).getDayOfMonth() + "/" + sat.plusDays(1).getMonthValue(), cap != null ? cap : 2));
            } else {
                sugg.add(t("chat.sg.deposit"));
            }
            if (rq.features().isEmpty() || !rq.features().contains(ChatRoomQuery.Feature.SEA)) {
                sugg.add(t("chat.find.sgSea"));
            }
            acts.add(action("chat.act.rooms", "/rooms"));
            return new Finder(String.join("\n", parts), cards, top.stream().map(c -> c.type().getName()).toList(), acts, sugg);
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
            return Money.format(amount);
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

        private String buildTourAnswer() {
            List<DivingTour> tours = divingTourRepository.findByActiveTrueOrderByPricePerPersonAsc();
            if (tours.isEmpty()) {
                return t("chat.tour.none");
            }
            StringBuilder sb = new StringBuilder(t("chat.tour.title"));
            for (DivingTour tour : tours) {
                sb.append("\n").append(t("chat.tour.line", pick(tour.getName(), tour.getNameEn()),
                        money(tour.getPricePerPerson()), tour.getDepartureTime().toString(), tour.getDurationHours()));
            }
            sb.append("\n").append(t("chat.tour.how", ServiceOrderService.TOUR_LEAD_HOURS));
            return sb.toString();
        }

        // Moi san bay 1 dong: gia 4 cho / 7 cho / 16 cho (bo qua san bay chua du gia)
        private String buildTransferAnswer() {
            Map<Airport, Map<VehicleType, BigDecimal>> table = new EnumMap<>(Airport.class);
            for (TransferRate rate : transferRateRepository.findAll()) {
                table.computeIfAbsent(rate.getAirport(), a -> new EnumMap<>(VehicleType.class))
                        .put(rate.getVehicleType(), rate.getPrice());
            }
            StringBuilder sb = new StringBuilder(t("chat.transfer.title"));
            for (Airport airport : Airport.values()) {
                Map<VehicleType, BigDecimal> prices = table.getOrDefault(airport, Map.of());
                if (prices.size() < VehicleType.values().length) {
                    continue;
                }
                sb.append("\n").append(t("chat.transfer.line", t("airport." + airport.name()),
                        money(prices.get(VehicleType.CAR_4)), money(prices.get(VehicleType.CAR_7)),
                        money(prices.get(VehicleType.VAN_16))));
            }
            sb.append("\n").append(t("chat.transfer.how", ServiceOrderService.TRANSFER_LEAD_HOURS));
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
                    .filter(c -> discountCodeService == null || discountCodeService.isAvailableNow(c))
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

    // Tu trong ten loai phong qua chung (dac diem / tu thuong gap) - khong dung de nhan ra loai phong
    private static final Set<String> GENERIC_NAME_WORDS = Set.of("phong", "room", "view", "huong", "bien", "vuon", "dao", "vinh",
            "pho", "gia", "dinh", "boi", "giuong", "sat", "toan", "canh", "hoang", "hon", "sea", "garden", "pool", "family",
            "city", "island", "bay", "beach", "ocean", "double", "twin", "bed", "with");

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private boolean nameMatches(String normalizedQuestion, String name) {
        String normName = normalize(name);
        if (normalizedQuestion.contains(normName)) {
            return true;
        }
        for (String word : normName.trim().split("\\s+")) {
            if (word.length() >= 3 && !GENERIC_NAME_WORDS.contains(word)
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
