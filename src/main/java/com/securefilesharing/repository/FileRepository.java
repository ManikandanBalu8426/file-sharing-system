package com.securefilesharing.repository;

import com.securefilesharing.entity.FileEntity;
import com.securefilesharing.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    List<FileEntity> findByOwner(User owner);

    @Query("select coalesce(sum(f.sizeBytes), 0) from FileEntity f")
    long sumAllSizeBytes();

    @Query("select f from FileEntity f where (:q is null or lower(f.fileName) like lower(concat('%', :q, '%'))) order by f.uploadTimestamp desc")
    Page<FileEntity> searchByFileName(@Param("q") String q, Pageable pageable);

    long countByIsDeletedFalse();
}
