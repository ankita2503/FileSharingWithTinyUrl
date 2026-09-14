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

    /** Live shares whose expiry has passed; their objects still need removing. */
    @Query("SELECT f FROM FileShare f WHERE f.expiresAt < :now AND f.status <> 'DELETED'")
    List<FileShare> findExpired(@Param("now") Instant now, Limit limit);

    /** Deleted shares past the grace period: purge bytes and drop the row. */
    @Query("SELECT f FROM FileShare f WHERE f.status = 'DELETED' AND f.deletedAt < :cutoff")
    List<FileShare> findPurgeable(@Param("cutoff") Instant cutoff, Limit limit);

    /** Uploads initiated but never completed; objects may be orphaned. */
    @Query("SELECT f FROM FileShare f WHERE f.status = 'PENDING' AND f.createdAt < :cutoff")
    List<FileShare> findAbandoned(@Param("cutoff") Instant cutoff, Limit limit);


}