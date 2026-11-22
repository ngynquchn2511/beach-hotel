package com.hotel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// Du bao thoi tiet Ha Long 16 ngay tu Open-Meteo (mien phi, khong can khoa, chi gui toa do resort - khong gui thong tin khach).
// Luu tam 30 phut; loi mang thi dung ban cu (toi da 6 tieng) hoac an khung thoi tiet, khong lam hong trang
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    static final Duration FRESH = Duration.ofMinutes(30);
    static final Duration STALE_OK = Duration.ofHours(6);
    static final Duration RETRY_AFTER_ERROR = Duration.ofMinutes(5);
    static final String URL = "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,uv_index_max,sunrise,sunset"
            + "&hourly=temperature_2m,weather_code,precipitation_probability,wind_speed_10m"
            + "&timezone=Asia%%2FBangkok&forecast_days=16";

    private volatile boolean enabled;
    private final String latitude;
    private final String longitude;
    private final ObjectMapper mapper = new ObjectMapper();
    private Function<URI, String> fetcher;

    private volatile List<Day> cached = List.of();
    private volatile Instant fetchedAt;
    private volatile Instant lastError;

    public WeatherService(@Value("${app.weather.enabled:true}") boolean enabled,
                          @Value("${app.weather.latitude:20.95}") String latitude,
                          @Value("${app.weather.longitude:107.08}") String longitude) {
        this.enabled = enabled;
        this.latitude = latitude;
        this.longitude = longitude;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        this.fetcher = uri -> {
            try {
                HttpResponse<String> res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new IllegalStateException("HTTP " + res.statusCode());
                }
                return res.body();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
            }
        };
    }

    // Test thay nguon du lieu (null = tat han)
    public void useFetcherForTest(Function<URI, String> fetcher) {
        this.enabled = fetcher != null;
        if (fetcher != null) {
            this.fetcher = fetcher;
        }
        this.cached = List.of();
        this.fetchedAt = null;
        this.lastError = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // Cac ngay du bao tu hom nay (rong neu tat / chua lay duoc)
    public List<Day> forecast() {
        if (!enabled) {
            return List.of();
        }
        Instant now = Instant.now();
        boolean fresh = fetchedAt != null && Duration.between(fetchedAt, now).compareTo(FRESH) < 0;
        boolean waitAfterError = lastError != null && Duration.between(lastError, now).compareTo(RETRY_AFTER_ERROR) < 0;
        if (!fresh && !waitAfterError) {
            synchronized (this) {
                if (fetchedAt == null || Duration.between(fetchedAt, Instant.now()).compareTo(FRESH) >= 0) {
                    try {
                        cached = parse(fetcher.apply(URI.create(String.format(URL, latitude, longitude))));
                        fetchedAt = Instant.now();
                        lastError = null;
                    } catch (RuntimeException ex) {
                        lastError = Instant.now();
                        log.warn("Khong lay duoc du bao thoi tiet: {}", ex.getMessage());
                    }
                }
            }
        }
        if (fetchedAt == null || Duration.between(fetchedAt, now).compareTo(STALE_OK) > 0) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        return cached.stream().filter(d -> !d.date().isBefore(today)).toList();
    }

    public List<Day> next(int days) {
        return forecast().stream().limit(days).toList();
    }

    // Du bao cho khoang ngay o (chi phan nam trong 16 ngay toi)
    public List<Day> between(LocalDate from, LocalDate toExclusive) {
        if (from == null || toExclusive == null) {
            return List.of();
        }
        return forecast().stream().filter(d -> !d.date().isBefore(from) && d.date().isBefore(toExclusive)).toList();
    }

    public Optional<Day> on(LocalDate day) {
        return forecast().stream().filter(d -> d.date().equals(day)).findFirst();
    }

    List<Day> parse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode daily = root.path("daily");
            JsonNode time = daily.path("time");
            Map<LocalDate, List<Hour>> hoursByDay = parseHours(root.path("hourly"));
            List<Day> out = new ArrayList<>();
            for (int i = 0; i < time.size(); i++) {
                LocalDate date = LocalDate.parse(time.get(i).asText());
                JsonNode uv = daily.path("uv_index_max").path(i);
                out.add(new Day(date, daily.path("weather_code").path(i).asInt(-1),
                        (int) Math.round(daily.path("temperature_2m_max").path(i).asDouble()),
                        (int) Math.round(daily.path("temperature_2m_min").path(i).asDouble()),
                        daily.path("precipitation_probability_max").path(i).isNull() ? null
                                : daily.path("precipitation_probability_max").path(i).asInt(),
                        (int) Math.round(daily.path("wind_speed_10m_max").path(i).asDouble()),
                        uv.isMissingNode() || uv.isNull() ? null : (int) Math.round(uv.asDouble()),
                        timeOf(daily.path("sunrise").path(i)), timeOf(daily.path("sunset").path(i)),
                        hoursByDay.getOrDefault(date, List.of())));
            }
            if (out.isEmpty()) {
                throw new IllegalStateException("Du lieu thoi tiet rong");
            }
            return out;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static LocalTime timeOf(JsonNode n) {
        if (n.isMissingNode() || n.isNull() || n.asText().length() < 16) {
            return null;
        }
        return LocalDateTime.parse(n.asText()).toLocalTime();
    }

    private static Map<LocalDate, List<Hour>> parseHours(JsonNode hourly) {
        Map<LocalDate, List<Hour>> out = new HashMap<>();
        JsonNode time = hourly.path("time");
        for (int i = 0; i < time.size(); i++) {
            LocalDateTime t = LocalDateTime.parse(time.get(i).asText());
            JsonNode rain = hourly.path("precipitation_probability").path(i);
            out.computeIfAbsent(t.toLocalDate(), k -> new ArrayList<>()).add(new Hour(t.getHour(),
                    hourly.path("weather_code").path(i).asInt(-1),
                    (int) Math.round(hourly.path("temperature_2m").path(i).asDouble()),
                    rain.isMissingNode() || rain.isNull() ? null : rain.asInt(),
                    (int) Math.round(hourly.path("wind_speed_10m").path(i).asDouble())));
        }
        return out;
    }

    // Ma thoi tiet WMO -> nhom (dung cho chu song ngu weather.kind.* va bieu tuong)
    public enum Kind {
        CLEAR("bi-sun"), PARTLY("bi-cloud-sun"), CLOUDY("bi-clouds"), FOG("bi-cloud-fog2"), DRIZZLE("bi-cloud-drizzle"),
        RAIN("bi-cloud-rain-heavy"), STORM("bi-cloud-lightning-rain"), UNKNOWN("bi-question-circle");

        private final String icon;

        Kind(String icon) {
            this.icon = icon;
        }

        public String getIcon() {
            return icon;
        }

        public static Kind of(int code) {
            if (code == 0) return CLEAR;
            if (code == 1 || code == 2) return PARTLY;
            if (code == 3) return CLOUDY;
            if (code == 45 || code == 48) return FOG;
            if (code >= 51 && code <= 57) return DRIZZLE;
            if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
            if (code >= 95) return STORM;
            return UNKNOWN;
        }
    }

    // Mot gio trong ngay (du bao theo gio)
    public record Hour(int hour, int code, int temp, Integer rainChance, int windKmh) {
        public Kind getKind() {
            return Kind.of(code);
        }

        public String getLabel() {
            return String.format("%02d:00", hour);
        }

        // Diem "dep troi" de chon gio dep nhat: nang > may > mua, it mua, 24-29 do, gio nhe
        int score() {
            int s = switch (getKind()) {
                case CLEAR -> 30;
                case PARTLY -> 28;
                case CLOUDY -> 18;
                case FOG -> 8;
                case DRIZZLE -> 5;
                case RAIN -> 0;
                case STORM -> -20;
                case UNKNOWN -> 10;
            };
            s -= (rainChance == null ? 0 : rainChance) * 3 / 10;
            s -= Math.max(0, Math.max(24 - temp, temp - 29)) * 3 / 2;
            s -= Math.max(0, windKmh - 25);
            return s;
        }
    }

    // Mot buoi trong ngay (weather.period.<key>)
    public record Period(String key, Kind kind, int minTemp, int maxTemp, Integer rainChance, int windKmh) {
    }

    // Goi y nen lam gi: chu song ngu weather.tip.<key>, arg = tham so (vd gio hoang hon)
    public record Tip(String key, String icon, String arg) {
    }

    public record Day(LocalDate date, int code, int maxTemp, int minTemp, Integer rainChance, int windKmh,
                      Integer uvMax, LocalTime sunrise, LocalTime sunset, List<Hour> hours) {

        public Day(LocalDate date, int code, int maxTemp, int minTemp, Integer rainChance, int windKmh) {
            this(date, code, maxTemp, minTemp, rainChance, windKmh, null, null, null, List.of());
        }

        public Kind getKind() {
            return Kind.of(code);
        }

        public boolean isToday() {
            return date.equals(LocalDate.now());
        }

        // Cac gio ban ngay (6h - 21h); hom nay chi tinh tu gio hien tai tro di
        public List<Hour> getDayHours() {
            int from = Math.max(6, isToday() ? LocalTime.now().getHour() : 0);
            return hours.stream().filter(x -> x.hour() >= from && x.hour() <= 21).toList();
        }

        // Du 24 gio cua ngay (0h - 23h, sap theo gio) cho bang chi tiet; gio da qua cua hom nay van giu (to mo)
        public List<Hour> getAllHours() {
            return hours.stream().sorted(java.util.Comparator.comparingInt(Hour::hour)).toList();
        }

        public boolean isPast(Hour h) {
            return isToday() && h.hour() < LocalTime.now().getHour();
        }

        // Tom tat 4 buoi: dem (0-5), sang (6-11), chieu (12-17), toi (18-23): troi chu yeu, nhiet do thap - cao, % mua cao nhat
        public List<Period> getPeriods() {
            List<Period> out = new ArrayList<>();
            String[] keys = {"night", "morning", "afternoon", "evening"};
            for (int p = 0; p < 4; p++) {
                int from = p * 6;
                List<Hour> part = hours.stream().filter(x -> x.hour() >= from && x.hour() < from + 6).toList();
                if (part.isEmpty()) {
                    continue;
                }
                // Troi "xau" nhat trong buoi neu co mua / giong, khong thi troi gap nhieu nhat
                Map<Kind, Integer> count = new HashMap<>();
                part.forEach(x -> count.merge(x.getKind(), 1, Integer::sum));
                Kind main = count.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
                for (Kind bad : List.of(Kind.STORM, Kind.RAIN)) {
                    if (count.containsKey(bad) && main != bad && count.get(bad) >= 2) {
                        main = bad;
                        break;
                    }
                }
                Integer rain = part.stream().map(Hour::rainChance).filter(java.util.Objects::nonNull).max(Integer::compare).orElse(null);
                out.add(new Period(keys[p], main, part.stream().mapToInt(Hour::temp).min().orElse(0),
                        part.stream().mapToInt(Hour::temp).max().orElse(0), rain,
                        part.stream().mapToInt(Hour::windKmh).max().orElse(0)));
            }
            return out;
        }

        // Gio dep troi nhat trong khung [from, to] (hom nay bo gio da qua); null neu khong co
        public Hour bestHourBetween(int from, int to) {
            Hour best = null;
            for (Hour h : hours) {
                if (h.hour() >= from && h.hour() <= to && !isPast(h) && (best == null || h.score() > best.score())) {
                    best = h;
                }
            }
            return best;
        }

        // Moc 3 tieng mot (6, 9, 12, 15, 18, 21) cho bang theo gio
        public List<Hour> getHourSteps() {
            return getDayHours().stream().filter(x -> x.hour() % 3 == 0).toList();
        }

        // Gio dep troi nhat luc con sang (sau binh minh, truoc hoang hon; mac dinh 7h - 17h); null neu khong co du lieu / da toi
        public Hour getBestHour() {
            int first = sunrise == null ? 7 : sunrise.getHour() + 1;
            int last = sunset == null ? 17 : sunset.getHour() - 1;
            Hour best = null;
            for (Hour h : getDayHours()) {
                if (h.hour() >= first && h.hour() <= last && (best == null || h.score() > best.score())) {
                    best = h;
                }
            }
            return best;
        }

        public String getSunsetLabel() {
            return sunset == null ? null : String.format("%02d:%02d", sunset.getHour(), sunset.getMinute());
        }

        public String getSunriseLabel() {
            return sunrise == null ? null : String.format("%02d:%02d", sunrise.getHour(), sunrise.getMinute());
        }

        // Goi y nen lam gi hop thoi tiet ngay nay (toi da 4)
        public List<Tip> getTips() {
            List<Tip> tips = new ArrayList<>();
            Kind k = getKind();
            boolean sunny = k == Kind.CLEAR || k == Kind.PARTLY;
            boolean wet = k == Kind.RAIN || k == Kind.DRIZZLE || (rainChance != null && rainChance >= 60);
            if (isRough()) {
                tips.add(new Tip("noSea", "bi-exclamation-triangle", null));
                tips.add(new Tip("stayIn", "bi-flower1", null));
            } else if (wet) {
                tips.add(new Tip("rain", "bi-umbrella", null));
            } else if (isBeachDay()) {
                tips.add(new Tip("beach", "bi-water", null));
                tips.add(new Tip("cruise", "bi-tsunami", null));
            } else if (maxTemp >= 18) {
                tips.add(new Tip("walk", "bi-signpost-split", null));
                tips.add(new Tip("cruise", "bi-tsunami", null));
            }
            if (k == Kind.FOG) {
                tips.add(new Tip("fog", "bi-cloud-fog2", null));
            }
            if (maxTemp >= 33) {
                tips.add(new Tip("hot", "bi-thermometer-sun", null));
            } else if (uvMax != null && uvMax >= 8 && sunny) {
                tips.add(new Tip("uv", "bi-brightness-high", null));
            }
            if (minTemp <= 17) {
                tips.add(new Tip("cold", "bi-snow", null));
            }
            if (sunny && !isRough() && sunset != null) {
                tips.add(new Tip("sunset", "bi-sunset", getSunsetLabel()));
            }
            if (tips.isEmpty()) {
                tips.add(new Tip("general", "bi-compass", null));
            }
            return tips.stream().limit(4).toList();
        }

        // Gio manh (>= 40 km/h) hoac giong bao -> nhac khach / le tan (tour bien, du thuyen co the hoan)
        public boolean isRough() {
            return windKmh >= 40 || getKind() == Kind.STORM;
        }

        public boolean isBeachDay() {
            return (getKind() == Kind.CLEAR || getKind() == Kind.PARTLY) && maxTemp >= 26 && !isRough();
        }
    }
}
