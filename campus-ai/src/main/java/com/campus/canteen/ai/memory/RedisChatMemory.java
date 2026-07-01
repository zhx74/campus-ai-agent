package com.campus.canteen.ai.memory;

import com.campus.canteen.ai.dto.ChatMessageDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class RedisChatMemory implements ChatMemory {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatModel chatModel;
    private final int maxMessages;
    private final boolean summaryEnabled;

    private static final String KEY_PREFIX = "chat:history:";
    private static final long TTL = Duration.ofHours(24).getSeconds();
    public static final int DEFAULT_MAX_MESSAGES = 20;

    public RedisChatMemory(StringRedisTemplate redisTemplate) {
        this(redisTemplate, null, DEFAULT_MAX_MESSAGES, false);
    }

    public RedisChatMemory(StringRedisTemplate redisTemplate, ChatModel chatModel,
                           int maxMessages, boolean summaryEnabled) {
        this.redisTemplate = redisTemplate;
        this.chatModel = chatModel;
        this.maxMessages = maxMessages;
        this.summaryEnabled = summaryEnabled;
    }

    private void addMessage(String conversationId, String role, String content) {
        String key = KEY_PREFIX + conversationId;
        try {
            ChatMessageDTO message = new ChatMessageDTO(role, content);
            String messageJson = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(key, messageJson);
            redisTemplate.expire(key, TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis 会话写入失败，降级跳过，conversationId={}", conversationId, e);
        }
    }

    private List<ChatMessageDTO> getAllHistory(String conversationId) {
        try {
            String key = KEY_PREFIX + conversationId;
            List<String> messagesJson = redisTemplate.opsForList().range(key, 0, -1);
            if (messagesJson == null || messagesJson.isEmpty()) {
                return List.of();
            }
            return messagesJson.stream()
                    .map(this::parseMessage)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Redis 读取失败，降级为空记忆，conversationId={}", conversationId, e);
            return List.of();
        }
    }

    private ChatMessageDTO parseMessage(String json) {
        try {
            return objectMapper.readValue(json, ChatMessageDTO.class);
        } catch (JsonProcessingException e) {
            log.warn("消息反序列化失败，跳过该条: {}", e.getMessage());
            return null;
        }
    }

    private void trimHistory(String conversationId, int keepFromIndex) {
        try {
            String key = KEY_PREFIX + conversationId;
            redisTemplate.opsForList().trim(key, keepFromIndex, -1);
        } catch (Exception e) {
            log.error("Redis trim 失败，conversationId={}", conversationId, e);
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message msg : messages) {
            String role = msg.getMessageType().getValue();
            String content = msg.getText();
            addMessage(conversationId, role, content);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        List<ChatMessageDTO> all = getAllHistory(conversationId);
        if (all.isEmpty()) {
            return List.of();
        }
        if (all.size() <= maxMessages) {
            return toMessages(all);
        }

        int overflowCount = all.size() - maxMessages;
        List<ChatMessageDTO> overflow = new ArrayList<>(all.subList(0, overflowCount));
        List<ChatMessageDTO> recent = new ArrayList<>(all.subList(overflowCount, all.size()));

        List<Message> result = new ArrayList<>();
        if (summaryEnabled && chatModel != null) {
            String summary = generateSummary(overflow);
            if (summary != null) {
                // 裁剪旧消息并将摘要写回 Redis 头部，保证下次 get() 仍可获取上下文
                trimHistory(conversationId, overflowCount);
                persistSummary(conversationId, summary);
                result.add(new UserMessage("【对话历史摘要】" + summary));
            } else {
                result.addAll(toMessages(overflow));
            }
        } else {
            result.addAll(toMessages(overflow));
        }
        result.addAll(toMessages(recent));
        return result;
    }

    /**
     * 将摘要作为系统消息插入 Redis 列表头部
     */
    private void persistSummary(String conversationId, String summary) {
        try {
            String key = KEY_PREFIX + conversationId;
            ChatMessageDTO summaryMsg = new ChatMessageDTO("system", "对话历史摘要：" + summary);
            String json = objectMapper.writeValueAsString(summaryMsg);
            redisTemplate.opsForList().leftPush(key, json);
        } catch (Exception e) {
            log.warn("摘要持久化失败，conversationId={}", conversationId, e);
        }
    }

    public List<Message> get(String conversationId, int lastN) {
        List<ChatMessageDTO> all = getAllHistory(conversationId);
        if (all.size() > lastN) {
            all = all.subList(all.size() - lastN, all.size());
        }
        return toMessages(all);
    }

    @Override
    public void clear(String conversationId) {
        try {
            redisTemplate.delete(KEY_PREFIX + conversationId);
        } catch (Exception e) {
            log.error("Redis 清除失败，conversationId={}", conversationId, e);
        }
    }

    private String generateSummary(List<ChatMessageDTO> messages) {
        StringBuilder history = new StringBuilder();
        for (ChatMessageDTO msg : messages) {
            String role = "user".equals(msg.getRole()) ? "用户" : "助手";
            history.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        String prompt = """
            请用2-3句话总结以下对话的关键信息，只保留：
            - 用户关注的问题/需求
            - 已经提供的关键信息
            - 待处理的事项
            不要添加任何解释，直接输出摘要。

            """ + history;
        try {
            String summary = chatModel.call(prompt);
            return summary;
        } catch (Exception e) {
            log.error("摘要生成失败", e);
            return null;
        }
    }

    private List<Message> toMessages(List<ChatMessageDTO> dtos) {
        return dtos.stream()
                .map(dto -> "user".equals(dto.getRole())
                        ? new UserMessage(dto.getContent())
                        : new AssistantMessage(dto.getContent()))
                .collect(Collectors.toList());
    }
}
