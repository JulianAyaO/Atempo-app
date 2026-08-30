package com.restaurant.orders;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableRepository tableRepository;

    public TableController(TableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    @GetMapping
    public ResponseEntity<List<TableEntity>> getAllTables() {
        return ResponseEntity.ok(tableRepository.findByActiveTrueOrderByTableNumber());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TableEntity> getTable(@PathVariable Long id) {
        return tableRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TableEntity> createTable(@RequestBody TableEntity table) {
        return ResponseEntity.ok(tableRepository.save(table));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TableEntity> updateTable(@PathVariable Long id, @RequestBody TableEntity updates) {
        return tableRepository.findById(id).map(t -> {
            if (updates.getName() != null) t.setName(updates.getName());
            if (updates.getCapacity() != null) t.setCapacity(updates.getCapacity());
            if (updates.getActive() != null) t.setActive(updates.getActive());
            if (updates.getTableNumber() != null) t.setTableNumber(updates.getTableNumber());
            return ResponseEntity.ok(tableRepository.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }
}
