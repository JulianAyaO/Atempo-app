package com.restaurant.reports;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.restaurant.common.exception.BusinessException;
import com.restaurant.inventory.InventoryService;
import com.restaurant.orders.Order;
import com.restaurant.orders.OrderItem;
import com.restaurant.orders.OrderRepository;
import com.restaurant.orders.Session;
import com.restaurant.orders.SessionRepository;
import com.restaurant.orders.TableEntity;
import com.restaurant.orders.TableRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ReportsService {

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final SessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final TableRepository tableRepository;

    public ReportsService(JdbcTemplate jdbcTemplate, InventoryService inventoryService,
                          SessionRepository sessionRepository, OrderRepository orderRepository,
                          TableRepository tableRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.tableRepository = tableRepository;
    }

    // ─── EXISTING (frontend compat) ──────────────────────────────────────
    public record SalesSummary(
        double totalSales, int totalOrders, double avgOrderValue,
        List<Map<String, Object>> byCategory,
        List<Map<String, Object>> byDay,
        List<TopProduct> topProducts,
        Map<String, Integer> byStatus,
        int cancelledOrders,
        double previousPeriodSales,
        double salesChangePct,
        List<Map<String, Object>> byHour
    ) {}
    public record TopProduct(Long productId, String productName, int totalQuantity, double totalRevenue) {}

    public SalesSummary getSalesSummary(String period) {
        String dateFilter = switch (period) {
            case "week" -> "AND o.created_at >= NOW() - INTERVAL '7 days'";
            case "month" -> "AND o.created_at >= NOW() - INTERVAL '30 days'";
            default -> "AND o.created_at >= CURRENT_DATE";
        };

        String sql = "SELECT COALESCE(SUM(o.total),0) as total_sales, COUNT(*) as total_orders, " +
                     "COALESCE(AVG(o.total),0) as avg_value " +
                     "FROM orders o WHERE o.status IN ('PAID','CLOSED') " + dateFilter;

        Map<String, Object> row = jdbcTemplate.queryForMap(sql);
        double totalSales = ((Number) row.get("total_sales")).doubleValue();
        int totalOrders = ((Number) row.get("total_orders")).intValue();
        double avgValue = ((Number) row.get("avg_value")).doubleValue();

        String catSql = "SELECT c.name as category, COALESCE(SUM(oi.line_total),0) as revenue, COUNT(oi.id) as items " +
                        "FROM order_items oi " +
                        "JOIN orders o ON o.id = oi.order_id " +
                        "JOIN products p ON p.id = oi.product_id " +
                        "JOIN categories c ON c.id = p.category_id " +
                        "WHERE o.status IN ('PAID','CLOSED') AND oi.status = 'ACTIVE' " + dateFilter +
                        " GROUP BY c.name ORDER BY revenue DESC";

        List<Map<String, Object>> byCategory = jdbcTemplate.queryForList(catSql);

        String dayFilter = "AND o.created_at >= CURRENT_DATE - INTERVAL '2 days'";
        String daySql = "SELECT TO_CHAR(o.created_at, 'YYYY-MM-DD') as day, " +
                        "COALESCE(SUM(o.total),0) as sales, COUNT(*) as orders " +
                        "FROM orders o WHERE o.status IN ('PAID','CLOSED') " + dayFilter +
                        " GROUP BY TO_CHAR(o.created_at, 'YYYY-MM-DD') ORDER BY day";
        List<Map<String, Object>> byDay = jdbcTemplate.queryForList(daySql);

        String topSql = "SELECT oi.product_id, p.name, SUM(oi.quantity) as total_qty, SUM(oi.line_total) as total_rev " +
                        "FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
                        "JOIN products p ON p.id = oi.product_id " +
                        "WHERE o.status IN ('PAID','CLOSED') AND oi.status = 'ACTIVE' " + dateFilter +
                        " GROUP BY oi.product_id, p.name ORDER BY total_rev DESC LIMIT 10";
        List<TopProduct> topProducts = jdbcTemplate.query(topSql, (rs, i) -> new TopProduct(
            rs.getLong("product_id"), rs.getString("name"),
            rs.getInt("total_qty"), rs.getDouble("total_rev")
        ));

        String statusSql = "SELECT status, COUNT(*) as count FROM orders o WHERE 1=1 " + dateFilter +
                           " GROUP BY status ORDER BY count DESC";
        List<Map<String, Object>> statusRows = jdbcTemplate.queryForList(statusSql);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        int cancelled = 0;
        for (Map<String, Object> r : statusRows) {
            String st = String.valueOf(r.get("status"));
            int cnt = ((Number) r.get("count")).intValue();
            byStatus.put(st, cnt);
            if ("CANCELLED".equals(st)) cancelled = cnt;
        }

        String prevFilter = switch (period) {
            case "week" -> "AND o.created_at >= NOW() - INTERVAL '14 days' AND o.created_at < NOW() - INTERVAL '7 days'";
            case "month" -> "AND o.created_at >= NOW() - INTERVAL '60 days' AND o.created_at < NOW() - INTERVAL '30 days'";
            default -> "AND o.created_at >= CURRENT_DATE - INTERVAL '1 day' AND o.created_at < CURRENT_DATE";
        };
        String prevSql = "SELECT COALESCE(SUM(o.total),0) as total_sales FROM orders o WHERE o.status IN ('PAID','CLOSED') " + prevFilter;
        double prevSales = ((Number) jdbcTemplate.queryForMap(prevSql).get("total_sales")).doubleValue();
        double changePct = prevSales > 0 ? ((totalSales - prevSales) / prevSales) * 100.0 : (totalSales > 0 ? 100.0 : 0.0);

        String hourSql = "SELECT LPAD(EXTRACT(HOUR FROM o.created_at)::int::text, 2, '0') as hour, " +
                         "COALESCE(SUM(o.total),0) as sales, COUNT(*) as orders " +
                         "FROM orders o WHERE o.status IN ('PAID','CLOSED') " + dateFilter +
                         " GROUP BY EXTRACT(HOUR FROM o.created_at) ORDER BY hour";
        List<Map<String, Object>> byHour = jdbcTemplate.queryForList(hourSql);

        return new SalesSummary(totalSales, totalOrders, avgValue, byCategory, byDay, topProducts, byStatus, cancelled,
            prevSales, changePct, byHour);
    }

    public List<TopProduct> getTopProducts(int limit) {
        String sql = "SELECT oi.product_id, p.name, SUM(oi.quantity) as total_qty, SUM(oi.line_total) as total_rev " +
                     "FROM order_items oi " +
                     "JOIN orders o ON o.id = oi.order_id " +
                     "JOIN products p ON p.id = oi.product_id " +
                     "WHERE o.status IN ('PAID','CLOSED') AND oi.status = 'ACTIVE' " +
                     "GROUP BY oi.product_id, p.name ORDER BY total_qty DESC LIMIT ?";

        return jdbcTemplate.query(sql, (rs, i) -> new TopProduct(
            rs.getLong("product_id"), rs.getString("name"),
            rs.getInt("total_qty"), rs.getDouble("total_rev")
        ), limit);
    }

    public Map<String, Object> getOrdersReport() {
        String sql = "SELECT status, COUNT(*) as count FROM orders GROUP BY status ORDER BY count DESC";
        List<Map<String, Object>> byStatus = jdbcTemplate.queryForList(sql);

        String todaySql = "SELECT COUNT(*) as today FROM orders WHERE created_at >= CURRENT_DATE";
        int today = jdbcTemplate.queryForObject(todaySql, Integer.class);

        return Map.of("byStatus", byStatus, "todayCount", today);
    }

    // ─── DASHBOARD DEL DÍA ────────────────────────────────────────────────
    public record DashboardDTO(
        String fecha,
        double ventasTotales,
        int totalPedidos,
        int pedidosPagados,
        int pedidosCancelados,
        int pedidosActivos,
        double tiempoPromedioMinutos,
        List<TopProduct> top5Productos,
        List<InventoryService.InventoryItemDTO> alertasInventario,
        Map<String, Integer> distribucionPorEstado,
        int activeTables,
        int pendingOrders,
        int inPreparationOrders,
        int readyOrders,
        int deliveredOrders,
        int draftOrders,
        int lowStockAlerts
    ) {}

    // ─── LIVE DASHBOARD (estado operativo en tiempo real) ─────────────────
    public record LiveDashboardDTO(
        int activeTables,
        int activeSessions,
        int pendingOrders,
        int inPreparationOrders,
        int readyOrders,
        int deliveredOrders,
        int draftOrders,
        int paidOrders,
        int closedOrders,
        int cancelledOrders,
        int totalOrdersToday,
        double todaySales,
        int lowStockAlerts,
        int outOfStockItems,
        int unavailableProducts,
        List<TopProduct> topProductsToday,
        List<Map<String, Object>> recentActivity
    ) {}

    public DashboardDTO getDashboard(LocalDate fecha) {
        String fechaStr = fecha.format(DateTimeFormatter.ISO_LOCAL_DATE);
        Timestamp start = Timestamp.valueOf(fecha.atStartOfDay());
        Timestamp end = Timestamp.valueOf(fecha.atTime(23, 59, 59));

        // Ventas totales
        String sqlVentas = "SELECT COALESCE(SUM(total),0) FROM orders WHERE status IN ('PAID','CLOSED') " +
                           "AND created_at BETWEEN ? AND ?";
        double ventas = jdbcTemplate.queryForObject(sqlVentas, Double.class, start, end);

        // Total pedidos
        String sqlTotal = "SELECT COUNT(*) FROM orders WHERE created_at BETWEEN ? AND ?";
        int total = jdbcTemplate.queryForObject(sqlTotal, Integer.class, start, end);

        // Pedidos pagados
        String sqlPagados = "SELECT COUNT(*) FROM orders WHERE status IN ('PAID','CLOSED') AND created_at BETWEEN ? AND ?";
        int pagados = jdbcTemplate.queryForObject(sqlPagados, Integer.class, start, end);

        // Pedidos cancelados
        String sqlCancelados = "SELECT COUNT(*) FROM orders WHERE status = 'CANCELLED' AND created_at BETWEEN ? AND ?";
        int cancelados = jdbcTemplate.queryForObject(sqlCancelados, Integer.class, start, end);

        // Pedidos activos (no CANCELLED, no PAID, no CLOSED)
        String sqlActivos = "SELECT COUNT(*) FROM orders WHERE status IN ('DRAFT','PENDING','IN_PREPARATION','READY','DELIVERED') " +
                            "AND created_at BETWEEN ? AND ?";
        int activos = jdbcTemplate.queryForObject(sqlActivos, Integer.class, start, end);

        // Tiempo promedio (min)
        String sqlTiempo = "SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 60), 0) " +
                           "FROM orders WHERE status IN ('PAID','CLOSED') AND created_at BETWEEN ? AND ?";
        double tiempo = jdbcTemplate.queryForObject(sqlTiempo, Double.class, start, end);

        // Top 5
        String sqlTop = "SELECT oi.product_id, p.name, SUM(oi.quantity) as total_qty, SUM(oi.line_total) as total_rev " +
                        "FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
                        "JOIN products p ON p.id = oi.product_id " +
                        "WHERE o.status IN ('PAID','CLOSED') AND oi.status = 'ACTIVE' " +
                        "AND o.created_at BETWEEN ? AND ? " +
                        "GROUP BY oi.product_id, p.name ORDER BY total_qty DESC LIMIT 5";
        List<TopProduct> top5 = jdbcTemplate.query(sqlTop, (rs, i) -> new TopProduct(
            rs.getLong("product_id"), rs.getString("name"),
            rs.getInt("total_qty"), rs.getDouble("total_rev")
        ), start, end);

        // Alertas inventario
        List<InventoryService.InventoryItemDTO> alertas = inventoryService.getLowStockAlerts();

        // Distribución por estado del día
        String sqlDistrib = "SELECT status, COUNT(*) as cnt FROM orders WHERE created_at BETWEEN ? AND ? GROUP BY status";
        List<Map<String, Object>> distRows = jdbcTemplate.queryForList(sqlDistrib, start, end);
        Map<String, Integer> distribucion = new LinkedHashMap<>();
        for (Map<String, Object> r : distRows) {
            distribucion.put((String) r.get("status"), ((Number) r.get("cnt")).intValue());
        }

        // Mesas activas (sesiones activas del día)
        String sqlActiveTables = "SELECT COUNT(DISTINCT table_id) FROM sessions WHERE status = 'ACTIVE'";
        int activeTables = jdbcTemplate.queryForObject(sqlActiveTables, Integer.class);

        // Pedidos por estado (del día)
        String sqlPending = "SELECT COUNT(*) FROM orders WHERE status = 'PENDING' AND created_at BETWEEN ? AND ?";
        int pending = jdbcTemplate.queryForObject(sqlPending, Integer.class, start, end);
        String sqlPrep = "SELECT COUNT(*) FROM orders WHERE status = 'IN_PREPARATION' AND created_at BETWEEN ? AND ?";
        int inPrep = jdbcTemplate.queryForObject(sqlPrep, Integer.class, start, end);
        String sqlReady = "SELECT COUNT(*) FROM orders WHERE status = 'READY' AND created_at BETWEEN ? AND ?";
        int ready = jdbcTemplate.queryForObject(sqlReady, Integer.class, start, end);
        String sqlDelivered = "SELECT COUNT(*) FROM orders WHERE status = 'DELIVERED' AND created_at BETWEEN ? AND ?";
        int delivered = jdbcTemplate.queryForObject(sqlDelivered, Integer.class, start, end);
        String sqlDraft = "SELECT COUNT(*) FROM orders WHERE status = 'DRAFT' AND created_at BETWEEN ? AND ?";
        int draft = jdbcTemplate.queryForObject(sqlDraft, Integer.class, start, end);

        return new DashboardDTO(fechaStr, ventas, total, pagados, cancelados, activos, tiempo, top5, alertas, distribucion,
            activeTables, pending, inPrep, ready, delivered, draft, alertas.size());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveTablesWithOrders() {
        List<TableEntity> tables = tableRepository.findByActiveTrueOrderByTableNumber();
        List<Session> activeSessions = sessionRepository.findByStatus("ACTIVE");
        Map<Long, Session> sessionByTablePk = new HashMap<>();
        for (Session session : activeSessions) {
            sessionByTablePk.put(session.getTableId(), session);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Set<Long> seenTablePks = new HashSet<>();
        for (TableEntity table : tables) {
            seenTablePks.add(table.getId());
            result.add(buildFloorTable(table.getId(), table.getTableNumber(), table.getName(),
                table.getCapacity(), sessionByTablePk.get(table.getId())));
        }
        for (Session session : activeSessions) {
            if (!seenTablePks.contains(session.getTableId())) {
                result.add(buildFloorTable(session.getTableId(), session.getTableId().intValue(),
                    "Mesa " + session.getTableId(), 0, session));
            }
        }
        return result;
    }

    private Map<String, Object> buildFloorTable(Long tablePk, Integer tableNumber, String name,
                                                Integer capacity, Session session) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("tableId", tablePk);
        table.put("tableNumber", tableNumber);
        table.put("name", name != null ? name : "Mesa " + tableNumber);
        table.put("capacity", capacity != null ? capacity : 0);
        boolean occupied = session != null;
        table.put("occupied", occupied);
        table.put("sessionId", occupied ? session.getId() : null);
        table.put("sessionStatus", occupied ? session.getStatus() : "FREE");
        table.put("sessionStartedAt", occupied && session.getStartedAt() != null ? session.getStartedAt().toString() : null);

        List<Map<String, Object>> orderMaps = new ArrayList<>();
        double generated = 0;
        if (occupied) {
            List<Order> orders = orderRepository.findBySessionId(session.getId());
            for (Order order : orders) {
                if (List.of("CLOSED", "CANCELLED").contains(order.getStatus())) continue;
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("orderId", order.getId());
                o.put("status", order.getStatus());
                o.put("total", order.getTotal());
                o.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
                o.put("updatedAt", order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null);
                List<Map<String, Object>> items = new ArrayList<>();
                if (order.getItems() != null) {
                    for (OrderItem item : order.getItems()) {
                        if (item.getStatus() != null && !"ACTIVE".equals(item.getStatus())) continue;
                        Map<String, Object> line = new LinkedHashMap<>();
                        line.put("itemId", item.getId());
                        line.put("productId", item.getProductId());
                        line.put("productName", lookupProductName(item.getProductId()));
                        line.put("quantity", item.getQuantity());
                        line.put("lineTotal", item.getLineTotal());
                        line.put("notes", item.getNotes());
                        items.add(line);
                    }
                }
                o.put("items", items);
                if (order.getTotal() != null) generated += order.getTotal().doubleValue();
                orderMaps.add(o);
            }
        }
        table.put("orders", orderMaps);
        table.put("generated", generated);
        return table;
    }

    private String lookupProductName(Long productId) {
        if (productId == null) return "Producto";
        try {
            String name = jdbcTemplate.queryForObject(
                "SELECT name FROM products WHERE id = ?", String.class, productId);
            return name != null ? name : "Producto";
        } catch (Exception e) {
            return "Producto #" + productId;
        }
    }

    public LiveDashboardDTO getLiveDashboard() {
        // Mesas con sesiones activas
        String sqlActiveTables = "SELECT COUNT(DISTINCT table_id) FROM sessions WHERE status = 'ACTIVE'";
        int activeTables = jdbcTemplate.queryForObject(sqlActiveTables, Integer.class);

        String sqlActiveSessions = "SELECT COUNT(*) FROM sessions WHERE status = 'ACTIVE'";
        int activeSessions = jdbcTemplate.queryForObject(sqlActiveSessions, Integer.class);

        // Pedidos por estado (todos, no solo hoy)
        String sqlPending = "SELECT COUNT(*) FROM orders WHERE status = 'PENDING'";
        int pending = jdbcTemplate.queryForObject(sqlPending, Integer.class);
        String sqlPrep = "SELECT COUNT(*) FROM orders WHERE status = 'IN_PREPARATION'";
        int inPrep = jdbcTemplate.queryForObject(sqlPrep, Integer.class);
        String sqlReady = "SELECT COUNT(*) FROM orders WHERE status = 'READY'";
        int ready = jdbcTemplate.queryForObject(sqlReady, Integer.class);
        String sqlDelivered = "SELECT COUNT(*) FROM orders WHERE status = 'DELIVERED'";
        int delivered = jdbcTemplate.queryForObject(sqlDelivered, Integer.class);
        String sqlDraft = "SELECT COUNT(*) FROM orders WHERE status = 'DRAFT'";
        int draft = jdbcTemplate.queryForObject(sqlDraft, Integer.class);
        String sqlPaid = "SELECT COUNT(*) FROM orders WHERE status = 'PAID'";
        int paid = jdbcTemplate.queryForObject(sqlPaid, Integer.class);
        String sqlClosed = "SELECT COUNT(*) FROM orders WHERE status = 'CLOSED'";
        int closed = jdbcTemplate.queryForObject(sqlClosed, Integer.class);
        String sqlCancelled = "SELECT COUNT(*) FROM orders WHERE status = 'CANCELLED'";
        int cancelled = jdbcTemplate.queryForObject(sqlCancelled, Integer.class);

        // Ventas de hoy
        String sqlTodaySales = "SELECT COALESCE(SUM(total),0) FROM orders WHERE status IN ('PAID','CLOSED') AND created_at >= CURRENT_DATE";
        double todaySales = jdbcTemplate.queryForObject(sqlTodaySales, Double.class);
        String sqlTodayOrders = "SELECT COUNT(*) FROM orders WHERE created_at >= CURRENT_DATE";
        int todayOrders = jdbcTemplate.queryForObject(sqlTodayOrders, Integer.class);

        // Inventario
        List<InventoryService.InventoryItemDTO> alertas = inventoryService.getLowStockAlerts();
        String sqlOutOfStock = "SELECT COUNT(*) FROM inventory WHERE quantity_available <= 0 OR is_available = false";
        int outOfStock = jdbcTemplate.queryForObject(sqlOutOfStock, Integer.class);
        String sqlUnavailableProds = "SELECT COUNT(*) FROM products WHERE active = false";
        int unavailableProds = jdbcTemplate.queryForObject(sqlUnavailableProds, Integer.class);

        // Top productos hoy
        String sqlTop = "SELECT oi.product_id, p.name, SUM(oi.quantity) as total_qty, SUM(oi.line_total) as total_rev " +
                        "FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
                        "JOIN products p ON p.id = oi.product_id " +
                        "WHERE o.status IN ('PAID','CLOSED') AND oi.status = 'ACTIVE' " +
                        "AND o.created_at >= CURRENT_DATE " +
                        "GROUP BY oi.product_id, p.name ORDER BY total_qty DESC LIMIT 5";
        List<TopProduct> top5 = jdbcTemplate.query(sqlTop, (rs, i) -> new TopProduct(
            rs.getLong("product_id"), rs.getString("name"),
            rs.getInt("total_qty"), rs.getDouble("total_rev")
        ));

        // Actividad reciente (últimas 10 órdenes)
        String sqlRecent = "SELECT o.id, o.table_id, o.status, o.total, o.created_at " +
                           "FROM orders o ORDER BY o.created_at DESC LIMIT 10";
        List<Map<String, Object>> recent = jdbcTemplate.queryForList(sqlRecent);
        List<Map<String, Object>> recentActivity = new ArrayList<>();
        for (Map<String, Object> r : recent) {
            Map<String, Object> act = new LinkedHashMap<>();
            act.put("orderId", r.get("id"));
            act.put("tableId", r.get("table_id"));
            act.put("status", r.get("status"));
            act.put("total", r.get("total"));
            act.put("timestamp", r.get("created_at") != null ? r.get("created_at").toString() : null);
            recentActivity.add(act);
        }

        return new LiveDashboardDTO(activeTables, activeSessions, pending, inPrep, ready, delivered, draft,
            paid, closed, cancelled, todayOrders, todaySales, alertas.size(), outOfStock, unavailableProds,
            top5, recentActivity);
    }

    // ─── REPORTES POR RANGO DE FECHAS ────────────────────────────────────
    public record VentasReport(double totalVentas, int pedidosPagados) {}
    public record ProductoTopReport(String producto, int cantidad) {}
    public record InventarioConsumidoReport(String ingrediente, double cantidadTotal) {}
    public record TiemposPromedioReport(double minutosPromedio, int muestras) {}

    public VentasReport getVentasReport(LocalDate desde, LocalDate hasta) {
        Timestamp s = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp e = Timestamp.valueOf(hasta.atTime(23, 59, 59));
        String sql = "SELECT COALESCE(SUM(total),0) as total_ventas, COUNT(*) as pedidos FROM orders WHERE status IN ('PAID','CLOSED') AND created_at BETWEEN ? AND ?";
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, s, e);
        double total = ((Number) row.get("total_ventas")).doubleValue();
        int count = ((Number) row.get("pedidos")).intValue();
        return new VentasReport(total, count);
    }

    public List<ProductoTopReport> getProductosTopReport(LocalDate desde, LocalDate hasta) {
        Timestamp s = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp e = Timestamp.valueOf(hasta.atTime(23, 59, 59));
        String sql = "SELECT p.name, SUM(oi.quantity) as total_qty " +
                     "FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
                     "JOIN products p ON p.id = oi.product_id " +
                     "WHERE o.status IN ('PAID','CLOSED') AND oi.status = 'ACTIVE' " +
                     "AND o.created_at BETWEEN ? AND ? " +
                     "GROUP BY oi.product_id, p.name ORDER BY total_qty DESC";
        return jdbcTemplate.query(sql, (rs, i) -> new ProductoTopReport(
            rs.getString("name"), rs.getInt("total_qty")
        ), s, e);
    }

    public List<InventarioConsumidoReport> getInventarioConsumido(LocalDate desde, LocalDate hasta) {
        Timestamp s = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp e = Timestamp.valueOf(hasta.atTime(23, 59, 59));
        String sql = "SELECT ingredient_name, SUM(quantity_delta) as total " +
                     "FROM inventory_movements WHERE movement_type = 'SALIDA' " +
                     "AND created_at BETWEEN ? AND ? " +
                     "GROUP BY ingredient_name ORDER BY total DESC";
        return jdbcTemplate.query(sql, (rs, i) -> new InventarioConsumidoReport(
            rs.getString("ingredient_name"), rs.getDouble("total")
        ), s, e);
    }

    public TiemposPromedioReport getTiemposPromedio(LocalDate desde, LocalDate hasta) {
        Timestamp s = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp e = Timestamp.valueOf(hasta.atTime(23, 59, 59));
        String sql = "SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 60), 0) as avg_min, " +
                     "COUNT(*) as muestras FROM orders " +
                     "WHERE status IN ('PAID','CLOSED') AND created_at BETWEEN ? AND ?";
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, s, e);
        double avg = ((Number) row.get("avg_min")).doubleValue();
        int muestras = ((Number) row.get("muestras")).intValue();
        return new TiemposPromedioReport(avg, muestras);
    }

    public Map<String, Object> getDateRangeFinancial(LocalDate desde, LocalDate hasta) {
        Timestamp s = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp e = Timestamp.valueOf(hasta.atTime(23, 59, 59));
        VentasReport ventas = getVentasReport(desde, hasta);
        double ticket = ventas.pedidosPagados() > 0 ? ventas.totalVentas() / ventas.pedidosPagados() : 0;

        List<Map<String, Object>> byDay = jdbcTemplate.queryForList(
            "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') as day, COALESCE(SUM(total),0) as sales, COUNT(*) as orders " +
            "FROM orders WHERE status IN ('PAID','CLOSED') AND created_at BETWEEN ? AND ? " +
            "GROUP BY TO_CHAR(created_at, 'YYYY-MM-DD') ORDER BY day", s, e);

        List<Map<String, Object>> byCategory = jdbcTemplate.queryForList(
            "SELECT c.name as category, COALESCE(SUM(oi.line_total),0) as revenue, COUNT(oi.id) as items " +
            "FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
            "JOIN products p ON p.id = oi.product_id JOIN categories c ON c.id = p.category_id " +
            "WHERE o.status IN ('PAID','CLOSED') AND oi.status = 'ACTIVE' AND o.created_at BETWEEN ? AND ? " +
            "GROUP BY c.name ORDER BY revenue DESC", s, e);

        List<TopProduct> topRevenue = jdbcTemplate.query(
            "SELECT oi.product_id, p.name, SUM(oi.quantity) as total_qty, SUM(oi.line_total) as total_rev " +
            "FROM order_items oi JOIN orders o ON o.id = oi.order_id JOIN products p ON p.id = oi.product_id " +
            "WHERE o.status IN ('PAID','CLOSED') AND oi.status = 'ACTIVE' AND o.created_at BETWEEN ? AND ? " +
            "GROUP BY oi.product_id, p.name ORDER BY total_rev DESC LIMIT 10",
            (rs, i) -> new TopProduct(rs.getLong("product_id"), rs.getString("name"),
                rs.getInt("total_qty"), rs.getDouble("total_rev")), s, e);

        List<Map<String, Object>> statusRows = jdbcTemplate.queryForList(
            "SELECT status, COUNT(*) as count FROM orders WHERE created_at BETWEEN ? AND ? GROUP BY status ORDER BY count DESC",
            s, e);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (Map<String, Object> r : statusRows) {
            byStatus.put(String.valueOf(r.get("status")), ((Number) r.get("count")).intValue());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ticketPromedio", ticket);
        out.put("ventasPorDia", byDay);
        out.put("ventasPorCategoria", byCategory);
        out.put("topProductosIngreso", topRevenue);
        out.put("distribucionEstado", byStatus);
        return out;
    }

    // ─── EXPORTACIÓN ──────────────────────────────────────────────────────
    public String exportDashboardToCsv(LocalDate fecha) {
        DashboardDTO d = getDashboard(fecha);
        StringWriter sw = new StringWriter();
        sw.write("Reporte del dia," + d.fecha() + "\n\n");
        sw.write("Ventas totales," + d.ventasTotales() + "\n");
        sw.write("Total pedidos," + d.totalPedidos() + "\n");
        sw.write("Pedidos pagados," + d.pedidosPagados() + "\n");
        sw.write("Pedidos cancelados," + d.pedidosCancelados() + "\n");
        sw.write("Pedidos activos," + d.pedidosActivos() + "\n");
        sw.write("Tiempo promedio (min)," + String.format("%.1f", d.tiempoPromedioMinutos()) + "\n\n");

        sw.write("Top Productos,Producto,Cantidad\n");
        for (TopProduct tp : d.top5Productos()) {
            sw.write("," + tp.productName() + "," + tp.totalQuantity() + "\n");
        }
        sw.write("\n");

        sw.write("Alertas Inventario,Ingrediente,Disponible,Minimo\n");
        for (InventoryService.InventoryItemDTO a : d.alertasInventario()) {
            sw.write("," + a.ingredientName() + "," + a.quantityAvailable() + "," + a.minThreshold() + "\n");
        }
        sw.write("\n");

        sw.write("Distribucion Estado,Estado,Cantidad\n");
        for (Map.Entry<String, Integer> entry : d.distribucionPorEstado().entrySet()) {
            sw.write("," + entry.getKey() + "," + entry.getValue() + "\n");
        }
        return sw.toString();
    }

    public byte[] exportDashboardToPdf(LocalDate fecha) {
        try {
            DashboardDTO d = getDashboard(fecha);
            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, Color.ORANGE);
            Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);
            Font normalFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.BLACK);

            doc.add(new Paragraph("Reporte del Dia: " + d.fecha(), titleFont));
            doc.add(Chunk.NEWLINE);

            // Summary table
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(60);
            summary.addCell(makeCell("Ventas totales", headerFont));
            summary.addCell(makeCell("$" + String.format("%,.2f", d.ventasTotales()), normalFont));
            summary.addCell(makeCell("Total pedidos", headerFont));
            summary.addCell(makeCell(String.valueOf(d.totalPedidos()), normalFont));
            summary.addCell(makeCell("Pedidos pagados", headerFont));
            summary.addCell(makeCell(String.valueOf(d.pedidosPagados()), normalFont));
            summary.addCell(makeCell("Pedidos cancelados", headerFont));
            summary.addCell(makeCell(String.valueOf(d.pedidosCancelados()), normalFont));
            summary.addCell(makeCell("Pedidos activos", headerFont));
            summary.addCell(makeCell(String.valueOf(d.pedidosActivos()), normalFont));
            summary.addCell(makeCell("Tiempo promedio (min)", headerFont));
            summary.addCell(makeCell(String.format("%.1f", d.tiempoPromedioMinutos()), normalFont));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            // Top 5
            if (!d.top5Productos().isEmpty()) {
                doc.add(new Paragraph("Top 5 Productos", headerFont));
                PdfPTable topTable = new PdfPTable(2);
                topTable.setWidthPercentage(80);
                topTable.addCell(makeCell("Producto", headerFont));
                topTable.addCell(makeCell("Cantidad", headerFont));
                for (TopProduct tp : d.top5Productos()) {
                    topTable.addCell(makeCell(tp.productName(), normalFont));
                    topTable.addCell(makeCell(String.valueOf(tp.totalQuantity()), normalFont));
                }
                doc.add(topTable);
                doc.add(Chunk.NEWLINE);
            }

            // Alerts
            if (!d.alertasInventario().isEmpty()) {
                doc.add(new Paragraph("Alertas de Inventario", headerFont));
                PdfPTable alertTable = new PdfPTable(3);
                alertTable.setWidthPercentage(80);
                alertTable.addCell(makeCell("Ingrediente", headerFont));
                alertTable.addCell(makeCell("Disponible", headerFont));
                alertTable.addCell(makeCell("Minimo", headerFont));
                for (InventoryService.InventoryItemDTO a : d.alertasInventario()) {
                    alertTable.addCell(makeCell(a.ingredientName(), normalFont));
                    alertTable.addCell(makeCell(String.valueOf(a.quantityAvailable()), normalFont));
                    alertTable.addCell(makeCell(String.valueOf(a.minThreshold()), normalFont));
                }
                doc.add(alertTable);
                doc.add(Chunk.NEWLINE);
            }

            // Distribution
            if (!d.distribucionPorEstado().isEmpty()) {
                doc.add(new Paragraph("Distribucion por Estado", headerFont));
                PdfPTable distTable = new PdfPTable(2);
                distTable.setWidthPercentage(60);
                distTable.addCell(makeCell("Estado", headerFont));
                distTable.addCell(makeCell("Cantidad", headerFont));
                for (Map.Entry<String, Integer> e : d.distribucionPorEstado().entrySet()) {
                    distTable.addCell(makeCell(e.getKey(), normalFont));
                    distTable.addCell(makeCell(String.valueOf(e.getValue()), normalFont));
                }
                doc.add(distTable);
            }

            doc.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("Error generando PDF: " + ex.getMessage());
        }
    }

    // ─── EXPORTACIÓN POR RANGO ─────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    public String exportDateRangeToCsv(LocalDate desde, LocalDate hasta) {
        var ventas = getVentasReport(desde, hasta);
        var top = getProductosTopReport(desde, hasta);
        var inv = getInventarioConsumido(desde, hasta);
        var tiempos = getTiemposPromedio(desde, hasta);
        var extra = getDateRangeFinancial(desde, hasta);

        StringWriter sw = new StringWriter();
        sw.write('\uFEFF');
        sw.write("Reporte financiero," + desde + " al " + hasta + "\n\n");
        sw.write("Resumen\n");
        sw.write("Ventas totales," + ventas.totalVentas() + "\n");
        sw.write("Pedidos pagados," + ventas.pedidosPagados() + "\n");
        sw.write("Ticket promedio," + extra.get("ticketPromedio") + "\n");
        sw.write("Tiempo promedio (min)," + String.format("%.1f", tiempos.minutosPromedio()) + "\n");
        sw.write("Muestras," + tiempos.muestras() + "\n\n");

        sw.write("Ventas por dia,Fecha,Ventas,Pedidos\n");
        for (Map<String, Object> d : (List<Map<String, Object>>) extra.get("ventasPorDia")) {
            sw.write("," + d.get("day") + "," + d.get("sales") + "," + d.get("orders") + "\n");
        }
        sw.write("\n");

        sw.write("Ventas por categoria,Categoria,Ingresos,Items\n");
        for (Map<String, Object> c : (List<Map<String, Object>>) extra.get("ventasPorCategoria")) {
            sw.write("," + c.get("category") + "," + c.get("revenue") + "," + c.get("items") + "\n");
        }
        sw.write("\n");

        sw.write("Top productos por ingreso,Producto,Cantidad,Ingreso\n");
        for (TopProduct p : (List<TopProduct>) extra.get("topProductosIngreso")) {
            sw.write("," + p.productName() + "," + p.totalQuantity() + "," + p.totalRevenue() + "\n");
        }
        sw.write("\n");

        sw.write("Top Productos por cantidad,Producto,Cantidad\n");
        for (ProductoTopReport p : top) {
            sw.write("," + p.producto() + "," + p.cantidad() + "\n");
        }
        sw.write("\n");

        sw.write("Distribucion por estado,Estado,Cantidad\n");
        for (Map.Entry<String, Integer> entry : ((Map<String, Integer>) extra.get("distribucionEstado")).entrySet()) {
            sw.write("," + entry.getKey() + "," + entry.getValue() + "\n");
        }
        sw.write("\n");

        sw.write("Inventario Consumido,Ingrediente,Cantidad\n");
        for (InventarioConsumidoReport i : inv) {
            sw.write("," + i.ingrediente() + "," + i.cantidadTotal() + "\n");
        }
        return sw.toString();
    }

    public byte[] exportDateRangeToPdf(LocalDate desde, LocalDate hasta) {
        try {
            var ventas = getVentasReport(desde, hasta);
            var top = getProductosTopReport(desde, hasta);
            var inv = getInventarioConsumido(desde, hasta);
            var tiempos = getTiemposPromedio(desde, hasta);

            Document doc = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, Color.ORANGE);
            Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);
            Font normalFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.BLACK);

            doc.add(new Paragraph("Reporte: " + desde + " al " + hasta, titleFont));
            doc.add(Chunk.NEWLINE);

            var extra = getDateRangeFinancial(desde, hasta);
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(60);
            summary.addCell(makeCell("Ventas totales", headerFont));
            summary.addCell(makeCell("$" + String.format("%,.2f", ventas.totalVentas()), normalFont));
            summary.addCell(makeCell("Pedidos pagados", headerFont));
            summary.addCell(makeCell(String.valueOf(ventas.pedidosPagados()), normalFont));
            summary.addCell(makeCell("Ticket promedio", headerFont));
            summary.addCell(makeCell("$" + String.format("%,.2f", ((Number) extra.get("ticketPromedio")).doubleValue()), normalFont));
            summary.addCell(makeCell("Tiempo promedio (min)", headerFont));
            summary.addCell(makeCell(String.format("%.1f", tiempos.minutosPromedio()), normalFont));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            if (!top.isEmpty()) {
                doc.add(new Paragraph("Productos más vendidos (cantidad)", headerFont));
                PdfPTable topTable = new PdfPTable(2);
                topTable.setWidthPercentage(80);
                topTable.addCell(makeCell("Producto", headerFont));
                topTable.addCell(makeCell("Cantidad", headerFont));
                for (ProductoTopReport p : top) {
                    topTable.addCell(makeCell(p.producto(), normalFont));
                    topTable.addCell(makeCell(String.valueOf(p.cantidad()), normalFont));
                }
                doc.add(topTable);
                doc.add(Chunk.NEWLINE);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> byDay = (List<Map<String, Object>>) extra.get("ventasPorDia");
            if (!byDay.isEmpty()) {
                doc.add(new Paragraph("Ventas por dia", headerFont));
                PdfPTable dayTable = new PdfPTable(3);
                dayTable.setWidthPercentage(90);
                dayTable.addCell(makeCell("Fecha", headerFont));
                dayTable.addCell(makeCell("Ventas", headerFont));
                dayTable.addCell(makeCell("Pedidos", headerFont));
                for (Map<String, Object> d : byDay) {
                    dayTable.addCell(makeCell(String.valueOf(d.get("day")), normalFont));
                    dayTable.addCell(makeCell(String.valueOf(d.get("sales")), normalFont));
                    dayTable.addCell(makeCell(String.valueOf(d.get("orders")), normalFont));
                }
                doc.add(dayTable);
                doc.add(Chunk.NEWLINE);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> byCat = (List<Map<String, Object>>) extra.get("ventasPorCategoria");
            if (!byCat.isEmpty()) {
                doc.add(new Paragraph("Ventas por categoria", headerFont));
                PdfPTable catTable = new PdfPTable(3);
                catTable.setWidthPercentage(90);
                catTable.addCell(makeCell("Categoria", headerFont));
                catTable.addCell(makeCell("Ingresos", headerFont));
                catTable.addCell(makeCell("Items", headerFont));
                for (Map<String, Object> c : byCat) {
                    catTable.addCell(makeCell(String.valueOf(c.get("category")), normalFont));
                    catTable.addCell(makeCell(String.valueOf(c.get("revenue")), normalFont));
                    catTable.addCell(makeCell(String.valueOf(c.get("items")), normalFont));
                }
                doc.add(catTable);
                doc.add(Chunk.NEWLINE);
            }

            @SuppressWarnings("unchecked")
            List<TopProduct> topRev = (List<TopProduct>) extra.get("topProductosIngreso");
            if (!topRev.isEmpty()) {
                doc.add(new Paragraph("Top productos por ingreso", headerFont));
                PdfPTable revTable = new PdfPTable(3);
                revTable.setWidthPercentage(90);
                revTable.addCell(makeCell("Producto", headerFont));
                revTable.addCell(makeCell("Cantidad", headerFont));
                revTable.addCell(makeCell("Ingreso", headerFont));
                for (TopProduct p : topRev) {
                    revTable.addCell(makeCell(p.productName(), normalFont));
                    revTable.addCell(makeCell(String.valueOf(p.totalQuantity()), normalFont));
                    revTable.addCell(makeCell("$" + String.format("%,.2f", p.totalRevenue()), normalFont));
                }
                doc.add(revTable);
                doc.add(Chunk.NEWLINE);
            }

            @SuppressWarnings("unchecked")
            Map<String, Integer> dist = (Map<String, Integer>) extra.get("distribucionEstado");
            if (!dist.isEmpty()) {
                doc.add(new Paragraph("Distribucion por estado", headerFont));
                PdfPTable distTable = new PdfPTable(2);
                distTable.setWidthPercentage(60);
                distTable.addCell(makeCell("Estado", headerFont));
                distTable.addCell(makeCell("Cantidad", headerFont));
                for (Map.Entry<String, Integer> entry : dist.entrySet()) {
                    distTable.addCell(makeCell(entry.getKey(), normalFont));
                    distTable.addCell(makeCell(String.valueOf(entry.getValue()), normalFont));
                }
                doc.add(distTable);
                doc.add(Chunk.NEWLINE);
            }

            if (!inv.isEmpty()) {
                doc.add(new Paragraph("Inventario Consumido", headerFont));
                PdfPTable invTable = new PdfPTable(2);
                invTable.setWidthPercentage(80);
                invTable.addCell(makeCell("Ingrediente", headerFont));
                invTable.addCell(makeCell("Cantidad", headerFont));
                for (InventarioConsumidoReport i : inv) {
                    invTable.addCell(makeCell(i.ingrediente(), normalFont));
                    invTable.addCell(makeCell(String.valueOf(i.cantidadTotal()), normalFont));
                }
                doc.add(invTable);
            }

            doc.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("Error generando PDF: " + ex.getMessage());
        }
    }

    private PdfPCell makeCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        return cell;
    }
}
