package com.kiniu.game.ai;

public record AIInvocationTelemetry(
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
