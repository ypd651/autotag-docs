package com.autotag.docs.controller;

import com.autotag.docs.dto.DocumentResponseDTO;
import com.autotag.docs.dto.N8nCallbackRequest;
import com.autotag.docs.service.DocumentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponseDTO> uploadDocument(@RequestParam("file") MultipartFile file) {
        log.info("Recibida peticion de subida de archivo: {}", file.getOriginalFilename());
        DocumentResponseDTO response = documentService.uploadAndProcess(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponseDTO>> getAllDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tag) {
        return ResponseEntity.ok(documentService.getAllDocuments(status, tag));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> getDocumentById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @PostMapping("/{id}/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @PathVariable UUID id,
            @Valid @RequestBody N8nCallbackRequest callbackRequest) {
        log.info("Recibido Webhook Callback de n8n para el documento {}", id);
        DocumentResponseDTO updated = documentService.handleN8nCallback(id, callbackRequest);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Estado de documento actualizado exitosamente",
                "documentId", updated.getId(),
                "status", updated.getStatus()
        ));
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<DocumentResponseDTO> reprocessDocument(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(documentService.reprocess(id));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id) {
        Resource file = documentService.getFileResource(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(file);
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getAllTags() {
        return ResponseEntity.ok(documentService.getAllTags());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Documento eliminado correctamente"));
    }
}
