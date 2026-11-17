package com.hotel.service.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

// Ket qua phan tich co cau truc Claude tra ve (schema sinh tu record nay - structured outputs).
// Uu tien truc quan: o chi so (kpis) + bieu do (charts) truoc, chu chi con y chinh rat ngan.
// Ban phan tich cu (truoc khi co kpis / charts) doc ra 2 truong nay = null
public record AiAnalysis(
        @JsonPropertyDescription("3-4 ô chỉ số quan trọng nhất của tháng (VD lãi vận hành, công suất, doanh thu, giá phòng TB)")
        List<Kpi> kpis,
        @JsonPropertyDescription("2-3 biểu đồ minh họa rõ nhất tình hình tháng (so sánh tháng, cơ cấu chi phí / doanh thu, "
                + "hiệu quả loại phòng...). Số lấy đúng từ dữ liệu, tiền đổi sang triệu đồng, làm tròn 1 chữ số thập phân")
        List<Chart> charts,
        @JsonPropertyDescription("1 câu kết luận, tối đa 25 từ")
        String summary,
        @JsonPropertyDescription("Tối đa 3 điểm tích cực")
        List<Finding> highlights,
        @JsonPropertyDescription("Tối đa 3 rủi ro, nghiêm trọng nhất trước")
        List<Finding> risks,
        @JsonPropertyDescription("3-4 khuyến nghị hành động, sắp theo ưu tiên")
        List<Recommendation> recommendations,
        @JsonPropertyDescription("Dự báo tháng tới, 1 câu tối đa 20 từ, nêu mức độ chắc chắn")
        String outlook) {

    public record Kpi(
            @JsonPropertyDescription("Tên chỉ số 1-3 từ, từ dễ hiểu (\"Giá phòng TB\" thay cho ADR, \"Doanh thu / phòng trống\" thay cho RevPAR)") String label,
            @JsonPropertyDescription("Giá trị ngắn gọn, VD \"-83,7 tr\", \"26,6%\"") String value,
            @JsonPropertyDescription("So sánh ngắn, VD \"-10,1 điểm so với T10\"") String note,
            @JsonPropertyDescription("good, bad hoặc neutral") String tone) {
    }

    public record Chart(
            @JsonPropertyDescription("bar (cột, so sánh nhiều nhóm), line (xu hướng, 1 series) hoặc hbar (thanh ngang xếp hạng / cơ cấu, 1 series)")
            String type,
            @JsonPropertyDescription("Tiêu đề ngắn kèm đơn vị, VD \"Chi phí theo nhóm (triệu đồng)\"") String title,
            @JsonPropertyDescription("Đơn vị ngắn hiển thị: tr, %, phòng-đêm...") String unit,
            @JsonPropertyDescription("Nhãn trục / tên từng thanh, thật ngắn; tháng ghi dạng \"T11/25\"") List<String> labels,
            @JsonPropertyDescription("Dãy số, mỗi series cùng số phần tử với labels") List<Series> series) {
    }

    public record Series(
            @JsonPropertyDescription("Tên dãy số") String name,
            @JsonPropertyDescription("Giá trị theo thứ tự labels") List<Double> values) {
    }

    public record Finding(
            @JsonPropertyDescription("Tiêu đề tối đa 8 từ") String title,
            @JsonPropertyDescription("1 ý kèm 1 con số, tối đa 15 từ") String detail) {
    }

    public record Recommendation(
            @JsonPropertyDescription("Việc cần làm, bắt đầu bằng động từ, tối đa 10 từ") String action,
            @JsonPropertyDescription("Vì sao, tối đa 12 từ, dẫn số liệu") String reason,
            @JsonPropertyDescription("Tác động dự kiến, tối đa 8 từ, VD \"+15 tr/tháng\"") String expectedImpact,
            @JsonPropertyDescription("CAO, TRUNG_BINH hoặc THAP") String priority,
            @JsonPropertyDescription("Bộ phận phụ trách: Lễ tân, Kinh doanh, Kế toán, Buồng phòng, Quản lý") String owner) {
    }
}
