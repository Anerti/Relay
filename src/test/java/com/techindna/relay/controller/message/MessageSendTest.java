package com.techindna.relay.controller.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.entity.enums.MessageIssuer;
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
class MessageSendTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String CONTENT = "Hi Alice, do you have a minute?";
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
    void sendsMessageToVerifiedUser() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        sendMessage(receiver.getId().toString(), body(CONTENT), tokenFor(sender))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.senderId").value(sender.getId().toString()))
                .andExpect(jsonPath("$.receiverId").value(receiver.getId().toString()))
                .andExpect(jsonPath("$.replyTo").value(nullValue()))
                .andExpect(jsonPath("$.issuer").value("human"))
                .andExpect(jsonPath("$.content").value(CONTENT))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").value(nullValue()));

        assertThat(messageRepository.count()).isOne();
        JMessage message = messageRepository.findAll().getFirst();
        assertThat(message.getSenderId()).isEqualTo(sender.getId());
        assertThat(message.getReceiver().getId()).isEqualTo(receiver.getId());
        assertThat(message.getReplyTo()).isNull();
        assertThat(message.getIssuer()).isEqualTo(MessageIssuer.HUMAN);
        assertThat(message.getContent()).isEqualTo(CONTENT);
        assertThat(message.getCreatedAt()).isNotNull();
        assertThat(message.getUpdatedAt()).isNull();
        assertThat(message.getDeletedAt()).isNull();
    }

    @Test
    void sentMessageIsLogged() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        sendMessage(receiver.getId().toString(), body(CONTENT), tokenFor(sender))
                .andExpect(status().isCreated());

        JHost host = hostRepository.findByUserAndIpAddress(sender, InetAddress.ofLiteral(CLIENT_IP)).orElseThrow();
        assertThat(hostRepository.count()).isOne();
        assertThat(eventLogRepository.count()).isOne();
        JEventLog event = eventLogRepository.findAll().getFirst();
        assertThat(event.getHost().getId()).isEqualTo(host.getId());
        assertThat(event.getSeverity()).isEqualTo(EventLogSeverity.INFO);
        assertThat(event.getDescription()).isEqualTo("[MESSAGE]: Message sent to " + receiver.getId());
        assertThat(event.getUserAgent()).isEqualTo(USER_AGENT);
    }

    @Test
    void secondMessageReusesHost() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        sendMessage(receiver.getId().toString(), body(CONTENT), tokenFor(sender))
                .andExpect(status().isCreated());
        sendMessage(receiver.getId().toString(), body(CONTENT), tokenFor(sender))
                .andExpect(status().isCreated());

        assertThat(hostRepository.count()).isOne();
        assertThat(eventLogRepository.count()).isEqualTo(2);
    }

    @Test
    void contentIsStoredVerbatim() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);
        String content = "  Line one\n\tLine two  ";

        sendMessage(receiver.getId().toString(), body(content), tokenFor(sender))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(content));

        assertThat(messageRepository.findAll().getFirst().getContent()).isEqualTo(content);
    }

    @Test
    void contentAtMaxLengthIsAccepted() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        sendMessage(receiver.getId().toString(), body("a".repeat(4000)), tokenFor(sender))
                .andExpect(status().isCreated());
    }

    @Test
    void contentLengthCountsCodePoints() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);
        String content = "😀".repeat(4000);

        sendMessage(receiver.getId().toString(), body(content), tokenFor(sender))
                .andExpect(status().isCreated());

        assertThat(messageRepository.findAll().getFirst().getContent()).isEqualTo(content);
    }

    @Test
    void superuserSendsMessageToSuperuser() throws Exception {
        JUser sender = createUser("admin", UserRole.SUPERUSER, true);
        JUser receiver = createUser("root", UserRole.SUPERUSER, true);

        sendMessage(receiver.getId().toString(), body(CONTENT), tokenFor(sender))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiverId").value(receiver.getId().toString()));
    }

    @Test
    void lockedVerifiedReceiverStillReceives() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);
        receiver.setStatus(UserStatus.LOCKED);
        userRepository.saveAndFlush(receiver);

        sendMessage(receiver.getId().toString(), body(CONTENT), tokenFor(sender))
                .andExpect(status().isCreated());
    }

    @Test
    void tokenWithoutRoleSendsMessage() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        sendMessage(receiver.getId().toString(), body(CONTENT),
                jwtTokenProvider.generateToken(sender.getId().toString(), null, CLIENT_IP))
                .andExpect(status().isCreated());
    }

    @Test
    void unverifiedReceiverIsNotFound() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, false);

        assertNotFound(receiver.getId().toString(), tokenFor(sender));
    }

    @Test
    void unknownReceiverIsNotFound() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);

        assertNotFound(UUID.randomUUID().toString(), tokenFor(sender));
    }

    @Test
    void deletedSenderIsNotFound() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);
        String token = tokenFor(sender);
        userRepository.delete(sender);

        assertNotFound(receiver.getId().toString(), token);
    }

    @Test
    void sendsMessageToSelf() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);

        sendMessage(sender.getId().toString(), body(CONTENT), tokenFor(sender))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(sender.getId().toString()))
                .andExpect(jsonPath("$.receiverId").value(sender.getId().toString()));

        JMessage message = messageRepository.findAll().getFirst();
        assertThat(message.getSenderId()).isEqualTo(sender.getId());
        assertThat(message.getReceiver().getId()).isEqualTo(sender.getId());
    }

    @Test
    void missingContentIsUnprocessable() throws Exception {
        assertUnprocessable("{}", "content is required and cannot be blank");
    }

    @Test
    void nullContentIsUnprocessable() throws Exception {
        assertUnprocessable("{\"content\":null}", "content is required and cannot be blank");
    }

    @Test
    void emptyContentIsUnprocessable() throws Exception {
        assertUnprocessable(body(""), "content is required and cannot be blank");
    }

    @Test
    void blankContentIsUnprocessable() throws Exception {
        assertUnprocessable(body(" \n\t "), "content is required and cannot be blank");
    }

    @Test
    void tooLongContentIsUnprocessable() throws Exception {
        assertUnprocessable(body("a".repeat(4001)), "content must not exceed 4000 characters");
    }

    @Test
    void malformedUserIdIsBadRequest() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);

        sendMessage("not-a-uuid", body(CONTENT), tokenFor(sender))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: userId"));
    }

    @Test
    void missingBodyIsBadRequest() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        perform(post("/messages/users/{userId}", receiver.getId()).contentType(MediaType.APPLICATION_JSON),
                tokenFor(sender))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));
    }

    @Test
    void malformedBodyIsBadRequest() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        sendMessage(receiver.getId().toString(), "{\"content\":", tokenFor(sender))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));
    }

    @Test
    void wrongContentTypeIsUnsupported() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        perform(post("/messages/users/{userId}", receiver.getId())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(CONTENT),
                tokenFor(sender))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported content type."));

        assertNothingSent();
    }

    @Test
    void acceptWithoutJsonIsNotAcceptableAndSendsNothing() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        perform(post("/messages/users/{userId}", receiver.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .content(body(CONTENT)),
                tokenFor(sender))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.message").value("Not acceptable."));

        assertNothingSent();
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        assertUnauthorized(receiver, null);
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        assertUnauthorized(receiver, "not-a-jwt");
    }

    @Test
    void tokenWithoutSubjectIsUnauthorized() throws Exception {
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        assertUnauthorized(receiver, jwtTokenProvider.generateToken(null, "GUEST", CLIENT_IP));
    }

    @Test
    void tokenFromAnotherIpIsForbidden() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        sendMessage(receiver.getId().toString(), body(CONTENT),
                jwtTokenProvider.generateToken(sender.getId().toString(), "GUEST", "198.51.100.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));

        assertNothingSent();
    }

    @Test
    void wrongMethodIsForbidden() throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        perform(put("/messages/users/{userId}", receiver.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CONTENT)),
                tokenFor(sender))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Insufficient privileges."));
    }

    private void assertNotFound(String receiverId, String token) throws Exception {
        sendMessage(receiverId, body(CONTENT), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        assertNothingSent();
    }

    private void assertUnprocessable(String body, String message) throws Exception {
        JUser sender = createUser("jdoe", UserRole.GUEST, true);
        JUser receiver = createUser("alice", UserRole.GUEST, true);

        sendMessage(receiver.getId().toString(), body, tokenFor(sender))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(message));

        assertNothingSent();
    }

    private void assertUnauthorized(JUser receiver, String token) throws Exception {
        sendMessage(receiver.getId().toString(), body(CONTENT), token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

        assertNothingSent();
    }

    private void assertNothingSent() {
        assertThat(messageRepository.count()).isZero();
        assertThat(hostRepository.count()).isZero();
        assertThat(eventLogRepository.count()).isZero();
    }

    private JUser createUser(String username, UserRole role, boolean verified) {
        return userRepository.saveAndFlush(JUser.builder()
                .username(username)
                .password("hash")
                .firstName("John")
                .lastName("Doe")
                .email(username + "@example.com")
                .verified(verified)
                .role(role)
                .build());
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private static String body(String content) {
        return new ObjectMapper().writeValueAsString(Map.of("content", content));
    }

    private ResultActions sendMessage(String userId, String body, String token) throws Exception {
        return perform(post("/messages/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body), token);
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
