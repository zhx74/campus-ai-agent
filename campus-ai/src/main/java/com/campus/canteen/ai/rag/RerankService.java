package com.campus.canteen.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * DashScope Rerank 服务 — 对候选文档做精排
 * 使用 gte-rerank 模型，输入 (query, documents[]) 返回按相关性排序的结果
 */
@Slf4j
public class RerankService {

    private static final String RERANK_URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank";
    private static final String DEFAULT_MODEL = "gte-rerank";

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RerankService(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public RerankService(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RerankService: DashScope API Key 为空，Rerank 功能将降级跳过");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.restTemplate = createRestTemplateWithTimeout();
        this.objectMapper = new ObjectMapper();
    }

    private RestTemplate createRestTemplateWithTimeout() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);   // 5s 连接超时
        factory.setReadTimeout(10000);     // 10s 读取超时
        return new RestTemplate(factory);
    }

    /**
     * 对候选文档做重排序
     *
     * @param query      用户查询
     * @param candidates 候选文档（text → Document 映射）
     * @param topK       返回前 K 条
     * @return 排序后的文本列表；若 API 调用失败返回空列表（调用方应 fallback）
     */
    public List<String> rerank(String query, List<String> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() == 1) {
            return candidates;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("query", query);
            input.put("documents", candidates);

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("top_n", topK);
            parameters.put("return_documents", false);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", input);
            body.put("parameters", parameters);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    RERANK_URL, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseRerankResponse(response.getBody(), candidates);
            } else {
                log.warn("Rerank API 返回异常状态: {}", response.getStatusCode());
                return List.of();
            }
        } catch (Exception e) {
            log.warn("Rerank API 调用失败，将使用粗排结果: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseRerankResponse(String responseBody, List<String> candidates) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.path("output").path("results");

            List<String> reranked = new ArrayList<>();
            for (JsonNode r : results) {
                int index = r.path("index").asInt(-1);
                double score = r.path("relevance_score").asDouble(0);
                if (index >= 0 && index < candidates.size()) {
                    log.debug("Rerank: index={}, score={}", index, score);
                    reranked.add(candidates.get(index));
                }
            }
            log.info("Rerank 完成，返回 {} 条结果", reranked.size());
            return reranked;
        } catch (Exception e) {
            log.warn("Rerank 响应解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
