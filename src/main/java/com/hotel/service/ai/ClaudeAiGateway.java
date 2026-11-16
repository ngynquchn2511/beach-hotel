package com.hotel.service.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.hotel.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

// Goi Claude qua Java SDK chinh thuc. Bat khi co ANTHROPIC_API_KEY (app.ai.api-key); khong co -> isEnabled() = false
@Component
public class ClaudeAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiGateway.class);
    // Tu chuyen sang mo hinh du phong neu bi bo loc an toan tu choi (server-side fallback, che do "default")
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final String apiKey;
    private final String model;
    private final boolean enabledFlag;
    private volatile AnthropicClient client;

    // Server-side fallback khi bi bo loc an toan tu choi: "auto" = chi gui voi model co ho tro (Opus 5.x, Fable, Sonnet 5.5);
    // Haiku 5.5 (model mac dinh, re nhat) KHONG co fallback phia server - gui tham so nay se loi. "true" / "false" de ep
    @Value("${app.ai.fallbacks:auto}")
    private String fallbacksMode = "auto";

    public ClaudeAiGateway(@Value("${app.ai.api-key:}") String apiKey, @Value("${app.ai.model:claude-haiku-5-5}") String model,
                           @Value("${app.ai.enabled:true}") boolean enabledFlag) {
        this.apiKey = apiKey;
        this.model = model;
        this.enabledFlag = enabledFlag;
    }

    @Override
    public boolean isEnabled() {
        return enabledFlag && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String model() {
        return model;
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofMinutes(5)).build();
                }
            }
        }
        return client;
    }

    // System prompt de cache (cung 1 thang hoi nhieu cau thi dung lai phan so lieu)
    private static List<TextBlockParam> system(String text) {
        return List.of(TextBlockParam.builder().text(text).cacheControl(CacheControlEphemeral.builder().build()).build());
    }

    @Override
    public Result<AiAnalysis> analyze(String systemPrompt, String userContent) {
        requireEnabled();
        StructuredMessageCreateParams.Builder<AiAnalysis> builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .systemOfTextBlockParams(system(systemPrompt))
                .addUserMessage(userContent)
                .outputConfig(AiAnalysis.class)
                .putAllAdditionalHeaders(fallbackHeaders())
                .putAllAdditionalBodyProperties(fallbackBody());
        // Bo buoc suy nghi truoc khi tra loi (model cho phep) - so lieu da tong hop san, viet thang ra nhanh hon nhieu
        if (canDisableThinking(model)) {
            builder.thinking(ThinkingConfigDisabled.builder().build());
        }
        StructuredMessageCreateParams<AiAnalysis> params = builder.build();
        try {
            StructuredMessage<AiAnalysis> response = client().messages().create(params);
            checkStop(response.stopReason().orElse(null));
            AiAnalysis value = response.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).findFirst()
                    .orElseThrow(() -> new BusinessException("AI không trả về kết quả, vui lòng thử lại"));
            return new Result<>(value, response.usage().inputTokens(), response.usage().outputTokens());
        } catch (AnthropicServiceException ex) {
            throw translate(ex);
        }
    }

    @Override
    public Result<String> answer(String systemPrompt, String userContent) {
        requireEnabled();
        MessageCreateParams params = answerParams(systemPrompt, userContent);
        try {
            Message response = client().messages().create(params);
            checkStop(response.stopReason().orElse(null));
            StringBuilder sb = new StringBuilder();
            response.content().forEach(b -> b.text().ifPresent(t -> sb.append(t.text())));
            return new Result<>(sb.toString().trim(), response.usage().inputTokens(), response.usage().outputTokens());
        } catch (AnthropicServiceException ex) {
            throw translate(ex);
        }
    }

    // Tra loi tung doan chu ngay khi Claude viet ra (hien dan tren trang) - nguoi dung thay chu sau ~1 giay
    @Override
    public Result<String> streamAnswer(String systemPrompt, String userContent, Consumer<String> onText) {
        requireEnabled();
        MessageAccumulator acc = MessageAccumulator.create();
        StringBuilder sb = new StringBuilder();
        try (StreamResponse<RawMessageStreamEvent> stream = client().messages().createStreaming(answerParams(systemPrompt, userContent))) {
            stream.stream().map(acc::accumulate)
                    .flatMap(e -> e.contentBlockDelta().stream())
                    .flatMap(d -> d.delta().text().stream())
                    .forEach(t -> {
                        sb.append(t.text());
                        onText.accept(t.text());
                    });
        } catch (AnthropicServiceException ex) {
            throw translate(ex);
        }
        Message response = acc.message();
        checkStop(response.stopReason().orElse(null));
        return new Result<>(sb.toString().trim(), response.usage().inputTokens(), response.usage().outputTokens());
    }

    private MessageCreateParams answerParams(String systemPrompt, String userContent) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .systemOfTextBlockParams(system(systemPrompt))
                .addUserMessage(userContent)
                // Hoi dap ngan ve so lieu: muc no luc thap la du, tra loi nhanh hon
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .putAllAdditionalHeaders(fallbackHeaders())
                .putAllAdditionalBodyProperties(fallbackBody());
        if (canDisableThinking(model)) {
            builder.thinking(ThinkingConfigDisabled.builder().build());
        }
        return builder.build();
    }

    // Haiku 5.x cho tat thinking (o muc effort <= high); Opus 5.5 / Sonnet 5.5 / Fable bao loi 400 neu tat -> chi ha effort
    static boolean canDisableThinking(String model) {
        return model != null && model.startsWith("claude-haiku-5");
    }

    boolean fallbacksEnabled() {
        return switch (fallbacksMode == null ? "auto" : fallbacksMode.trim().toLowerCase()) {
            case "true" -> true;
            case "false" -> false;
            default -> supportsServerFallback(model);
        };
    }

    static boolean supportsServerFallback(String model) {
        return model != null && (model.startsWith("claude-opus-5") || model.startsWith("claude-fable-5") || model.equals("claude-sonnet-5-5"));
    }

    private java.util.Map<String, Iterable<String>> fallbackHeaders() {
        return fallbacksEnabled() ? java.util.Map.of("anthropic-beta", List.of(FALLBACK_BETA)) : java.util.Map.of();
    }

    private java.util.Map<String, JsonValue> fallbackBody() {
        return fallbacksEnabled() ? java.util.Map.of("fallbacks", JsonValue.from("default")) : java.util.Map.of();
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new BusinessException("Chưa bật AI: cần đặt app.ai.api-key trong file secrets.properties (hoặc biến môi trường ANTHROPIC_API_KEY) rồi khởi động lại ứng dụng");
        }
    }

    private static void checkStop(StopReason stop) {
        if (stop == null) {
            return;
        }
        if (StopReason.REFUSAL.equals(stop)) {
            throw new BusinessException("AI từ chối yêu cầu này - hãy diễn đạt lại câu hỏi");
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new BusinessException("Câu trả lời của AI bị cắt do quá dài, vui lòng thu hẹp câu hỏi");
        }
    }

    private static BusinessException translate(AnthropicServiceException ex) {
        log.warn("Goi Claude that bai: status {} - {}", ex.statusCode(), ex.getMessage());
        if (ex instanceof RateLimitException) {
            return new BusinessException("AI đang quá tải giới hạn gọi, vui lòng thử lại sau ít phút");
        }
        if (ex instanceof UnauthorizedException) {
            return new BusinessException("Khóa ANTHROPIC_API_KEY không hợp lệ hoặc đã bị thu hồi");
        }
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (msg.contains("credit balance")) {
            return new BusinessException("Tài khoản Anthropic đã hết credit - vào console.anthropic.com > Plans & Billing để nạp tiền rồi thử lại");
        }
        return new BusinessException("Gọi AI thất bại (mã " + ex.statusCode() + "), vui lòng thử lại sau");
    }
}
