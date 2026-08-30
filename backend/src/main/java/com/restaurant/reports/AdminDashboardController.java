package com.restaurant.reports;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final ReportsService reportsService;

    public AdminDashboardController(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ReportsService.DashboardDTO> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        if (fecha == null) fecha = LocalDate.now();
        return ResponseEntity.ok(reportsService.getDashboard(fecha));
    }

    @GetMapping("/dashboard/live")
    public ResponseEntity<ReportsService.LiveDashboardDTO> getLiveDashboard() {
        return ResponseEntity.ok(reportsService.getLiveDashboard());
    }

    @GetMapping("/dashboard/tables")
    public ResponseEntity<List<Map<String, Object>>> getActiveTables() {
        return ResponseEntity.ok(reportsService.getActiveTablesWithOrders());
    }

    @GetMapping("/reportes/date-range")
    public ResponseEntity<Map<String, Object>> getDateRangeReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        var ventas = reportsService.getVentasReport(desde, hasta);
        var top = reportsService.getProductosTopReport(desde, hasta);
        var inv = reportsService.getInventarioConsumido(desde, hasta);
        var tiempos = reportsService.getTiemposPromedio(desde, hasta);
        var extra = reportsService.getDateRangeFinancial(desde, hasta);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("ventas", ventas);
        body.put("topProductos", top);
        body.put("inventarioConsumido", inv);
        body.put("tiemposPromedio", tiempos);
        body.put("desde", desde.toString());
        body.put("hasta", hasta.toString());
        body.put("ticketPromedio", extra.get("ticketPromedio"));
        body.put("ventasPorDia", extra.get("ventasPorDia"));
        body.put("ventasPorCategoria", extra.get("ventasPorCategoria"));
        body.put("distribucionEstado", extra.get("distribucionEstado"));
        body.put("topProductosIngreso", extra.get("topProductosIngreso"));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/reportes/exportar/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        if (fecha == null) fecha = LocalDate.now();
        String csv = reportsService.exportDashboardToCsv(fecha);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "reporte.csv");
        return ResponseEntity.ok().headers(headers).body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/reportes/exportar/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        if (fecha == null) fecha = LocalDate.now();
        byte[] pdf = reportsService.exportDashboardToPdf(fecha);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "reporte.pdf");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping("/reportes/exportar/csv-range")
    public ResponseEntity<byte[]> exportCsvRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        String csv = reportsService.exportDateRangeToCsv(desde, hasta);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "reporte_" + desde + "_" + hasta + ".csv");
        return ResponseEntity.ok().headers(headers).body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/reportes/exportar/pdf-range")
    public ResponseEntity<byte[]> exportPdfRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        byte[] pdf = reportsService.exportDateRangeToPdf(desde, hasta);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "reporte_" + desde + "_" + hasta + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
