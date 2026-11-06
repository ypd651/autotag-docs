package com.autotag.docs.controller;

import com.autotag.docs.dto.SearchResultDTO;
import com.autotag.docs.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);
    private final DocumentService documentService;

    public SearchController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<List<SearchResultDTO>> search(
            @RequestParam(name = "q") String query,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        log.info("Ejecutando busqueda semantica para consulta: '{}' con limite {}", query, limit);
        List<SearchResultDTO> results = documentService.searchSemantically(query, limit);
        return ResponseEntity.ok(results);
    }
}
