package com.techindna.relay.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.entity.enums.HostStatus;
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
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRegistrationTest extends TestcontainersConfig {

    private static final String TOKEN_PREFIX = "auth:verification:";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String NAME_FORMAT =
            " must contain only letters, with single spaces, apostrophes, or hyphens between words";

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
    private DataSource dataSource;

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
    void registersUnverifiedUserAndEmailsVerificationLink() throws Exception {
        register(body(Map.of("username", "JDoe", "email", "JDoe@Example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("An email has been sent to verify your account"));

        JUser user = userRepository.findAll().getFirst();
        assertThat(user.getUsername()).isEqualTo("jdoe");
        assertThat(user.getEmail()).isEqualTo("jdoe@example.com");
        assertThat(user.getVerified()).isFalse();

        String key = redis.keys(TOKEN_PREFIX + "*").iterator().next();
        assertThat(redis.opsForValue().get(key)).isEqualTo("REGISTER:" + user.getId());

        String token = key.substring(TOKEN_PREFIX.length());
        assertThat(latestEmail()).contains(
                "jdoe@example.com",
                "Verify your account",
                "http://localhost:8080/auth/verify/" + token,
                "203.0.113.42",
                "Lisbon",
                "Europe/Lisbon",
                "MEO - SERVICOS DE COMUNICACOES E MULTIMEDIA S.A.");

        JHost host = hostRepository.findByUserAndIpAddress(user, InetAddress.ofLiteral("203.0.113.42")).orElseThrow();
        assertThat(host.getStatus()).isEqualTo(HostStatus.AUTHORIZED);

        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getHost().getId()).isEqualTo(host.getId());
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.INFO);
        assertThat(event.getDescription()).isEqualTo("[REGISTRATION]: Account registered");
        assertThat(event.getUserAgent()).isEqualTo("AuthRegistrationTest");
    }

    @Test
    void blankUsernameIsUnprocessable() throws Exception {
        assertUnprocessable("username", "", "username is required and cannot be blank");
    }

    @Test
    void tooLongUsernameIsUnprocessable() throws Exception {
        assertUnprocessable("username", "j".repeat(51), "username must not exceed 50 characters");
    }

    @Test
    void invalidUsernameIsUnprocessable() throws Exception {
        assertUnprocessable("username", "j", "Username j is invalid");
    }

    @Test
    void blankPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("password", "", "password is required and cannot be blank");
    }

    @Test
    void tooLongPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("password", PASSWORD + "a".repeat(240), "password must not exceed 255 characters");
    }

    @Test
    void shortPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("password", "Short1!", "Password must be at least 12 characters");
    }

    @Test
    void passwordWithoutUppercaseIsUnprocessable() throws Exception {
        assertUnprocessable("password", "strongpassword1!", "Password must contain at least one uppercase character");
    }

    @Test
    void passwordWithoutLowercaseIsUnprocessable() throws Exception {
        assertUnprocessable("password", "STRONGPASSWORD1!", "Password must contain at least one lowercase character");
    }

    @Test
    void passwordWithoutDigitIsUnprocessable() throws Exception {
        assertUnprocessable("password", "StrongPassword!!", "Password must contain at least one digit");
    }

    @Test
    void passwordWithoutSpecialCharacterIsUnprocessable() throws Exception {
        assertUnprocessable("password", "StrongPassword12", "Password must contain at least one special character");
    }

    @Test
    void passwordWithLineBreakIsUnprocessable() throws Exception {
        assertUnprocessable("password", "Strong\nPassword1!", "Password must not contain line breaks or control characters");
    }

    @Test
    void blankConfirmPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("confirmPassword", "", "confirmPassword is required and cannot be blank");
    }

    @Test
    void mismatchedPasswordsAreUnprocessable() throws Exception {
        assertUnprocessable("confirmPassword", "OtherPassword1!", "Passwords do not match");
    }

    @Test
    void blankFirstNameIsUnprocessable() throws Exception {
        assertUnprocessable("firstName", "", "firstName is required and cannot be blank");
    }

    @Test
    void tooLongFirstNameIsUnprocessable() throws Exception {
        assertUnprocessable("firstName", "J".repeat(101), "firstName must not exceed 100 characters");
    }

    @Test
    void invalidFirstNameIsUnprocessable() throws Exception {
        assertUnprocessable("firstName", "J0hn", "firstName" + NAME_FORMAT);
    }

    @Test
    void blankLastNameIsUnprocessable() throws Exception {
        assertUnprocessable("lastName", "", "lastName is required and cannot be blank");
    }

    @Test
    void tooLongLastNameIsUnprocessable() throws Exception {
        assertUnprocessable("lastName", "D".repeat(101), "lastName must not exceed 100 characters");
    }

    @Test
    void invalidLastNameIsUnprocessable() throws Exception {
        assertUnprocessable("lastName", "Doe--Smith", "lastName" + NAME_FORMAT);
    }

    @Test
    void blankEmailIsUnprocessable() throws Exception {
        assertUnprocessable("email", "", "email is required and cannot be blank");
    }

    @Test
    void tooLongEmailIsUnprocessable() throws Exception {
        assertUnprocessable("email", "a".repeat(89) + "@example.com", "email must not exceed 100 characters");
    }

    @Test
    void invalidEmailIsUnprocessable() throws Exception {
        assertUnprocessable("email", "not-an-email", "Email not-an-email is not valid");
    }

    @Test
    void duplicateUsernameIsConflict() throws Exception {
        register(body(Map.of()));

        register(body(Map.of("username", "JDoe", "email", "other@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You cannot use this username"));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        register(body(Map.of()));

        register(body(Map.of("username", "other", "email", "JDoe@Example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You cannot use this email address"));
    }

    @Test
    void malformedBodyIsBadRequest() throws Exception {
        register("{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));
    }

    @Test
    void unsupportedContentTypeIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.TEXT_PLAIN).content(body(Map.of())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported content type."));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void conflictErrorDoesNotDependOnDefaultLocale() throws Exception {
        register(body(Map.of()));

        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            register(body(Map.of("email", "other@example.com")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CONFLICT"));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void hostAndEventAreSavedBeforeEmailIsSent() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Long> countsAtSend = new ArrayList<>();
        doAnswer(invocation -> {
            countsAtSend.add(jdbc.queryForObject("SELECT count(*) FROM relay_app.host", Long.class));
            countsAtSend.add(jdbc.queryForObject("SELECT count(*) FROM relay_app.event_log", Long.class));
            return invocation.callRealMethod();
        }).when(emailService).sendMail(any());

        register(body(Map.of())).andExpect(status().isAccepted());

        assertThat(countsAtSend).containsExactly(1L, 1L);
    }

    @Test
    void failedEmailRollsBackRegistration() throws Exception {
        doThrow(new MailSendException("SMTP unavailable")).when(emailService).sendMail(any());

        register(body(Map.of()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong, please try again later"));

        assertThat(userRepository.count()).isZero();
        assertThat(hostRepository.count()).isZero();
        assertThat(eventLogRepository.count()).isZero();
        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
    }

    @Test
    void failedGeoIpLookupShowsNotAvailable() throws Exception {
        register(body(Map.of()), "198.51.100.7").andExpect(status().isAccepted());

        assertThat(latestEmail()).contains("198.51.100.7", "N/A");
        assertThat(userRepository.count()).isOne();
    }

    private void assertUnprocessable(String field, String value, String message) throws Exception {
        register(body(Map.of(field, value)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(message));

        assertThat(userRepository.count()).isZero();
    }

    private ResultActions register(String body) throws Exception {
        return register(body, "203.0.113.42");
    }

    private ResultActions register(String body, String ipAddress) throws Exception {
        return mockMvc.perform(post("/auth/register")
                .with(request -> {
                    request.setRemoteAddr(ipAddress);
                    return request;
                })
                .header(HttpHeaders.USER_AGENT, "AuthRegistrationTest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String body(Map<String, String> changes) {
        Map<String, String> fields = new HashMap<>(Map.of(
                "username", "jdoe",
                "password", PASSWORD,
                "confirmPassword", PASSWORD,
                "firstName", "John",
                "lastName", "Doe",
                "email", "jdoe@example.com"));
        fields.putAll(changes);
        return new ObjectMapper().writeValueAsString(fields);
    }

    private static String latestEmail() {
        return RestClient.create(mailpitApiUrl()).get().uri("/message/latest").retrieve().body(String.class);
    }
}
