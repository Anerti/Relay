package com.techindna.relay.service;

import com.techindna.relay.entity.GeoIp;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);

    private final RestTemplate geoIpRestTemplate;

    public GeoIp lookup(String ipAddress) {
        try {
            return geoIpRestTemplate.getForObject("/geoip?ip={ip}", GeoIp.class, ipAddress);
        } catch (RestClientException e) {
            log.warn("GeoIP lookup failed for {}", ipAddress, e);
            return null;
        }
    }
}
