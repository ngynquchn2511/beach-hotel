package com.hotel.service;

import com.hotel.entity.Expense;
import com.hotel.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Tro ly phan tich theo quy tac (khong can AI, khong ton tien): doc lai lo + huy phong cua thang, chi ra diem bat thuong
// va goi y hanh dong. Nguong dua theo thong le van hanh khach san nho, chinh o hang so ben duoi
@Service
public class InsightService {

    static final double REVENUE_SWING_PERCENT = 10;
    static final double LOW_MARGIN_PERCENT = 15;
    static final double LOW_OCCUPANCY_PERCENT = 50;
    static final double HIGH_OCCUPANCY_PERCENT = 85;
    static final double ADR_DROP_PERCENT = 5;
    static final double LOW_SERVICE_SHARE_PERCENT = 10;
    static final double HIGH_CANCEL_PERCENT = 20;
    static final double EXPENSE_SPIKE_PERCENT = 30;
    static final double EXPENSE_SPIKE_MIN_SHARE = 5;

    public enum Level {
        CRITICAL("Nghiêm trọng", "danger"), WARNING("Cần chú ý", "warning"), INFO("Thông tin", "info"), GOOD("Tốt", "success");

        private final String label;
        private final String css;

        Level(String label, String css) {
            this.label = label;
            this.css = css;
        }

        public String getLabel() {
            return label;
        }

        public String getCss() {
            return css;
        }
    }

    public record Insight(Level level, String title, String detail, String recommendation) {
    }

    private final ProfitService profitService;
    private final CancellationReportService cancellationService;
    private BudgetService budgetService;

