package com.hotel.service.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Mac dinh dung Haiku 5.5 (re nhat) va khong gui tham so fallbacks (Haiku khong co fallback phia server)
class ClaudeAiGatewayTest {

    @Test
    void serverFallbackOnlyForSupportedModels() {
        assertThat(ClaudeAiGateway.supportsServerFallback("claude-haiku-5-5")).isFalse();
        assertThat(ClaudeAiGateway.supportsServerFallback("claude-opus-5-5")).isTrue();
        assertThat(ClaudeAiGateway.supportsServerFallback("claude-sonnet-5-5")).isTrue();
        assertThat(ClaudeAiGateway.supportsServerFallback("claude-fable-5-1")).isTrue();

        ClaudeAiGateway gateway = new ClaudeAiGateway("", "claude-haiku-5-5", true);
        assertThat(gateway.model()).isEqualTo("claude-haiku-5-5");
        assertThat(gateway.fallbacksEnabled()).isFalse();
        assertThat(gateway.isEnabled()).isFalse();
        assertThat(new ClaudeAiGateway("k", "claude-opus-5-5", true).fallbacksEnabled()).isTrue();
    }

    @Test
    void pricingPerModel() {
        assertThat(AiAdvisorService.pricePerMTok("claude-haiku-5-5")).containsExactly(0.10, 0.50);
        assertThat(AiAdvisorService.pricePerMTok("claude-opus-5-5")).containsExactly(4.0, 20.0);
    }
}
