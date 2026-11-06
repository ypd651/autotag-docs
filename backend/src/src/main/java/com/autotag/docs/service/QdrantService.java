package com.autotag.docs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class QdrantService {

    private static final Logger log = LoggerFactory.getLogger(QdrantService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6333}")
    private int qdrantPort;

    @Value("${qdrant.collection:autotag_documents}")
    private String collectionName;

    @Value("${qdrant.vector-size:384}")
    private int vectorSize;

    public QdrantService(WebClient webClient) {
        this.webClient = webClient;
    }

    private String getBaseUrl() {
        return "http://" + qdrantHost + ":" + qdrantPort;
    }

    public static class ScoredChunkResult {
        private UUID documentId;
        private String fileName;
        private Integer chunkIndex;
        private String text;
        private Double score;
        private List<String> tags;

        public ScoredChunkResult(UUID documentId, String fileName, Integer chunkIndex, String text, Double score, List<String> tags) {
            this.documentId = documentId;
            this.fileName = fileName;
            this.chunkIndex = chunkIndex;
            this.text = text;
            this.score = score;
            this.tags = tags;
        }

        public UUID getDocumentId() { return documentId; }
        public String getFileName() { return fileName; }
        public Integer getChunkIndex() { return chunkIndex; }
        public String getText() { return text; }
        public Double getScore() { return score; }
        public List<String> getTags() { return tags; }
    }

    public List<ScoredChunkResult> searchSimilarChunks(List<Double> queryVector, int limit) {
        String url = getBaseUrl() + "/collections/" + collectionName + "/points/search";

        Map<String, Object> requestBody = Map.of(
                "vector", queryVector,
                "limit", limit,
                "with_payload", true,
                "with_vector", false
        );

        try {
            String response = webClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseSearchResponse(response);
        } catch (Exception e) {
            log.error("Error al consultar Qdrant en {}: {}", url, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ScoredChunkResult> parseSearchResponse(String jsonResponse) {
        List<ScoredChunkResult> results = new ArrayList<>();
        if (jsonResponse == null || jsonResponse.isBlank()) {
            return results;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode hits = root.path("result");

            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    double score = hit.path("score").asDouble();
                    JsonNode payload = hit.path("payload");

                    String docIdStr = payload.path("document_id").asText();
                    UUID docId = null;
                    if (docIdStr != null && !docIdStr.isBlank()) {
                        try {
                            docId = UUID.fromString(docIdStr);
                        } catch (IllegalArgumentException ignored) {}
                    }

                    String fileName = payload.path("file_name").asText();
                    int chunkIndex = payload.path("chunk_index").asInt(0);
                    String text = payload.path("text").asText();

                    List<String> tags = new ArrayList<>();
                    JsonNode tagsNode = payload.path("tags");
                    if (tagsNode.isArray()) {
                        for (JsonNode t : tagsNode) {
                            tags.add(t.asText());
                        }
                    }

                    if (docId != null) {
                        results.add(new ScoredChunkResult(docId, fileName, chunkIndex, text, score, tags));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Fallo al parsear respuesta de Qdrant: {}", e.getMessage());
        }

        return results;
    }

    public void deleteDocumentPoints(UUID documentId) {
        String url = getBaseUrl() + "/collections/" + collectionName + "/points/delete";
        Map<String, Object> requestBody = Map.of(
                "filter", Map.of(
                        "must", List.of(
                                Map.of(
                                        "key", "document_id",
                                        "match", Map.of("value", documentId.toString())
                                )
                        )
                )
        );

        try {
            webClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            res -> log.info("Puntos vectoriales eliminados en Qdrant para doc {}", documentId),
                            err -> log.warn("No se pudieron eliminar puntos en Qdrant para doc {}: {}", documentId, err.getMessage())
                    );
        } catch (Exception e) {
            log.warn("Error enviando peticion de eliminacion a Qdrant: {}", e.getMessage());
        }
    }
}
