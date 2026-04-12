package com.kiniu.game.ai;

import org.springframework.stereotype.Component;

@Component
public class AIRuntimeContext {

    private final ThreadLocal<AIRequestConfig> currentConfig = new ThreadLocal<>();

    public void set(AIRequestConfig config) {
        currentConfig.set(config);
    }

    public AIRequestConfig get() {
        return currentConfig.get();
    }

    public void clear() {
        currentConfig.remove();
    }
}
