package com.autotag.docs.service;

import com.autotag.docs.dto.DocumentResponseDTO;
import com.autotag.docs.dto.N8nCallbackRequest;
import com.autotag.docs.dto.SearchResultDTO;
import com.autotag.docs.entity.Document;
import com.autotag.docs.entity.DocumentStatus;
import com.autotag.docs.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final N8nWebhookService n8nWebhookService;
    private final QdrantService qdrantService;
    private final EmbeddingService embeddingService;

    public DocumentService(DocumentRepository documentRepository,
                           FileStorageService fileStorageService,
                           N8nWebhookService n8nWebhookService,
                           QdrantService qdrantService,
                           EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.n8nWebhookService = n8nWebhookService;
        this.qdrantService = qdrantService;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public DocumentResponseDTO uploadAndProcess(MultipartFile file) {
        UUID docId = UUID.randomUUID();
        Path savedPath = fileStorageService.store(file, docId);

        Document document = new Document();
        document.setId(docId);
        document.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf");
        document.setFilePath(savedPath.toString());
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType() != null ? file.getContentType() : "application/pdf");
        document.setStatus(DocumentStatus.PROCESANDO);
        document.setSummary("En cola para analisis y extraccion vectorial en n8n...");
        document.setChunksCount(0);

        Document savedDoc = documentRepository.save(document);
        log.info("Documento registrado en PostgreSQL con ID: {}", savedDoc.getId());

        // Disparo asincrono del Webhook hacia n8n
        n8nWebhookService.triggerN8nIngestion(savedDoc);

        return DocumentResponseDTO.fromEntity(savedDoc);
    }

    @Transactional
    public DocumentResponseDTO handleN8nCallback(UUID documentId, N8nCallbackRequest callback) {
        log.info("CALLBACK RECIBIDO de n8n para Documento {}: Status={}, Tags={}",
                documentId, callback.getStatus(), callback.getTags());

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Documento no encontrado con ID: " + documentId));

        document.setStatus(callback.getStatus() != null ? callback.getStatus() : DocumentStatus.LISTO);
        
        if (callback.getSummary() != null && !callback.getSummary().isBlank()) {
            document.setSummary(callback.getSummary());
        }

        if (callback.getChunksCount() != null) {
            document.setChunksCount(callback.getChunksCount());
        }

        if (callback.getVectorCollection() != null) {
            document.setVectorCollection(callback.getVectorCollection());
        }

        if (callback.getErrorMessage() != null) {
            document.setErrorMessage(callback.getErrorMessage());
        }

        if (callback.getTags() != null && !callback.getTags().isEmpty()) {
            document.clearTags();
            for (String tag : callback.getTags()) {
                document.addTag(tag, 1.0);
            }
        }

        Document updated = documentRepository.save(document);
        log.info("Documento {} actualizado con exito en PostgreSQL", updated.getId());

        return DocumentResponseDTO.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> getAllDocuments(String statusFilter, String tagFilter) {
        List<Document> list;
        if (tagFilter != null && !tagFilter.isBlank()) {
            list = documentRepository.findByTagName(tagFilter.trim());
        } else if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                DocumentStatus status = DocumentStatus.valueOf(statusFilter.toUpperCase().trim());
                list = documentRepository.findByStatusOrderByCreatedAtDesc(status);
            } catch (IllegalArgumentException e) {
                list = documentRepository.findAllByOrderByCreatedAtDesc();
            }
        } else {
            list = documentRepository.findAllByOrderByCreatedAtDesc();
        }

        return list.stream().map(DocumentResponseDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DocumentResponseDTO getDocumentById(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Documento no encontrado con ID: " + id));
        return DocumentResponseDTO.fromEntity(doc);
    }

    @Transactional
    public DocumentResponseDTO reprocess(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Documento no encontrado con ID: " + id));

        doc.setStatus(DocumentStatus.PROCESANDO);
        doc.setErrorMessage(null);
        Document saved = documentRepository.save(doc);

        n8nWebhookService.triggerN8nIngestion(saved);
        return DocumentResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<String> getAllTags() {
        return documentRepository.findAllDistinctTagNames();
    }

    public Resource getFileResource(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Documento no encontrado: " + id));
        String fileName = doc.getId() + ".pdf";
        return fileStorageService.loadAsResource(fileName);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Documento no encontrado: " + id));

        fileStorageService.delete(doc.getId() + ".pdf");
        qdrantService.deleteDocumentPoints(id);
        documentRepository.delete(doc);
        log.info("Documento {} eliminado por completo", id);
    }

    @Transactional(readOnly = true)
    public List<SearchResultDTO> searchSemantically(String query, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<Double> queryEmbedding = embeddingService.generateEmbedding(query);
        List<QdrantService.ScoredChunkResult> chunkHits = qdrantService.searchSimilarChunks(queryEmbedding, limit * 2);

        if (chunkHits.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> docIds = chunkHits.stream().map(QdrantService.ScoredChunkResult::getDocumentId).distinct().toList();
        Map<UUID, Document> docMap = documentRepository.findAllByIds(docIds).stream()
                .collect(Collectors.toMap(Document::getId, d -> d));

        List<SearchResultDTO> results = new ArrayList<>();
        Set<UUID> seenDocs = new HashSet<>();

        for (QdrantService.ScoredChunkResult hit : chunkHits) {
            UUID docId = hit.getDocumentId();
            if (seenDocs.contains(docId)) {
                continue;
            }
            seenDocs.add(docId);

            Document doc = docMap.get(docId);
            List<String> tags = (doc != null && doc.getTags() != null)
                    ? doc.getTags().stream().map(t -> t.getTagName()).toList()
                    : hit.getTags();

            results.add(new SearchResultDTO(
                    docId,
                    doc != null ? doc.getFileName() : hit.getFileName(),
                    Math.round(hit.getScore() * 100.0) / 100.0,
                    hit.getText(),
                    hit.getChunkIndex(),
                    tags,
                    doc != null ? doc.getSummary() : ""
            ));

            if (results.size() >= limit) break;
        }

        return results;
    }
}
