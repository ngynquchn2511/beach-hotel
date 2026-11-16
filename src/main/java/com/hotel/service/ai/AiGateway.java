package com.hotel.service.ai;

// Cong goi mo hinh AI - tach interface de test khong goi API that
public interface AiGateway {

    boolean isEnabled();

    String model();

    // Phan tich co cau truc
    Result<AiAnalysis> analyze(String systemPrompt, String userContent);

    // Tra loi tu do (hoi dap ve so lieu)
    Result<String> answer(String systemPrompt, String userContent);

    // Tra loi dang dong chay: goi onText voi tung doan chu. Mac dinh (gateway gia trong test): tra ca cau 1 lan
    default Result<String> streamAnswer(String systemPrompt, String userContent, java.util.function.Consumer<String> onText) {
        Result<String> r = answer(systemPrompt, userContent);
        onText.accept(r.value());
        return r;
    }

    record Result<T>(T value, long inputTokens, long outputTokens) {
    }
}
