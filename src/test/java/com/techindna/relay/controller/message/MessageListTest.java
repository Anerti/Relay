package com.techindna.relay.controller.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.entity.enums.UserRole;
import com.techindna.relay.repository.EventLogRepository;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.MessageRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JEventLog;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JMessage;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.jwt.JwtTokenProvider;
import java.net.InetAddress;
import java.time.Instant;
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
class MessageListTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String USER_AGENT = "RelayTest/1.0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

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
        messageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listsConversationOldestFirst() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        JMessage first = createMessage(other, caller, "Hi John");
        JMessage second = createMessage(caller, other, "Hi Alice");
        JMessage third = createMessage(other, caller, "How are you?");

        listMessages(other.getId().toString(), tokenFor(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$[0].senderId").value(other.getId().toString()))
                .andExpect(jsonPath("$[0].receiverId").value(caller.getId().toString()))
                .andExpect(jsonPath("$[0].replyTo").value(nullValue()))
                .andExpect(jsonPath("$[0].issuer").value("human"))
                .andExpect(jsonPath("$[0].content").value("Hi John"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].updatedAt").value(nullValue()))
                .andExpect(jsonPath("$[1].id").value(second.getId().toString()))
                .andExpect(jsonPath("$[1].senderId").value(caller.getId().toString()))
                .andExpect(jsonPath("$[2].id").value(third.getId().toString()));
    }

    @Test
    void listingIsLogged() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);

        listMessages(other.getId().toString(), tokenFor(caller))
                .andExpect(status().isOk());

        JHost host = hostRepository.findByUserAndIpAddress(caller, InetAddress.ofLiteral(CLIENT_IP)).orElseThrow();
        assertThat(hostRepository.count()).isOne();
        assertThat(eventLogRepository.count()).isOne();
        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getHost().getId()).isEqualTo(host.getId());
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.INFO);
        assertThat(event.getDescription()).isEqualTo("[MESSAGE]: Conversation viewed with " + other.getId());
        assertThat(event.getUserAgent()).isEqualTo(USER_AGENT);
    }

    @Test
    void secondListingReusesHost() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);

        listMessages(other.getId().toString(), tokenFor(caller))
                .andExpect(status().isOk());
        listMessages(other.getId().toString(), tokenFor(caller))
                .andExpect(status().isOk());

        assertThat(hostRepository.count()).isOne();
        assertThat(eventLogRepository.count()).isEqualTo(2);
    }

    @Test
    void otherUserSeesSameConversation() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        JMessage first = createMessage(caller, other, "Hi Alice");
        JMessage second = createMessage(other, caller, "Hi John");

        listMessages(caller.getId().toString(), tokenFor(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(second.getId().toString()));
    }

    @Test
    void excludesOtherConversations() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        JUser third = createUser("bob", UserRole.GUEST);
        JMessage message = createMessage(caller, other, "Hi Alice");
        createMessage(caller, third, "Hi Bob");
        createMessage(third, other, "Hi Alice, it's Bob");

        listMessages(other.getId().toString(), tokenFor(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(message.getId().toString()));
    }

    @Test
    void excludesDeletedMessages() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        JMessage kept = createMessage(caller, other, "Hi Alice");
        JMessage deleted = createMessage(other, caller, "Hi John");
        deleted.setDeletedAt(Instant.now());
        messageRepository.saveAndFlush(deleted);

        listMessages(other.getId().toString(), tokenFor(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(kept.getId().toString()));
    }

    @Test
    void listsMessagesToSelfOnce() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, caller, "Note to self");
        createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");

        listMessages(caller.getId().toString(), tokenFor(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(message.getId().toString()));
    }

    @Test
    void noMessagesGivesEmptyList() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);

        listMessages(other.getId().toString(), tokenFor(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unknownUserGivesEmptyList() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);

        listMessages(UUID.randomUUID().toString(), tokenFor(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void superuserSeesOnlyOwnConversation() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JUser guest = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        createMessage(guest, other, "Hi Alice");

        listMessages(other.getId().toString(), tokenFor(superuser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void tokenWithoutRoleListsMessages() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        createMessage(caller, other, "Hi Alice");

        listMessages(other.getId().toString(),
                jwtTokenProvider.generateToken(caller.getId().toString(), null, CLIENT_IP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void deletedCallerIsNotFound() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        String token = tokenFor(caller);
        userRepository.delete(caller);

        listMessages(other.getId().toString(), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        assertNothingRecorded();
    }

    @Test
    void malformedUserIdIsBadRequest() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);

        listMessages("not-a-uuid", tokenFor(caller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: userId"));

        assertNothingRecorded();
    }

    @Test
    void acceptWithoutJsonIsNotAcceptableAndRecordsNothing() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);

        perform(get("/messages/users/{userId}", other.getId()).accept(MediaType.TEXT_PLAIN), tokenFor(caller))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.message").value("Not acceptable."));

        assertNothingRecorded();
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        assertUnauthorized(null);
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        assertUnauthorized("not-a-jwt");
    }

    @Test
    void tokenWithoutSubjectIsUnauthorized() throws Exception {
        assertUnauthorized(jwtTokenProvider.generateToken(null, "GUEST", CLIENT_IP));
    }

    @Test
    void tokenFromAnotherIpIsForbidden() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);

        listMessages(other.getId().toString(),
                jwtTokenProvider.generateToken(caller.getId().toString(), "GUEST", "198.51.100.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));

        assertNothingRecorded();
    }

    private void assertUnauthorized(String token) throws Exception {
        JUser other = createUser("alice", UserRole.GUEST);

        listMessages(other.getId().toString(), token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

        assertNothingRecorded();
    }

    private void assertNothingRecorded() {
        assertThat(hostRepository.count()).isZero();
        assertThat(eventLogRepository.count()).isZero();
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

    private JMessage createMessage(JUser sender, JUser receiver, String content) {
        return messageRepository.saveAndFlush(JMessage.builder()
                .senderId(sender.getId())
                .receiver(receiver)
                .content(content)
                .build());
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private ResultActions listMessages(String userId, String token) throws Exception {
        return perform(get("/messages/users/{userId}", userId), token);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String token) throws Exception {
        return mockMvc.perform(builder
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    request.addHeader(HttpHeaders.USER_AGENT, USER_AGENT);
                    if (token != null) {
                        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                    return request;
                }));
    }
}
