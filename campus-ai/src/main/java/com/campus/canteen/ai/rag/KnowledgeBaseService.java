package com.campus.canteen.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

@Slf4j
public class KnowledgeBaseService {

    private final VectorStore vectorStore;

    public KnowledgeBaseService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String retrieveKnowledge(String query) {
        log.info("向量检索知识，query={}", query);
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(3)
                .build();
        List<Document> results = vectorStore.similaritySearch(searchRequest);
        if (results == null || results.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【相关知识】\n");
        for (int i = 0; i < results.size(); i++) {
            sb.append(i + 1).append(". ").append(results.get(i).getText()).append("\n");
        }
        return sb.toString();
    }
}
