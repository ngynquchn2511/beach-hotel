package com.hotel.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// Sinh ma don dang thang + ngay + so thu tu trong ngay, VD don dau tien ngay 14/11 la "11140001".
// Tang bo dem bang UPDATE trong cung transaction voi viec luu don: dong bo dem bi khoa den khi commit
// nen 2 don tao cung luc khong trung so, don luu loi thi so cung khong bi nhay
@Component
public class BookingCodeGenerator {

    private final JdbcTemplate jdbc;

    public BookingCodeGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next(LocalDate day) {
        return format(day, nextNumber(day));
    }

    // Qua 9999 don / ngay thi so thu tu dai them, khong cat bot
    public static String format(LocalDate day, int number) {
        return String.format("%02d%02d%04d", day.getMonthValue(), day.getDayOfMonth(), number);
    }

    private int nextNumber(LocalDate day) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (jdbc.update("update booking_code_counters set last_number = last_number + 1 where code_date = ?", day) > 0) {
                Integer n = jdbc.queryForObject("select last_number from booking_code_counters where code_date = ?", Integer.class, day);
                return n == null ? 1 : n;
            }
            try {
                jdbc.update("insert into booking_code_counters (code_date, last_number) values (?, 1)", day);
                return 1;
            } catch (DuplicateKeyException e) {
                // Don khac vua tao dong cua ngay nay - quay lai UPDATE
            }
        }
        throw new IllegalStateException("Không sinh được mã đơn cho ngày " + day);
    }
}
