package com.techindna.relay.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.repository.EventLogRepository;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthRegistrationForwardedForTest extends TestcontainersConfig {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        eventLogRepository.deleteAll();
        hostRepository.deleteAll();
        userRepository.deleteAll();
        redis.delete(redis.keys("auth:verification:*"));
    }

    @Test
    void forwardedForHeaderIsIgnoredWithoutTrustedProxy() {
        RestClient.create("http://127.0.0.1:" + port).post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.42")
                .body("""
                        {"username": "jdoe", "password": "StrongPassword1!", "confirmPassword": "StrongPassword1!",
                         "firstName": "John", "lastName": "Doe", "email": "jdoe@example.com"}
                        """)
                .retrieve()
                .toBodilessEntity();

        assertThat(hostRepository.findAll().getFirst().getIpAddress().getHostAddress()).isEqualTo("127.0.0.1");
    }
}
