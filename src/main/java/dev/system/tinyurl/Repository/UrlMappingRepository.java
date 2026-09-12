package dev.system.tinyurl.Repository;

import dev.system.tinyurl.url.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortKey(String shortKey);

    @Query(value = "SELECT * FROM urls WHERE md5(long_url) = md5(:longUrl) AND long_url = :longUrl LIMIT 1",
            nativeQuery = true)
    Optional<UrlMapping> findByLongUrl(@Param("longUrl") String longUrl);
}
