package com.restaurant.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Order> findById(Long id);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM Order o WHERE o.sessionId = :sessionId AND o.status = 'DRAFT'")
    Optional<Order> findDraftBySessionId(String sessionId);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM Order o WHERE o.sessionId = :sessionId ORDER BY o.createdAt DESC")
    List<Order> findBySessionId(String sessionId);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM Order o WHERE o.status IN ('PENDING','IN_PREPARATION','READY') ORDER BY o.createdAt")
    List<Order> findActiveOrders();

    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM Order o WHERE o.tableId = :tableId AND o.status NOT IN ('CLOSED','CANCELLED','PAID') ORDER BY o.createdAt DESC")
    List<Order> findActiveOrdersByTableId(Long tableId);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM Order o WHERE o.status = 'PAYMENT_REQUESTED'")
    List<Order> findPaymentRequestedOrders();

    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM Order o WHERE o.tableId = :tableId AND o.status = 'DRAFT'")
    List<Order> findDraftsByTableId(Long tableId);
}
