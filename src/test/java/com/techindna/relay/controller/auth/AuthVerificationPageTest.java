package com.techindna.relay.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.repository.EventLogRepository;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.enums.VerificationFlow;
import com.techindna.relay.service.redis.VerificationTokenService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AuthVerificationPageTest extends TestcontainersConfig {

    private static final String TOKEN_PREFIX = "auth:verification:";
    private static final String PASSWORD = "StrongPassword1!";

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

    @MockitoSpyBean
    private VerificationTokenService verificationTokenService;

    @BeforeEach
    void clean() {
        eventLogRepository.deleteAll();
        hostRepository.deleteAll();
        userRepository.deleteAll();
        redis.delete(redis.keys(TOKEN_PREFIX + "*"));
    }

    @Test
    void registrationLinkShowsConfirmationFormWithoutConsumingToken() throws Exception {
        String token = registerAndGetToken();
        JUser user = userRepository.findAll().getFirst();

        verificationPage(token)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(containsString(
                        "<form method=\"post\" action=\"/auth/mfa/confirm/register/" + token + "\">")));

        assertThat(redis.opsForValue().get(TOKEN_PREFIX + token)).isEqualTo("REGISTER:" + user.getId());
        assertThat(userRepository.findAll().getFirst().getVerified()).isFalse();
    }

    @Test
    void loginLinkShowsLoginConfirmationFormWithoutConsumingToken() throws Exception {
        JUser user = userRepository.saveAndFlush(JUser.builder()
                .username("jdoe")
                .password("hash")
                .firstName("John")
                .lastName("Doe")
                .email("jdoe@example.com")
                .verified(true)
                .build());
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        verificationPage(token)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<form method=\"post\" action=\"/auth/mfa/confirm/login/" + token + "\">")));

        assertThat(redis.opsForValue().get(TOKEN_PREFIX + token)).isEqualTo("LOGIN:" + user.getId());
    }

    @Test
    void malformedTokenValueFallsBackToRegisterForm() throws Exception {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(TOKEN_PREFIX + token, "not-a-valid-value");

        verificationPage(token)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<form method=\"post\" action=\"/auth/mfa/confirm/register/" + token + "\">")));
    }

    @Test
    void redisFailureFallsBackToRegisterForm() throws Exception {
        String token = UUID.randomUUID().toString();
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(verificationTokenService).findFlow(any());

        verificationPage(token)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<form method=\"post\" action=\"/auth/mfa/confirm/register/" + token + "\">")));
    }

    @Test
    void unknownFlowFallsBackToRegisterForm() throws Exception {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(TOKEN_PREFIX + token, "BOGUS:" + UUID.randomUUID());

        verificationPage(token)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<form method=\"post\" action=\"/auth/mfa/confirm/register/" + token + "\">")));
    }

    @Test
    void tokenIsEncodedInFormAction() throws Exception {
        verificationPage("\"><script>alert(1)")
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(containsString(
                        "action=\"/auth/mfa/confirm/register/%22%3E%3Cscript%3Ealert(1)\"")));
    }

    @Test
    void onlyGetIsPublic() throws Exception {
        mockMvc.perform(post("/auth/verify/{token}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTokenIsNotFound() throws Exception {
        mockMvc.perform(get("/auth/verify/"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    private ResultActions verificationPage(String token) throws Exception {
        return mockMvc.perform(get("/auth/verify/{token}", token).accept(MediaType.TEXT_HTML));
    }

    private String registerAndGetToken() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.42");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "jdoe", "password": "%s", "confirmPassword": "%s",
                                 "firstName": "John", "lastName": "Doe", "email": "jdoe@example.com"}
                                """.formatted(PASSWORD, PASSWORD)))
                .andExpect(status().isAccepted());
        return redis.keys(TOKEN_PREFIX + "*").iterator().next().substring(TOKEN_PREFIX.length());
    }
}
