package com.techindna.relay.repository;

import com.techindna.relay.repository.model.JEventLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventLogRepository extends JpaRepository<JEventLog, UUID> {}
