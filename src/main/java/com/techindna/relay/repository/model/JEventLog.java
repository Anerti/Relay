package com.techindna.relay.repository.model;

import com.techindna.relay.entity.enums.EventLogSeverity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "event_log", schema = "relay_app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id")
    private JHost host;

    @Column(name = "user_agent", nullable = false, length = 512)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "severity", nullable = false)
    private EventLogSeverity severity;

    @Column(name = "description", length = 100)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
