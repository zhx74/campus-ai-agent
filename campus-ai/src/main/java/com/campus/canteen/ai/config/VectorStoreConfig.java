package com.campus.canteen.ai.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.milvus.host:localhost}")
    private String milvusHost;

    @Value("${spring.ai.milvus.port:19530}")
    private int milvusPort;

    @Value("${spring.ai.milvus.collection-name:campus_canteen}")
    private String knowledgeCollectionName;

    @Value("${spring.ai.milvus.memory-collection-name:campus_memory}")
    private String memoryCollectionName;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(milvusHost)
                .withPort(milvusPort)
                .withConnectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .withKeepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        return new MilvusServiceClient(connectParam);
    }

    @Bean
    @Primary
    public VectorStore knowledgeVectorStore(EmbeddingModel dashscopeEmbeddingModel,
                                            MilvusServiceClient milvusServiceClient) {
        return MilvusVectorStore.builder(milvusServiceClient, dashscopeEmbeddingModel)
                .collectionName(knowledgeCollectionName)
                .embeddingDimension(1536)
                .initializeSchema(true)
                .build();
    }

    @Bean
    public VectorStore memoryVectorStore(EmbeddingModel dashscopeEmbeddingModel,
                                         MilvusServiceClient milvusServiceClient) {
        return MilvusVectorStore.builder(milvusServiceClient, dashscopeEmbeddingModel)
                .collectionName(memoryCollectionName)
                .embeddingDimension(1536)
                .initializeSchema(true)
                .build();
    }

    @PreDestroy
    public void closeClient() {
        // MilvusServiceClient 由 Spring 容器管理生命周期，无需手动关闭
    }
}
