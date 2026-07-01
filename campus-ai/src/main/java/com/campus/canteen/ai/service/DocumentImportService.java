package com.campus.canteen.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentImportService {

    private static final int BATCH_SIZE = 25; // DashScope Embedding API 单次最多 25 条

    private final VectorStore vectorStore;

    public DocumentImportService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 从 classpath:docs/ 目录读取所有 TXT 文件，切片后存入 Milvus
     */
    public Map<String, Object> importDocuments() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Document> allChunks = new ArrayList<>();

        try {
            // 扫描 classpath:docs/*.txt
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:docs/*.txt");

            if (resources.length == 0) {
                log.warn("classpath:docs/ 下没有找到 TXT 文件");
                result.put("status", "no_files");
                result.put("message", "classpath:docs/ 下没有找到 TXT 文件");
                return result;
            }

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                log.info("读取文档: {}", filename);

                // 读取文件内容
                String content;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    content = reader.lines().collect(Collectors.joining("\n"));
                }

                if (content.isBlank()) {
                    log.warn("跳过空文件: {}", filename);
                    continue;
                }

                // 创建 Document 对象，附带来源元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", filename);
                metadata.put("type", "imported_doc");
                Document doc = new Document(content, metadata);

                // TokenTextSplitter 切片：每块约 500 token，最少 100 字符
                TokenTextSplitter splitter = TokenTextSplitter.builder()
                        .withChunkSize(500)
                        .withMinChunkSizeChars(100)
                        .withMinChunkLengthToEmbed(50)
                        .withMaxNumChunks(50)
                        .withKeepSeparator(true)
                        .build();

                List<Document> chunks = splitter.apply(List.of(doc));
                log.info("文档 {} 切片完成，生成 {} 个片段", filename, chunks.size());
                allChunks.addAll(chunks);
            }

            if (allChunks.isEmpty()) {
                result.put("status", "empty");
                result.put("message", "所有文件内容为空或切片失败");
                return result;
            }

            // 分批写入 Milvus（每批 25 条，DashScope 限制）
            int totalBatches = (allChunks.size() + BATCH_SIZE - 1) / BATCH_SIZE;
            for (int i = 0; i < allChunks.size(); i += BATCH_SIZE) {
                var batch = allChunks.subList(i, Math.min(i + BATCH_SIZE, allChunks.size()));
                int batchNum = i / BATCH_SIZE + 1;
                try {
                    vectorStore.add(batch);
                    log.info("文档导入进度: 批次 {}/{}，本批 {} 条", batchNum, totalBatches, batch.size());
                } catch (Exception e) {
                    log.error("文档导入失败，批次 {}: {}", batchNum, e.getMessage());
                    result.put("status", "partial_fail");
                    result.put("error", "批次 " + batchNum + " 失败: " + e.getMessage());
                    return result;
                }
            }

            log.info("文档导入完成，共 {} 个切片写入 Milvus", allChunks.size());
            result.put("status", "success");
            result.put("totalFiles", resources.length);
            result.put("totalChunks", allChunks.size());
            result.put("message", "导入成功，共处理 " + resources.length + " 个文件，生成 " + allChunks.size() + " 个向量切片");

        } catch (Exception e) {
            log.error("文档导入异常: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("error", e.getMessage());
        }

        return result;
    }
}
