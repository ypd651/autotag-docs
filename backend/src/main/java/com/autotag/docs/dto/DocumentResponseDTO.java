package com.autotag.docs.dto;

import com.autotag.docs.entity.Document;
import com.autotag.docs.entity.DocumentStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DocumentResponseDTO {
    private UUID id;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private DocumentStatus status;
    private String summary;
    private Integer chunksCount;
    private String vectorCollection;
    private String errorMessage;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;

    public DocumentResponseDTO() {}

    public static DocumentResponseDTO fromEntity(Document doc) {
        DocumentResponseDTO dto = new DocumentResponseDTO();
        dto.setId(doc.getId());
        dto.setFileName(doc.getFileName());
        dto.setFileSize(doc.getFileSize());
        dto.setMimeType(doc.getMimeType());
        dto.setStatus(doc.getStatus());
        dto.setSummary(doc.getSummary());
        dto.setChunksCount(doc.getChunksCount());
        dto.setVectorCollection(doc.getVectorCollection());
        dto.setErrorMessage(doc.getErrorMessage());
        dto.setCreatedAt(doc.getCreatedAt());
        dto.setUpdatedAt(doc.getUpdatedAt());

        List<String> tagList = doc.getTags() != null
                ? doc.getTags().stream().map(t -> t.getTagName()).collect(Collectors.toList())
                : Collections.emptyList();
        dto.setTags(tagList);

        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getChunksCount() { return chunksCount; }
    public void setChunksCount(Integer chunksCount) { this.chunksCount = chunksCount; }

    public String getVectorCollection() { return vectorCollection; }
    public void setVectorCollection(String vectorCollection) { this.vectorCollection = vectorCollection; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
