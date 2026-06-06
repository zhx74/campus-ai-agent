package com.campus.canteen.ai.agent;

import com.campus.canteen.ai.memory.LongTermMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ReActAgent {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final ToolRegistry toolRegistry;
    private final String systemPrompt;
    private final ChatMemory chatMemory;
    private final LongTermMemoryService longTermMemory;
    private final int maxIterations;
    private final int historyWindowSize;
    private final boolean longTermMemoryEnabled;

    private static final Pattern ACTION_PATTERN =
            Pattern.compile("Action:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_INPUT_PATTERN =
            Pattern.compile("Action\\s*Input:\\s*(.+?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FINAL_ANSWER_PATTERN =
            Pattern.compile("Final\\s*Answer:\\s*([\\s\\S]+)", Pattern.CASE_INSENSITIVE);

    public ReActAgent(ChatModel chatModel, StreamingChatModel streamingChatModel,
                      ToolRegistry toolRegistry,
                      String systemPrompt, ChatMemory chatMemory,
                      LongTermMemoryService longTermMemory,
                      int maxIterations, int historyWindowSize,
                      boolean longTermMemoryEnabled) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.toolRegistry = toolRegistry;
        this.systemPrompt = systemPrompt;
        this.chatMemory = chatMemory;
        this.longTermMemory = longTermMemory;
        this.maxIterations = maxIterations;
        this.historyWindowSize = historyWindowSize;
        this.longTermMemoryEnabled = longTermMemoryEnabled;
    }

    public String execute(String userMessage, String sessionId) {
        return execute(userMessage, sessionId, null);
    }

    public String execute(String userMessage, String sessionId, String userId) {
        return executeStream(userMessage, sessionId, userId)
                .collectList()
                .map(list -> String.join("", list))
                .block(Duration.ofSeconds(120));
    }

    /**
     * 流式执行：ReAct 推理循环保持同步，最终答案通过 StreamingChatModel 逐 token 推送。
     * 若 StreamingChatModel 不可用，降级为按句切分的伪流式。
     */
    public Flux<String> executeStream(String userMessage, String sessionId, String userId) {
        return Flux.defer(() -> {
            List<Message> messages = buildMessages(userMessage, sessionId, userId);

            for (int i = 0; i < maxIterations; i++) {
                log.info("═══ ReAct stream iteration {}/{} ═══", i + 1, maxIterations);

                String llmOutput = callLLM(messages);
                log.info("LLM output:\n{}", llmOutput);

                // Final Answer → 流式输出
                Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(llmOutput);
                if (finalMatcher.find()) {
                    String finalAnswer = finalMatcher.group(1).trim();
                    log.info("ReAct stream: Final Answer reached, switching to streaming");
                    persistExchange(sessionId, userMessage, finalAnswer);
                    extractLongTermMemory(userMessage, finalAnswer, userId);
                    return streamFinalAnswer(messages, userMessage, finalAnswer);
                }

                // Action → 执行工具，继续循环
                Matcher actionMatcher = ACTION_PATTERN.matcher(llmOutput);
                if (actionMatcher.find()) {
                    String actionName = actionMatcher.group(1).trim();
                    String actionInput = extractActionInput(llmOutput);
                    log.info("ReAct stream: Action → {}({})", actionName, actionInput);
                    String observation = toolRegistry.execute(actionName, actionInput);
                    log.info("ReAct stream: Observation → {}", observation);
                    messages.add(new AssistantMessage(llmOutput));
                    messages.add(new UserMessage("Observation: " + observation));
                    continue;
                }

                // 格式错误 → 重试
                log.warn("ReAct stream: No structured tag detected, retrying");
                messages.add(new AssistantMessage(llmOutput));
                messages.add(new UserMessage("""
                    你的输出不符合 ReAct 格式要求，请严格按照以下格式重新输出：

                    如果需要调用工具：
                    Thought: <你的思考>
                    Action: <工具名>
                    Action Input: <参数>

                    如果不需要调用工具：
                    Thought: <你的思考>
                    Final Answer: <你的回答>

                    请现在重新输出。"""));
            }

            log.warn("ReAct stream: Max iterations ({}) exceeded", maxIterations);
            String fallback = "抱歉，我暂时无法处理这个请求，请换个方式提问或稍后再试。";
            persistExchange(sessionId, userMessage, fallback);
            return Flux.just(fallback);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 用 StreamingChatModel 流式生成最终答案。
     * 构造干净的 prompt（去掉 ReAct 格式要求），避免模型再次输出 Thought/Action 标签。
     */
    private Flux<String> streamFinalAnswer(List<Message> conversation,
                                           String originalQuestion,
                                           String fallbackAnswer) {
        if (streamingChatModel == null) {
            return pseudoStream(fallbackAnswer);
        }

        List<Message> cleanMessages = buildCleanMessagesForStreaming(conversation, originalQuestion);

        return streamingChatModel.stream(new Prompt(cleanMessages))
                .map(response -> {
                    if (response.getResult() != null
                            && response.getResult().getOutput() != null
                            && response.getResult().getOutput().getText() != null) {
                        return response.getResult().getOutput().getText();
                    }
                    return "";
                })
                .filter(text -> !text.isEmpty())
                .onErrorResume(e -> {
                    log.warn("Streaming LLM call failed, falling back to pseudo-stream", e);
                    return pseudoStream(fallbackAnswer);
                });
    }

    /**
     * 构造流式调用的干净 prompt：
     * 只保留 Observation 事实 + 用户原始问题，去掉 ReAct 格式约束。
     */
    private List<Message> buildCleanMessagesForStreaming(List<Message> conversation,
                                                         String originalQuestion) {
        List<Message> clean = new ArrayList<>();
        clean.add(new SystemMessage(
                "你是一个校园食堂智能助手。请根据以下已知信息，直接、自然地回答用户的问题。"
                + "不要输出思考过程、推理步骤或任何格式标签，只输出面向用户的最终回答。"));

        for (Message m : conversation) {
            if (m instanceof UserMessage) {
                String text = m.getText();
                if (text.startsWith("Observation:")) {
                    clean.add(m);
                }
            }
        }

        clean.add(new UserMessage(originalQuestion));
        return clean;
    }

    /**
     * 零成本伪流式：将已有文本按中英文句末标点切分，逐段推送。
     * 作为 StreamingChatModel 不可用或流式调用失败时的降级方案。
     */
    private Flux<String> pseudoStream(String text) {
        if (text == null || text.isEmpty()) {
            return Flux.empty();
        }
        String[] parts = text.split("(?<=[。！？；\\n.!?;])");
        return Flux.fromArray(parts)
                .filter(s -> !s.isEmpty())
                .delayElements(Duration.ofMillis(40));
    }

    private List<Message> buildMessages(String userMessage, String sessionId, String userId) {
        List<Message> messages = new ArrayList<>();

        String fullPrompt = systemPrompt;

        if (!toolRegistry.isEmpty()) {
            fullPrompt += "\n\n【可用工具】\n" + toolRegistry.describeAll();
        }

        // 注入长期记忆
        if (longTermMemoryEnabled && longTermMemory != null && userId != null) {
            String memories = longTermMemory.search(userId, userMessage, 5);
            if (memories != null && !memories.isEmpty()) {
                fullPrompt += "\n\n【关于该用户的长期记忆】\n" + memories;
                log.info("长期记忆注入，userId={}", userId);
            }
        }

        messages.add(new SystemMessage(fullPrompt));

        // 短期记忆（对话历史）
        List<Message> history = chatMemory.get(sessionId);
        if (!history.isEmpty()) {
            int from = Math.max(0, history.size() - historyWindowSize);
            messages.addAll(history.subList(from, history.size()));
        }

        messages.add(new UserMessage(userMessage));
        return messages;
    }

    private String callLLM(List<Message> messages) {
        Prompt prompt = new Prompt(messages);
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }

    private String extractActionInput(String llmOutput) {
        Matcher m = ACTION_INPUT_PATTERN.matcher(llmOutput);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private void persistExchange(String sessionId, String userMessage, String assistantMessage) {
        try {
            chatMemory.add(sessionId, List.of(
                    new UserMessage(userMessage),
                    new AssistantMessage(assistantMessage)
            ));
        } catch (Exception e) {
            log.error("Failed to persist chat memory for session {}", sessionId, e);
        }
    }

    private void extractLongTermMemory(String userMessage, String assistantMessage, String userId) {
        if (!longTermMemoryEnabled || longTermMemory == null || userId == null) {
            return;
        }
        try {
            String combined = "用户: " + userMessage + "\n助手: " + assistantMessage;
            longTermMemory.extractAndSave(userId, combined);
        } catch (Exception e) {
            log.error("Failed to extract long-term memory for userId={}", userId, e);
        }
    }
}
