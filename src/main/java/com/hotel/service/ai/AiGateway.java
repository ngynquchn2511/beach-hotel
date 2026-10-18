package com.hotel.service.ai;

// Cong goi mo hinh AI - tach interface de test khong goi API that
public interface AiGateway {

    boolean isEnabled();

    String model();

    // Phan tich co cau truc
    Result<AiAnalysis> analyze(String systemPrompt, String userContent);

    // Tra loi tu do (hoi dap ve so lieu)
    Result<String> answer(String systemPrompt, String userContent);

    record Result<T>(T value, long inputTokens, long outputTokens) {
    }
}
