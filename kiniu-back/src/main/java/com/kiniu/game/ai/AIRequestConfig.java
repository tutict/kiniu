package com.kiniu.game.ai;

public record AIRequestConfig(
        String providerUrl,
        String apiKey,
        String model) {

    public boolean isConfigured() {
        return providerUrl != null
                && !providerUrl.isBlank()
                && apiKey != null
                && !apiKey.isBlank()
                && model != null
                && !model.isBlank();
    }
}
