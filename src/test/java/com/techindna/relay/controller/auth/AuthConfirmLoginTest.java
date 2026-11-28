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
import com.techindna.relay.entity.enums.HostStatus;
import com.techindna.relay.entity.enums.UserStatus;
import com.techindna.relay.repository.EventLogRepository;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JEventLog;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.jwt.JwtTokenProvider;
import com.techindna.relay.service.enums.VerificationFlow;
import com.techindna.relay.service.redis.VerificationTokenService;
import io.jsonwebtoken.Claims;
import java.net.InetAddress;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthConfirmLoginTest extends TestcontainersConfig {

    private static final String TOKEN_PREFIX = "auth:verification:";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String LOGIN_IP = "203.0.113.42";
    private static final String CONFIRM_IP = "198.51.100.7";
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
    private VerificationTokenService verificationTokenService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        eventLogRepository.deleteAll();
        hostRepository.deleteAll();
        userRepository.deleteAll();
        redis.delete(redis.keys(TOKEN_PREFIX + "*"));
    }

    @Test
    void confirmsLoginAndReturnsJwtBoundToCallerIp() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        String body = confirm(token, CONFIRM_IP)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.username").value("jdoe"))
                .andExpect(jsonPath("$.user.firstName").value("John"))
                .andExpect(jsonPath("$.user.lastName").value("Doe"))
                .andExpect(jsonPath("$.user.email").value("jdoe@example.com"))
                .andExpect(jsonPath("$.user.role").value("guest"))
                .andExpect(jsonPath("$.user.userStatus").value("active"))
                .andExpect(jsonPath("$.user.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.user", not(hasKey("verified"))))
                .andExpect(jsonPath("$.user", not(hasKey("password"))))
                .andReturn().getResponse().getContentAsString();

        Claims claims = jwtTokenProvider.validateToken(new ObjectMapper().readTree(body).get("token").asString());
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo("GUEST");
        assertThat(claims.get("ip_address", String.class)).isEqualTo(CONFIRM_IP);

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();

        JHost host = hostRepository.findByUserAndIpAddress(user, InetAddress.ofLiteral(CONFIRM_IP)).orElseThrow();
        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getHost().getId()).isEqualTo(host.getId());
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.INFO);
        assertThat(event.getDescription()).isEqualTo("[LOGIN]: Account signed in");
        assertThat(event.getUserAgent()).isEqualTo("AuthConfirmLoginTest");
    }

    @Test
    void loginLinkTokenCanBeConfirmedFromAnotherIp() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        String token = loginAndGetToken();

        confirm(token, CONFIRM_IP)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()));

        assertThat(hostRepository.count()).isEqualTo(2);
    }

    @Test
    void replayedTokenIsUnauthorized() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        confirm(token, CONFIRM_IP).andExpect(status().isOk());

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
    void malformedUserIdInTokenValueIsUnauthorizedAndConsumed() throws Exception {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(TOKEN_PREFIX + token, "LOGIN:not-a-uuid");

        assertUnauthorized(token);
        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
    }

    @Test
    void registerTokenIsUnauthorizedAndConsumed() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.REGISTER);

        assertUnauthorized(token);
        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
    }

    @Test
    void tokenOfDeletedUserIsUnauthorizedAndConsumed() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);
        userRepository.deleteAll();

        assertUnauthorized(token);
        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
    }

    @Test
    void lockedAccountIsForbiddenAndTokenIsConsumed() throws Exception {
        JUser user = createUser(true, UserStatus.LOCKED);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        confirm(token, CONFIRM_IP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Account is locked. Please request account access restoration to continue."));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
        assertThat(hostRepository.count()).isOne();
        assertBlockedEvent("account locked");
    }

    @Test
    void inactiveAccountCanConfirmLogin() throws Exception {
        JUser user = createUser(true, UserStatus.INACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        confirm(token, CONFIRM_IP)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.userStatus").value("inactive"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
        assertThat(eventLogRepository.findAll().getFirst().getDescription())
                .isEqualTo("[LOGIN]: Account signed in");
    }

    @Test
    void unverifiedAccountIsForbiddenAndTokenIsConsumed() throws Exception {
        JUser user = createUser(false, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        confirm(token, CONFIRM_IP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Verify your email address before signing in."));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
        assertThat(hostRepository.count()).isOne();
        assertBlockedEvent("account not verified");
    }

    @Test
    void bannedHostIsForbiddenAndTokenIsConsumed() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        hostRepository.saveAndFlush(JHost.builder()
                .user(user)
                .ipAddress(InetAddress.ofLiteral(CONFIRM_IP))
                .status(HostStatus.BANNED)
                .build());
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        confirm(token, CONFIRM_IP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Host " + CONFIRM_IP + " is banned from accessing this account"));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
        assertThat(hostRepository.findAll().getFirst().getStatus()).isEqualTo(HostStatus.BANNED);
        assertBlockedEvent("host banned");
    }

    @Test
    void onlyPostIsPublic() throws Exception {
        mockMvc.perform(get("/auth/mfa/confirm/login/{token}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void textOnlyClientIsNotAcceptableAndTokenIsKept() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        mockMvc.perform(post("/auth/mfa/confirm/login/{token}", token)
                        .with(request -> {
                            request.setRemoteAddr(CONFIRM_IP);
                            return request;
                        })
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.message").value("Not acceptable."));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).hasSize(1);
        assertThat(hostRepository.count()).isZero();
    }

    private void assertUnauthorized(String token) throws Exception {
        confirm(token, CONFIRM_IP)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN));
    }

    private void assertBlockedEvent(String reason) {
        assertThat(eventLogRepository.count()).isOne();
        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.MEDIUM);
        assertThat(event.getDescription()).isEqualTo("[LOGIN FAILED]: " + reason);
    }

    private ResultActions confirm(String token, String ipAddress) throws Exception {
        return mockMvc.perform(post("/auth/mfa/confirm/login/{token}", token)
                .with(request -> {
                    request.setRemoteAddr(ipAddress);
                    return request;
                })
                .header(HttpHeaders.USER_AGENT, "AuthConfirmLoginTest")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED));
    }

    private String loginAndGetToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(LOGIN_IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "jdoe", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isAccepted());
        return redis.keys(TOKEN_PREFIX + "*").iterator().next().substring(TOKEN_PREFIX.length());
    }

    private JUser createUser(boolean verified, UserStatus status) {
        return userRepository.saveAndFlush(JUser.builder()
                .username("jdoe")
                .password(passwordEncoder.encode(PASSWORD))
                .firstName("John")
                .lastName("Doe")
                .email("jdoe@example.com")
                .verified(verified)
                .status(status)
                .build());
    }
}
