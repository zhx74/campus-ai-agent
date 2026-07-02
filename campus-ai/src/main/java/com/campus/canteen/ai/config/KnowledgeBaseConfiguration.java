package com.campus.canteen.ai.config;

import com.campus.canteen.ai.rag.KnowledgeBaseService;
import com.campus.canteen.ai.rag.RerankService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库配置 — 从 classpath:docs/*.txt 读取文档，切片后播种到 Milvus。
 * 使用 Redis 标记防止重复播种，文件列表变化时自动重新播种。
 */
@Configuration
public class KnowledgeBaseConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseConfiguration.class);
    private static final String SEED_KEY_PREFIX = "knowledge:seeded:";
    private static final int BATCH_SIZE = 25; // DashScope Embedding API 单次最多 25 条

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${spring.ai.milvus.collection-name:campus_canteen}")
    private String knowledgeCollectionName;

    @Bean
    public RerankService rerankService() {
        return new RerankService(dashscopeApiKey);
    }

    @Bean
    public KnowledgeBaseService knowledgeBaseService(VectorStore vectorStore,
                                                     MilvusServiceClient milvusClient,
                                                     StringRedisTemplate redisTemplate,
                                                     RerankService rerankService) {
        KnowledgeBaseService service = new KnowledgeBaseService(vectorStore, rerankService);

        // 扫描 classpath:docs/*.txt
        Resource[] resources;
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            resources = resolver.getResources("classpath:docs/*.txt");
        } catch (Exception e) {
            log.warn("扫描知识库文件失败: {}", e.getMessage());
            return service;
        }

        if (resources.length == 0) {
            log.warn("classpath:docs/ 下没有找到 TXT 文件，知识库将为空");
            return service;
        }

        // 计算播种指纹：文件数量 + 文件名排序后的 hash
        List<String> filenames = Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
        String fingerprint = resources.length + ":" + filenames.hashCode();
        String seedKey = SEED_KEY_PREFIX + fingerprint;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(seedKey))) {
            log.info("知识库已播种过（{} 个文件，指纹={}），跳过重复插入", resources.length, fingerprint);
            return service;
        }

        // 清理旧的播种标记
        try {
            var oldKeys = redisTemplate.keys(SEED_KEY_PREFIX + "*");
            if (oldKeys != null && !oldKeys.isEmpty()) {
                redisTemplate.delete(oldKeys);
            }
        } catch (Exception e) {
            log.warn("清理旧播种标记失败: {}", e.getMessage());
        }

        // 清除旧的向量数据（保留 collection 结构，只删数据）
        // 不用 dropCollection — 否则 MilvusVectorStore bean 状态不一致导致后续搜索报错
        try {
            R<Boolean> hasCollection = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(knowledgeCollectionName)
                            .build());
            if (hasCollection.getData() != null && hasCollection.getData()) {
                milvusClient.delete(
                        io.milvus.param.dml.DeleteParam.newBuilder()
                                .withCollectionName(knowledgeCollectionName)
                                .withExpr("doc_id != \"\"")
                                .build());
                log.info("已清空旧知识库数据: {}", knowledgeCollectionName);
            }
        } catch (Exception e) {
            log.warn("清空旧数据失败（不影响后续播种）: {}", e.getMessage());
        }

        // 读取并切片文档
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(50)
                .withMaxNumChunks(50)
                .withKeepSeparator(true)
                .build();

        List<Document> allChunks = new ArrayList<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String content = reader.lines().collect(Collectors.joining("\n"));
                if (content.isBlank()) {
                    log.warn("跳过空文件: {}", filename);
                    continue;
                }
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", filename);
                metadata.put("type", "knowledge_base");
                Document doc = new Document(content, metadata);
                List<Document> chunks = splitter.apply(List.of(doc));
                log.info("知识库文件 {} 切片完成，生成 {} 个片段", filename, chunks.size());
                allChunks.addAll(chunks);
            } catch (Exception e) {
                log.warn("读取知识库文件失败 {}: {}", filename, e.getMessage());
            }
        }

        if (allChunks.isEmpty()) {
            log.warn("所有知识库文件切片为空，跳过播种");
            return service;
        }

        // 分批写入 Milvus
        boolean allSuccess = true;
        int totalBatches = (allChunks.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        for (int i = 0; i < allChunks.size(); i += BATCH_SIZE) {
            var batch = allChunks.subList(i, Math.min(i + BATCH_SIZE, allChunks.size()));
            int batchNum = i / BATCH_SIZE + 1;
            try {
                vectorStore.add(batch);
                log.info("知识库播种进度: 批次 {}/{}，本批 {} 条", batchNum, totalBatches, batch.size());
            } catch (Exception e) {
                allSuccess = false;
                log.warn("知识库播种失败（批次 {}）: {}", batchNum, e.getMessage());
                break;
            }
        }

        if (allSuccess) {
            log.info("知识库播种完成，{} 个文件共生成 {} 个向量切片", resources.length, allChunks.size());
            try {
                redisTemplate.opsForValue().set(seedKey, String.valueOf(allChunks.size()));
            } catch (Exception e) {
                log.warn("知识库播种标记写入失败: {}", e.getMessage());
            }
        }
        return service;
    }
}
