package com.techindna.relay.controller.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class HostListTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String FORBIDDEN = "Cannot access this resource.";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

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
    void guestListsOwnHostsMostRecentFirst() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost older = createHost(user, "198.51.100.1", NOW.minusSeconds(60));
        JHost newer = createHost(user, "198.51.100.2", NOW);

        listHosts(user.getId().toString(), "", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(contains(newer.getId().toString(), older.getId().toString())))
                .andExpect(jsonPath("$.data[0].ipAddress").value("198.51.100.2"))
                .andExpect(jsonPath("$.data[0].status").value("authorized"))
                .andExpect(jsonPath("$.data[0].lastSeenAt").value(NOW.toString()))
                .andExpect(jsonPath("$.data[0].updatedAt").value(nullValue()))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test
    void listsOnlyTheTargetUsersHosts() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("jane", UserRole.GUEST);
        JHost own = createHost(user, "198.51.100.1", NOW);
        createHost(other, "198.51.100.1", NOW);

        listHosts(user.getId().toString(), "", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(contains(own.getId().toString())))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void bannedHostShowsStatusAndUpdatedAt() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        hostRepository.saveAndFlush(JHost.builder()
                .user(user)
                .ipAddress(InetAddress.ofLiteral("198.51.100.1"))
                .status(HostStatus.BANNED)
                .lastSeenAt(NOW.minusSeconds(60))
                .updatedAt(NOW)
                .build());

        listHosts(user.getId().toString(), "", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("banned"))
                .andExpect(jsonPath("$.data[0].updatedAt").value(NOW.toString()));
    }

    @Test
    void superuserListsGuestHosts() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(guest, "198.51.100.1", NOW);

        listHosts(guest.getId().toString(), "", superuser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(contains(host.getId().toString())));
    }

    @Test
    void superuserListsOwnHosts() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JHost host = createHost(superuser, "198.51.100.1", NOW);

        listHosts(superuser.getId().toString(), "", superuser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(contains(host.getId().toString())));
    }

    @Test
    void noHostsReturnsNullData() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        listHosts(user.getId().toString(), "", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    void paginatesResults() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        createHost(user, "198.51.100.1", NOW.minusSeconds(120));
        JHost middle = createHost(user, "198.51.100.2", NOW.minusSeconds(60));
        createHost(user, "198.51.100.3", NOW);

        listHosts(user.getId().toString(), "?page=2&size=1", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(middle.getId().toString()))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.size").value(1))
                .andExpect(jsonPath("$.meta.total").value(3));
    }

    @Test
    void orderAscListsOldestFirst() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost older = createHost(user, "198.51.100.1", NOW.minusSeconds(60));
        JHost newer = createHost(user, "198.51.100.2", NOW);

        listHosts(user.getId().toString(), "?order=asc", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(contains(older.getId().toString(), newer.getId().toString())));
    }

    @Test
    void unknownOrderListsMostRecentFirst() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost older = createHost(user, "198.51.100.1", NOW.minusSeconds(60));
        JHost newer = createHost(user, "198.51.100.2", NOW);

        listHosts(user.getId().toString(), "?order=sideways", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(contains(newer.getId().toString(), older.getId().toString())));
    }

    @Test
    void listingRecordsNothing() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        JHost host = createHost(user, "198.51.100.1", NOW);

        listHosts(user.getId().toString(), "", user).andExpect(status().isOk());

        assertThat(hostRepository.count()).isEqualTo(1);
        assertThat(hostRepository.findById(host.getId()).orElseThrow().getLastSeenAt()).isEqualTo(NOW);
        assertThat(eventLogRepository.count()).isZero();
    }

    @Test
    void guestCannotListAnotherUsersHosts() throws Exception {
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("jane", UserRole.GUEST);
        createHost(other, "198.51.100.1", NOW);

        listHosts(other.getId().toString(), "", guest)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN));
    }

    @Test
    void superuserCannotListAnotherSuperusersHosts() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser other = createUser("root", UserRole.SUPERUSER);

        listHosts(other.getId().toString(), "", superuser)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN));
    }

    @Test
    void tokenWithoutRoleIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        perform(get("/users/{userId}/hosts", user.getId()),
                jwtTokenProvider.generateToken(user.getId().toString(), null, CLIENT_IP))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges to access this resource"));
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        listHosts(UUID.randomUUID().toString(), "", user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void malformedUserIdIsBadRequest() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        listHosts("not-a-uuid", "", user)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: userId"));
    }

    @Test
    void pageZeroIsBadRequest() throws Exception {
        assertBadPagination("?page=0");
    }

    @Test
    void sizeZeroIsBadRequest() throws Exception {
        assertBadPagination("?size=0");
    }

    @Test
    void sizeAboveMaximumIsBadRequest() throws Exception {
        assertBadPagination("?size=101");
    }

    @Test
    void pageBeyondOffsetLimitIsBadRequest() throws Exception {
        assertBadPagination("?page=2147483647&size=100");
    }

    @Test
    void nonNumericPageIsBadRequest() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        listHosts(user.getId().toString(), "?page=abc", user)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: page"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        perform(get("/users/{userId}/hosts", UUID.randomUUID()), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        perform(get("/users/{userId}/hosts", UUID.randomUUID()), "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void tokenFromAnotherIpIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        perform(get("/users/{userId}/hosts", user.getId()),
                jwtTokenProvider.generateToken(user.getId().toString(), "GUEST", "198.51.100.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));
    }

    @Test
    void trailingSlashIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        perform(get("/users/" + user.getId() + "/hosts/"), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));
    }

    @Test
    void wrongMethodIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        perform(post("/users/{userId}/hosts", user.getId()), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));
    }

    private void assertBadPagination(String query) throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        listHosts(user.getId().toString(), query, user)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid pagination parameters"));
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

    private JHost createHost(JUser user, String ipAddress, Instant lastSeenAt) {
        return hostRepository.saveAndFlush(JHost.builder()
                .user(user)
                .ipAddress(InetAddress.ofLiteral(ipAddress))
                .lastSeenAt(lastSeenAt)
                .build());
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private ResultActions listHosts(String userId, String query, JUser caller) throws Exception {
        return perform(get("/users/" + userId + "/hosts" + query), tokenFor(caller));
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
