package com.campus.canteen.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;

/**
 * 混合检索 + RRF 融合 + Rerank 精排
 *
 * 检索链路：
 * 1. 向量检索（Milvus ANN，top 30）— 语义匹配
 * 2. BM25 关键词检索（候选池内评分）— 精确匹配
 * 3. RRF（Reciprocal Rank Fusion）融合两路排名
 * 4. DashScope gte-rerank 精排取 topK
 */
@Slf4j
public class KnowledgeBaseService {

    private static final int VECTOR_SEARCH_TOP_K = 30;   // 向量粗排召回数
    private static final int RRF_FUSION_TOP_K = 15;       // RRF 融合后候选数
    private static final int FINAL_TOP_K = 3;             // 最终返回数
    private static final double BM25_K1 = 1.5;            // BM25 词频饱和度
    private static final double BM25_B = 0.75;            // BM25 文档长度归一化
    private static final int RRF_K = 60;                  // RRF 平滑常数

    private final VectorStore vectorStore;
    private final RerankService rerankService;

    public KnowledgeBaseService(VectorStore vectorStore, RerankService rerankService) {
        this.vectorStore = vectorStore;
        this.rerankService = rerankService;
    }

    public String retrieveKnowledge(String query) {
        log.info("混合检索知识，query={}", query);
        long start = System.currentTimeMillis();

        // ═══════ Phase 1: 向量检索（语义匹配）═══════
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(VECTOR_SEARCH_TOP_K)
                .build();
        List<Document> vectorDocs = vectorStore.similaritySearch(searchRequest);
        if (vectorDocs == null) {
            vectorDocs = List.of();
        }

        // 向量检索为空时，拆分关键词逐词重试（最多扩展 5 个词，应对 ReAct 传入关键词式查询）
        if (vectorDocs.isEmpty()) {
            log.info("向量检索无结果，尝试拆词扩展召回...");
            Set<String> seenIds = new LinkedHashSet<>();
            List<Document> expanded = new ArrayList<>();
            String[] terms = query.split("[\\s,，、]+");
            int expansionCount = 0;
            for (String term : terms) {
                if (term.isBlank() || expansionCount >= 5) continue;
                expansionCount++;
                SearchRequest expandedReq = SearchRequest.builder()
                        .query(term.trim())
                        .topK(VECTOR_SEARCH_TOP_K)
                        .build();
                List<Document> hits = vectorStore.similaritySearch(expandedReq);
                if (hits != null) {
                    for (Document d : hits) {
                        if (seenIds.add(d.getId())) {
                            expanded.add(d);
                        }
                    }
                }
            }
            vectorDocs = expanded;
            if (!vectorDocs.isEmpty()) {
                log.info("拆词扩展召回 {} 条候选", vectorDocs.size());
            }
        }

        if (vectorDocs.isEmpty()) {
            log.info("所有检索路径均无结果");
            return null;
        }
        log.info("向量检索命中 {} 条候选", vectorDocs.size());

        // 构建候选池（保持向量排名顺序，跳过空文档）
        List<Candidate> candidates = new ArrayList<>();
        for (Document doc : vectorDocs) {
            String text = doc.getText();
            if (text == null || text.isBlank()) continue;
            String source = doc.getMetadata() != null
                    ? String.valueOf(doc.getMetadata().getOrDefault("source", "unknown"))
                    : "unknown";
            candidates.add(new Candidate(text, source));
        }

        if (candidates.isEmpty()) {
            log.info("候选池为空（所有文档 text 为 null）");
            return null;
        }

        // ═══════ Phase 2: BM25 关键词检索（精确匹配）═══════
        // 在候选池上做 BM25 评分（字符 bigram 分词，适合中文）
        List<String> queryTokens = extractBigrams(query);
        double avgDl = candidates.stream().mapToInt(c -> c.text.length()).average().orElse(1.0);
        int N = candidates.size();

        // 计算每个 bigram 的文档频率（DF）
        Map<String, Integer> dfMap = new HashMap<>();
        for (String qt : queryTokens) {
            int df = 0;
            for (Candidate c : candidates) {
                if (c.text.contains(qt)) df++;
            }
            if (df > 0) dfMap.put(qt, df);
        }

        // 对每个候选文档计算 BM25 分数
        Map<Integer, Double> bm25Scores = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            double score = 0;
            String docText = candidates.get(i).text;
            int dl = docText.length();
            for (Map.Entry<String, Integer> entry : dfMap.entrySet()) {
                String term = entry.getKey();
                int df = entry.getValue();
                int tf = countOccurrences(docText, term);
                if (tf > 0) {
                    double idf = Math.log((N - df + 0.5) / (df + 0.5) + 1);
                    double tfNorm = (tf * (BM25_K1 + 1)) / (tf + BM25_K1 * (1 - BM25_B + BM25_B * dl / avgDl));
                    score += idf * tfNorm;
                }
            }
            if (score > 0) {
                bm25Scores.put(i, score);
            }
        }