    public InsightService(ProfitService profitService, CancellationReportService cancellationService) {
        this.profitService = profitService;
        this.cancellationService = cancellationService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setBudgetService(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Transactional(readOnly = true)
    public List<Insight> analyze(YearMonth month) {
        ProfitService.Report report = profitService.report(month);
        List<Insight> list = analyze(report, cancellationService.build(month, month).total().cancelRate());
        if (budgetService != null) {
            budgetService.variance(report.current()).ifPresent(v -> list.addAll(budgetInsights(v, report.current())));
            list.sort(Comparator.comparing(Insight::level));
        }
        return list;
    }

    // Tien do / ket qua so voi ke hoach thang
    static List<Insight> budgetInsights(BudgetService.Variance v, ProfitService.MonthPnl c) {
        List<Insight> out = new ArrayList<>();
        if (v.revenuePercent() != null) {
            if (v.revenuePercent() >= 100) {
                out.add(new Insight(Level.GOOD, "Đạt " + v.revenuePercent() + "% kế hoạch doanh thu", v.inProgress()
                        ? "Doanh thu đã có trên sổ đã vượt chỉ tiêu tháng." : "Tháng vượt chỉ tiêu doanh thu.", null));
            } else {
                BigDecimal gap = v.getRevenueShortfall();
                String nights = c.adr().signum() > 0
                        ? " - khoảng " + gap.divide(c.adr(), 0, RoundingMode.UP) + " phòng-đêm ở ADR hiện tại" : "";
                out.add(new Insight(v.revenuePercent() < 80 ? Level.WARNING : Level.INFO,
                        (v.inProgress() ? "Mới có " : "Chỉ đạt ") + v.revenuePercent() + "% kế hoạch doanh thu",
                        "Còn thiếu " + Money.format(gap) + " VND" + nights + ".",
                        v.inProgress() ? "Đẩy bán phòng trống còn lại trong tháng: khuyến mãi ngắn ngày, mời nâng hạng, nhắc khách cũ quay lại."
                                : "Xem lại chỉ tiêu tháng sau hoặc kế hoạch bán hàng mùa thấp điểm."));
            }
        }
        if (v.costPercent() != null && v.costPercent() > 100) {
            out.add(new Insight(Level.WARNING, "Chi phí vượt ngân sách: " + v.costPercent() + "%",
                    "Chi phí " + Money.format(c.totalCost()) + " VND so với ngân sách " + Money.format(v.budget().getCostBudget()) + " VND.",
                    "Hoãn khoản chi chưa cấp thiết sang tháng sau."));
        }
        return out;
    }

    // Tach rieng de unit test voi so lieu dung san
    static List<Insight> analyze(ProfitService.Report r, double cancelRate) {
        List<Insight> out = new ArrayList<>();
        ProfitService.MonthPnl c = r.current();
        ProfitService.MonthPnl p = r.previous();
        if (c.totalRevenue().signum() == 0) {
            out.add(new Insight(Level.INFO, "Chưa có doanh thu trong tháng",
                    "Tháng này chưa ghi nhận đêm phòng hay dịch vụ nào.", "Kiểm tra lại tháng đang xem hoặc chờ có đơn."));
            return out;
        }
        if (c.expensesTotal().signum() == 0) {
            out.add(new Insight(Level.INFO, "Chưa nhập chi phí vận hành",
                    "Lãi đang tính khi chưa có lương, điện nước, thuê... nên cao hơn thực tế.",
                    "Nhập các khoản chi cố định hàng tháng ở trang Chi phí vận hành để có lãi lỗ thật."));
        }

        // Lai / bien loi nhuan
        if (c.operatingProfit().signum() < 0) {
            out.add(new Insight(Level.CRITICAL, "Tháng đang lỗ " + Money.format(c.operatingProfit().negate()) + " VND",
                    "Chi phí " + Money.format(c.totalCost()) + " VND vượt doanh thu " + Money.format(c.totalRevenue()) + " VND.",
                    "Rà soát 3 khoản chi lớn nhất bên dưới và đẩy bán phòng trống bằng khuyến mãi ngắn ngày."));
        } else if (c.margin() < LOW_MARGIN_PERCENT) {
            out.add(new Insight(Level.WARNING, "Biên lợi nhuận thấp: " + c.margin() + "%",
                    "Mỗi 100.000 VND doanh thu chỉ còn lại khoảng " + Math.round(c.margin() * 1000) + " VND lãi.",
                    "Tăng giá ngày cao điểm / cuối tuần và cắt chi phí không tạo doanh thu."));
        } else {
            out.add(new Insight(Level.GOOD, "Biên lợi nhuận " + c.margin() + "%", "Lãi vận hành " + Money.format(c.operatingProfit()) + " VND.", null));
        }

        // Doanh thu so voi thang truoc / cung ky
        Double rev = r.vsPrevious().revenue();
        if (rev != null && Math.abs(rev) >= REVENUE_SWING_PERCENT) {
            out.add(new Insight(rev < 0 ? Level.WARNING : Level.GOOD,
                    "Doanh thu " + (rev < 0 ? "giảm " : "tăng ") + Math.abs(rev) + "% so với tháng trước",
                    "Tháng trước " + Money.format(p.totalRevenue()) + " VND, tháng này " + Money.format(c.totalRevenue()) + " VND.",
                    rev < 0 ? "Xem kênh đặt và lý do hủy; cân nhắc khuyến mãi đặt sớm cho tháng tới." : null));
        }
        Double yoy = r.vsLastYear().revenue();
        if (yoy != null && Math.abs(yoy) >= REVENUE_SWING_PERCENT) {
            out.add(new Insight(yoy < 0 ? Level.WARNING : Level.GOOD,
                    "Doanh thu " + (yoy < 0 ? "thấp hơn " : "cao hơn ") + Math.abs(yoy) + "% so với cùng kỳ năm trước", "So sánh loại bỏ yếu tố mùa vụ.",
                    yoy < 0 ? "So lại giá bán và lịch khuyến mãi với cùng kỳ năm trước." : null));
        }

        // Chi phi tang nhanh hon doanh thu
        Double cost = r.vsPrevious().cost();
        if (cost != null && rev != null && cost - rev >= 5 && cost > 0) {
            out.add(new Insight(Level.WARNING, "Chi phí tăng nhanh hơn doanh thu",
                    "Chi phí +" + cost + "% trong khi doanh thu " + (rev >= 0 ? "+" : "") + rev + "% so với tháng trước.",
                    "Kiểm tra khoản chi tăng mạnh bên dưới; chi phí cố định nên giữ nguyên khi doanh thu chưa tăng."));
        }

        // Khoan chi tang dot bien so voi trung binh 3 thang truoc
        List<ProfitService.MonthPnl> trend = r.trend();
        if (trend.size() >= 4) {
            List<ProfitService.MonthPnl> before = trend.subList(trend.size() - 4, trend.size() - 1);
            for (Expense.Category cat : Expense.Category.values()) {
                BigDecimal now = c.expenses().getOrDefault(cat, BigDecimal.ZERO);
                BigDecimal avg = before.stream().map(m -> m.expenses().getOrDefault(cat, BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(before.size()), 0, RoundingMode.HALF_UP);
                if (avg.signum() == 0 || now.signum() == 0) {
                    continue;
                }
                double up = now.subtract(avg).multiply(BigDecimal.valueOf(100)).divide(avg, 1, RoundingMode.HALF_UP).doubleValue();
                double share = now.multiply(BigDecimal.valueOf(100)).divide(c.totalRevenue(), 1, RoundingMode.HALF_UP).doubleValue();
                if (up >= EXPENSE_SPIKE_PERCENT && share >= EXPENSE_SPIKE_MIN_SHARE) {
                    out.add(new Insight(Level.WARNING, cat.getLabel() + " tăng " + up + "%",
                            "Tháng này " + Money.format(now) + " VND so với trung bình 3 tháng trước " + Money.format(avg) + " VND (" + share + "% doanh thu).",
                            "Xem lại hóa đơn / hợp đồng của nhóm chi này."));
                }
            }
        }

        // Cong suat va gia
        if (c.occupancy() < LOW_OCCUPANCY_PERCENT) {
            out.add(new Insight(Level.WARNING, "Công suất thấp: " + c.occupancy() + "%",
                    c.availableRoomNights() - c.soldRoomNights() + " phòng-đêm còn trống trong tháng.",
                    "Mở khuyến mãi ở dài / đặt sớm cho ngày thường, bật nâng hạng phòng và giá không hoàn tiền để giữ khách."));
        } else if (c.occupancy() >= HIGH_OCCUPANCY_PERCENT) {
            Double adrChange = ProfitService.pct(c.adr(), p.adr());
            out.add(new Insight(Level.GOOD, "Công suất cao: " + c.occupancy() + "%", "ADR " + Money.format(c.adr()) + " VND.",
                    adrChange == null || adrChange < 3 ? "Phòng gần kín mà giá chưa tăng - có thể nâng giá ngày cao điểm hoặc đặt số đêm tối thiểu." : null));
        }
        Double adrChange = ProfitService.pct(c.adr(), p.adr());
        if (adrChange != null && adrChange <= -ADR_DROP_PERCENT && c.occupancy() > p.occupancy()) {
            out.add(new Insight(Level.WARNING, "Giá phòng bình quân giảm " + Math.abs(adrChange) + "%",
                    "Công suất tăng " + r.vsPrevious().occupancyPoints() + " điểm nhưng ADR giảm - đang bán bằng giảm giá.",
                    "Siết mã giảm giá / khuyến mãi; ưu tiên ưu đãi có điều kiện (ở dài, không hoàn tiền) thay vì giảm thẳng."));
        }

        // Ty trong dich vu
        BigDecimal services = c.serviceRevenueTotal();
        double serviceShare = services.multiply(BigDecimal.valueOf(100)).divide(c.totalRevenue(), 1, RoundingMode.HALF_UP).doubleValue();
        if (serviceShare < LOW_SERVICE_SHARE_PERCENT) {
            out.add(new Insight(Level.INFO, "Dịch vụ chỉ chiếm " + serviceShare + "% doanh thu",
                    "Doanh thu ngoài tiền phòng (spa, gọi lên phòng, tour, đưa đón) còn thấp.",
                    "Gửi gợi ý spa / tour trong email nhắc lịch, đặt combo trên trang đặt phòng và bật lời mời nâng hạng."));
        }

        // Huy phong
        if (cancelRate >= HIGH_CANCEL_PERCENT) {
            out.add(new Insight(Level.WARNING, "Tỷ lệ hủy " + cancelRate + "%",
                    "Đơn đặt trong tháng bị hủy nhiều, phòng bị giữ rồi trả lại sát ngày.",
                    "Bật / tăng đặt cọc, thêm giá không hoàn tiền và rút ngắn hạn hủy miễn phí."));
        }

        // Chi phi tren moi phong-dem so voi gia ban
        if (c.soldRoomNights() > 0 && c.adr().signum() > 0
                && c.cpor().compareTo(c.adr().multiply(BigDecimal.valueOf(0.8))) > 0) {
            out.add(new Insight(Level.WARNING, "Chi phí mỗi phòng-đêm sát giá bán",
                    "CPOR " + Money.format(c.cpor()) + " VND so với ADR " + Money.format(c.adr()) + " VND.",
                    "Với chi phí cố định hiện tại cần bán thêm phòng-đêm; xem điểm hòa vốn ở phân tích AI."));
        }

        out.sort(Comparator.comparing(Insight::level));
        return out;
    }

    // Top khoan chi lon nhat thang (de hien canh insight)
    public static List<java.util.Map.Entry<Expense.Category, BigDecimal>> topExpenses(ProfitService.MonthPnl m, int n) {
        return m.expenses().entrySet().stream().sorted(java.util.Map.Entry.<Expense.Category, BigDecimal>comparingByValue().reversed())
                .limit(n).toList();
    }
}
