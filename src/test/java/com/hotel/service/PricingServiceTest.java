package com.hotel.service;

import com.hotel.entity.PricingSettings;
import com.hotel.entity.SpecialRate;
import com.hotel.exception.BusinessException;
import com.hotel.repository.PricingSettingsRepository;
import com.hotel.repository.SpecialRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingServiceTest {

    @Mock private PricingSettingsRepository settingsRepository;
    @Mock private SpecialRateRepository specialRateRepository;

    @InjectMocks private PricingService pricingService;

    private static final BigDecimal PRICE = BigDecimal.valueOf(500000);
    // Thu 5 tuan sau -> dem thu 5 (thuong), thu 6, thu 7 (cuoi tuan), chu nhat (thuong)
    private static final LocalDate THURSDAY = LocalDate.now().plusDays(1).with(TemporalAdjusters.next(DayOfWeek.THURSDAY));

    private void settings(int weekend, int deposit) {
        when(settingsRepository.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.of(
                PricingSettings.builder().id(1L).weekendSurchargePercent(weekend).depositPercent(deposit).depositDeadlineHours(24).build()));
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void noSettings_everyNightIsBasePrice_andNoDeposit() {
        BigDecimal amount = pricingService.roomAmount(PRICE, THURSDAY, THURSDAY.plusDays(4));
        assertThat(amount).isEqualByComparingTo(bd(2000000));
        assertThat(pricingService.depositFor(amount)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void weekendSurcharge_onlyFridayAndSaturdayNights() {
        settings(40, 0);
        List<PricingService.NightPrice> nights = pricingService.nightlyPrices(PRICE, THURSDAY, THURSDAY.plusDays(4));

        assertThat(nights).extracting(PricingService.NightPrice::price)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd(500000), bd(700000), bd(700000), bd(500000));
        assertThat(nights.get(1).label()).isEqualTo("Cuối tuần (+40%)");
        assertThat(nights.get(0).label()).isEqualTo("Ngày thường");
        assertThat(nights.get(0).dayName()).isEqualTo("Thứ 5");
        assertThat(nights.get(3).dayName()).isEqualTo("Chủ nhật");
    }

    @Test
    void holidayRate_overridesWeekend_andHighestOverlapWins() {
        settings(40, 0);
        LocalDate friday = THURSDAY.plusDays(1);
        when(specialRateRepository.findOverlapping(any(), any())).thenReturn(List.of(
                SpecialRate.builder().name("Lễ").startDate(friday).endDate(friday).surchargePercent(80).build(),
                SpecialRate.builder().name("Cao điểm").startDate(THURSDAY).endDate(friday).surchargePercent(20).build()));

        List<PricingService.NightPrice> nights = pricingService.nightlyPrices(PRICE, THURSDAY, THURSDAY.plusDays(3));

        // Thu 5: cao diem +20%, thu 6: le +80% (cao hon), thu 7: cuoi tuan +40%
        assertThat(nights).extracting(PricingService.NightPrice::price)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd(600000), bd(900000), bd(700000));
        assertThat(nights.get(1).label()).isEqualTo("Lễ (+80%)");
    }

    @Test
    void deposit_roundedUpToThousand_andNeverAboveTotal() {
        settings(0, 30);
        assertThat(pricingService.depositFor(bd(1234567))).isEqualByComparingTo(bd(371000));
        settings(0, 100);
        assertThat(pricingService.depositFor(bd(1234567))).isEqualByComparingTo(bd(1234567));
    }

    @Test
    void invalidRange_returnsNoNights() {
        assertThat(pricingService.nightlyPrices(PRICE, THURSDAY, THURSDAY)).isEmpty();
        assertThat(pricingService.roomAmount(PRICE, THURSDAY.plusDays(2), THURSDAY)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void updateSettings_validatesRanges() {
        assertThatThrownBy(() -> pricingService.updateSettings(-1, 30, 24)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> pricingService.updateSettings(30, 101, 24)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> pricingService.updateSettings(30, 30, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> pricingService.updateSettings(null, 30, 24)).isInstanceOf(BusinessException.class);

        when(settingsRepository.save(any(PricingSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        PricingSettings saved = pricingService.updateSettings(30, 50, 12);
        assertThat(saved.getId()).isEqualTo(PricingSettings.SINGLETON_ID);
        assertThat(saved.getDepositPercent()).isEqualTo(50);
    }

    @Test
    void createSpecialRate_validates() {
        LocalDate d = LocalDate.now();
        assertThatThrownBy(() -> pricingService.createSpecialRate(" ", d, d, 50)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> pricingService.createSpecialRate("Tết", d, d.minusDays(1), 50)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> pricingService.createSpecialRate("Tết", d, d, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> pricingService.createSpecialRate("Tết", null, d, 50)).isInstanceOf(BusinessException.class);

        when(specialRateRepository.save(any(SpecialRate.class))).thenAnswer(inv -> inv.getArgument(0));
        SpecialRate r = pricingService.createSpecialRate("  Tết  ", d, d.plusDays(3), 100);
        assertThat(r.getName()).isEqualTo("Tết");
    }
}
