package com.techindna.relay.service;

import com.techindna.relay.entity.GeoIp;
import com.techindna.relay.repository.GeoIpRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);

    private final GeoIpRepository geoIpRepository;

    public GeoIp lookup(String ipAddress) {
        try {
            return geoIpRepository.lookup(ipAddress);
        } catch (DataAccessException e) {
            log.warn("GeoIP lookup failed for {}", ipAddress, e);
            return null;
        }
    }
}