        // 按 BM25 分数排序得到关键词排名
        List<Integer> keywordRanked = bm25Scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        log.info("BM25 关键词匹配 {} 条（共 {} 个 bigram 命中）",
                keywordRanked.size(), dfMap.size());

        // ═══════ Phase 3: RRF 融合（Reciprocal Rank Fusion）═══════
        Map<Integer, Double> rrfScores = new HashMap<>();

        // 向量排名
        for (int rank = 0; rank < candidates.size(); rank++) {
            rrfScores.merge(rank, 1.0 / (RRF_K + rank + 1), Double::sum);
        }
        // 关键词排名
        for (int rank = 0; rank < keywordRanked.size(); rank++) {
            int idx = keywordRanked.get(rank);
            rrfScores.merge(idx, 1.0 / (RRF_K + rank + 1), Double::sum);
        }

        // 取 RRF 分数最高的候选
        List<Integer> fusedRanked = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(RRF_FUSION_TOP_K)
                .map(Map.Entry::getKey)
                .toList();

        List<String> fusedTexts = fusedRanked.stream()
                .map(i -> candidates.get(i).text)
                .toList();

        log.info("RRF 融合后 {} 条候选", fusedTexts.size());

        // ═══════ Phase 4: Rerank 精排（DashScope gte-rerank）═══════
        List<String> finalTexts;
        if (rerankService != null && fusedTexts.size() > 1) {
            List<String> reranked = rerankService.rerank(query, fusedTexts, FINAL_TOP_K);
            if (reranked != null && !reranked.isEmpty()) {
                finalTexts = reranked;
                log.info("Rerank 精排完成，取 top {}", finalTexts.size());
            } else {
                // Rerank 失败，fallback 到 RRF 结果
                finalTexts = fusedTexts.stream().limit(FINAL_TOP_K).toList();
                log.info("Rerank 降级为 RRF 粗排，取 top {}", finalTexts.size());
            }
        } else {
            finalTexts = fusedTexts.stream().limit(FINAL_TOP_K).toList();
        }

        // ═══════ 格式化输出 ═══════
        StringBuilder sb = new StringBuilder();
        sb.append("【相关知识】\n");
        for (int i = 0; i < finalTexts.size(); i++) {
            sb.append(i + 1).append(". ").append(finalTexts.get(i)).append("\n");
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("知识检索完成，耗时 {}ms（向量→BM25→RRF→Rerank）", elapsed);

        return sb.toString();
    }

    // ─── 辅助方法 ───

    /**
     * 提取文本的字符 bigram（适合中英文混合文本的粗粒度分词）
     * 例如 "重庆邮电大学" → ["重庆", "庆邮", "邮电", "电大", "大学"]
     */
    private List<String> extractBigrams(String text) {
        List<String> bigrams = new ArrayList<>();
        // 去除空白和标点
        String cleaned = text.replaceAll("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF]", "");
        for (int i = 0; i < cleaned.length() - 1; i++) {
            bigrams.add(cleaned.substring(i, i + 2));
        }
        return bigrams;
    }

    /**
     * 统计子串在文本中的出现次数（非重叠）
     */
    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    /**
     * 候选文档封装
     */
    private record Candidate(String text, String source) {}
}
