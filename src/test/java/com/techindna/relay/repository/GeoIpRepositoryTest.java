package com.techindna.relay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.relay.config.TestcontainersConfig;
import com.techindna.relay.entity.GeoIp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GeoIpRepositoryTest extends TestcontainersConfig {

    @Autowired
    private GeoIpRepository geoIpRepository;

    @Test
    void ipv4InKnownNetworkReturnsEnglishLocationAndAsn() {
        assertThat(geoIpRepository.lookup("203.0.113.42")).isEqualTo(new GeoIp(
                "Lisbon", "Portugal", "PT", "Europe", "Lisbon", "1000-001", "Europe/Lisbon",
                38.7167, -9.1333, 3243, "MEO - SERVICOS DE COMUNICACOES E MULTIMEDIA S.A."));
    }

    @Test
    void ipv6WithoutAsnReturnsLocationAndNullAsn() {
        assertThat(geoIpRepository.lookup("2001:db8::1")).isEqualTo(new GeoIp(
                "Lisbon", "Portugal", "PT", "Europe", "Lisbon", "1000-001", "Europe/Lisbon",
                38.7167, -9.1333, null, null));
    }

    @Test
    void unknownIpReturnsAllNulls() {
        assertThat(geoIpRepository.lookup("198.51.100.7")).isEqualTo(new GeoIp(
                null, null, null, null, null, null, null, null, null, null, null));
    }
}
