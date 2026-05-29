package com.campus.canteen.ai.service;

import reactor.core.publisher.Flux;

public interface AiChatService {
    Flux<String> chatStream(String message, String sessionId, String userId);
    String chat(String message, String sessionId, String userId);
}
