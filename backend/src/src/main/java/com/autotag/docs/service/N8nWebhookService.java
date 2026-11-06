package com.autotag.docs.service;

import com.autotag.docs.dto.N8nIngestTriggerPayload;
import com.autotag.docs.entity.Document;
import com.autotag.docs.entity.DocumentStatus;
import com.autotag.docs.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class N8nWebhookService {

    private static final Logger log = LoggerFactory.getLogger(N8nWebhookService.class);

    private final WebClient webClient;
    private final DocumentRepository documentRepository;

    @Value("${n8n.webhook.url:http://localhost:5678/webhook/process-pdf}")
    private String n8nWebhookUrl;

    @Value("${n8n.backend-callback-base-url:http://backend:8080}")
    private String backendCallbackBaseUrl;

    public N8nWebhookService(WebClient webClient, DocumentRepository documentRepository) {
        this.webClient = webClient;
        this.documentRepository = documentRepository;
    }

    @Async
    public void triggerN8nIngestion(Document document) {
        String callbackUrl = backendCallbackBaseUrl + "/api/documents/" + document.getId() + "/callback";

        N8nIngestTriggerPayload payload = new N8nIngestTriggerPayload();
        payload.setDocumentId(document.getId());
        payload.setFileName(document.getFileName());
        payload.setFilePath(document.getFilePath());
        payload.setFileSize(document.getFileSize());
        payload.setMimeType(document.getMimeType());
        payload.setCallbackUrl(callbackUrl);

        log.info("DISPARANDO TRIGGER WEBHOOK hacia n8n: URL={}, DocID={}", n8nWebhookUrl, document.getId());

        webClient.post()
                .uri(n8nWebhookUrl)
                .header("Content-Type", "application/json")
                .header("X-Source-Service", "AutoTag-SpringBoot-25")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(throwable -> !(throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest)))
                .subscribe(
                        response -> {
                            log.info("TRIGGER EXITOSO: n8n confirmo recepcion para DocID {}. Respuesta: {}", document.getId(), response);
                        },
                        error -> {
                            log.error("ERROR EN TRIGGER: Fallo al notificar webhook de n8n para DocID {}: {}",
                                    document.getId(), error.getMessage());
                            documentRepository.findById(document.getId()).ifPresent(doc -> {
                                if (doc.getStatus() == DocumentStatus.PROCESANDO) {
                                    doc.setErrorMessage("Fallo al contactar orquestador n8n: " + error.getMessage());
                                    documentRepository.save(doc);
                                }
                            });
                        }
                );
    }
}
