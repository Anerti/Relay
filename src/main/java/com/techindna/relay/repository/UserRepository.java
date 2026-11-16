package com.techindna.relay.repository;

import com.techindna.relay.repository.model.JUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<JUser, UUID> {

    Optional<JUser> findByUsername(String username);

    Optional<JUser> findByEmail(String email);
}
