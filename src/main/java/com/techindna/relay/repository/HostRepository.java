package com.techindna.relay.repository;

import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostRepository extends JpaRepository<JHost, UUID> {

    Optional<JHost> findByUserAndIpAddress(JUser user, InetAddress ipAddress);
}
