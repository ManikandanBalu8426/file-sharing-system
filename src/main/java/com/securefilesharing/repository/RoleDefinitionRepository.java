package com.securefilesharing.repository;

import com.securefilesharing.entity.RoleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, Long> {
    Optional<RoleDefinition> findByName(String name);
    boolean existsByName(String name);
}
