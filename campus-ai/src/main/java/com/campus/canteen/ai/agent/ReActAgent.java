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
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ReActAgent {

    private final ChatModel chatModel;
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

    public ReActAgent(ChatModel chatModel, ToolRegistry toolRegistry,
                      String systemPrompt, ChatMemory chatMemory,
                      LongTermMemoryService longTermMemory,
                      int maxIterations, int historyWindowSize,
                      boolean longTermMemoryEnabled) {
        this.chatModel = chatModel;
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
        List<Message> messages = buildMessages(userMessage, sessionId, userId);

        for (int i = 0; i < maxIterations; i++) {
            log.info("═══ ReAct iteration {}/{} ═══", i + 1, maxIterations);

            String llmOutput = callLLM(messages);
            log.info("LLM output:\n{}", llmOutput);

            Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(llmOutput);
            if (finalMatcher.find()) {
                String finalAnswer = finalMatcher.group(1).trim();
                log.info("ReAct: Final Answer reached");
                persistExchange(sessionId, userMessage, finalAnswer);
                extractLongTermMemory(userMessage, finalAnswer, userId);
                return finalAnswer;
            }

            Matcher actionMatcher = ACTION_PATTERN.matcher(llmOutput);
            if (actionMatcher.find()) {
                String actionName = actionMatcher.group(1).trim();
                String actionInput = extractActionInput(llmOutput);
                log.info("ReAct: Action → {}({})", actionName, actionInput);
                String observation = toolRegistry.execute(actionName, actionInput);
                log.info("ReAct: Observation → {}", observation);
                messages.add(new AssistantMessage(llmOutput));
                messages.add(new UserMessage("Observation: " + observation));
                continue;
            }

            log.warn("ReAct: No structured tag detected, asking LLM to retry");
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

        log.warn("ReAct: Max iterations ({}) exceeded, returning fallback", maxIterations);
        String fallback = "抱歉，我暂时无法处理这个请求，请换个方式提问或稍后再试。";
        persistExchange(sessionId, userMessage, fallback);
        return fallback;
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
