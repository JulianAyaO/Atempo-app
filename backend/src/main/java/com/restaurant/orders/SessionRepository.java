package com.restaurant.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, String> {
    Optional<Session> findByTableIdAndStatus(Long tableId, String status);
    List<Session> findByStatus(String status);
}
