package com.securefilesharing.repository;

import com.securefilesharing.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    List<User> findByActiveFalse();

    long countByActiveTrue();

    @Modifying
    @Transactional
    @Query("update User u set u.active = true where u.active = false")
    int enableAllInactiveUsers();

    long countByStatus(String status);

    List<User> findByStatus(String status);
}
