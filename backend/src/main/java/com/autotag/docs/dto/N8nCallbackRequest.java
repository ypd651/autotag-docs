package com.autotag.docs.dto;

import com.autotag.docs.entity.DocumentStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public class N8nCallbackRequest {

    @NotNull(message = "El documentId es requerido")
    private UUID documentId;

    @NotNull(message = "El status es requerido")
    private DocumentStatus status;

    private List<String> tags;
    private String summary;
    private Integer chunksCount;
    private String vectorCollection;
    private String errorMessage;

    public N8nCallbackRequest() {}

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getChunksCount() { return chunksCount; }
    public void setChunksCount(Integer chunksCount) { this.chunksCount = chunksCount; }

    public String getVectorCollection() { return vectorCollection; }
    public void setVectorCollection(String vectorCollection) { this.vectorCollection = vectorCollection; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
