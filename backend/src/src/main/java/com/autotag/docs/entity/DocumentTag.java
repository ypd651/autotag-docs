package com.autotag.docs.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "document_tags")
public class DocumentTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @JsonBackReference
    private Document document;

    @Column(name = "tag_name", nullable = false, length = 100)
    private String tagName;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore = BigDecimal.valueOf(1.0);

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public DocumentTag() {}

    public DocumentTag(Document document, String tagName, BigDecimal confidenceScore) {
        this.document = document;
        this.tagName = tagName;
        this.confidenceScore = confidenceScore != null ? confidenceScore : BigDecimal.valueOf(1.0);
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
