package com.autotag.docs.dto;

import java.util.List;
import java.util.UUID;

public class SearchResultDTO {
    private UUID documentId;
    private String fileName;
    private Double score;
    private String matchingSnippet;
    private Integer chunkIndex;
    private List<String> tags;
    private String summary;

    public SearchResultDTO() {}

    public SearchResultDTO(UUID documentId, String fileName, Double score, String matchingSnippet, Integer chunkIndex, List<String> tags, String summary) {
        this.documentId = documentId;
        this.fileName = fileName;
        this.score = score;
        this.matchingSnippet = matchingSnippet;
        this.chunkIndex = chunkIndex;
        this.tags = tags;
        this.summary = summary;
    }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getMatchingSnippet() { return matchingSnippet; }
    public void setMatchingSnippet(String matchingSnippet) { this.matchingSnippet = matchingSnippet; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
