package com.restaurant.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class CatalogRAGService {

    private static final Logger log = LoggerFactory.getLogger(CatalogRAGService.class);
    private static final MediaType JSON_MT = MediaType.parse("application/json");

    private final JdbcTemplate jdbcTemplate;
    private final OkHttpClient httpClient;
    private final ObjectMapper om;

    @Value("${app.openai.api-key}") private String apiKey;
    @Value("${app.openai.base-url}") private String baseUrl;
    @Value("${app.openai.embedding-model}") private String embModel;
    @Value("${app.conversation.max-rag-results}") private int maxResults;

    public CatalogRAGService(JdbcTemplate jdbc, OkHttpClient http, ObjectMapper om) {
        this.jdbcTemplate = jdbc; this.httpClient = http; this.om = om;
    }

    public record RAGResult(String entityType, Long entityId, String content, double similarity) {}

    public float[] generateEmbedding(String text) {
        try {
            var body = om.createObjectNode(); body.put("model", embModel); body.put("input", text);
            var req = new Request.Builder().url(baseUrl + "/v1/embeddings")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(om.writeValueAsString(body), JSON_MT)).build();
            try (Response res = httpClient.newCall(req).execute()) {
                if (!res.isSuccessful()) return null;
                JsonNode root = om.readTree(res.body().string());
                JsonNode emb = root.path("data").get(0).path("embedding");
                float[] v = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) v[i] = (float) emb.get(i).asDouble();
                return v;
            }
        } catch (IOException e) { log.error("Embedding error", e); return null; }
    }

    public void storeEmbedding(String type, Long id, String content, float[] emb) {
        jdbcTemplate.update("""
            INSERT INTO catalog_embeddings (entity_type,entity_id,content,embedding)
            VALUES(?,?,?,?::vector)
            ON CONFLICT (entity_type, entity_id)
            DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding, created_at = NOW()
            """, type, id, content, Arrays.toString(emb));
    }

    public List<RAGResult> search(String query) {
        float[] qe = generateEmbedding(query);
        if (qe == null) return searchByText(query);
        String v = Arrays.toString(qe);
        return jdbcTemplate.query("SELECT entity_type,entity_id,content,1-(embedding <=> ?::vector) as sim FROM catalog_embeddings ORDER BY embedding <=> ?::vector LIMIT ?",
            (rs, i) -> new RAGResult(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getDouble(4)), v, v, maxResults);
    }

    public List<RAGResult> searchByText(String query) {
        String like = "%" + query + "%";
        try {
            return jdbcTemplate.query("SELECT entity_type,entity_id,content,0.5 as sim FROM catalog_embeddings WHERE lower(content) LIKE lower(?) LIMIT ?",
                (rs, i) -> new RAGResult(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getDouble(4)), like, maxResults);
        } catch (Exception e) { return List.of(); }
    }
}
