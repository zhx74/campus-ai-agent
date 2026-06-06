package com.campus.canteen.ai.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class LongTermMemoryService {

    private final VectorStore vectorStore;
    private final StringRedisTemplate redisTemplate;
    private final MemoryExtractor memoryExtractor;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final int maxFactsPerUser;

    private static final String REDIS_KEY_PREFIX = "user:memory:";
    private static final String DELETED_KEY_PREFIX = "user:memory:deleted:";
    private static final String USER_ID_PREFIX = "user:";

    public LongTermMemoryService(VectorStore vectorStore, StringRedisTemplate redisTemplate,
                                 MemoryExtractor memoryExtractor, int maxFactsPerUser) {
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
        this.memoryExtractor = memoryExtractor;
        this.maxFactsPerUser = maxFactsPerUser;
    }

    @PostConstruct
    public void restore() {
        log.info("开始从 Redis 回灌长期记忆...");
        var keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            log.info("未找到长期记忆数据");
            return;
        }
        int total = 0;
        for (String key : keys) {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            for (Object entry : entries.values()) {
                try {
                    Map<String, Object> map = objectMapper.readValue(
                            entry.toString(), new TypeReference<>() {});
                    String text = (String) map.get("text");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
                    Document doc = new Document(text, metadata != null ? metadata : Map.of());
                    vectorStore.add(List.of(doc));
                    total++;
                } catch (Exception e) {
                    log.error("回灌记忆失败: {}", entry, e);
                }
            }
        }
        log.info("长期记忆回灌完成，共 {} 条", total);
    }

    public void save(String userId, String fact, Map<String, Object> meta) {
        String id = UUID.randomUUID().toString();
        var metadata = new java.util.HashMap<>(meta != null ? meta : Map.of());
        metadata.put("_id", id);
        Document doc = new Document(USER_ID_PREFIX + userId + " | " + fact, metadata);
        try {
            vectorStore.add(List.of(doc));
            String json = objectMapper.writeValueAsString(doc);
            redisTemplate.opsForHash().put(REDIS_KEY_PREFIX + userId, id, json);
        } catch (JsonProcessingException e) {
            log.error("长期记忆保存失败，userId={}", userId, e);
        }
    }

    public void extractAndSave(String userId, String dialogue) {
        if (memoryExtractor == null) {
            return;
        }
        Long count = redisTemplate.opsForHash().size(REDIS_KEY_PREFIX + userId);
        if (count != null && count >= maxFactsPerUser) {
            return;
        }
        String extracted = memoryExtractor.extract(dialogue);
        if (extracted == null || extracted.isEmpty()) {
            return;
        }
        for (String line : extracted.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) {
                save(userId, line, Map.of("source", "auto-extract"));
            }
        }
    }

    public String search(String userId, String query, int topK) {
        try {
            String contextualQuery = USER_ID_PREFIX + userId + " | " + query;
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(contextualQuery)
                    .topK(topK)
                    .build();
            List<Document> results = vectorStore.similaritySearch(searchRequest);
            if (results == null || results.isEmpty()) {
                return null;
            }

            // 获取已删除的 factId 集合，过滤掉已从 Redis 删除但 VectorStore 中残留的向量
            Set<String> deletedIds = getDeletedIds(userId);

            String joined = results.stream()
                    .filter(d -> {
                        if (deletedIds.isEmpty()) return true;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> meta = d.getMetadata();
                        String factId = meta != null ? (String) meta.get("_id") : null;
                        return factId == null || !deletedIds.contains(factId);
                    })
                    .map(d -> "- " + d.getText().replace(USER_ID_PREFIX + userId + " | ", ""))
                    .collect(Collectors.joining("\n"));

            return joined.isEmpty() ? null : joined;
        } catch (Exception e) {
            log.error("长期记忆检索失败，userId={}", userId, e);
            return null;
        }
    }

    public void delete(String userId, String factId) {
        try {
            redisTemplate.opsForHash().delete(REDIS_KEY_PREFIX + userId, factId);
            // 记录已删除的 factId，用于在 search() 中过滤 VectorStore 残留向量
            redisTemplate.opsForSet().add(DELETED_KEY_PREFIX + userId, factId);
        } catch (Exception e) {
            log.error("长期记忆删除失败，userId={}", userId, e);
        }
    }

    /**
     * 获取某用户已删除的 factId 集合。
     * SimpleVectorStore 不支持 delete，通过此集合在检索时过滤残留向量。
     */
    private Set<String> getDeletedIds(String userId) {
        try {
            Set<String> ids = redisTemplate.opsForSet().members(DELETED_KEY_PREFIX + userId);
            return ids != null ? ids : Set.of();
        } catch (Exception e) {
            log.warn("获取已删除记忆ID失败，userId={}", userId, e);
            return Set.of();
        }
    }
}
