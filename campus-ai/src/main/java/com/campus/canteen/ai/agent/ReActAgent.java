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

    // 支持 Markdown 加粗格式（**Action:** → Action:），跳过冒号后的 * 字符
    private static final Pattern ACTION_PATTERN =
            Pattern.compile("Action:\\s*\\*{0,2}\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_INPUT_PATTERN =
            Pattern.compile("Action\\s*Input:\\s*\\*{0,2}\\s*(.+?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
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
            boolean toolCalled = false;

            for (int i = 0; i < maxIterations; i++) {
                log.info("═══ ReAct stream iteration {}/{} ═══", i + 1, maxIterations);

                String llmOutput = callLLM(messages);
                log.info("LLM output:\n{}", llmOutput);

                // Action → 执行工具，继续循环（优先检查，防止 LLM 跳过工具调用）
                Matcher actionMatcher = ACTION_PATTERN.matcher(llmOutput);
                if (actionMatcher.find()) {
                    String actionName = actionMatcher.group(1).trim().replace("*", "");
                    String actionInput = sanitizeActionInput(extractActionInput(llmOutput));
                    log.info("ReAct stream: Action → {}({})", actionName, actionInput);
                    String observation = toolRegistry.execute(actionName, actionInput);
                    log.info("ReAct stream: Observation → {}", observation);
                    messages.add(new AssistantMessage(llmOutput));
                    messages.add(new UserMessage("Observation: " + observation));
                    toolCalled = true;
                    continue;
                }

                // Final Answer → 流式输出
                Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(llmOutput);
                if (finalMatcher.find()) {
                    // 如果从未调用过工具、工具库非空、且问题涉及业务知识，强制 LLM 先调工具
                    if (!toolCalled && !toolRegistry.isEmpty() && isKnowledgeQuery(userMessage)) {
                        log.info("ReAct stream: Final Answer 但未调用任何工具，强制要求先检索");
                        messages.add(new AssistantMessage(llmOutput));
                        messages.add(new UserMessage(
                            "【系统强制】你还没有调用任何工具就直接回答了，这违反了规则。"
                            + "你必须先用 Action 调用 searchKnowledge 检索知识库，"
                            + "拿到 Observation 后再给出 Final Answer。请立即执行。"));
                        continue;
                    }
                    String finalAnswer = finalMatcher.group(1).trim();
                    log.info("ReAct stream: Final Answer reached, switching to streaming");
                    persistExchange(sessionId, userMessage, finalAnswer);
                    extractLongTermMemory(userMessage, finalAnswer, userId);
                    return streamFinalAnswer(messages, userMessage, finalAnswer);
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
     * 保留对话历史（上下文记忆）+ Observation 事实 + 用户原始问题，去掉 ReAct 格式约束。
     */
    private List<Message> buildCleanMessagesForStreaming(List<Message> conversation,
                                                         String originalQuestion) {
        List<Message> clean = new ArrayList<>();
        clean.add(new SystemMessage(
                "你是一个校园食堂智能助手。请根据以下已知信息和对话历史，直接、自然地回答用户的问题。"
                + "不要输出思考过程、推理步骤或任何格式标签，只输出面向用户的最终回答。"));

        for (Message m : conversation) {
            if (m instanceof UserMessage) {
                String text = m.getText();
                // 保留 Observation（工具结果）和历史用户消息，跳过 ReAct 格式纠错提示
                if (text.startsWith("Observation:")
                        || text.startsWith("【系统强制】")
                        || text.startsWith("你的输出不符合")) {
                    if (text.startsWith("Observation:")) {
                        clean.add(m);
                    }
                } else if (!text.equals(originalQuestion)) {
                    clean.add(m);  // 历史对话中的用户消息
                }
            } else if (m instanceof AssistantMessage) {
                clean.add(m);  // 历史对话中的助手回复
            }
        }

        clean.add(new UserMessage(originalQuestion));
        return clean;
    }

//    /**
//     * 零成本伪流式：将已有文本按中英文句末标点切分，逐段推送。
//     * 作为 StreamingChatModel 不可用或流式调用失败时的降级方案。
//     */
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

    /**
     * 清洗 LLM 生成的工具参数：去除填充语、引号、限制长度。
     * 防止 LLM 输出 "让我查一下退款规则" 而不是纯粹的 "退款规则"。
     */
    private String sanitizeActionInput(String input) {
        if (input == null || input.isBlank()) return "";

        String cleaned = input;

        // 去除常见填充前缀
        String[] fillerPrefixes = {
            "让我查一下", "我来搜索", "我来查询", "我来帮你查", "我来帮你查询",
            "让我搜索", "让我查询", "帮我查", "帮我搜索", "请查询",
            "搜索", "查询", "查找"
        };
        for (String filler : fillerPrefixes) {
            if (cleaned.startsWith(filler)) {
                cleaned = cleaned.substring(filler.length()).trim();
            }
        }

        // 去除周围引号
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("「") && cleaned.endsWith("」"))
                || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        // 限制最大长度（防止 LLM 输出整段文本作为查询）
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100);
            log.warn("Action Input 过长，截断为: {}", cleaned);
        }

        return cleaned;
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

    /**
     * 判断用户消息是否涉及业务知识（食堂/学校相关），决定是否强制调用工具。
     * 寒暄类消息（你好、谢谢等）不强制，避免无谓的 RAG 调用。
     */
    private boolean isKnowledgeQuery(String message) {
        if (message == null || message.isBlank()) return false;
        String[] keywords = {
            "食堂", "餐厅", "吃饭", "菜品", "菜", "饭", "价格", "多少钱",
            "营业", "开门", "关门", "时间", "地址", "在哪", "位置",
            "支付", "付款", "退款", "退钱", "取消", "校园卡",
            "外卖", "配送", "订单", "状态",
            "学校", "学院", "宿舍", "图书馆", "交通", "校园",
            "重邮", "CQUPT", "邮电",
            "怎么", "如何", "什么", "哪", "几", "推荐"
        };
        for (String kw : keywords) {
            if (message.contains(kw)) return true;
        }
        return false;
    }
}
