package com.techindna.relay.controller;

import com.techindna.relay.entity.GeoIp;
import com.techindna.relay.repository.GeoIpRepository;
import com.techindna.relay.validator.DataValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GeoIpController {

    private final GeoIpRepository geoIpRepository;
    private final DataValidator dataValidator;

    @GetMapping("/geoip/{ip}")
    public GeoIp getGeoIp(@PathVariable String ip) {
        return geoIpRepository.lookup(dataValidator.validateIpAddress(ip));
    }
}
