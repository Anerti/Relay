package com.techindna.relay.repository.model;

import com.techindna.relay.entity.enums.MessageIssuer;
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
@Table(name = "message", schema = "relay_app")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sender_id")
    private UUID senderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    private JUser receiver;

    @Column(name = "reply_to")
    private UUID replyTo;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "issuer", nullable = false)
    @Builder.Default
    private MessageIssuer issuer = MessageIssuer.HUMAN;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
