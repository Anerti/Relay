package com.techindna.relay.controller.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.enums.UserRole;
import com.techindna.relay.repository.EventLogRepository;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.jwt.JwtTokenProvider;
import java.util.Map;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UserUpdateTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String FORBIDDEN = "Cannot access this resource.";
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
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void clean() {
        eventLogRepository.deleteAll();
        hostRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void guestPartiallyUpdatesOwnProfile() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(user.getId().toString(), body(Map.of("firstName", "Jonathan")), tokenFor(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.username").value("jdoe"))
                .andExpect(jsonPath("$.firstName").value("Jonathan"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.email").value("jdoe@example.com"))
                .andExpect(jsonPath("$.role").value("guest"))
                .andExpect(jsonPath("$.userStatus").value("active"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$", not(hasKey("verified"))))
                .andExpect(jsonPath("$", not(hasKey("password"))));

        JUser updated = reload(user);
        assertThat(updated.getFirstName()).isEqualTo("Jonathan");
        assertThat(updated.getLastName()).isEqualTo("Doe");
        assertThat(updated.getUsername()).isEqualTo("jdoe");
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void guestUpdatesEveryField() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(user.getId().toString(),
                body(Map.of("username", "jsmith", "firstName", "Jane", "lastName", "Smith")),
                tokenFor(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jsmith"))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Smith"));

        JUser updated = reload(user);
        assertThat(updated.getUsername()).isEqualTo("jsmith");
        assertThat(updated.getFirstName()).isEqualTo("Jane");
        assertThat(updated.getLastName()).isEqualTo("Smith");
    }

    @Test
    void emptyBodyLeavesUserUnchanged() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(user.getId().toString(), "{}", tokenFor(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jdoe"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void usernameIsTrimmedAndLowerCased() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(user.getId().toString(), body(Map.of("username", "  JDoe_2  ")), tokenFor(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jdoe_2"));

        assertThat(reload(user).getUsername()).isEqualTo("jdoe_2");
    }

    @Test
    void namesAreTrimmed() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(user.getId().toString(),
                body(Map.of("firstName", "  maría ", "lastName", " de la Cruz  ")),
                tokenFor(user))
                .andExpect(status().isOk());

        JUser updated = reload(user);
        assertThat(updated.getFirstName()).isEqualTo("maría");
        assertThat(updated.getLastName()).isEqualTo("de la Cruz");
    }

    @Test
    void ownUsernameInAnotherCaseIsAllowed() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(user.getId().toString(), body(Map.of("username", "JDOE")), tokenFor(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jdoe"));
    }

    @Test
    void duplicateUsernameIsConflict() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        createUser("jane", UserRole.GUEST);

        updateUser(user.getId().toString(), body(Map.of("username", "Jane", "firstName", "Jonathan")), tokenFor(user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot use this username"));

        JUser unchanged = reload(user);
        assertThat(unchanged.getUsername()).isEqualTo("jdoe");
        assertThat(unchanged.getFirstName()).isEqualTo("John");
    }

    @Test
    void superuserUpdatesOwnProfile() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);

        updateUser(superuser.getId().toString(), body(Map.of("firstName", "Adam")), tokenFor(superuser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Adam"))
                .andExpect(jsonPath("$.role").value("superuser"));
    }

    @Test
    void guestCannotUpdateAnotherUser() throws Exception {
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("jane", UserRole.GUEST);

        assertForbidden(other, tokenFor(guest));
    }

    @Test
    void superuserCannotUpdateGuest() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser guest = createUser("jdoe", UserRole.GUEST);

        assertForbidden(guest, tokenFor(superuser));
    }

    @Test
    void superuserCannotUpdateAnotherSuperuser() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser other = createUser("root", UserRole.SUPERUSER);

        assertForbidden(other, tokenFor(superuser));
    }

    @Test
    void otherUnknownIdIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(UUID.randomUUID().toString(), body(Map.of("firstName", "Jonathan")), tokenFor(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN));
    }

    @Test
    void deletedOwnAccountIsNotFound() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);
        String token = tokenFor(user);
        userRepository.delete(user);

        updateUser(user.getId().toString(), body(Map.of("firstName", "Jonathan")), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void tooLongUsernameIsUnprocessable() throws Exception {
        assertUnprocessable("username", "a".repeat(51), "username must not exceed 50 characters");
    }

    @Test
    void invalidUsernameIsUnprocessable() throws Exception {
        assertUnprocessable("username", "a", "Username a is invalid");
    }

    @Test
    void tooLongFirstNameIsUnprocessable() throws Exception {
        assertUnprocessable("firstName", "J" + "a".repeat(100), "firstName must not exceed 100 characters");
    }

    @Test
    void invalidFirstNameIsUnprocessable() throws Exception {
        assertUnprocessable("firstName", "J0hn", "firstName" + NAME_FORMAT);
    }

    @Test
    void tooLongLastNameIsUnprocessable() throws Exception {
        assertUnprocessable("lastName", "D" + "a".repeat(100), "lastName must not exceed 100 characters");
    }

    @Test
    void invalidLastNameIsUnprocessable() throws Exception {
        assertUnprocessable("lastName", "D0e", "lastName" + NAME_FORMAT);
    }

    @Test
    void malformedUserIdIsBadRequest() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser("not-a-uuid", body(Map.of("firstName", "Jonathan")), tokenFor(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: userId"));
    }

    @Test
    void missingBodyIsBadRequest() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        perform(patch("/users/{userId}", user.getId()).contentType(MediaType.APPLICATION_JSON), tokenFor(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));
    }

    @Test
    void wrongContentTypeIsUnsupported() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        perform(patch("/users/{userId}", user.getId())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("firstName=Jonathan"),
                tokenFor(user))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported content type."));
    }

    @Test
    void acceptWithoutJsonIsNotAcceptableAndChangesNothing() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        perform(patch("/users/{userId}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .content(body(Map.of("firstName", "Jonathan"))),
                tokenFor(user))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.message").value("Not acceptable."));

        assertThat(reload(user).getFirstName()).isEqualTo("John");
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(user.getId().toString(), body(Map.of("firstName", "Jonathan")), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

        assertThat(reload(user).getFirstName()).isEqualTo("John");
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(user.getId().toString(), body(Map.of("firstName", "Jonathan")), "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

        assertThat(reload(user).getFirstName()).isEqualTo("John");
    }

    @Test
    void tokenFromAnotherIpIsForbidden() throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(user.getId().toString(), body(Map.of("firstName", "Jonathan")),
                jwtTokenProvider.generateToken(user.getId().toString(), "GUEST", "198.51.100.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));

        assertThat(reload(user).getFirstName()).isEqualTo("John");
    }

    private void assertForbidden(JUser target, String token) throws Exception {
        updateUser(target.getId().toString(), body(Map.of("firstName", "Hacked")), token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN));

        assertThat(reload(target).getFirstName()).isEqualTo("John");
    }

    private void assertUnprocessable(String field, String value, String message) throws Exception {
        JUser user = createUser("jdoe", UserRole.GUEST);

        updateUser(user.getId().toString(), body(Map.of(field, value)), tokenFor(user))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(message));

        JUser unchanged = reload(user);
        assertThat(unchanged.getUsername()).isEqualTo("jdoe");
        assertThat(unchanged.getFirstName()).isEqualTo("John");
        assertThat(unchanged.getLastName()).isEqualTo("Doe");
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

    private JUser reload(JUser user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private static String body(Map<String, String> fields) {
        return new ObjectMapper().writeValueAsString(fields);
    }

    private ResultActions updateUser(String userId, String body, String token) throws Exception {
        return perform(patch("/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body), token);
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
