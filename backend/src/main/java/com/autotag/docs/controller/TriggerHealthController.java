package com.autotag.docs.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/system")
public class TriggerHealthController {

    private static final Logger log = LoggerFactory.getLogger(TriggerHealthController.class);

    private final WebClient webClient;
    private final DataSource dataSource;

    @Value("${n8n.webhook.url:http://localhost:5678/webhook/process-pdf}")
    private String n8nWebhookUrl;

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6333}")
    private int qdrantPort;

    @Value("${qdrant.collection:autotag_documents}")
    private String collectionName;

    public TriggerHealthController(WebClient webClient, DataSource dataSource) {
        this.webClient = webClient;
        this.dataSource = dataSource;
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<Map<String, Object>> getSystemDiagnostics() {
        Map<String, Object> statusMap = new HashMap<>();

        // 1. PostgreSQL Status
        boolean postgresOk = false;
        String postgresMsg = "OK";
        try (Connection conn = dataSource.getConnection()) {
            postgresOk = conn.isValid(2);
        } catch (Exception e) {
            postgresMsg = e.getMessage();
        }
        statusMap.put("postgres", Map.of("status", postgresOk ? "UP" : "DOWN", "detail", postgresMsg));

        // 2. Qdrant Vector DB Status
        boolean qdrantOk = false;
        String qdrantMsg = "OK";
        try {
            String qdrantUrl = "http://" + qdrantHost + ":" + qdrantPort + "/readyz";
            String qRes = webClient.get()
                    .uri(qdrantUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            qdrantOk = (qRes != null && (qRes.contains("all") || qRes.contains("ready") || qRes.contains("ok")));
        } catch (Exception e) {
            qdrantMsg = e.getMessage();
        }
        statusMap.put("qdrant", Map.of(
                "status", qdrantOk ? "UP" : "DOWN",
                "collection", collectionName,
                "detail", qdrantMsg
        ));

        // 3. n8n Trigger Webhook Status
        boolean n8nOk = false;
        String n8nMsg = "Pending Verification";
        try {
            String n8nBase = n8nWebhookUrl.substring(0, n8nWebhookUrl.indexOf("/webhook"));
            String health = webClient.get()
                    .uri(n8nBase + "/healthz")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            n8nOk = (health != null);
            n8nMsg = "n8n activo";
        } catch (Exception e) {
            n8nMsg = "n8n no responde en healthz: " + e.getMessage();
        }
        statusMap.put("n8n_trigger", Map.of(
                "status", n8nOk ? "UP" : "DOWN",
                "webhook_url", n8nWebhookUrl,
                "detail", n8nMsg
        ));

        return ResponseEntity.ok(statusMap);
    }

    @PostMapping("/triggers/test-n8n")
    public ResponseEntity<Map<String, Object>> testN8nTrigger() {
        Map<String, Object> testPayload = Map.of(
                "documentId", UUID.randomUUID().toString(),
                "fileName", "test-diagnostic-document.pdf",
                "filePath", "/shared-storage/uploads/test.pdf",
                "fileSize", 1024,
                "mimeType", "application/pdf",
                "callbackUrl", "http://backend:8080/api/system/triggers/dummy-callback"
        );

        try {
            String response = webClient.post()
                    .uri(n8nWebhookUrl)
                    .header("Content-Type", "application/json")
                    .bodyValue(testPayload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "targetUrl", n8nWebhookUrl,
                    "n8nResponse", response != null ? response : "Sin contenido pero 200 OK"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of(
                    "success", false,
                    "targetUrl", n8nWebhookUrl,
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/triggers/dummy-callback")
    public ResponseEntity<Map<String, String>> dummyCallback(@RequestBody Map<String, Object> body) {
        log.info("Dummy callback recibido: {}", body);
        return ResponseEntity.ok(Map.of("message", "Callback de prueba recibido exitosamente"));
    }
}
