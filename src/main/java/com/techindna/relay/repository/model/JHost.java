package com.techindna.relay.repository.model;

import com.techindna.relay.entity.enums.HostStatus;
import jakarta.persistence.*;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "host", schema = "relay_app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JHost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private JUser user;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", nullable = false)
    private InetAddress ipAddress;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private HostStatus status = HostStatus.AUTHORIZED;

    @Column(name = "last_seen_at", nullable = false)
    @Builder.Default
    private Instant lastSeenAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
