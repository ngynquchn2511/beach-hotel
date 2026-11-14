package com.hotel.service;

import com.hotel.entity.Booking;
import jakarta.persistence.PrePersist;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;

// Gan ma don khi luu don moi - moi noi tao don (web, tai quay, OTA, doan, demo) deu di qua day.
// Hibernate tao listener qua Spring (SpringBeanContainer) nen nhan duoc bean; context khong co bean thi bo qua
public class BookingCodeListener {

    private final ObjectProvider<BookingCodeGenerator> generator;

    public BookingCodeListener(ObjectProvider<BookingCodeGenerator> generator) {
        this.generator = generator;
    }

    @PrePersist
    void assignCode(Booking booking) {
        if (booking.getBookingCode() == null) {
            generator.ifAvailable(g -> booking.setBookingCode(g.next(LocalDate.now())));
        }
    }
}
