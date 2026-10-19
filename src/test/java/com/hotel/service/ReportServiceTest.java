package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Bao cao chi tiet: kenh dat, ma giam gia, huy don, dich vu them - tinh theo ngay tao don, bo don huy
class ReportServiceTest {

    private BookingRepository bookingRepository;
    private BookingChargeRepository chargeRepository;
    private ReportService service;

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);
    private long ids = 1;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        chargeRepository = mock(BookingChargeRepository.class);
        service = new ReportService(bookingRepository, chargeRepository);
    }

    private Booking booking(String email, BookingChannel channel, BookingStatus status, long total, LocalDateTime createdAt) {
        User customer = User.builder().id(ids).email(email).build();
        return Booking.builder().id(ids++).customer(customer).channel(channel).status(status)
                .totalAmount(BigDecimal.valueOf(total)).discountAmount(BigDecimal.ZERO).createdAt(createdAt).build();
    }

    @Test
    void buildsChannelsDiscountsCancellationsAndServices() {
        LocalDateTime in = LocalDateTime.of(2026, 10, 10, 9, 0);
        DiscountCode sale = DiscountCode.builder().id(1L).code("SALE10").build();

        Booking online = booking("a@mail.com", BookingChannel.ONLINE, BookingStatus.CHECKED_OUT, 1_000_000, in);
        online.setDiscountCode(sale);
        online.setDiscountAmount(BigDecimal.valueOf(100_000));
        Booking walkIn = booking("0900@khachvanglai.local", BookingChannel.WALK_IN, BookingStatus.CONFIRMED, 500_000, in);
        // Don cu chua ghi kenh: email tam cua le tan -> tai quay; email that -> online
        Booking legacyWalkIn = booking("0911@khachvanglai.local", null, BookingStatus.CHECKED_OUT, 500_000, in);
        Booking legacyOnline = booking("b@mail.com", null, BookingStatus.PENDING, 1_000_000, in);
        Booking cancelled = booking("c@mail.com", BookingChannel.ONLINE, BookingStatus.CANCELLED, 2_000_000, in);
        cancelled.setDepositAmount(BigDecimal.valueOf(600_000));
        cancelled.setDepositPaidAt(in);
        cancelled.setRefundAmount(BigDecimal.valueOf(300_000));
        cancelled.setRefundPercent(50);
        // Ngoai khoang ngay -> bo qua
        Booking outside = booking("d@mail.com", BookingChannel.ONLINE, BookingStatus.CHECKED_OUT, 9_000_000, in.minusMonths(2));

        // Repository loc theo ngay tao: don "outside" khong nam trong ket qua
        when(bookingRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(online, walkIn, legacyWalkIn, legacyOnline, cancelled));
        List<BookingCharge> allCharges = List.of(
                BookingCharge.builder().booking(online).type(ChargeType.DIVING_TOUR).amount(BigDecimal.valueOf(400_000)).build(),
                BookingCharge.builder().booking(walkIn).type(ChargeType.MINIBAR).amount(BigDecimal.valueOf(100_000)).build(),
                // Phu phi cua don da huy / ngoai khoang -> khong tinh
                BookingCharge.builder().booking(cancelled).type(ChargeType.MINIBAR).amount(BigDecimal.valueOf(999_000)).build(),
                BookingCharge.builder().booking(outside).type(ChargeType.DIVING_TOUR).amount(BigDecimal.valueOf(999_000)).build());
        when(chargeRepository.findByBookingIdIn(any())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            return allCharges.stream().filter(c -> ids.contains(c.getBooking().getId())).toList();
        });

        ReportService.Report report = service.build(FROM, TO);

        // Online: 1.000.000 + 400.000 tour + 1.000.000 don cu = 2.400.000 (2 don); tai quay: 500.000 + 100.000 + 500.000
        ReportService.ChannelRow onlineRow = report.channels().get(0);
        ReportService.ChannelRow walkInRow = report.channels().get(1);
        assertThat(onlineRow.channel()).isEqualTo(BookingChannel.ONLINE);
        assertThat(onlineRow.bookings()).isEqualTo(2);
        assertThat(onlineRow.revenue()).isEqualByComparingTo("2400000");
        assertThat(walkInRow.bookings()).isEqualTo(2);
        assertThat(walkInRow.revenue()).isEqualByComparingTo("1100000");
        assertThat(onlineRow.share()).isEqualTo(68.6);
        assertThat(walkInRow.share()).isEqualTo(31.4);

        assertThat(report.discounts()).singleElement().satisfies(d -> {
            assertThat(d.code()).isEqualTo("SALE10");
            assertThat(d.uses()).isEqualTo(1);
            assertThat(d.discountTotal()).isEqualByComparingTo("100000");
            assertThat(d.revenue()).isEqualByComparingTo("1400000");
        });

        ReportService.CancellationStats cs = report.cancellations();
        assertThat(cs.created()).isEqualTo(5);
        assertThat(cs.cancelled()).isEqualTo(1);
        assertThat(cs.cancelRate()).isEqualTo(20.0);
        assertThat(cs.withDepositPaid()).isEqualTo(1);
        assertThat(cs.refunded()).isEqualByComparingTo("300000");
        assertThat(cs.forfeited()).isEqualByComparingTo("300000");

        assertThat(report.services()).extracting(ReportService.ServiceRow::type)
                .containsExactly(ChargeType.DIVING_TOUR, ChargeType.MINIBAR);
        assertThat(report.servicesTotal()).isEqualByComparingTo("500000");
    }

    @Test
    void emptyRange_noDivisionByZero() {
        when(bookingRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());

        ReportService.Report report = service.build(FROM, TO);

        assertThat(report.channels()).allSatisfy(c -> assertThat(c.share()).isZero());
        assertThat(report.cancellations().cancelRate()).isZero();
        assertThat(report.discounts()).isEmpty();
        assertThat(report.services()).isEmpty();
    }
}
