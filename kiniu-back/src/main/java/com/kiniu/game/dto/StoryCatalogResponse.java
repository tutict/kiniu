package com.kiniu.game.dto;

import java.util.List;

public record StoryCatalogResponse(String entryNodeId, List<StoryNodeView> nodes) {
}
