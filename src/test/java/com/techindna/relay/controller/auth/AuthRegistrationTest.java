package com.techindna.relay.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.mail.EmailService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRegistrationTest extends TestcontainersConfig {

    private static final String TOKEN_PREFIX = "auth:verification:";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String NAME_FORMAT =
            " must contain only letters, with single spaces, apostrophes, or hyphens between words";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redis;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
        redis.delete(redis.keys(TOKEN_PREFIX + "*"));
    }

    @Test
    void registersUnverifiedUserAndEmailsVerificationLink() throws Exception {
        register(body(Map.of("username", "JDoe", "email", "JDoe@Example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("An email has been sent to verify your account"));

        JUser user = userRepository.findAll().getFirst();
        assertThat(user.getUsername()).isEqualTo("jdoe");
        assertThat(user.getEmail()).isEqualTo("jdoe@example.com");
        assertThat(user.getVerified()).isFalse();

        String key = redis.keys(TOKEN_PREFIX + "*").iterator().next();
        assertThat(redis.opsForValue().get(key)).isEqualTo("REGISTER:" + user.getId());

        String token = key.substring(TOKEN_PREFIX.length());
        assertThat(latestEmail()).contains(
                "jdoe@example.com",
                "Verify your account",
                "http://localhost:8080/auth/verify/" + token,
                "203.0.113.42",
                "Lisbon",
                "Europe/Lisbon",
                "MEO - SERVICOS DE COMUNICACOES E MULTIMEDIA S.A.");
    }

    @Test
    void blankUsernameIsUnprocessable() throws Exception {
        assertUnprocessable("username", "", "username is required and cannot be blank");
    }

    @Test
    void tooLongUsernameIsUnprocessable() throws Exception {
        assertUnprocessable("username", "j".repeat(51), "username must not exceed 50 characters");
    }

    @Test
    void invalidUsernameIsUnprocessable() throws Exception {
        assertUnprocessable("username", "j", "Username j is invalid");
    }

    @Test
    void blankPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("password", "", "password is required and cannot be blank");
    }

    @Test
    void tooLongPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("password", PASSWORD + "a".repeat(240), "password must not exceed 255 characters");
    }

    @Test
    void shortPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("password", "Short1!", "Password must be at least 12 characters");
    }

    @Test
    void passwordWithoutUppercaseIsUnprocessable() throws Exception {
        assertUnprocessable("password", "strongpassword1!", "Password must contain at least one uppercase character");
    }

    @Test
    void passwordWithoutLowercaseIsUnprocessable() throws Exception {
        assertUnprocessable("password", "STRONGPASSWORD1!", "Password must contain at least one lowercase character");
    }

    @Test
    void passwordWithoutDigitIsUnprocessable() throws Exception {
        assertUnprocessable("password", "StrongPassword!!", "Password must contain at least one digit");
    }

    @Test
    void passwordWithoutSpecialCharacterIsUnprocessable() throws Exception {
        assertUnprocessable("password", "StrongPassword12", "Password must contain at least one special character");
    }

    @Test
    void blankConfirmPasswordIsUnprocessable() throws Exception {
        assertUnprocessable("confirmPassword", "", "confirmPassword is required and cannot be blank");
    }

    @Test
    void mismatchedPasswordsAreUnprocessable() throws Exception {
        assertUnprocessable("confirmPassword", "OtherPassword1!", "Passwords do not match");
    }

    @Test
    void blankFirstNameIsUnprocessable() throws Exception {
        assertUnprocessable("firstName", "", "firstName is required and cannot be blank");
    }

    @Test
    void tooLongFirstNameIsUnprocessable() throws Exception {
        assertUnprocessable("firstName", "J".repeat(101), "firstName must not exceed 100 characters");
    }

    @Test
    void invalidFirstNameIsUnprocessable() throws Exception {
        assertUnprocessable("firstName", "J0hn", "firstName" + NAME_FORMAT);
    }

    @Test
    void blankLastNameIsUnprocessable() throws Exception {
        assertUnprocessable("lastName", "", "lastName is required and cannot be blank");
    }

    @Test
    void tooLongLastNameIsUnprocessable() throws Exception {
        assertUnprocessable("lastName", "D".repeat(101), "lastName must not exceed 100 characters");
    }

    @Test
    void invalidLastNameIsUnprocessable() throws Exception {
        assertUnprocessable("lastName", "Doe--Smith", "lastName" + NAME_FORMAT);
    }

    @Test
    void blankEmailIsUnprocessable() throws Exception {
        assertUnprocessable("email", "", "email is required and cannot be blank");
    }

    @Test
    void tooLongEmailIsUnprocessable() throws Exception {
        assertUnprocessable("email", "a".repeat(89) + "@example.com", "email must not exceed 100 characters");
    }

    @Test
    void invalidEmailIsUnprocessable() throws Exception {
        assertUnprocessable("email", "not-an-email", "Email not-an-email is not valid");
    }

    @Test
    void duplicateUsernameIsConflict() throws Exception {
        register(body(Map.of()));

        register(body(Map.of("username", "JDoe", "email", "other@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You cannot use this username"));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        register(body(Map.of()));

        register(body(Map.of("username", "other", "email", "JDoe@Example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You cannot use this email address"));
    }

    @Test
    void malformedBodyIsBadRequest() throws Exception {
        register("{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));
    }

    @Test
    void failedEmailRollsBackRegistration() throws Exception {
        doThrow(new MailSendException("SMTP unavailable")).when(emailService).sendMail(any());

        register(body(Map.of()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong, please try again later"));

        assertThat(userRepository.count()).isZero();
        assertThat(redis.keys(TOKEN_PREFIX + "*")).isEmpty();
    }

    private void assertUnprocessable(String field, String value, String message) throws Exception {
        register(body(Map.of(field, value)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(message));

        assertThat(userRepository.count()).isZero();
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/auth/register")
                .with(request -> {
                    request.setRemoteAddr("203.0.113.42");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String body(Map<String, String> changes) {
        Map<String, String> fields = new HashMap<>(Map.of(
                "username", "jdoe",
                "password", PASSWORD,
                "confirmPassword", PASSWORD,
                "firstName", "John",
                "lastName", "Doe",
                "email", "jdoe@example.com"));
        fields.putAll(changes);
        return new ObjectMapper().writeValueAsString(fields);
    }

    private static String latestEmail() {
        return RestClient.create(mailpitApiUrl()).get().uri("/message/latest").retrieve().body(String.class);
    }
}
