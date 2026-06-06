package com.campus.canteen.ai.service;

import com.campus.canteen.ai.agent.ReActAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private final ReActAgent reActAgent;

    public AiChatServiceImpl(ReActAgent reActAgent) {
        this.reActAgent = reActAgent;
    }

    @Override
    public Flux<String> chatStream(String message, String sessionId, String userId) {
        return reActAgent.executeStream(message, sessionId, userId)
                .doOnError(e -> log.error("AI stream error, sessionId={}", sessionId, e));
    }

    @Override
    public String chat(String message, String sessionId, String userId) {
        log.info("AI chat start, sessionId={}", sessionId);
        try {
            String result = reActAgent.execute(message, sessionId, userId);
            log.info("AI chat done, sessionId={}", sessionId);
            return result;
        } catch (Exception e) {
            log.error("AI chat error, sessionId={}", sessionId, e);
            return "抱歉，服务暂时不可用，请稍后再试。";
        }
    }
}
