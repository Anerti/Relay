package com.techindna.relay.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.techindna.relay.service.mail.EmailService;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthChangePasswordTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.42";
    private static final String UNKNOWN_GEOIP_IP = "198.51.100.7";
    private static final String USER_AGENT = "AuthChangePasswordTest";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String NEW_PASSWORD = "N3wSecurePass!";
    private static final String INVALID_CREDENTIALS = "Invalid credentials";

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    void clean() {
        eventLogRepository.deleteAll();
        hostRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void changesPasswordAndSendsNotification() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        changePassword(body(Map.of()), tokenFor(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        JUser updated = reload(user);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, updated.getPassword())).isFalse();
        assertThat(latestEmail()).contains(
                "jdoe@example.com",
                "Your password was changed",
                "The password for your account was changed.",
                CLIENT_IP,
                "Europe",
                "Portugal",
                "Lisbon",
                "Europe/Lisbon",
                "38.7167",
                "-9.1333",
                "20 km",
                "MEO - SERVICOS DE COMUNICACOES E MULTIMEDIA S.A.",
                USER_AGENT,
                "If you did not change your password, contact support immediately.")
                .doesNotContain("N/A", "sign in and change it")
                .containsPattern("Changed At\\s+\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");

        JHost host = hostRepository.findByUserAndIpAddress(user, InetAddress.ofLiteral(CLIENT_IP)).orElseThrow();
        assertThat(eventLogRepository.count()).isOne();
        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getHost().getId()).isEqualTo(host.getId());
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.INFO);
        assertThat(event.getDescription()).isEqualTo("[PASSWORD]: Password changed");
        assertThat(event.getUserAgent()).isEqualTo(USER_AGENT);
    }

    @Test
    void failedGeoIpLookupShowsNotAvailable() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        perform(post("/auth/change-password").contentType(MediaType.APPLICATION_JSON).content(body(Map.of())),
                jwtTokenProvider.generateToken(user.getId().toString(), user.getRole().name(), UNKNOWN_GEOIP_IP),
                UNKNOWN_GEOIP_IP)
                .andExpect(status().isOk());

        assertThat(latestEmail()).contains(UNKNOWN_GEOIP_IP, "N/A", USER_AGENT).doesNotContain("Lisbon");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reload(user).getPassword())).isTrue();
    }

    @Test
    void existingHostIsReused() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        JHost host = createHost(user, HostStatus.AUTHORIZED);

        changePassword(body(Map.of()), tokenFor(user)).andExpect(status().isOk());

        assertThat(hostRepository.count()).isOne();
        assertThat(eventLogRepository.findAll().getFirst().getHost().getId()).isEqualTo(host.getId());
    }

    @Test
    void passwordIsSavedBeforeTheEmailIsSent() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> hashesAtSend = new ArrayList<>();
        doAnswer(invocation -> {
            hashesAtSend.add(jdbc.queryForObject(
                    "SELECT password FROM relay_app.\"user\" WHERE id = ?", String.class, user.getId()));
            return invocation.callRealMethod();
        }).when(emailService).sendMail(any());

        changePassword(body(Map.of()), tokenFor(user)).andExpect(status().isOk());

        assertThat(hashesAtSend).hasSize(1);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, hashesAtSend.getFirst())).isTrue();
    }

    @Test
    void failedEmailRollsBackPasswordChange() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        doThrow(new MailSendException("SMTP unavailable")).when(emailService).sendMail(any());

        changePassword(body(Map.of()), tokenFor(user))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Failed to send password-change notification email"));

        assertPasswordUnchanged(user);
        assertThat(hostRepository.count()).isZero();
        assertThat(eventLogRepository.count()).isZero();
    }

    @Test
    void inactiveAccountCanChangePassword() throws Exception {
        JUser user = createUser(true, UserStatus.INACTIVE);

        changePassword(body(Map.of()), tokenFor(user)).andExpect(status().isOk());

        assertThat(passwordEncoder.matches(NEW_PASSWORD, reload(user).getPassword())).isTrue();
    }

    @Test
    void tokenWithoutRoleChangesPassword() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        changePassword(body(Map.of()), jwtTokenProvider.generateToken(user.getId().toString(), null, CLIENT_IP))
                .andExpect(status().isOk());

        assertThat(passwordEncoder.matches(NEW_PASSWORD, reload(user).getPassword())).isTrue();
    }

    @Test
    void wrongOldPasswordIsUnauthorizedAndLogged() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        changePassword(body(Map.of("oldPassword", "WrongPassword1!")), tokenFor(user))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));

        assertPasswordUnchanged(user);
        assertBlockedEventLogged(user, "[PASSWORD]: Operation failed due to invalid credentials");
    }

    @Test
    void lockedAccountIsForbiddenAndLogged() throws Exception {
        JUser user = createUser(true, UserStatus.LOCKED);

        changePassword(body(Map.of()), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account is locked and cannot change its password."));

        assertPasswordUnchanged(user);
        assertBlockedEventLogged(user, "[PASSWORD]: Operation failed due to locked account");
    }

    @Test
    void unverifiedAccountIsForbiddenAndLogged() throws Exception {
        JUser user = createUser(false, UserStatus.ACTIVE);

        changePassword(body(Map.of()), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account has not been verified"));

        assertPasswordUnchanged(user);
        assertBlockedEventLogged(user, "[PASSWORD]: Operation failed due to unverified account");
    }

    @Test
    void bannedHostIsForbiddenAndLogged() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        createHost(user, HostStatus.BANNED);

        changePassword(body(Map.of()), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Host " + CLIENT_IP + " is banned from accessing this account"));

        assertPasswordUnchanged(user);
        assertBlockedEventLogged(user, "[PASSWORD]: Operation failed due to banned host");
    }

    @Test
    void wrongOldPasswordOnLockedAccountIsUnauthorized() throws Exception {
        JUser user = createUser(true, UserStatus.LOCKED);

        changePassword(body(Map.of("oldPassword", "WrongPassword1!")), tokenFor(user))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));

        assertBlockedEventLogged(user, "[PASSWORD]: Operation failed due to invalid credentials");
    }

    @Test
    void deletedAccountIsUnauthorized() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);
        String token = tokenFor(user);
        userRepository.delete(user);

        changePassword(body(Map.of()), token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));

        assertNothingRecorded();
    }

    @Test
    void blankOldPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("oldPassword", "", "oldPassword is required and cannot be blank");
    }

    @Test
    void tooLongOldPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("oldPassword", PASSWORD + "a".repeat(240), "oldPassword must not exceed 255 characters");
    }

    @Test
    void blankNewPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("newPassword", "", "newPassword is required and cannot be blank");
    }

    @Test
    void tooLongNewPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("newPassword", NEW_PASSWORD + "a".repeat(242), "newPassword must not exceed 255 characters");
    }

    @Test
    void shortNewPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("newPassword", "Short1!a", "New password must be at least 12 characters");
    }

    @Test
    void newPasswordWithoutUppercaseIsUnprocessable() throws Exception {
        assertUnprocessable("newPassword", "n3wsecurepass!", "New password must contain at least one uppercase character");
    }

    @Test
    void newPasswordWithoutLowercaseIsUnprocessable() throws Exception {
        assertUnprocessable("newPassword", "N3WSECUREPASS!", "New password must contain at least one lowercase character");
    }

    @Test
    void newPasswordWithoutDigitIsUnprocessable() throws Exception {
        assertUnprocessable("newPassword", "NewSecurePass!", "New password must contain at least one digit");
    }

    @Test
    void newPasswordWithoutSpecialCharacterIsUnprocessable() throws Exception {
        assertUnprocessable("newPassword", "N3wSecurePass1", "New password must contain at least one special character");
    }

    @Test
    void newPasswordWithLineBreakIsUnprocessable() throws Exception {
        assertUnprocessable("newPassword", "N3wSecure\nPass!", "New password must not contain line breaks or control characters");
    }

    @Test
    void blankConfirmationIsUnprocessable() throws Exception {
        assertUnprocessable("confirmNewPassword", "", "confirmNewPassword is required and cannot be blank");
    }

    @Test
    void mismatchedConfirmationIsUnprocessable() throws Exception {
        assertUnprocessable("confirmNewPassword", "OtherPassword1!", "Passwords do not match");
    }

    @Test
    void reusedPasswordIsUnprocessable() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        changePassword(body(Map.of("newPassword", PASSWORD, "confirmNewPassword", PASSWORD)), tokenFor(user))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Cannot use this password"));

        assertPasswordUnchanged(user);
        assertNothingRecorded();
    }

    @Test
    void missingBodyIsBadRequest() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        perform(post("/auth/change-password").contentType(MediaType.APPLICATION_JSON), tokenFor(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));

        assertNothingRecorded();
    }

    @Test
    void wrongContentTypeIsUnsupported() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        perform(post("/auth/change-password").contentType(MediaType.TEXT_PLAIN).content(body(Map.of())), tokenFor(user))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported content type."));

        assertPasswordUnchanged(user);
        assertNothingRecorded();
    }

    @Test
    void acceptWithoutJsonIsNotAcceptableAndChangesNothing() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        perform(post("/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .content(body(Map.of())),
                tokenFor(user))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.message").value("Not acceptable."));

        assertPasswordUnchanged(user);
        assertNothingRecorded();
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        changePassword(body(Map.of()), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

        assertPasswordUnchanged(user);
        assertNothingRecorded();
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        changePassword(body(Map.of()), "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

        assertPasswordUnchanged(user);
        assertNothingRecorded();
    }

    @Test
    void tokenFromAnotherIpIsForbidden() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        changePassword(body(Map.of()),
                jwtTokenProvider.generateToken(user.getId().toString(), "GUEST", "198.51.100.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));

        assertPasswordUnchanged(user);
        assertNothingRecorded();
    }

    @Test
    void wrongMethodIsForbidden() throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        perform(get("/auth/change-password"), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));
    }

    private void assertUnprocessable(String field, String value, String message) throws Exception {
        JUser user = createUser(true, UserStatus.ACTIVE);

        changePassword(body(Map.of(field, value)), tokenFor(user))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(message));

        assertPasswordUnchanged(user);
        assertNothingRecorded();
    }

    private void assertBlockedEventLogged(JUser user, String description) {
        JHost host = hostRepository.findByUserAndIpAddress(user, InetAddress.ofLiteral(CLIENT_IP)).orElseThrow();
        assertThat(eventLogRepository.count()).isOne();
        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getHost().getId()).isEqualTo(host.getId());
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.MEDIUM);
        assertThat(event.getDescription()).isEqualTo(description);
        verify(emailService, never()).sendMail(any());
    }

    private void assertPasswordUnchanged(JUser user) {
        assertThat(reload(user).getPassword()).isEqualTo(user.getPassword());
    }

    private void assertNothingRecorded() {
        assertThat(hostRepository.count()).isZero();
        assertThat(eventLogRepository.count()).isZero();
        verify(emailService, never()).sendMail(any());
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

    private JHost createHost(JUser user, HostStatus status) {
        return hostRepository.saveAndFlush(JHost.builder()
                .user(user)
                .ipAddress(InetAddress.ofLiteral(CLIENT_IP))
                .status(status)
                .build());
    }

    private JUser reload(JUser user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private static String body(Map<String, String> changes) {
        Map<String, String> fields = new HashMap<>(Map.of(
                "oldPassword", PASSWORD, "newPassword", NEW_PASSWORD, "confirmNewPassword", NEW_PASSWORD));
        fields.putAll(changes);
        return new ObjectMapper().writeValueAsString(fields);
    }

    private ResultActions changePassword(String body, String token) throws Exception {
        return perform(post("/auth/change-password").contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String token) throws Exception {
        return perform(builder, token, CLIENT_IP);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String token, String ipAddress)
            throws Exception {
        return mockMvc.perform(builder
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .with(request -> {
                    request.setRemoteAddr(ipAddress);
                    if (token != null) {
                        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                    return request;
                }));
    }

    private static String latestEmail() {
        return RestClient.create(mailpitApiUrl()).get().uri("/message/latest").retrieve().body(String.class);
    }
}
