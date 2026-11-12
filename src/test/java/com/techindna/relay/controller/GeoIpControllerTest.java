package com.techindna.relay.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.security.jwt.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class GeoIpControllerTest extends TestcontainersConfig {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void knownIpv4ReturnsEveryDetail() throws Exception {
        lookup("203.0.113.42")
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "ipAddress": "203.0.113.42",
                          "network": "203.0.113.0/24",
                          "continentCode": "EU",
                          "continent": "Europe",
                          "countryCode": "PT",
                          "country": "Portugal",
                          "registeredCountryCode": "ES",
                          "registeredCountry": "Spain",
                          "subdivision1": "Lisbon",
                          "subdivision2": null,
                          "city": "Lisbon",
                          "postalCode": "1000-001",
                          "timezone": "Europe/Lisbon",
                          "latitude": 38.7167,
                          "longitude": -9.1333,
                          "accuracyRadius": 20,
                          "autonomousSystemNumber": 3243,
                          "autonomousSystemOrganization": "MEO - SERVICOS DE COMUNICACOES E MULTIMEDIA S.A."
                        }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void knownIpv6IsNormalized() throws Exception {
        lookup("2001:DB8:0:0:0:0:0:1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ipAddress").value("2001:db8::1"))
                .andExpect(jsonPath("$.network").value("2001:db8::/32"))
                .andExpect(jsonPath("$.autonomousSystemNumber").value(nullValue()));
    }

    @Test
    void ipv4MappedIpv6UsesIpv4Data() throws Exception {
        lookup("::ffff:203.0.113.42")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ipAddress").value("203.0.113.42"))
                .andExpect(jsonPath("$.network").value("203.0.113.0/24"))
                .andExpect(jsonPath("$.city").value("Lisbon"));
    }

    @Test
    void asnIsReturnedWithoutCityData() throws Exception {
        lookup("192.0.2.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.network").value(nullValue()))
                .andExpect(jsonPath("$.country").value(nullValue()))
                .andExpect(jsonPath("$.autonomousSystemNumber").value(4200000000L))
                .andExpect(jsonPath("$.autonomousSystemOrganization").value("TEST-NET-1 32-bit ASN"));
    }

    @Test
    void unknownIpReturnsNullDetails() throws Exception {
        lookup("198.51.100.7")
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "ipAddress": "198.51.100.7",
                          "network": null,
                          "continentCode": null,
                          "continent": null,
                          "countryCode": null,
                          "country": null,
                          "registeredCountryCode": null,
                          "registeredCountry": null,
                          "subdivision1": null,
                          "subdivision2": null,
                          "city": null,
                          "postalCode": null,
                          "timezone": null,
                          "latitude": null,
                          "longitude": null,
                          "accuracyRadius": null,
                          "autonomousSystemNumber": null,
                          "autonomousSystemOrganization": null
                        }
                        """, JsonCompareMode.STRICT));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"999.0.0.1", "example.com"})
    void invalidIpIsRejected(String ip) throws Exception {
        lookup(ip)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD REQUEST"))
                .andExpect(jsonPath("$.message").value("IP address " + ip + " is not valid"));
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        lookup("203.0.113.42", "Bearer " + tokenBoundTo("127.0.0.1"))
                .andExpect(status().isOk());
    }

    @Test
    void tokenFromAnotherIpIsRejected() throws Exception {
        lookup("203.0.113.42", "Bearer " + tokenBoundTo("198.51.100.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot access this resource from the current IP address."));
    }

    @Test
    void malformedTokenIsIgnored() throws Exception {
        lookup("203.0.113.42", "Bearer not-a-jwt")
                .andExpect(status().isOk());
    }

    @Test
    void onlyGetIsPublic() throws Exception {
        mockMvc.perform(post("/geoip/{ip}", "203.0.113.42"))
                .andExpect(status().isUnauthorized());
    }

    private String tokenBoundTo(String ipAddress) {
        return jwtTokenProvider.generateToken(UUID.randomUUID().toString(), "GUEST", ipAddress);
    }

    private ResultActions lookup(String ip) throws Exception {
        return mockMvc.perform(get("/geoip/{ip}", ip));
    }

    private ResultActions lookup(String ip, String authorization) throws Exception {
        return mockMvc.perform(get("/geoip/{ip}", ip).header(HttpHeaders.AUTHORIZATION, authorization));
    }
}
