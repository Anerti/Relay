package com.techindna.relay.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.techindna.relay.service.mail.EmailService;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthLoginTest extends TestcontainersConfig {

    private static final String TOKEN_PREFIX = "auth:verification:";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String LOGIN_IP = "203.0.113.42";
    private static final String INVALID_CREDENTIALS = "Invalid username/email or password.";

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

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    void clean() {
        eventLogRepository.deleteAll();
        hostRepository.deleteAll();
        userRepository.deleteAll();
        redis.delete(redis.keys(TOKEN_PREFIX + "*"));
    }

    @Test
    void sendsLoginVerificationLinkAndRecordsHost() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        login(body("JDoe", null, PASSWORD), LOGIN_IP)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("A verification link has been sent to your email"));

        String key = redis.keys(TOKEN_PREFIX + "*").iterator().next();
        assertThat(redis.opsForValue().get(key)).isEqualTo("LOGIN:" + user.getId());

        String token = key.substring(TOKEN_PREFIX.length());
        assertThat(latestEmail()).contains(
                "jdoe@example.com",
                "Sign in to your account",
                "http://localhost:8080/auth/verify/" + token,
                "203.0.113.42",
                "Lisbon",
                "Europe/Lisbon",
                "MEO - SERVICOS DE COMUNICACOES E MULTIMEDIA S.A.")
                .doesNotContain("Username jdoe", "Name John Doe", "Email jdoe@example.com");

        JHost host = hostRepository.findByUserAndIpAddress(user, InetAddress.ofLiteral(LOGIN_IP)).orElseThrow();
        assertThat(host.getStatus()).isEqualTo(HostStatus.AUTHORIZED);
        assertThat(host.getLastSeenAt()).isNotNull();

        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getHost().getId()).isEqualTo(host.getId());
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.INFO);
        assertThat(event.getDescription()).isEqualTo("[LOGIN]: Verification link requested");
        assertThat(event.getUserAgent()).isEqualTo("AuthLoginTest");
    }

    @Test
    void logsInWithEmailNormalizedToLowerCase() throws Exception {
        createUser(true, UserStatus.ACTIVE);

        login(body(null, " JDoe@Example.com ", PASSWORD), LOGIN_IP)
                .andExpect(status().isAccepted());

        assertThat(redis.keys(TOKEN_PREFIX + "*")).hasSize(1);
    }

    @Test
    void wrongPasswordIsUnauthorizedAndLogged() throws Exception {
        createUser(true, UserStatus.ACTIVE);

        login(body("jdoe", null, "WrongPassword1!"), LOGIN_IP)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
        assertThat(hostRepository.count()).isOne();
        assertThat(eventLogRepository.count()).isOne();
        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.MEDIUM);
        assertThat(event.getDescription()).isEqualTo("[LOGIN FAILED]: invalid credentials");
        assertThat(event.getUserAgent()).isEqualTo("AuthLoginTest");
    }

    @Test
    void unknownUsernameIsUnauthorized() throws Exception {
        login(body("unknown", null, PASSWORD), LOGIN_IP)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
    }

    @Test
    void unknownUserStillHashesPassword() throws Exception {
        login(body("unknown", null, PASSWORD), LOGIN_IP)
                .andExpect(status().isUnauthorized());

        verify(passwordEncoder).encode(PASSWORD);
    }

    @Test
    void concurrentAttemptsFromNewHostAreAllAudited() throws Exception {
        createUser(true, UserStatus.ACTIVE);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            statuses.add(pool.submit(() -> {
                start.await();
                return login(body("jdoe", null, "WrongPassword1!"), "198.51.100.99")
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();

        for (Future<Integer> s : statuses) {
            assertThat(s.get()).isEqualTo(401);
        }
        pool.shutdown();
        assertThat(hostRepository.count()).isOne();
        assertThat(eventLogRepository.count()).isEqualTo(6);
    }

    @Test
    void unknownEmailIsUnauthorized() throws Exception {
        login(body(null, "unknown@example.com", PASSWORD), LOGIN_IP)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
    }

    @Test
    void lockedAccountIsForbidden() throws Exception {
        createUser(true, UserStatus.LOCKED);

        login(body("jdoe", null, PASSWORD), LOGIN_IP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "This account has been locked after too many unsuccessful sign-in attempts."));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
        assertThat(hostRepository.count()).isOne();
        assertBlockedEvent("account locked");
    }

    @Test
    void inactiveAccountCanRequestLoginLink() throws Exception {
        JUser user = createUser(true, UserStatus.INACTIVE);

        login(body("jdoe", null, PASSWORD), LOGIN_IP)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("A verification link has been sent to your email"));

        String key = redis.keys(TOKEN_PREFIX + "*").iterator().next();
        assertThat(redis.opsForValue().get(key)).isEqualTo("LOGIN:" + user.getId());
        assertThat(eventLogRepository.findAll().getFirst().getDescription())
                .isEqualTo("[LOGIN]: Verification link requested");
    }

    @Test
    void unverifiedAccountIsForbidden() throws Exception {
        createUser(false, UserStatus.ACTIVE);

        login(body("jdoe", null, PASSWORD), LOGIN_IP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Verify your email address before signing in."));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
        assertThat(hostRepository.count()).isOne();
        assertBlockedEvent("account not verified");
    }

    @Test
    void bannedHostIsForbidden() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        hostRepository.saveAndFlush(JHost.builder()
                .user(user)
                .ipAddress(InetAddress.ofLiteral(LOGIN_IP))
                .status(HostStatus.BANNED)
                .build());

        login(body("jdoe", null, PASSWORD), LOGIN_IP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Host " + LOGIN_IP + " is banned from accessing this account"));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
        assertThat(hostRepository.findAll().getFirst().getStatus()).isEqualTo(HostStatus.BANNED);
        assertBlockedEvent("host banned");
    }

    @Test
    void anotherHostCanStillLogInWhenOneIsBanned() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        hostRepository.saveAndFlush(JHost.builder()
                .user(user)
                .ipAddress(InetAddress.ofLiteral(LOGIN_IP))
                .status(HostStatus.BANNED)
                .build());

        login(body("jdoe", null, PASSWORD), "198.51.100.7")
                .andExpect(status().isAccepted());

        assertThat(redis.keys(TOKEN_PREFIX + "*")).hasSize(1);
    }

    @Test
    void missingUsernameAndEmailIsUnprocessable() throws Exception {
        assertUnprocessable(
                body(null, null, PASSWORD),
                "Username or email is required and cannot be blank");
    }

    @Test
    void blankUsernameWithoutEmailIsUnprocessable() throws Exception {
        assertUnprocessable(
                body("  ", null, PASSWORD),
                "Username or email is required and cannot be blank");
    }

    @Test
    void usernameAndEmailTogetherAreUnprocessable() throws Exception {
        assertUnprocessable(
                body("jdoe", "jdoe@example.com", PASSWORD),
                "Provide either username or email, not both");
    }

    @Test
    void invalidUsernameIsUnprocessable() throws Exception {
        assertUnprocessable(body("a", null, PASSWORD), "Username a is invalid");
    }

    @Test
    void invalidEmailIsUnprocessable() throws Exception {
        assertUnprocessable(
                body(null, "not-an-email", PASSWORD), "Email not-an-email is not valid");
    }

    @Test
    void tooLongEmailIsUnprocessable() throws Exception {
        assertUnprocessable(
                body(null, "a".repeat(89) + "@example.com", PASSWORD),
                "email must not exceed 100 characters");
    }

    @Test
    void blankPasswordIsUnprocessable() throws Exception {
        assertUnprocessable(
                body("jdoe", null, ""), "password is required and cannot be blank");
    }

    @Test
    void malformedBodyIsBadRequest() throws Exception {
        login("{").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));
    }

    @Test
    void unsupportedContentTypeIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(body("jdoe", null, PASSWORD)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported content type."));
    }

    @Test
    void textOnlyClientIsNotAcceptableBeforeAnythingIsSaved() throws Exception {
        createUser(true, UserStatus.ACTIVE);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .content(body("jdoe", null, PASSWORD)))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.message").value("Not acceptable."));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
        assertThat(hostRepository.count()).isZero();
        verify(emailService, never()).sendMail(any());
    }

    @Test
    void getIsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(jsonPath("$.message").value("Method not allowed."));
    }

    @Test
    void failedEmailRollsBackHostAndEventAndDeletesToken() throws Exception {
        createUser(true, UserStatus.ACTIVE);
        doThrow(new MailSendException("SMTP unavailable")).when(emailService).sendMail(any());

        login(body("jdoe", null, PASSWORD), LOGIN_IP)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong, please try again later"));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
        assertThat(hostRepository.count()).isZero();
        assertThat(eventLogRepository.count()).isZero();
    }

    private void assertUnprocessable(String body, String message) throws Exception {
        login(body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(message));

        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
    }

    private void assertBlockedEvent(String reason) {
        assertThat(eventLogRepository.count()).isOne();
        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.MEDIUM);
        assertThat(event.getDescription()).isEqualTo("[LOGIN FAILED]: " + reason);
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

    private ResultActions login(String body) throws Exception {
        return login(body, LOGIN_IP);
    }

    private ResultActions login(String body, String ipAddress) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ipAddress);
                    return request;
                })
                .header(HttpHeaders.USER_AGENT, "AuthLoginTest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String body(String username, String email, String password) {
        Map<String, String> fields = new HashMap<>();
        if (username != null) {
            fields.put("username", username);
        }
        if (email != null) {
            fields.put("email", email);
        }
        fields.put("password", password);
        return new ObjectMapper().writeValueAsString(fields);
    }

    private static String latestEmail() {
        return RestClient.create(mailpitApiUrl()).get().uri("/message/latest").retrieve().body(String.class);
    }
}
