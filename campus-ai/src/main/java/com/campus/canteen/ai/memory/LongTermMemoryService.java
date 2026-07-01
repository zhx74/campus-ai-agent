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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final String USER_ID_PREFIX = "user:";

    public LongTermMemoryService(VectorStore vectorStore, StringRedisTemplate redisTemplate,
                                 MemoryExtractor memoryExtractor, int maxFactsPerUser) {
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
        this.memoryExtractor = memoryExtractor;
        this.maxFactsPerUser = maxFactsPerUser;
    }

    private static final String RESTORE_KEY = "memory:restored";

    @PostConstruct
    public void restore() {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(RESTORE_KEY))) {
                log.info("长期记忆已在 Milvus 中，跳过回灌");
                return;
            }
        } catch (Exception e) {
            log.warn("检查回灌标记失败，继续执行回灌: {}", e.getMessage());
        }

        log.info("开始从 Redis 回灌长期记忆...");
        var keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            log.info("未找到长期记忆数据");
            markRestored();
            return;
        }

        // 收集所有文档，按 25 条一批写入 Milvus（DashScope Embedding API 限制）
        List<Document> allDocs = new ArrayList<>();
        for (String key : keys) {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            for (Object entry : entries.values()) {
                try {
                    Map<String, Object> map = objectMapper.readValue(
                            entry.toString(), new TypeReference<>() {});
                    String id = (String) map.get("id");
                    String text = (String) map.get("text");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
                    allDocs.add(new Document(id, text, metadata != null ? metadata : Map.of()));
                } catch (Exception e) {
                    log.error("回灌记忆解析失败: {}", entry, e);
                }
            }
        }

        int BATCH_SIZE = 25;
        for (int i = 0; i < allDocs.size(); i += BATCH_SIZE) {
            var batch = allDocs.subList(i, Math.min(i + BATCH_SIZE, allDocs.size()));
            try {
                vectorStore.add(batch);
            } catch (Exception e) {
                log.error("回灌批次写入失败（{}/{}）: {}", i / BATCH_SIZE + 1,
                        (allDocs.size() + BATCH_SIZE - 1) / BATCH_SIZE, e.getMessage());
            }
        }
        log.info("长期记忆回灌完成，共 {} 条", allDocs.size());
        markRestored();
    }

    private void markRestored() {
        try {
            redisTemplate.opsForValue().set(RESTORE_KEY, "1");
        } catch (Exception e) {
            log.warn("回灌标记写入失败: {}", e.getMessage());
        }
    }

    public void save(String userId, String fact, Map<String, Object> meta) {
        String id = UUID.randomUUID().toString();
        var metadata = new java.util.HashMap<>(meta != null ? meta : Map.of());
        metadata.put("_id", id);
        Document doc = new Document(id, USER_ID_PREFIX + userId + " | " + fact, metadata);
        try {
            vectorStore.add(List.of(doc));
            String json = objectMapper.writeValueAsString(doc);
            redisTemplate.opsForHash().put(REDIS_KEY_PREFIX + userId, id, json);
        } catch (Exception e) {
            log.error("长期记忆保存失败，userId={}", userId, e);
        }
    }

    public synchronized void extractAndSave(String userId, String dialogue) {
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
                    .topK(topK * 10)  // 多取一些，按用户过滤后仍够数
                    .build();
            List<Document> results = vectorStore.similaritySearch(searchRequest);
            if (results == null || results.isEmpty()) {
                log.info("长期记忆搜索无结果，userId={}, query={}", userId, query);
                return null;
            }

            // 只保留属于该用户的记忆（以 "user:{userId}" 开头的文档）
            String userPrefix = USER_ID_PREFIX + userId + " | ";
            List<Document> userMemories = results.stream()
                    .filter(d -> d.getText() != null && d.getText().startsWith(userPrefix))
                    .limit(topK)
                    .collect(Collectors.toList());

            log.info("长期记忆搜索：共返回 {} 条，其中用户记忆 {} 条", results.size(), userMemories.size());
            for (Document d : userMemories) {
                log.info("  → {}", d.getText());
            }

            if (userMemories.isEmpty()) {
                log.info("长期记忆：无匹配的用户记忆，userId={}", userId);
                return null;
            }

            String joined = userMemories.stream()
                    .map(d -> "- " + d.getText().replace(userPrefix, ""))
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
            // Milvus 支持原生按 id 删除，无需 Redis 标记补偿
            vectorStore.delete(List.of(factId));
        } catch (Exception e) {
            log.error("长期记忆删除失败，userId={}", userId, e);
        }
    }
}
