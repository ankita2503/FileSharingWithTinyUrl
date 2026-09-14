package dev.system.tinyurl.Repository;

import dev.system.tinyurl.files.FileShare;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FileShareRepository extends JpaRepository<FileShare, Long> {

    Optional<FileShare> findBySecretKey(String secretKey);

    /** Expired shares whose object still needs removing. */
    @Query("SELECT f FROM FileShare f WHERE f.expiresAt < :now AND f.status <> 'DELETED'")
    List<FileShare> findExpired(@Param("now") Instant now, Limit limit);

    /** Uploads that were initiated but never completed; their objects may be orphaned. */
    @Query("SELECT f FROM FileShare f WHERE f.status = 'PENDING' AND f.createdAt < :cutoff")
    List<FileShare> findAbandoned(@Param("cutoff") Instant cutoff, Limit limit);
}