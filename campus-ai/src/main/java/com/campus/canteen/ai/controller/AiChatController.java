package com.campus.canteen.ai.controller;

import com.campus.canteen.ai.service.AiChatService;
import com.campus.canteen.ai.dto.ChatDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
@Tag(name = "AI 智能客服")
@Slf4j
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI 对话（流式 SSE）")
    public Flux<String> chatStream(@RequestBody ChatDTO chatDTO) {
        log.info("AI stream chat");
        return aiChatService.chatStream(
                chatDTO.getMessage(),
                chatDTO.getSessionId(),
                chatDTO.getUserId()
        );
    }

    @PostMapping("/chat")
    @Operation(summary = "AI 对话（同步）")
    public String chat(@RequestBody ChatDTO chatDTO) {
        log.info("AI sync chat");
        return aiChatService.chat(
                chatDTO.getMessage(),
                chatDTO.getSessionId(),
                chatDTO.getUserId()
        );
    }
}
