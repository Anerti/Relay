package com.techindna.relay.controller.user;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.enums.UserRole;
import com.techindna.relay.repository.EventLogRepository;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.jwt.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class UserGetTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String FORBIDDEN = "Cannot access this resource.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void clean() {
        eventLogRepository.deleteAll();
        hostRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void guestGetsOwnProfile() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        getUser(user.getId().toString(), tokenFor(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.username").value("jdoe"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.email").value("jdoe@example.com"))
                .andExpect(jsonPath("$.role").value("guest"))
                .andExpect(jsonPath("$.userStatus").value("active"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$", not(hasKey("verified"))))
                .andExpect(jsonPath("$", not(hasKey("password"))));
    }

    @Test
    void superuserGetsGuestProfile() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser guest = createUser("jdoe", UserRole.GUEST);

        getUser(guest.getId().toString(), tokenFor(superuser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(guest.getId().toString()))
                .andExpect(jsonPath("$.role").value("guest"));
    }

    @Test
    void superuserGetsOwnProfile() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);

        getUser(superuser.getId().toString(), tokenFor(superuser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("superuser"));
    }

    @Test
    void guestCannotGetAnotherUser() throws Exception {
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("jane", UserRole.GUEST);

        getUser(other.getId().toString(), tokenFor(guest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN));
    }

    @Test
    void superuserCannotGetAnotherSuperuser() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser other = createUser("root", UserRole.SUPERUSER);

        getUser(other.getId().toString(), tokenFor(superuser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN));
    }

    @Test
    void tokenWithoutRoleIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        getUser(user.getId().toString(), jwtTokenProvider.generateToken(user.getId().toString(), null, CLIENT_IP))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges to access this resource"));
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        getUser(UUID.randomUUID().toString(), tokenFor(user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void malformedUserIdIsBadRequest() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        getUser("not-a-uuid", tokenFor(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: userId"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        getUser(user.getId().toString(), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        getUser(user.getId().toString(), "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void tokenFromAnotherIpIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        getUser(user.getId().toString(),
                jwtTokenProvider.generateToken(user.getId().toString(), "GUEST", "198.51.100.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));
    }

    @Test
    void trailingSlashIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        perform(get("/users/" + user.getId() + "/"), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));
    }

    @Test
    void unknownPathIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        perform(get("/users/{userId}/unknown", user.getId()), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));
    }

    @Test
    void unknownPathWithoutTokenIsUnauthorized() throws Exception {
        perform(get("/users/{userId}/unknown", UUID.randomUUID()), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void wrongMethodIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        perform(put("/users/{userId}", user.getId()), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));
    }

    private JUser createUser(String username, UserRole role) {
        return userRepository.saveAndFlush(JUser.builder()
                .username(username)
                .password("hash")
                .firstName("John")
                .lastName("Doe")
                .email(username + "@example.com")
                .verified(true)
                .role(role)
                .build());
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private ResultActions getUser(String userId, String token) throws Exception {
        return perform(get("/users/{userId}", userId), token);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String token) throws Exception {
        return mockMvc.perform(builder
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    if (token != null) {
                        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                    return request;
                }));
    }
}
