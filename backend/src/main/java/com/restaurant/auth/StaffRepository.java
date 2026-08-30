package com.restaurant.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByNameAndActive(String name, boolean active);
    Optional<Staff> findByNameIgnoreCaseAndActive(String name, boolean active);
}
