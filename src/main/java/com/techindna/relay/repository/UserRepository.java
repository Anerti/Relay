package com.techindna.relay.repository;

import com.techindna.relay.entity.enums.UserRole;
import com.techindna.relay.repository.model.JUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<JUser, UUID> {

    Optional<JUser> findByUsername(String username);

    Optional<JUser> findByEmail(String email);

    Optional<JUser> findByIdAndVerifiedTrue(UUID id);

    @Query(
            """
            SELECT u FROM JUser u
            WHERE u.role = :role
              AND (LOCATE(:search, LOWER(u.username)) > 0
                   OR LOCATE(:search, LOWER(u.firstName)) > 0
                   OR LOCATE(:search, LOWER(u.lastName)) > 0
                   OR LOCATE(:search, LOWER(u.email)) > 0
                   OR :search IS NULL)
            """)
    Page<JUser> findByRoleAndSearch(
            @Param("role") UserRole role, @Param("search") String search, Pageable pageable);
}
