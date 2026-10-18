package com.hotel.service;

import com.hotel.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Doc so tien / file sao ke VNPay va so khop
class AccountingServiceTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1500000|1500000", "1.500.000|1500000", "1,500,000|1500000", "1,500,000.00|1500000", "1.500.000,00|1500000",
            "350.000đ|350000", "350000 VND|350000", "-400000|-400000", "(400.000)|-400000"})
    void parseAmount_commonFormats(String raw, long expected) {
        assertThat(AccountingService.parseAmount(raw)).isEqualByComparingTo(BigDecimal.valueOf(expected));
    }

    @ParameterizedTest
    @CsvSource({"''", "abc", "So tien"})
    void parseAmount_notANumber(String raw) {
        assertThat(AccountingService.parseAmount(raw)).isNull();
    }

    @Test
    void parseStatement_sumsDuplicateRefs_skipsHeaderAndTabs() {
        String csv = "TransactionNo\tAmount\n111111\t100000\n111111\t50000\n\n222222\t-30000\n";
        Map<String, BigDecimal> m = AccountingService.parseStatement("a.txt", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(m).hasSize(2);
        assertThat(m.get("111111")).isEqualByComparingTo("150000");
        assertThat(m.get("222222")).isEqualByComparingTo("30000");
        assertThatThrownBy(() -> AccountingService.parseStatement("a.csv", new byte[0])).isInstanceOf(BusinessException.class);
    }

    @Test
    void reconcile_problemsFirst() {
        var t1 = new AccountingService.VnPayTxn("A1", LocalDateTime.now(), AccountingService.VnPayKind.DEPOSIT, "x", BigDecimal.valueOf(100));
        var t2 = new AccountingService.VnPayTxn("A2", LocalDateTime.now(), AccountingService.VnPayKind.DEPOSIT, "y", BigDecimal.valueOf(200));
        var r = AccountingService.reconcile(List.of(t1, t2), Map.of("A1", BigDecimal.valueOf(100)));
        assertThat(r.matched()).isEqualTo(1);
        assertThat(r.problems()).isEqualTo(1);
        assertThat(r.lines().get(0).status()).isEqualTo(AccountingService.MatchStatus.MISSING_IN_STATEMENT);
        assertThat(r.lines().get(0).getDifference()).isEqualByComparingTo("-200");
    }
}
