package com.techindna.relay.controller.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.entity.enums.UserRole;
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
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;

@SpringBootTest
@AutoConfigureMockMvc
class UserDeleteTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String USER_AGENT = "UserDeleteTest";
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
    void guestDeletesOwnAccountAndGetsNotification() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        deleteUser(user.getId().toString(), tokenFor(user))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(latestEmail()).contains(
                "jdoe@example.com",
                "Your account was deleted",
                "Your account has been deleted. If you made this request",
                CLIENT_IP,
                USER_AGENT)
                .containsPattern("Deleted At\\s+\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    }

    @Test
    void superuserDeletesOwnAccount() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);

        deleteUser(superuser.getId().toString(), tokenFor(superuser)).andExpect(status().isNoContent());

        assertThat(userRepository.findById(superuser.getId())).isEmpty();
        assertThat(latestEmail()).contains("admin@example.com", CLIENT_IP, USER_AGENT);
    }

    @Test
    void hostsAndEventLogsOutliveTheAccount() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = hostRepository.saveAndFlush(JHost.builder()
                .user(user)
                .ipAddress(InetAddress.ofLiteral(CLIENT_IP))
                .build());
        eventLogRepository.saveAndFlush(JEventLog.builder()
                .host(host)
                .userAgent(USER_AGENT)
                .severity(EventLogSeverity.INFO)
                .description("[LOGIN]: Account signed in")
                .build());

        deleteUser(user.getId().toString(), tokenFor(user)).andExpect(status().isNoContent());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM relay_app.host WHERE id = ? AND user_id IS NULL", Long.class, host.getId()))
                .isOne();
        assertThat(eventLogRepository.count()).isOne();
    }

    @Test
    void accountIsDeletedBeforeTheEmailIsSent() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Long> countsAtSend = new ArrayList<>();
        doAnswer(invocation -> {
            countsAtSend.add(jdbc.queryForObject(
                    "SELECT count(*) FROM relay_app.\"user\" WHERE id = ?", Long.class, user.getId()));
            return invocation.callRealMethod();
        }).when(emailService).sendMail(any());

        deleteUser(user.getId().toString(), tokenFor(user)).andExpect(status().isNoContent());

        assertThat(countsAtSend).containsExactly(0L);
    }

    @Test
    void failedEmailRollsBackDeletion() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        doThrow(new MailSendException("SMTP unavailable")).when(emailService).sendMail(any());

        deleteUser(user.getId().toString(), tokenFor(user))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Failed to send account-deletion notification email"));

        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @Test
    void guestCannotDeleteAnotherUser() throws Exception {
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("jane", UserRole.GUEST);

        assertNotDeleted(other, tokenFor(guest), FORBIDDEN);
    }

    @Test
    void guestCannotDeleteSuperuser() throws Exception {
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JUser superuser = createUser("admin", UserRole.SUPERUSER);

        assertNotDeleted(superuser, tokenFor(guest), FORBIDDEN);
    }

    @Test
    void superuserCannotDeleteGuest() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser guest = createUser("jdoe", UserRole.GUEST);

        assertNotDeleted(guest, tokenFor(superuser), FORBIDDEN);
    }

    @Test
    void superuserCannotDeleteAnotherSuperuser() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser other = createUser("root", UserRole.SUPERUSER);

        assertNotDeleted(other, tokenFor(superuser), FORBIDDEN);
    }

    @Test
    void tokenWithoutRoleDeletesOwnAccount() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        deleteUser(user.getId().toString(), jwtTokenProvider.generateToken(user.getId().toString(), null, CLIENT_IP))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void tokenFromAnotherIpIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        assertNotDeleted(user, jwtTokenProvider.generateToken(user.getId().toString(), "GUEST", "198.51.100.1"),
                "Cannot access this resource from the current IP address.");
    }

    @Test
    void otherUnknownIdIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        deleteUser(UUID.randomUUID().toString(), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN));

        assertThat(userRepository.count()).isOne();
        verify(emailService, never()).sendMail(any());
    }

    @Test
    void deletedOwnAccountIsNotFound() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        String token = tokenFor(user);
        userRepository.delete(user);

        deleteUser(user.getId().toString(), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        verify(emailService, never()).sendMail(any());
    }

    @Test
    void deletingTwiceIsNotFound() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        deleteUser(user.getId().toString(), tokenFor(user)).andExpect(status().isNoContent());
        deleteUser(user.getId().toString(), tokenFor(user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void malformedUserIdIsBadRequest() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        deleteUser("not-a-uuid", tokenFor(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: userId"));

        verify(emailService, never()).sendMail(any());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        deleteUser(user.getId().toString(), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        deleteUser(user.getId().toString(), "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    private void assertNotDeleted(JUser target, String token, String message) throws Exception {
        deleteUser(target.getId().toString(), token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(message));

        assertThat(userRepository.findById(target.getId())).isPresent();
        verify(emailService, never()).sendMail(any());
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

    private ResultActions deleteUser(String userId, String token) throws Exception {
        return mockMvc.perform(delete("/users/{userId}", userId)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
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
