package com.techindna.relay.controller.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.entity.enums.HostStatus;
import com.techindna.relay.entity.enums.UserRole;
import com.techindna.relay.entity.enums.UserStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class MessageDeleteTest extends TestcontainersConfig {

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
    void softDeletesOwnMessage() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        JMessage message = createMessage(caller, other, "Hi Alice");

        deleteMessage(message.getId().toString(), tokenFor(caller))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        JMessage stored = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(stored.getDeletedAt()).isNotNull();
        assertThat(stored.getContent()).isEqualTo("Hi Alice");
        assertThat(stored.getUpdatedAt()).isNull();
        assertThat(messageRepository.count()).isOne();
    }

    @Test
    void deletionIsLogged() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");

        deleteMessage(message.getId().toString(), tokenFor(caller))
                .andExpect(status().isNoContent());

        JHost host = hostRepository.findByUserAndIpAddress(caller, InetAddress.ofLiteral(CLIENT_IP)).orElseThrow();
        assertThat(hostRepository.count()).isOne();
        assertThat(eventLogRepository.count()).isOne();
        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getHost().getId()).isEqualTo(host.getId());
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.INFO);
        assertThat(event.getDescription()).isEqualTo("[MESSAGE]: Message " + message.getId() + " deleted");
        assertThat(event.getUserAgent()).isEqualTo(USER_AGENT);
    }

    @Test
    void secondDeletionReusesHost() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        JMessage first = createMessage(caller, other, "One");
        JMessage second = createMessage(caller, other, "Two");

        deleteMessage(first.getId().toString(), tokenFor(caller))
                .andExpect(status().isNoContent());
        deleteMessage(second.getId().toString(), tokenFor(caller))
                .andExpect(status().isNoContent());

        assertThat(hostRepository.count()).isOne();
        assertThat(eventLogRepository.count()).isEqualTo(2);
    }

    @Test
    void deletingTwiceIsNotFound() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");

        deleteMessage(message.getId().toString(), tokenFor(caller))
                .andExpect(status().isNoContent());
        deleteMessage(message.getId().toString(), tokenFor(caller))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Message not found"));

        assertThat(eventLogRepository.count()).isOne();
    }

    @Test
    void alreadyDeletedMessageIsNotFound() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");
        Instant deletedAt = Instant.parse("2026-01-01T00:00:00Z");
        message.setDeletedAt(deletedAt);
        messageRepository.saveAndFlush(message);

        assertMessageNotFound(message.getId().toString(), tokenFor(caller));
        assertThat(messageRepository.findById(message.getId()).orElseThrow().getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void receivedMessageIsNotFound() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(createUser("alice", UserRole.GUEST), caller, "Hi John");

        assertMessageNotFound(message.getId().toString(), tokenFor(caller));
        assertNotDeleted(message);
    }

    @Test
    void otherUsersMessageIsNotFound() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(
                createUser("alice", UserRole.GUEST), createUser("bob", UserRole.GUEST), "Hi Bob");

        assertMessageNotFound(message.getId().toString(), tokenFor(caller));
        assertNotDeleted(message);
    }

    @Test
    void superuserCannotDeleteGuestMessage() throws Exception {
        JUser superuser = createUser("admin", UserRole.SUPERUSER);
        JMessage message = createMessage(
                createUser("jdoe", UserRole.GUEST), createUser("alice", UserRole.GUEST), "Hi Alice");

        assertMessageNotFound(message.getId().toString(), tokenFor(superuser));
        assertNotDeleted(message);
    }

    @Test
    void unknownMessageIsNotFound() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);

        assertMessageNotFound(UUID.randomUUID().toString(), tokenFor(caller));
    }

    @Test
    void deletedCallerIsNotFound() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");
        String token = tokenFor(caller);
        userRepository.delete(caller);

        deleteMessage(message.getId().toString(), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        assertNothingRecorded();
        assertNotDeleted(message);
    }

    @Test
    void messageToDeletedReceiverIsDeleted() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        JMessage message = createMessage(caller, other, "Hi Alice");
        userRepository.delete(other);

        deleteMessage(message.getId().toString(), tokenFor(caller))
                .andExpect(status().isNoContent());

        JMessage stored = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(stored.getDeletedAt()).isNotNull();
        assertThat(stored.getReceiver()).isNull();
    }

    @Test
    void deletesOnlyTargetMessage() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        JMessage deleted = createMessage(caller, other, "One");
        JMessage kept = createMessage(caller, other, "Two");

        deleteMessage(deleted.getId().toString(), tokenFor(caller))
                .andExpect(status().isNoContent());

        assertNotDeleted(kept);
    }

    @Test
    void deletedMessageLeavesConversation() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JUser other = createUser("alice", UserRole.GUEST);
        JMessage deleted = createMessage(caller, other, "One");
        JMessage kept = createMessage(other, caller, "Two");

        deleteMessage(deleted.getId().toString(), tokenFor(caller))
                .andExpect(status().isNoContent());

        perform(get("/messages/users/{userId}", other.getId()), tokenFor(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(kept.getId().toString()));
    }

    @Test
    void inactiveCallerDeletesMessage() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");
        caller.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(caller);

        deleteMessage(message.getId().toString(), tokenFor(caller))
                .andExpect(status().isNoContent());

        assertThat(messageRepository.findById(message.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void tokenWithoutRoleDeletesMessage() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");

        deleteMessage(message.getId().toString(),
                jwtTokenProvider.generateToken(caller.getId().toString(), null, CLIENT_IP))
                .andExpect(status().isNoContent());

        assertThat(messageRepository.findById(message.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void lockedCallerIsForbidden() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");
        caller.setStatus(UserStatus.LOCKED);
        userRepository.saveAndFlush(caller);

        deleteMessage(message.getId().toString(), tokenFor(caller))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account is locked and cannot delete messages."));

        assertBlockedDeleteLogged(caller, "[MESSAGE]: Delete failed due to locked account");
        assertNotDeleted(message);
    }

    @Test
    void bannedHostIsForbidden() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");
        hostRepository.saveAndFlush(JHost.builder()
                .user(caller)
                .ipAddress(InetAddress.ofLiteral(CLIENT_IP))
                .status(HostStatus.BANNED)
                .build());

        deleteMessage(message.getId().toString(), tokenFor(caller))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Host " + CLIENT_IP + " is banned from accessing this account"));

        assertBlockedDeleteLogged(caller, "[MESSAGE]: Delete failed due to banned host");
        assertNotDeleted(message);
    }

    @Test
    void malformedMessageIdIsBadRequest() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);

        deleteMessage("not-a-uuid", tokenFor(caller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: messageId"));

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
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");

        deleteMessage(message.getId().toString(),
                jwtTokenProvider.generateToken(caller.getId().toString(), "GUEST", "198.51.100.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));

        assertNothingRecorded();
        assertNotDeleted(message);
    }

    @Test
    void wrongMethodIsForbidden() throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");

        perform(put("/messages/{messageId}", message.getId()), tokenFor(caller))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));

        assertNothingRecorded();
        assertNotDeleted(message);
    }

    private void assertUnauthorized(String token) throws Exception {
        JUser caller = createUser("jdoe", UserRole.GUEST);
        JMessage message = createMessage(caller, createUser("alice", UserRole.GUEST), "Hi Alice");

        deleteMessage(message.getId().toString(), token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

        assertNothingRecorded();
        assertNotDeleted(message);
    }

    private void assertMessageNotFound(String messageId, String token) throws Exception {
        deleteMessage(messageId, token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Message not found"));

        assertNothingRecorded();
    }

    private void assertBlockedDeleteLogged(JUser caller, String description) {
        JHost host = hostRepository.findByUserAndIpAddress(caller, InetAddress.ofLiteral(CLIENT_IP)).orElseThrow();
        assertThat(eventLogRepository.count()).isOne();
        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getHost().getId()).isEqualTo(host.getId());
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.MEDIUM);
        assertThat(event.getDescription()).isEqualTo(description);
    }

    private void assertNotDeleted(JMessage message) {
        assertThat(messageRepository.findById(message.getId()).orElseThrow().getDeletedAt()).isNull();
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

    private ResultActions deleteMessage(String messageId, String token) throws Exception {
        return perform(delete("/messages/{messageId}", messageId), token);
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
