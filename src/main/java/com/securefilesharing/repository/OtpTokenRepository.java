package com.securefilesharing.repository;

import com.securefilesharing.entity.OtpPurpose;
import com.securefilesharing.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findByIdAndPurpose(Long id, OtpPurpose purpose);
}
