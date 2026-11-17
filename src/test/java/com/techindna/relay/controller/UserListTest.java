package com.techindna.relay.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.enums.UserRole;
import com.techindna.relay.repository.EventLogRepository;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.jwt.JwtTokenProvider;
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
class UserListTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";

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
    void defaultsToGuestsOldestFirst() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);
        JUser jane = createUser("jane", UserRole.GUEST);
        createUser("john", UserRole.GUEST);

        listUsers("/users", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", contains("jane", "john")))
                .andExpect(jsonPath("$.data[0].id").value(jane.getId().toString()))
                .andExpect(jsonPath("$.data[0].firstName").value("Jane"))
                .andExpect(jsonPath("$.data[0].lastName").value("Doe"))
                .andExpect(jsonPath("$.data[0].email").value("jane@example.com"))
                .andExpect(jsonPath("$.data[0].role").value("guest"))
                .andExpect(jsonPath("$.data[0].userStatus").value("active"))
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0]", not(hasKey("password"))))
                .andExpect(jsonPath("$.data[0]", not(hasKey("verified"))))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test
    void filtersBySuperuserRole() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);
        createUser("root", UserRole.SUPERUSER);
        createUser("jane", UserRole.GUEST);

        listUsers("/users?role=superuser", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", contains("admin", "root")))
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test
    void unknownRoleDefaultsToGuest() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);
        createUser("jane", UserRole.GUEST);

        listUsers("/users?role=admin", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", contains("jane")));
    }

    @Test
    void combinesRoleFilterWithSearch() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);
        createUser("jane", UserRole.GUEST);
        createUser("janet", UserRole.SUPERUSER);
        createUser("john", UserRole.GUEST);

        listUsers("/users?role=guest&search=jan", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", contains("jane")))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);
        createUser("jane", UserRole.GUEST);
        createUser("john", UserRole.GUEST);

        listUsers("/users?search=JANE", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", contains("jane")));
    }

    @Test
    void searchMatchesNamesAndEmail() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);
        createUser("jane", UserRole.GUEST);
        createUser("john", UserRole.GUEST);

        listUsers("/users?search=doe", admin)
                .andExpect(jsonPath("$.data[*].username", contains("jane", "john")));
        listUsers("/users?search=Jane", admin)
                .andExpect(jsonPath("$.data[*].username", contains("jane")));
        listUsers("/users?search=john@example", admin)
                .andExpect(jsonPath("$.data[*].username", contains("john")));
    }

    @Test
    void noMatchReturnsNullData() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);
        createUser("jane", UserRole.GUEST);

        listUsers("/users?search=zzz", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    void underscoreInSearchIsLiteral() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);
        createUser("j_doe", UserRole.GUEST);
        createUser("jxdoe", UserRole.GUEST);

        listUsers("/users?search=j_d", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", contains("j_doe")));
    }

    @Test
    void paginatesResults() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);
        createUser("jane", UserRole.GUEST);
        createUser("john", UserRole.GUEST);
        createUser("alice", UserRole.GUEST);

        listUsers("/users?page=1&size=2", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", contains("jane", "john")))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(2))
                .andExpect(jsonPath("$.meta.total").value(3));

        listUsers("/users?page=2&size=2", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", contains("alice")))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.total").value(3));
    }

    @Test
    void orderDescListsNewestFirst() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);
        createUser("jane", UserRole.GUEST);
        createUser("john", UserRole.GUEST);

        listUsers("/users?order=desc", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", contains("john", "jane")));
    }

    @Test
    void invalidSearchCharactersAreUnprocessable() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);

        listUsers("/users?search=abc$", admin)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message")
                        .value("Search may only contain letters, digits, dot, @, underscore, hyphen, space, and éèê"));
    }

    @Test
    void searchTooLongIsUnprocessable() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);

        listUsers("/users?search=" + "a".repeat(101), admin)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("search must not exceed 100 characters"));
    }

    @Test
    void pageZeroIsBadRequest() throws Exception {
        assertBadPagination("/users?page=0");
    }

    @Test
    void sizeZeroIsBadRequest() throws Exception {
        assertBadPagination("/users?size=0");
    }

    @Test
    void sizeAboveMaximumIsBadRequest() throws Exception {
        assertBadPagination("/users?size=101");
    }

    @Test
    void pageBeyondOffsetLimitIsBadRequest() throws Exception {
        assertBadPagination("/users?page=2147483647&size=100");
    }

    @Test
    void trailingSlashIsNotFound() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);

        listUsers("/users/", admin)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @Test
    void nonNumericPageIsBadRequest() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);

        listUsers("/users?page=abc", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: page"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        perform(get("/users"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void guestIsForbidden() throws Exception {
        JUser guest = createUser("jane", UserRole.GUEST);

        listUsers("/users", guest)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));
    }

    @Test
    void tokenWithoutRoleIsForbidden() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);

        perform(get("/users"), jwtTokenProvider.generateToken(admin.getId().toString(), null, CLIENT_IP))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));
    }

    @Test
    void tokenFromAnotherIpIsForbidden() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);

        perform(get("/users"), jwtTokenProvider.generateToken(admin.getId().toString(), "SUPERUSER", "198.51.100.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));
    }

    @Test
    void wrongMethodIsForbidden() throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);

        perform(post("/users"), tokenFor(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));
    }

    private void assertBadPagination(String path) throws Exception {
        JUser admin = createUser("admin", UserRole.SUPERUSER);

        listUsers(path, admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid pagination parameters"));
    }

    private JUser createUser(String username, UserRole role) {
        return userRepository.saveAndFlush(JUser.builder()
                .username(username)
                .password("hash")
                .firstName(Character.toUpperCase(username.charAt(0)) + username.substring(1))
                .lastName("Doe")
                .email(username + "@example.com")
                .verified(true)
                .role(role)
                .build());
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private ResultActions listUsers(String path, JUser caller) throws Exception {
        return perform(get(path), tokenFor(caller));
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
