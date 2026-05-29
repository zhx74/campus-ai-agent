package com.campus.canteen.ai.config;

import com.campus.canteen.ai.rag.KnowledgeBaseService;
import com.campus.canteen.ai.spi.KnowledgeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class KnowledgeBaseConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseConfiguration.class);

    @Bean
    public KnowledgeBaseService knowledgeBaseService(VectorStore vectorStore,
                                                     List<KnowledgeProvider> knowledgeProviders) {
        KnowledgeBaseService service = new KnowledgeBaseService(vectorStore);
        for (KnowledgeProvider provider : knowledgeProviders) {
            var docs = provider.getDocuments();
            if (docs != null && !docs.isEmpty()) {
                try {
                    vectorStore.add(docs);
                    log.info("知识库播种完成，{} 条文档", docs.size());
                } catch (Exception e) {
                    log.warn("知识库播种失败（Embedding 服务不可用），知识检索将受限: {}",
                            e.getMessage());
                }
            }
        }
        return service;
    }
}
