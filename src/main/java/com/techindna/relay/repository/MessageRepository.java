package com.techindna.relay.repository;

import com.techindna.relay.repository.model.JMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<JMessage, UUID> {}
