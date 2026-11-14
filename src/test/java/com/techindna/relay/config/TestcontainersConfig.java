package com.techindna.relay.config;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class TestcontainersConfig {

    private static final String TEST_JWT_SECRET =
            Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(48));
    
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27"))
                    .withExposedPorts(1025, 8025);

    private static final String GEOIP_RESPONSE = """
            {
              "network": "203.0.113.0/24",
              "continentCode": "EU",
              "continent": "Europe",
              "countryCode": "PT",
              "country": "Portugal",
              "registeredCountryCode": "PT",
              "registeredCountry": "Portugal",
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
            """;

    private static final HttpServer GEOIP = geoIpServer();

    static {
        POSTGRES.start();
        REDIS.start();
        MAILPIT.start();
        GEOIP.start();
    }

    private static HttpServer geoIpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/geoip", exchange -> {
                boolean known = "ip=203.0.113.42".equals(exchange.getRequestURI().getQuery());
                byte[] body = (known ? GEOIP_RESPONSE : "{}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(known ? 200 : 400, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void configureTestContainers(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.geoip.url", () -> "http://localhost:" + GEOIP.getAddress().getPort());
        registry.add(
                "spring.data.redis.url",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    protected static String mailpitApiUrl() {
        return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1";
    }
}
