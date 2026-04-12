package com.kiniu.game.dto;

public record AIInvocationView(
        String operation,
        String targetId,
        boolean providerAttempted,
        boolean providerSucceeded,
        boolean fallbackUsed,
        String providerUrl,
        String model,
        long latencyMs,
        String errorMessage) {
}
