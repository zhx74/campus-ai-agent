package com.campus.canteen.ai.spi;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 知识提供者 SPI —— 宿主系统通过此接口提供业务知识文档，用于 RAG 向量检索。
 * 引擎启动时调用 getDocuments() 播种知识库。
 */
@FunctionalInterface
public interface KnowledgeProvider {
    List<Document> getDocuments();
}
