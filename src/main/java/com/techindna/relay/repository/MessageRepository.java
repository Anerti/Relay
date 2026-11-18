package com.techindna.relay.repository;

import com.techindna.relay.repository.model.JMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<JMessage, UUID> {

    @Query(
            """
            SELECT m FROM JMessage m
            WHERE m.deletedAt IS NULL
              AND ((m.senderId = :callerId AND m.receiver.id = :userId)
                   OR (m.senderId = :userId AND m.receiver.id = :callerId))
            ORDER BY m.createdAt, m.id
            """)
    List<JMessage> findConversation(@Param("callerId") UUID callerId, @Param("userId") UUID userId);
}
