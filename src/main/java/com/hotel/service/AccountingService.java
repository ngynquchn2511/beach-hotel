package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.CashShift;
import com.hotel.entity.GiftVoucher;
import com.hotel.entity.PaymentMethod;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.CashShiftRepository;
import com.hotel.repository.GiftVoucherRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

// Bao cao cho ke toan: so quy theo thang (xuat Excel) va doi soat giao dich VNPay theo ngay voi sao ke cua VNPay
@Service
public class AccountingService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Pattern DECIMAL_TAIL = Pattern.compile("[.,]\\d{1,2}$");
    public static final int MAX_STATEMENT_ROWS = 5000;

    private final PaymentService paymentService;
    private final BookingRepository bookingRepository;
    private final GiftVoucherRepository giftVoucherRepository;
    private final CashShiftRepository cashShiftRepository;

    public AccountingService(PaymentService paymentService, BookingRepository bookingRepository,
                             GiftVoucherRepository giftVoucherRepository, CashShiftRepository cashShiftRepository) {
        this.paymentService = paymentService;
        this.bookingRepository = bookingRepository;
        this.giftVoucherRepository = giftVoucherRepository;
        this.cashShiftRepository = cashShiftRepository;
    }

    // ===== So quy thang =====

    public record DaySummary(LocalDate day, BigDecimal cash, BigDecimal transfer, BigDecimal online, BigDecimal refunds, BigDecimal net) {
    }

    public record MonthReport(YearMonth month, List<DaySummary> days, Map<PaymentMethod, BigDecimal> totals, BigDecimal refunds,
                              BigDecimal net, List<PaymentService.LedgerEntry> entries, List<CashShift> shifts) {
    }

    @Transactional(readOnly = true)
    public MonthReport month(YearMonth month) {
        LocalDateTime from = month.atDay(1).atStartOfDay();
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay().minusNanos(1);
        List<PaymentService.LedgerEntry> entries = paymentService.ledger(from, to);
        List<DaySummary> days = new ArrayList<>();
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            LocalDate day = d;
            List<PaymentService.LedgerEntry> ofDay = entries.stream().filter(e -> e.time().toLocalDate().equals(day)).toList();
            if (ofDay.isEmpty()) {
                continue;
            }
            days.add(new DaySummary(day, income(ofDay, PaymentMethod.CASH), income(ofDay, PaymentMethod.BANK_TRANSFER),
                    income(ofDay, PaymentMethod.ONLINE), refunds(ofDay), sum(ofDay)));
        }
        return new MonthReport(month, days, PaymentService.totalsByMethod(entries), refunds(entries), sum(entries), entries,
                cashShiftRepository.findOpenedBetween(from, to));
    }

    private static BigDecimal income(List<PaymentService.LedgerEntry> entries, PaymentMethod method) {
        return entries.stream().filter(e -> e.method() == method && e.amount().signum() > 0)
                .map(PaymentService.LedgerEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Tong chi (hoan coc) - so duong
    private static BigDecimal refunds(List<PaymentService.LedgerEntry> entries) {
        return entries.stream().filter(e -> e.amount().signum() < 0)
                .map(e -> e.amount().negate()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sum(List<PaymentService.LedgerEntry> entries) {
        return entries.stream().map(PaymentService.LedgerEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public byte[] monthExcel(YearMonth month) {
        MonthReport report = month(month);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);

            Sheet summary = wb.createSheet("Tong hop theo ngay");
            header(summary, st, "Ngày", "Tiền mặt", "Chuyển khoản", "Online (VNPay)", "Hoàn cọc (chi)", "Thực thu");
            int r = 1;
            for (DaySummary d : report.days()) {
                Row row = summary.createRow(r++);
                row.createCell(0).setCellValue(d.day().format(DAY));
                money(row, 1, d.cash(), st);
                money(row, 2, d.transfer(), st);
                money(row, 3, d.online(), st);
                money(row, 4, d.refunds(), st);
                money(row, 5, d.net(), st);
            }
            Row total = summary.createRow(r);
            Cell label = total.createCell(0);
            label.setCellValue("Cộng tháng " + month.getMonthValue() + "/" + month.getYear());
            label.setCellStyle(st.bold);
            money(total, 1, incomeTotal(report, PaymentMethod.CASH), st.boldMoney);
            money(total, 2, incomeTotal(report, PaymentMethod.BANK_TRANSFER), st.boldMoney);
            money(total, 3, incomeTotal(report, PaymentMethod.ONLINE), st.boldMoney);
            money(total, 4, report.refunds(), st.boldMoney);
            money(total, 5, report.net(), st.boldMoney);
            autosize(summary, 6);

            Sheet detail = wb.createSheet("Chi tiet");
            header(detail, st, "Thời gian", "Nội dung", "Mã đơn", "Phương thức", "Thu", "Chi");
            r = 1;
            for (PaymentService.LedgerEntry e : report.entries()) {
                Row row = detail.createRow(r++);
                row.createCell(0).setCellValue(e.time().format(DAY_TIME));
                row.createCell(1).setCellValue(e.label());
                row.createCell(2).setCellValue(e.bookingId() != null ? "#" + e.bookingId() : "");
                row.createCell(3).setCellValue(e.method().getVietnameseLabel());
                if (e.amount().signum() >= 0) {
                    money(row, 4, e.amount(), st);
                } else {
                    money(row, 5, e.amount().negate(), st);
                }
            }
            autosize(detail, 6);

            Sheet shifts = wb.createSheet("Ca truc");
            header(shifts, st, "Ca", "Mở ca", "Người mở", "Đóng ca", "Người đóng", "Tiền đầu ca", "Thu tiền mặt",
                    "Thu chuyển khoản", "Thu online", "Tiền mặt đếm", "Chênh lệch", "Nộp két", "Ghi chú bàn giao");
            r = 1;
            for (CashShift s : report.shifts()) {
                Row row = shifts.createRow(r++);
                row.createCell(0).setCellValue("#" + s.getId());
                row.createCell(1).setCellValue(s.getOpenedAt().format(DAY_TIME));
                row.createCell(2).setCellValue(s.getOpenedBy().getFullName());
                row.createCell(3).setCellValue(s.getClosedAt() != null ? s.getClosedAt().format(DAY_TIME) : "Đang mở");
                row.createCell(4).setCellValue(s.getClosedBy() != null ? s.getClosedBy().getFullName() : "");
                money(row, 5, s.getOpeningCash(), st);
                money(row, 6, s.getCashIn(), st);
                money(row, 7, s.getTransferIn(), st);
                money(row, 8, s.getOnlineIn(), st);
                money(row, 9, s.getCountedCash(), st);
                money(row, 10, s.getCashDifference(), st);
                money(row, 11, s.getCashToSafe(), st);
                row.createCell(12).setCellValue(s.getHandoverNote() != null ? s.getHandoverNote() : "");
            }
            autosize(shifts, 12);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static BigDecimal incomeTotal(MonthReport report, PaymentMethod method) {
        return income(report.entries(), method);
    }

    // ===== Doi soat VNPay =====

    public enum VnPayKind {
        DEPOSIT("Cọc đơn"), GROUP_DEPOSIT("Cọc đoàn"), GIFT_VOUCHER("Thẻ quà tặng"), REFUND("Hoàn cọc");

        private final String label;

        VnPayKind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // 1 giao dich VNPay theo ghi nhan cua he thong (ma = vnp_TransactionNo). Hoan coc: amount duong, kind REFUND
    public record VnPayTxn(String ref, LocalDateTime time, VnPayKind kind, String description, BigDecimal amount) {
    }

    public enum MatchStatus {
        MATCHED("Khớp"), AMOUNT_MISMATCH("Lệch số tiền"), MISSING_IN_STATEMENT("Không có trong sao kê"),
        MISSING_IN_SYSTEM("Không có trong hệ thống");

        private final String label;

        MatchStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public record MatchLine(String ref, VnPayTxn system, BigDecimal statementAmount, MatchStatus status) {
        public BigDecimal getDifference() {
            BigDecimal mine = system != null ? system.amount() : BigDecimal.ZERO;
            BigDecimal theirs = statementAmount != null ? statementAmount : BigDecimal.ZERO;
            return theirs.subtract(mine);
        }
    }

    public record Reconciliation(List<MatchLine> lines, int matched, int problems) {
    }

    @Transactional(readOnly = true)
    public List<VnPayTxn> vnpayDay(LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay().minusNanos(1);
        // Coc doan: moi phong 1 don nhung chung 1 ma giao dich -> gop lai
        Map<String, VnPayTxn> deposits = new LinkedHashMap<>();
        for (Booking b : bookingRepository.findByDepositPaidAtBetween(from, to)) {
            if (!b.isDepositPaidOnline()) {
                continue;
            }
            BigDecimal amount = b.getDepositPaidAmount();
            VnPayTxn prev = deposits.get(b.getDepositOnlineRef());
            if (prev == null) {
                boolean group = b.getGroup() != null;
                deposits.put(b.getDepositOnlineRef(), new VnPayTxn(b.getDepositOnlineRef(), b.getDepositPaidAt(),
                        group ? VnPayKind.GROUP_DEPOSIT : VnPayKind.DEPOSIT,
                        group ? "Đoàn " + b.getGroup().getCode() + ": đơn #" + b.getId() : "Đơn #" + b.getId(), amount));
            } else {
                deposits.put(prev.ref(), new VnPayTxn(prev.ref(), prev.time(), VnPayKind.GROUP_DEPOSIT,
                        prev.description() + ", #" + b.getId(), prev.amount().add(amount)));
            }
        }
        List<VnPayTxn> list = new ArrayList<>(deposits.values());
        for (GiftVoucher v : giftVoucherRepository.findByPaidAtBetween(from, to)) {
            if (v.getOnlineRef() != null) {
                list.add(new VnPayTxn(v.getOnlineRef(), v.getPaidAt(), VnPayKind.GIFT_VOUCHER, "Thẻ " + v.getCode(), v.getAmount()));
            }
        }
        for (Booking b : bookingRepository.findByRefundedAtBetween(from, to)) {
            if (b.getRefundOnlineRef() != null && b.getRefundAmount() != null) {
                list.add(new VnPayTxn(b.getRefundOnlineRef(), b.getRefundedAt(), VnPayKind.REFUND, "Hoàn cọc đơn #" + b.getId(),
                        b.getRefundAmount()));
            }
        }
        list.sort(Comparator.comparing(VnPayTxn::time));
        return list;
    }

    // So khop: ma co o ca 2 ben -> so sanh so tien; chi 1 ben co -> bao thieu
    public static Reconciliation reconcile(List<VnPayTxn> system, Map<String, BigDecimal> statement) {
        Map<String, BigDecimal> rest = new LinkedHashMap<>(statement);
        List<MatchLine> lines = new ArrayList<>();
        int matched = 0;
        for (VnPayTxn t : system) {
            BigDecimal theirs = rest.remove(t.ref());
            MatchStatus status = theirs == null ? MatchStatus.MISSING_IN_STATEMENT
                    : theirs.compareTo(t.amount()) == 0 ? MatchStatus.MATCHED : MatchStatus.AMOUNT_MISMATCH;
            if (status == MatchStatus.MATCHED) {
                matched++;
            }
            lines.add(new MatchLine(t.ref(), t, theirs, status));
        }
        rest.forEach((ref, amount) -> lines.add(new MatchLine(ref, null, amount, MatchStatus.MISSING_IN_SYSTEM)));
        // Dong co van de len truoc
        lines.sort(Comparator.comparing((MatchLine l) -> l.status() == MatchStatus.MATCHED));
        return new Reconciliation(lines, matched, lines.size() - matched);
    }

    // Cot doc duoc tu dong tieu de (file goc cong merchant): ma GD, so tien, (tuy chon) trang thai.
    // amountTimes100: cot vnp_Amount (VNPay ghi so tien x100). headerRow = -1: khong co tieu de -> cot 1 / cot 2
    record Columns(int ref, int amount, int status, boolean amountTimes100, int headerRow) {
        static final Columns DEFAULT = new Columns(0, 1, -1, false, -1);
    }

    private static final int HEADER_SCAN_ROWS = 20;

    // Bo dau tieng Viet + chu thuong + gom khoang trang (so khop tieu de cot)
    static String norm(String s) {
        if (s == null) {
            return "";
        }
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        return t.replaceAll("[^a-z0-9_]+", " ").trim();
    }

    static Columns detectColumns(List<String[]> rows) {
        for (int r = 0; r < Math.min(HEADER_SCAN_ROWS, rows.size()); r++) {
            String[] row = rows.get(r);
            int ref = -1;
            int refScore = 0;
            int amount = -1;
            boolean times100 = false;
            int status = -1;
            for (int c = 0; c < row.length; c++) {
                String h = norm(row[c]);
                if (h.isEmpty()) {
                    continue;
                }
                int score = refScore(h);
                if (score > refScore) {
                    ref = c;
                    refScore = score;
                }
                if (amount < 0 && (h.equals("vnp_amount") || h.equals("so tien") || h.startsWith("so tien ") || h.equals("amount")
                        || h.equals("tong tien") || h.equals("so tien giao dich") || h.startsWith("amount "))) {
                    amount = c;
                    times100 = h.equals("vnp_amount");
                }
                if (status < 0 && (h.equals("trang thai") || h.startsWith("trang thai ") || h.equals("status") || h.equals("ket qua"))) {
                    status = c;
                }
            }
            if (ref >= 0 && amount >= 0) {
                return new Columns(ref, amount, status, times100, r);
            }
        }
        return Columns.DEFAULT;
    }

    // Diem cang cao cang chac la cot ma giao dich phia VNPay (khong phai ma don cua resort)
    private static int refScore(String h) {
        if (h.equals("vnp_transactionno") || h.equals("transactionno") || h.equals("transaction no")) {
            return 5;
        }
        if (h.contains("vnpay") && (h.contains("ma gd") || h.contains("ma giao dich") || h.contains("so giao dich"))) {
            return 4;
        }
        if (h.equals("so giao dich") || h.equals("ma giao dich") || h.equals("ma gd")) {
            return 3;
        }
        if (h.contains("ma tham chieu") || h.contains("ma don") || h.contains("txnref") || h.contains("order")) {
            return 0;
        }
        return h.startsWith("ma giao dich") || h.startsWith("ma gd") ? 2 : 0;
    }

    // Dong co trang thai that bai / huy -> khong tinh tien
    private static boolean failedStatus(String raw) {
        String s = norm(raw);
        return s.contains("that bai") || s.contains("khong thanh cong") || s.contains("failed") || s.contains("huy")
                || s.contains("cancel") || s.contains("loi") || s.contains("error");
    }

    // Doc sao ke VNPay (.csv hoac .xlsx). Co dong tieu de nhan ra duoc (file goc cong merchant) -> lay cot theo tieu de;
    // khong co -> cot 1 = ma giao dich VNPay, cot 2 = so tien. Hoan tien ghi so am van tinh theo gia tri tuyet doi;
    // 1 ma xuat hien nhieu dong -> cong don
    public static Map<String, BigDecimal> parseStatement(String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException("Vui lòng chọn file sao kê VNPay (.csv hoặc .xlsx)");
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        List<String[]> rows = name.endsWith(".xlsx") ? readXlsx(content) : readCsv(content);
        Columns cols = detectColumns(rows);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (int i = cols.headerRow() + 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length <= Math.max(cols.ref(), cols.amount())) {
                continue;
            }
            if (cols.status() >= 0 && cols.status() < row.length && failedStatus(row[cols.status()])) {
                continue;
            }
            String ref = row[cols.ref()] == null ? "" : row[cols.ref()].trim().replace("\"", "");
            BigDecimal amount = parseAmount(row[cols.amount()]);
            if (ref.isEmpty() || amount == null || !ref.matches("[A-Za-z0-9_-]{4,40}")) {
                continue;
            }
            if (cols.amountTimes100()) {
                amount = amount.divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP);
            }
            result.merge(ref, amount.abs(), BigDecimal::add);
            if (result.size() > MAX_STATEMENT_ROWS) {
                throw new BusinessException("Sao kê quá dài (tối đa " + MAX_STATEMENT_ROWS + " giao dịch)");
            }
        }
        if (result.isEmpty()) {
            throw new BusinessException("Không đọc được giao dịch nào: cần cột tiêu đề \"Mã giao dịch VNPay\" và \"Số tiền\", "
                    + "hoặc cột 1 là mã giao dịch, cột 2 là số tiền");
        }
        return result;
    }

    static BigDecimal parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace("\"", "").replace(" ", "").replace("VND", "").replace("đ", "");
        if (s.isEmpty()) {
            return null;
        }
        boolean negative = s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"));
        s = DECIMAL_TAIL.matcher(s).replaceFirst("");
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 15) {
            return null;
        }
        BigDecimal value = new BigDecimal(digits);
        return negative ? value.negate() : value;
    }

    private static List<String[]> readCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<String[]> rows = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            char sep = line.indexOf(';') >= 0 ? ';' : line.indexOf('\t') >= 0 ? '\t' : ',';
            rows.add(splitCsv(line, sep));
        }
        return rows;
    }

    // Tach 1 dong CSV, ton trong dau ngoac kep ("1,500,000")
    private static String[] splitCsv(String line, char sep) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (char ch : line.toCharArray()) {
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == sep && !quoted) {
                cells.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        cells.add(cur.toString());
        return cells.toArray(String[]::new);
    }

    private static List<String[]> readXlsx(byte[] content) {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.US);
            List<String[]> rows = new ArrayList<>();
            for (Row row : sheet) {
                int last = Math.max(row.getLastCellNum(), 0);
                String[] cells = new String[last];
                for (int c = 0; c < last; c++) {
                    Cell cell = row.getCell(c);
                    // So nguyen lon (ma GD, so tien) doc dung chu so, khong ra dang 1.4E7
                    cells[c] = cell == null ? "" : cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)
                            ? BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString() : fmt.formatCellValue(cell);
                }
                rows.add(cells);
            }
            return rows;
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException("File Excel không đọc được - hãy lưu lại dạng .xlsx hoặc .csv");
        }
    }

    @Transactional(readOnly = true)
    public byte[] vnpayExcel(LocalDate day, Reconciliation reconciliation) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            Sheet sheet = wb.createSheet("VNPay " + day.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
            if (reconciliation == null) {
                header(sheet, st, "Mã GD VNPay", "Thời gian", "Loại", "Nội dung", "Số tiền");
                int r = 1;
                for (VnPayTxn t : vnpayDay(day)) {
                    Row row = sheet.createRow(r++);
                    txnCells(row, t, st);
                }
                autosize(sheet, 5);
            } else {
                header(sheet, st, "Mã GD VNPay", "Thời gian", "Loại", "Nội dung", "Số tiền hệ thống", "Số tiền sao kê", "Chênh lệch", "Kết quả");
                int r = 1;
                for (MatchLine l : reconciliation.lines()) {
                    Row row = sheet.createRow(r++);
                    if (l.system() != null) {
                        txnCells(row, l.system(), st);
                    } else {
                        row.createCell(0).setCellValue(l.ref());
                    }
                    money(row, 5, l.statementAmount(), st);
                    money(row, 6, l.getDifference(), st);
                    row.createCell(7).setCellValue(l.status().getLabel());
                }
                autosize(sheet, 8);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void txnCells(Row row, VnPayTxn t, Styles st) {
        row.createCell(0).setCellValue(t.ref());
        row.createCell(1).setCellValue(t.time().format(DAY_TIME));
        row.createCell(2).setCellValue(t.kind().getLabel());
        row.createCell(3).setCellValue(t.description());
        money(row, 4, t.kind() == VnPayKind.REFUND ? t.amount().negate() : t.amount(), st);
    }

    // ===== Excel helpers =====

    private static final class Styles {
        final CellStyle bold;
        final CellStyle money;
        final CellStyle boldMoney;

        Styles(Workbook wb) {
            Font font = wb.createFont();
            font.setBold(true);
            bold = wb.createCellStyle();
            bold.setFont(font);
            short format = wb.createDataFormat().getFormat("#,##0");
            money = wb.createCellStyle();
            money.setDataFormat(format);
            boldMoney = wb.createCellStyle();
            boldMoney.setDataFormat(format);
            boldMoney.setFont(font);
        }
    }

    private static void header(Sheet sheet, Styles st, String... titles) {
        Row head = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell c = head.createCell(i);
            c.setCellValue(titles[i]);
            c.setCellStyle(st.bold);
        }
    }

    private static void money(Row row, int col, BigDecimal value, Styles st) {
        money(row, col, value, st.money);
    }

    private static void money(Row row, int col, BigDecimal value, CellStyle style) {
        if (value == null) {
            return;
        }
        Cell c = row.createCell(col);
        c.setCellValue(value.doubleValue());
        c.setCellStyle(style);
    }

    private static void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
