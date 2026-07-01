package com.campus.canteen.ai.config;

import com.campus.canteen.ai.memory.LongTermMemoryService;
import com.campus.canteen.ai.memory.MemoryExtractor;
import com.campus.canteen.ai.memory.RedisChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class AiFoundationConfiguration {

    @Bean
    public RedisChatMemory redisChatMemory(StringRedisTemplate redisTemplate, ChatModel chatModel) {
        return new RedisChatMemory(redisTemplate, chatModel,
                RedisChatMemory.DEFAULT_MAX_MESSAGES, true);
    }

    @Bean
    public ChatMemory chatMemory(RedisChatMemory redisChatMemory) {
        return redisChatMemory;
    }

    @Bean
    @Primary
    public ChatModel primaryChatModel(ChatModel openAiChatModel) {
        return openAiChatModel;
    }

    @Bean
    public MemoryExtractor memoryExtractor(ChatModel chatModel) {
        return new MemoryExtractor(chatModel);
    }

    @Bean
    @ConditionalOnProperty(name = "campus.ai.long-term-memory.enabled", havingValue = "true", matchIfMissing = true)
    public LongTermMemoryService longTermMemoryService(@Qualifier("memoryVectorStore") VectorStore memoryVectorStore,
                                                       StringRedisTemplate redisTemplate,
                                                       MemoryExtractor memoryExtractor) {
        return new LongTermMemoryService(memoryVectorStore, redisTemplate, memoryExtractor, 50);
    }
}
