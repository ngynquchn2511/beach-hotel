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

    @Test
    void merchantExcel_detectsColumnsByHeader_skipsFailedRows() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = wb.createSheet("Giao dich");
            sheet.createRow(0).createCell(0).setCellValue("BÁO CÁO GIAO DỊCH - SÓNG XANH RESORT");
            sheet.createRow(1).createCell(0).setCellValue("Từ ngày 14/03/2025 đến ngày 14/03/2025");
            String[] head = {"STT", "Mã đơn hàng", "Mã GD VNPay", "Ngân hàng", "Số tiền (VND)", "Trạng thái", "Thời gian"};
            var h = sheet.createRow(3);
            for (int i = 0; i < head.length; i++) {
                h.createCell(i).setCellValue(head[i]);
            }
            Object[][] data = {{1, "D15_1710400000", 14000001d, "NCB", 300000d, "Thành công"},
                    {2, "G7_1710400100", 14000002d, "VCB", 450000d, "Thành công"},
                    {3, "D16_1710400200", 14000005d, "NCB", 990000d, "Thất bại"}};
            for (int r = 0; r < data.length; r++) {
                var row = sheet.createRow(4 + r);
                row.createCell(0).setCellValue((Integer) data[r][0]);
                row.createCell(1).setCellValue((String) data[r][1]);
                row.createCell(2).setCellValue((Double) data[r][2]);
                row.createCell(3).setCellValue((String) data[r][3]);
                row.createCell(4).setCellValue((Double) data[r][4]);
                row.createCell(5).setCellValue((String) data[r][5]);
            }
            wb.write(out);
        }
        Map<String, BigDecimal> m = AccountingService.parseStatement("vnpay-merchant.xlsx", out.toByteArray());
        assertThat(m).containsOnlyKeys("14000001", "14000002");
        assertThat(m.get("14000002")).isEqualByComparingTo("450000");
    }

    @Test
    void vnpAmountHeader_isDividedBy100() {
        String csv = "vnp_TxnRef,vnp_TransactionNo,vnp_Amount,vnp_ResponseCode\nD1_1,14000009,30000000,00\n";
        Map<String, BigDecimal> m = AccountingService.parseStatement("ipn.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(m).containsOnlyKeys("14000009");
        assertThat(m.get("14000009")).isEqualByComparingTo("300000");
    }
}
