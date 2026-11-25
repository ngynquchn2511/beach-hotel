package com.hotel.unit;

import com.hotel.service.ChatRoomQuery;
import com.hotel.service.ChatRoomQuery.Feature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Doc cau khach nhan Song khi tim phong: dac diem, ngay, so nguoi, ngan sach (hom nay co dinh 20/11/2026, thu 6)
class ChatRoomQueryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private static ChatRoomQuery q(String s) {
        return ChatRoomQuery.parse(s, TODAY);
    }

    @Test
    void rangeWithoutMonth_isThisMonthOrNext() {
        ChatRoomQuery r = q("tôi muốn đặt 1 phòng ngày 25 - 27 view biển");
        assertThat(r.checkIn()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(r.checkOut()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(r.features()).containsExactly(Feature.SEA);
        assertThat(r.bookingIntent()).isTrue();
        // Ngay da qua trong thang -> thang sau
        assertThat(q("ngay 5-7 nhe").checkIn()).isEqualTo(LocalDate.of(2026, 12, 5));
        // Qua cuoi thang: 30 -> 2
        ChatRoomQuery cross = q("từ 30 đến 2");
        assertThat(cross.checkIn()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(cross.checkOut()).isEqualTo(LocalDate.of(2026, 12, 2));
    }

    @Test
    void rangeWithMonth_andNextYear() {
        ChatRoomQuery r = q("Đặt phòng 30/12 - 2/1 cho 4 người");
        assertThat(r.checkIn()).isEqualTo(LocalDate.of(2026, 12, 30));
        assertThat(r.checkOut()).isEqualTo(LocalDate.of(2027, 1, 2));
        assertThat(r.guests()).isEqualTo(4);
        ChatRoomQuery m = q("ở từ 10 đến 12 tháng 2");
        assertThat(m.checkIn()).isEqualTo(LocalDate.of(2027, 2, 10));
        assertThat(m.checkOut()).isEqualTo(LocalDate.of(2027, 2, 12));
        assertThat(q("25-27/12").checkIn()).isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    void singleDateWithNights_andRelativeDays() {
        ChatRoomQuery r = q("check in ngày 28/11 ở 3 đêm");
        assertThat(r.checkIn()).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(r.checkOut()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(q("tối nay còn phòng không").checkIn()).isEqualTo(TODAY);
        assertThat(q("ngày mai còn phòng view vườn không").checkIn()).isEqualTo(TODAY.plusDays(1));
        ChatRoomQuery w = q("cuối tuần này 2 vợ chồng");
        assertThat(w.checkIn()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(w.checkOut()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(w.guests()).isEqualTo(2);
        // "nguoi lon 2" khong duoc doc thanh "on 2" (ngay 2)
        ChatRoomQuery f = q("gia đình 2 người lớn 2 trẻ em cuối tuần này");
        assertThat(f.checkIn()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(f.guests()).isEqualTo(4);
    }

    @Test
    void guestCountsAreNotDates() {
        ChatRoomQuery r = q("phòng cho 2-3 người");
        assertThat(r.checkIn()).isNull();
        assertThat(q("gia đình 2 người lớn 2 trẻ em").guests()).isEqualTo(4);
        assertThat(q("gia đình 2 người lớn 2 trẻ em").features()).contains(Feature.FAMILY);
        assertThat(q("giờ nhận phòng 14:00").checkIn()).isNull();
        assertThat(q("đặt một phòng").checkIn()).isNull();
    }

    @Test
    void featuresAndBudget() {
        assertThat(q("tôi muốn có view vườn").features()).containsExactly(Feature.GARDEN);
        assertThat(q("phòng có bồn tắm và ban công cho cặp đôi").features())
                .contains(Feature.BATHTUB, Feature.BALCONY, Feature.COUPLE);
        assertThat(q("muốn ngắm biển").features()).contains(Feature.SEA);
        assertThat(q("phòng dưới 1 triệu").maxPrice()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        assertThat(q("phòng dưới 800k").maxPrice()).isEqualByComparingTo(BigDecimal.valueOf(800_000));
        assertThat(q("tối đa 1.200.000").maxPrice()).isEqualByComparingTo(BigDecimal.valueOf(1_200_000));
        assertThat(q("phòng giá rẻ").cheapest()).isTrue();
        assertThat(q("giá phòng bao nhiêu").hasCriteria()).isFalse();
        assertThat(q("có cần đặt cọc không").hasCriteria()).isFalse();
    }
}
