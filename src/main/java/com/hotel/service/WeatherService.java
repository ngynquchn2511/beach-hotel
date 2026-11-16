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
import java.util.ArrayList;
import java.util.List;
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
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max"
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
            JsonNode daily = mapper.readTree(json).path("daily");
            JsonNode time = daily.path("time");
            List<Day> out = new ArrayList<>();
            for (int i = 0; i < time.size(); i++) {
                out.add(new Day(LocalDate.parse(time.get(i).asText()), daily.path("weather_code").path(i).asInt(-1),
                        (int) Math.round(daily.path("temperature_2m_max").path(i).asDouble()),
                        (int) Math.round(daily.path("temperature_2m_min").path(i).asDouble()),
                        daily.path("precipitation_probability_max").path(i).isNull() ? null
                                : daily.path("precipitation_probability_max").path(i).asInt(),
                        (int) Math.round(daily.path("wind_speed_10m_max").path(i).asDouble())));
            }
            if (out.isEmpty()) {
                throw new IllegalStateException("Du lieu thoi tiet rong");
            }
            return out;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
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

    public record Day(LocalDate date, int code, int maxTemp, int minTemp, Integer rainChance, int windKmh) {
        public Kind getKind() {
            return Kind.of(code);
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
