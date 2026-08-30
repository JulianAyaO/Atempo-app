package com.restaurant.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TableRepository extends JpaRepository<TableEntity, Long> {
    Optional<TableEntity> findByTableNumber(Integer tableNumber);
    List<TableEntity> findByActiveTrueOrderByTableNumber();
}
