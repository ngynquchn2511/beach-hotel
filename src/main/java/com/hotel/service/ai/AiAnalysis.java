package com.hotel.service.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

// Ket qua phan tich co cau truc Claude tra ve (schema sinh tu record nay - structured outputs)
public record AiAnalysis(
        @JsonPropertyDescription("Tóm tắt 2-4 câu tình hình kinh doanh tháng, nêu số chính (doanh thu, lãi, biên, công suất)")
        String summary,
        @JsonPropertyDescription("Điểm tích cực đáng chú ý, mỗi ý có số liệu chứng minh")
        List<Finding> highlights,
        @JsonPropertyDescription("Rủi ro / điểm bất thường cần xử lý, sắp từ nghiêm trọng nhất")
        List<Finding> risks,
        @JsonPropertyDescription("3-6 khuyến nghị hành động cụ thể cho quản lý resort, sắp theo ưu tiên")
        List<Recommendation> recommendations,
        @JsonPropertyDescription("Dự báo ngắn cho tháng tới dựa trên xu hướng 12 tháng, nói rõ mức độ chắc chắn")
        String outlook) {

    public record Finding(
            @JsonPropertyDescription("Tiêu đề ngắn") String title,
            @JsonPropertyDescription("Giải thích kèm số liệu từ dữ liệu được cung cấp") String detail) {
    }

    public record Recommendation(
            @JsonPropertyDescription("Việc cần làm, cụ thể, bắt đầu bằng động từ") String action,
            @JsonPropertyDescription("Vì sao - dẫn số liệu") String reason,
            @JsonPropertyDescription("Tác động dự kiến, ước lượng bằng VND hoặc điểm % nếu có cơ sở") String expectedImpact,
            @JsonPropertyDescription("CAO, TRUNG_BINH hoặc THAP") String priority,
            @JsonPropertyDescription("Bộ phận phụ trách: Lễ tân, Kinh doanh, Kế toán, Buồng phòng, Quản lý") String owner) {
    }
}
