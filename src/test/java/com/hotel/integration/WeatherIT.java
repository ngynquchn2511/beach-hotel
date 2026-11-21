package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.ChatbotService;
import com.hotel.service.WeatherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Du bao thoi tiet (Open-Meteo gia): doc JSON, luu tam, loi mang thi an khung; hien o trang chu / chi tiet phong / Hom nay / chatbot
class WeatherIT extends ItFixtures {

    @Autowired private WeatherService weather;
    @Autowired private ChatbotService chatbot;

    static String json(LocalDate start) {
        StringBuilder t = new StringBuilder();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            t.append(i == 0 ? "" : ",").append('"').append(start.plusDays(i)).append('"');
            code.append(i == 0 ? "" : ",").append(i == 0 ? 95 : i % 2 == 0 ? 0 : 61);
        }
        String n16 = "28,".repeat(15) + "28";
        return "{\"daily\":{\"time\":[" + t + "],\"weather_code\":[" + code + "],\"temperature_2m_max\":[" + n16
                + "],\"temperature_2m_min\":[" + "22.4,".repeat(15) + "22.4" + "],\"precipitation_probability_max\":[" + "40,".repeat(15) + "40"
                + "],\"wind_speed_10m_max\":[45" + ",12".repeat(15) + "],\"sunset\":[" + ("\"" + start + "T17:21\",").repeat(15)
                + "\"" + start + "T17:21\"]},\"hourly\":" + hourly(start) + "}";
    }

    // Theo gio: 9h nang, cac gio khac nhieu may -> gio dep nhat la 09:00
    static String hourly(LocalDate start) {
        StringBuilder t = new StringBuilder();
        StringBuilder code = new StringBuilder();
        StringBuilder other = new StringBuilder();
        for (int d = 0; d < 16; d++) {
            for (int h = 0; h < 24; h++) {
                String sep = t.isEmpty() ? "" : ",";
                t.append(sep).append('"').append(start.plusDays(d)).append(String.format("T%02d:00", h)).append('"');
                code.append(sep).append(h == 9 ? 0 : 3);
                other.append(sep).append(h == 9 ? 27 : 30);
            }
        }
        return "{\"time\":[" + t + "],\"weather_code\":[" + code + "],\"temperature_2m\":[" + other
                + "],\"precipitation_probability\":[" + other + "],\"wind_speed_10m\":[" + other + "]}";
    }

    @AfterEach
    void off() {
        weather.useFetcherForTest(null);
    }

    @Test
    void parsesCaches_andHidesOnError() {
        AtomicInteger calls = new AtomicInteger();
        LocalDate today = LocalDate.now();
        weather.useFetcherForTest(uri -> {
            calls.incrementAndGet();
            assertThat(uri.toString()).contains("latitude=20.95").contains("forecast_days=16");
            return json(today);
        });
        assertThat(weather.next(7)).hasSize(7);
        WeatherService.Day d0 = weather.next(1).get(0);
        assertThat(d0.getKind()).isEqualTo(WeatherService.Kind.STORM);
        assertThat(d0.isRough()).isTrue();
        assertThat(d0.minTemp()).isEqualTo(22);
        assertThat(weather.on(today.plusDays(2)).orElseThrow().getKind()).isEqualTo(WeatherService.Kind.CLEAR);
        assertThat(weather.between(today.plusDays(3), today.plusDays(5))).hasSize(2);
        // Chi tiet ngay: gio dep nhat, goi y nen lam gi
        WeatherService.Day d2 = weather.on(today.plusDays(2)).orElseThrow();
        assertThat(d2.getBestHour().hour()).isEqualTo(9);
        assertThat(d2.getHourSteps()).extracting(WeatherService.Hour::hour).containsExactly(6, 9, 12, 15, 18, 21);
        assertThat(d2.getTips()).extracting(WeatherService.Tip::key).contains("beach", "sunset");
        assertThat(d0.getTips()).extracting(WeatherService.Tip::key).contains("noSea").doesNotContain("beach");
        assertThat(weather.on(today.plusDays(1)).orElseThrow().getTips()).extracting(WeatherService.Tip::key).contains("rain");
        weather.forecast();
        assertThat(calls.get()).isEqualTo(1);

        weather.useFetcherForTest(uri -> {
            throw new IllegalStateException("mat mang");
        });
        assertThat(weather.forecast()).isEmpty();
    }

    @Test
    void pagesAndChatbotShowForecast() throws Exception {
        LocalDate today = LocalDate.now();
        weather.useFetcherForTest(uri -> json(today));

        mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Dự báo thời tiết Hạ Long")))
                .andExpect(content().string(containsString("Giông")))
                .andExpect(content().string(containsString("Hôm nay")))
                .andExpect(content().string(containsString("Đẹp trời nhất lúc 09:00")))
                .andExpect(content().string(containsString("Ngắm hoàng hôn lúc 17:21")))
                .andExpect(content().string(containsString("nên hoãn du thuyền")));

        Room room = persistRoom(persistRoomType("Hang Thoi Tiet IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        mockMvc.perform(get("/customer/rooms/" + room.getId()).param("checkIn", today.plusDays(2).toString())
                        .param("checkOut", today.plusDays(4).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thời tiết những ngày bạn ở")));

        mockMvc.perform(get("/admin/today").with(as(persistUser(UserRole.STAFF, null)))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hôm nay gió mạnh")));

        // Trang Goi y phong cua khach: 7 ngay tinh ca hom nay (thu + ngay/thang) + canh bao gio manh, ca truoc va sau khi tra loi
        mockMvc.perform(get("/rooms/suggest").with(as(persistUser(UserRole.CUSTOMER, CustomerType.NEW))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dự báo thời tiết Hạ Long")))
                .andExpect(content().string(containsString(dow(today.plusDays(6)))))
                .andExpect(content().string(containsString(today.plusDays(6).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")))))
                .andExpect(content().string(containsString("weather-rough")));
        mockMvc.perform(get("/rooms/suggest").param("guests", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dự báo thời tiết Hạ Long")))
                .andExpect(content().string(containsString("Thời tiết này nên làm gì?")));

        assertThat(chatbot.answer("thời tiết mấy hôm tới thế nào")).contains("Dự báo Hạ Long").contains("Giông");
        assertThat(chatbot.reply("nên chọn phòng nào cho gia đình").actions()).anyMatch(a -> a.url().equals("/rooms/suggest"));

        // Tat -> khong co khung
        weather.useFetcherForTest(null);
        mockMvc.perform(get("/")).andExpect(content().string(not(containsString("Dự báo thời tiết Hạ Long"))));
    }

    static String dow(LocalDate d) {
        return d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? "Chủ nhật" : "Thứ " + (d.getDayOfWeek().getValue() + 1);
    }
}
