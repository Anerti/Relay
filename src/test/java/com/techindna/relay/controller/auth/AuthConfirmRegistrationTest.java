package com.techindna.relay.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.repository.EventLogRepository;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JEventLog;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.net.InetAddress;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthConfirmRegistrationTest extends TestcontainersConfig {

    private static final String TOKEN_PREFIX = "auth:verification:";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String REGISTRATION_IP = "203.0.113.42";
    private static final String INVALID_TOKEN = "Invalid or expired token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void clean() {
        eventLogRepository.deleteAll();
        hostRepository.deleteAll();
        userRepository.deleteAll();
        redis.delete(redis.keys(TOKEN_PREFIX + "*"));
    }

    @Test
    void confirmsRegistrationAndReturnsJwtBoundToCallerIp() throws Exception {
        String token = registerAndGetToken();
        JUser user = userRepository.findAll().getFirst();

        String body = confirm(token, "198.51.100.7")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.username").value("jdoe"))
                .andExpect(jsonPath("$.user.firstName").value("John"))
                .andExpect(jsonPath("$.user.lastName").value("Doe"))
                .andExpect(jsonPath("$.user.email").value("jdoe@example.com"))
                .andExpect(jsonPath("$.user.role").value("guest"))
                .andExpect(jsonPath("$.user.userStatus").value("active"))
                .andExpect(jsonPath("$.user.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.user.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.user", not(hasKey("verified"))))
                .andExpect(jsonPath("$.user", not(hasKey("password"))))
                .andReturn().getResponse().getContentAsString();

        Claims claims = jwtTokenProvider.validateToken(new ObjectMapper().readTree(body).get("token").asString());
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo("GUEST");
        assertThat(claims.get("ip_address", String.class)).isEqualTo("198.51.100.7");

        assertThat(userRepository.findById(user.getId()).orElseThrow().getVerified()).isTrue();
        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();

        JHost host = hostRepository.findByUserAndIpAddress(user, InetAddress.ofLiteral("198.51.100.7")).orElseThrow();
        assertThat(hostRepository.count()).isEqualTo(2);
        JEventLog event = eventLogRepository.findAll().stream()
                .max(Comparator.comparing(JEventLog::getCreatedAt)).orElseThrow();
        assertThat(event.getHost().getId()).isEqualTo(host.getId());
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.INFO);
        assertThat(event.getDescription()).isEqualTo("[REGISTRATION]: Account verified");
        assertThat(event.getUserAgent()).isEqualTo("AuthConfirmRegistrationTest");
    }

    @Test
    void confirmationFromRegistrationIpReusesHost() throws Exception {
        confirm(registerAndGetToken(), REGISTRATION_IP).andExpect(status().isOk());

        assertThat(hostRepository.count()).isOne();
        assertThat(eventLogRepository.count()).isEqualTo(2);
    }

    @Test
    void replayedTokenIsUnauthorized() throws Exception {
        String token = registerAndGetToken();
        confirm(token, REGISTRATION_IP).andExpect(status().isOk());

        assertUnauthorized(token);
    }

    @Test
    void unknownTokenIsUnauthorized() throws Exception {
        assertUnauthorized(UUID.randomUUID().toString());
    }

    @Test
    void malformedTokenValueIsUnauthorizedAndConsumed() throws Exception {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(TOKEN_PREFIX + token, "not-a-valid-value");

        assertUnauthorized(token);
        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
    }

    @Test
    void tokenOfDeletedUserIsUnauthorizedAndConsumed() throws Exception {
        String token = registerAndGetToken();
        userRepository.deleteAll();

        assertUnauthorized(token);
        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
    }

    @Test
    void onlyPostIsPublic() throws Exception {
        mockMvc.perform(get("/auth/mfa/confirm/register/{token}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private void assertUnauthorized(String token) throws Exception {
        confirm(token, REGISTRATION_IP)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN));
    }

    private ResultActions confirm(String token, String ipAddress) throws Exception {
        return mockMvc.perform(post("/auth/mfa/confirm/register/{token}", token)
                .with(request -> {
                    request.setRemoteAddr(ipAddress);
                    return request;
                })
                .header(HttpHeaders.USER_AGENT, "AuthConfirmRegistrationTest")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED));
    }

    private String registerAndGetToken() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .with(request -> {
                            request.setRemoteAddr(REGISTRATION_IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "jdoe", "password": "%s", "confirmPassword": "%s",
                                 "firstName": "John", "lastName": "Doe", "email": "jdoe@example.com"}
                                """.formatted(PASSWORD, PASSWORD)))
                .andExpect(status().isAccepted());
        return redis.keys(TOKEN_PREFIX + "*").iterator().next().substring(TOKEN_PREFIX.length());
    }
}
