package com.autotag.docs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int DEFAULT_DIMENSION = 384;

    public List<Double> generateEmbedding(String text) {
        return generateEmbedding(text, DEFAULT_DIMENSION);
    }

    public List<Double> generateEmbedding(String text, int dimension) {
        if (text == null || text.isBlank()) {
            List<Double> zeros = new ArrayList<>(dimension);
            for (int i = 0; i < dimension; i++) zeros.add(0.0);
            return zeros;
        }

        double[] vector = new double[dimension];
        String normalized = text.toLowerCase().trim();

        for (int i = 0; i < normalized.length(); i++) {
            int charCode = normalized.charAt(i);
            int idx = Math.abs((charCode * 31 + i * 17) % dimension);
            vector[idx] += Math.sin(charCode + i);
        }

        double sumSquares = 0.0;
        for (double v : vector) {
            sumSquares += v * v;
        }
        double norm = Math.sqrt(sumSquares);
        if (norm == 0.0) norm = 1.0;

        List<Double> normalizedList = new ArrayList<>(dimension);
        for (double v : vector) {
            normalizedList.add(Math.round((v / norm) * 1_000_000.0) / 1_000_000.0);
        }

        return normalizedList;
    }
}
