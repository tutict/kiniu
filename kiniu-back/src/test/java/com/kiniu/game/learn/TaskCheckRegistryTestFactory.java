package com.kiniu.game.learn;

import tools.jackson.databind.ObjectMapper;
import java.util.List;

final class TaskCheckRegistryTestFactory {

    private TaskCheckRegistryTestFactory() {
    }

    static TaskCheckRegistry standard(ObjectMapper objectMapper) {
        return new TaskCheckRegistry(List.of(
                new ContainsTaskCheckRule(),
                new MarkdownSectionTaskCheckRule(),
                new RegexTaskCheckRule(),
                new FrontmatterRegexTaskCheckRule(),
                new JsonFieldTaskCheckRule(objectMapper),
                new JsonArrayMinTaskCheckRule(objectMapper),
                new JsonPointerPresentTaskCheckRule(objectMapper),
                new JsonArrayShapeTaskCheckRule(objectMapper),
                new JsonNumberRangeTaskCheckRule(objectMapper),
                new MinLengthTaskCheckRule()));
    }
}
