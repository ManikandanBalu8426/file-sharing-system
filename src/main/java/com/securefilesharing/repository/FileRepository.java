package com.securefilesharing.repository;

import com.securefilesharing.entity.FileEntity;
import com.securefilesharing.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    List<FileEntity> findByOwner(User owner);
}
