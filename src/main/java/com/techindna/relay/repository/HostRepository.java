package com.techindna.relay.repository;

import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface HostRepository extends JpaRepository<JHost, UUID> {

    Optional<JHost> findByUserAndIpAddress(JUser user, InetAddress ipAddress);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO relay_app.host (user_id, ip_address, last_seen_at)
            VALUES (:userId, CAST(:ipAddress AS inet), :lastSeenAt)
            ON CONFLICT (user_id, ip_address) DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at
            """, nativeQuery = true)
    void upsertLastSeen(
            @Param("userId") UUID userId,
            @Param("ipAddress") String ipAddress,
            @Param("lastSeenAt") Instant lastSeenAt);
}
