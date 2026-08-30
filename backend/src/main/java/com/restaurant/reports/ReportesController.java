package com.restaurant.reports;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasRole('ADMIN')")
@Deprecated(since = "1.0", forRemoval = false)
public class ReportesController {

    private final ReportsService reportsService;

    public ReportesController(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @GetMapping("/ventas")
    public ResponseEntity<ReportsService.VentasReport> getVentas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reportsService.getVentasReport(desde, hasta));
    }

    @GetMapping("/productos-top")
    public ResponseEntity<List<ReportsService.ProductoTopReport>> getProductosTop(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reportsService.getProductosTopReport(desde, hasta));
    }

    @GetMapping("/inventario-consumido")
    public ResponseEntity<List<ReportsService.InventarioConsumidoReport>> getInventarioConsumido(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reportsService.getInventarioConsumido(desde, hasta));
    }

    @GetMapping("/tiempos-promedio")
    public ResponseEntity<ReportsService.TiemposPromedioReport> getTiemposPromedio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reportsService.getTiemposPromedio(desde, hasta));
    }
}
