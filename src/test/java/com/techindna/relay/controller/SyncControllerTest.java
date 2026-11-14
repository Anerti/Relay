package com.techindna.relay.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.security.jwt.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class SyncControllerTest extends TestcontainersConfig {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void synReturnsAck() throws Exception {
        mockMvc.perform(get("/syn"))
                .andExpect(status().isOk())
                .andExpect(content().string("syn-ack"));
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        syn("Bearer " + tokenBoundTo("127.0.0.1"))
                .andExpect(status().isOk());
    }

    @Test
    void tokenFromAnotherIpIsRejected() throws Exception {
        syn("Bearer " + tokenBoundTo("198.51.100.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));
    }

    @Test
    void malformedTokenIsIgnored() throws Exception {
        syn("Bearer not-a-jwt")
                .andExpect(status().isOk());
    }

    @Test
    void onlyGetIsPublic() throws Exception {
        mockMvc.perform(post("/syn"))
                .andExpect(status().isUnauthorized());
    }

    private String tokenBoundTo(String ipAddress) {
        return jwtTokenProvider.generateToken(UUID.randomUUID().toString(), "GUEST", ipAddress);
    }

    private ResultActions syn(String authorization) throws Exception {
        return mockMvc.perform(get("/syn").header(HttpHeaders.AUTHORIZATION, authorization));
    }
}
