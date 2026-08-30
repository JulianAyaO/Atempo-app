package com.restaurant.reports;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN')")
@Deprecated(since = "1.0", forRemoval = false)
public class ReportsController {

    private final ReportsService reportsService;

    public ReportsController(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @GetMapping("/sales")
    public ResponseEntity<ReportsService.SalesSummary> getSales(
            @RequestParam(defaultValue = "today") String period) {
        return ResponseEntity.ok(reportsService.getSalesSummary(period));
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<ReportsService.TopProduct>> getTopProducts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportsService.getTopProducts(limit));
    }

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> getOrdersReport() {
        return ResponseEntity.ok(reportsService.getOrdersReport());
    }
}
