package com.restaurant.config;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final OkHttpClient httpClient;

    @Value("${app.openai.base-url}")
    private String ollamaBaseUrl;

    @Value("${app.uploads.dir:uploads/products}")
    private String uploadsDir;

    public HealthController(DataSource dataSource, OkHttpClient httpClient) {
        this.dataSource = dataSource;
        this.httpClient = httpClient;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", checkDatabase());
        checks.put("ollama", checkOllama());
        checks.put("uploads", checkUploads());

        boolean up = checks.values().stream().allMatch("UP"::equals);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", up ? "UP" : "DEGRADED");
        body.put("service", "RestauranteChat Backend");
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("version", "1.0.0");
        body.put("checks", checks);
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String checkOllama() {
        try (var response = httpClient.newCall(new Request.Builder()
                .url(ollamaBaseUrl + "/api/tags")
                .get()
                .build()).execute()) {
            return response.isSuccessful() ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String checkUploads() {
        try {
            Path path = Path.of(uploadsDir);
            Files.createDirectories(path);
            return Files.isWritable(path) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
