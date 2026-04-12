package com.kiniu.game.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AITelemetryCollector {

    private final ThreadLocal<List<AIInvocationTelemetry>> current = ThreadLocal.withInitial(ArrayList::new);

    public void record(AIInvocationTelemetry telemetry) {
        current.get().add(telemetry);
    }

    public List<AIInvocationTelemetry> snapshot() {
        return List.copyOf(current.get());
    }

    public void clear() {
        current.remove();
    }
}
