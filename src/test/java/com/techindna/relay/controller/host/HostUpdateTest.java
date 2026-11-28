package com.techindna.relay.controller.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.entity.enums.HostStatus;
import com.techindna.relay.entity.enums.UserRole;
import com.techindna.relay.repository.EventLogRepository;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.jwt.JwtTokenProvider;
import java.net.InetAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class HostUpdateTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String HOST_IP = "198.51.100.1";
    private static final String USER_AGENT = "HostUpdateTest/1.0";
    private static final String BAN = "{\"status\":\"banned\"}";
    private static final String AUTHORIZE = "{\"status\":\"authorized\"}";
    private static final String FORBIDDEN = "Cannot access this resource.";
    private static final String INVALID_STATUS = "status must be authorized or banned";
    private static final Instant LAST_SEEN = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS);

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
    void guestBansOwnHost() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);
        Instant before = Instant.now();

        updateHost(user.getId().toString(), host.getId().toString(), BAN, user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(host.getId().toString()))
                .andExpect(jsonPath("$.ipAddress").value(HOST_IP))
                .andExpect(jsonPath("$.status").value("banned"))
                .andExpect(jsonPath("$.lastSeenAt").value(LAST_SEEN.toString()))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        JHost stored = hostRepository.findById(host.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(HostStatus.BANNED);
        assertThat(stored.getUpdatedAt()).isAfterOrEqualTo(before.truncatedTo(ChronoUnit.MICROS));
        assertThat(stored.getLastSeenAt()).isEqualTo(LAST_SEEN);
        assertEventOnCallerHost(user, "[HOST]: Host " + HOST_IP + " banned");
    }

    @Test
    void guestAuthorizesBannedHost() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.BANNED);

        updateHost(user.getId().toString(), host.getId().toString(), AUTHORIZE, user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("authorized"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(hostRepository.findById(host.getId()).orElseThrow().getStatus()).isEqualTo(HostStatus.AUTHORIZED);
        assertEventOnCallerHost(user, "[HOST]: Host " + HOST_IP + " authorized");
    }

    @Test
    void sameStatusChangesNothing() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);

        updateHost(user.getId().toString(), host.getId().toString(), AUTHORIZE, user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("authorized"))
                .andExpect(jsonPath("$.updatedAt").value(nullValue()));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    @Test
    void secondChangeReusesCallerHost() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);

        updateHost(user.getId().toString(), host.getId().toString(), BAN, user).andExpect(status().isOk());
        updateHost(user.getId().toString(), host.getId().toString(), AUTHORIZE, user).andExpect(status().isOk());

        assertThat(hostRepository.count()).isEqualTo(2);
        assertThat(eventLogRepository.count()).isEqualTo(2);
    }

    @Test
    void superuserBansGuestHost() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(guest, HOST_IP, HostStatus.AUTHORIZED);

        updateHost(guest.getId().toString(), host.getId().toString(), BAN, superuser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("banned"));

        assertThat(hostRepository.findById(host.getId()).orElseThrow().getStatus()).isEqualTo(HostStatus.BANNED);
        assertEventOnCallerHost(superuser, "[HOST]: Host " + HOST_IP + " banned");
    }

    @Test
    void superuserBansOwnHost() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JHost host = createHost(superuser, HOST_IP, HostStatus.AUTHORIZED);

        updateHost(superuser.getId().toString(), host.getId().toString(), BAN, superuser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("banned"));
    }

    @Test
    void superuserMayBanGuestHostWithTheRequestIp() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(guest, CLIENT_IP, HostStatus.AUTHORIZED);

        updateHost(guest.getId().toString(), host.getId().toString(), BAN, superuser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("banned"));
    }

    @Test
    void cannotBanTheHostInUse() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, CLIENT_IP, HostStatus.AUTHORIZED);

        updateHost(user.getId().toString(), host.getId().toString(), BAN, user)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Cannot ban the actual host you"));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    @Test
    void bannedHostInUseCanBeAuthorized() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, CLIENT_IP, HostStatus.BANNED);

        updateHost(user.getId().toString(), host.getId().toString(), AUTHORIZE, user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("authorized"));

        assertThat(hostRepository.count()).isEqualTo(1);
        assertEventOnCallerHost(user, "[HOST]: Host " + CLIENT_IP + " authorized");
    }

    @Test
    void missingStatusIsUnprocessable() throws Exception {
        assertUnprocessable("{}");
    }

    @Test
    void nullStatusIsUnprocessable() throws Exception {
        assertUnprocessable("{\"status\":null}");
    }

    @Test
    void unknownStatusIsUnprocessable() throws Exception {
        assertUnprocessable("{\"status\":\"blocked\"}");
    }

    @Test
    void upperCaseStatusIsUnprocessable() throws Exception {
        assertUnprocessable("{\"status\":\"BANNED\"}");
    }

    @Test
    void guestCannotUpdateAnotherUsersHost() throws Exception {
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("jane", UserRole.GUEST);
        JHost host = createHost(other, HOST_IP, HostStatus.AUTHORIZED);

        updateHost(other.getId().toString(), host.getId().toString(), BAN, guest)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    @Test
    void superuserCannotUpdateAnotherSuperusersHost() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser other = createUser("root", UserRole.SUPERUSER);
        JHost host = createHost(other, HOST_IP, HostStatus.AUTHORIZED);

        updateHost(other.getId().toString(), host.getId().toString(), BAN, superuser)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    @Test
    void tokenWithoutRoleIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);

        perform(patchHost(user.getId().toString(), host.getId().toString(), BAN),
                jwtTokenProvider.generateToken(user.getId().toString(), null, CLIENT_IP))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges to access this resource"));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateHost(UUID.randomUUID().toString(), UUID.randomUUID().toString(), BAN, user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        assertNothingRecorded();
    }

    @Test
    void deletedCallerIsNotFound() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(guest, HOST_IP, HostStatus.AUTHORIZED);
        String token = tokenFor(superuser);
        userRepository.delete(superuser);

        perform(patchHost(guest.getId().toString(), host.getId().toString(), BAN), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    @Test
    void unknownHostIsNotFound() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateHost(user.getId().toString(), UUID.randomUUID().toString(), BAN, user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Host not found"));

        assertNothingRecorded();
    }

    @Test
    void hostOfAnotherAccountIsNotFound() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("jane", UserRole.GUEST);
        JHost host = createHost(other, HOST_IP, HostStatus.AUTHORIZED);

        updateHost(user.getId().toString(), host.getId().toString(), BAN, user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Host not found"));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    @Test
    void malformedUserIdIsBadRequest() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateHost("not-a-uuid", UUID.randomUUID().toString(), BAN, user)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: userId"));
    }

    @Test
    void malformedHostIdIsBadRequest() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateHost(user.getId().toString(), "not-a-uuid", BAN, user)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: hostId"));
    }

    @Test
    void missingBodyIsBadRequest() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);

        perform(patch("/users/{userId}/hosts/{hostId}", user.getId(), host.getId())
                .contentType(MediaType.APPLICATION_JSON), tokenFor(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));

        assertUnchanged(host, HostStatus.AUTHORIZED);
    }

    @Test
    void malformedBodyIsBadRequest() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);

        updateHost(user.getId().toString(), host.getId().toString(), "{\"status\":", user)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));

        assertUnchanged(host, HostStatus.AUTHORIZED);
    }

    @Test
    void nonJsonContentTypeIsUnsupported() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);

        perform(patch("/users/{userId}/hosts/{hostId}", user.getId(), host.getId())
                .contentType(MediaType.TEXT_PLAIN)
                .content(BAN), tokenFor(user))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported content type."));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    @Test
    void nonJsonAcceptChangesNothing() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);

        perform(patchHost(user.getId().toString(), host.getId().toString(), BAN)
                .accept(MediaType.TEXT_PLAIN), tokenFor(user))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.message").value("Not acceptable."));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        perform(patchHost(UUID.randomUUID().toString(), UUID.randomUUID().toString(), BAN), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        perform(patchHost(UUID.randomUUID().toString(), UUID.randomUUID().toString(), BAN), "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void tokenFromAnotherIpIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);

        perform(patchHost(user.getId().toString(), host.getId().toString(), BAN),
                jwtTokenProvider.generateToken(user.getId().toString(), "GUEST", "198.51.100.9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    @Test
    void wrongMethodIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);

        perform(put("/users/{userId}/hosts/{hostId}", user.getId(), host.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BAN), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));

        assertUnchanged(host, HostStatus.AUTHORIZED);
    }

    private void assertUnprocessable(String body) throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, HOST_IP, HostStatus.AUTHORIZED);

        updateHost(user.getId().toString(), host.getId().toString(), body, user)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(INVALID_STATUS));

        assertUnchanged(host, HostStatus.AUTHORIZED);
        assertNothingRecorded();
    }

    private void assertUnchanged(JHost host, HostStatus status) {
        JHost stored = hostRepository.findById(host.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(status);
        assertThat(stored.getUpdatedAt()).isNull();
    }

    private void assertNothingRecorded() {
        assertThat(hostRepository.findAll()).allSatisfy(
                host -> assertThat(host.getLastSeenAt()).isEqualTo(LAST_SEEN));
        assertThat(eventLogRepository.count()).isZero();
    }

    private void assertEventOnCallerHost(JUser caller, String description) {
        JHost callerHost = hostRepository
                .findByUserIdAndIpAddress(caller.getId(), InetAddress.ofLiteral(CLIENT_IP))
                .orElseThrow();
        assertThat(eventLogRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getHost().getId()).isEqualTo(callerHost.getId());
                    assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.INFO);
                    assertThat(event.getDescription()).isEqualTo(description);
                    assertThat(event.getUserAgent()).isEqualTo(USER_AGENT);
                });
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

    private JHost createHost(JUser user, String ipAddress, HostStatus status) {
        return hostRepository.saveAndFlush(JHost.builder()
                .user(user)
                .ipAddress(InetAddress.ofLiteral(ipAddress))
                .status(status)
                .lastSeenAt(LAST_SEEN)
                .build());
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private MockHttpServletRequestBuilder patchHost(String userId, String hostId, String body) {
        return patch("/users/" + userId + "/hosts/" + hostId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private ResultActions updateHost(String userId, String hostId, String body, JUser caller) throws Exception {
        return perform(patchHost(userId, hostId, body), tokenFor(caller));
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String token) throws Exception {
        return mockMvc.perform(builder
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    if (token != null) {
                        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                    return request;
                }));
    }
}
