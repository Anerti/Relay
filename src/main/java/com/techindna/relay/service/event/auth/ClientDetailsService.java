package com.techindna.relay.service.event.auth;

import com.techindna.relay.config.ClientDataConfig;
import com.techindna.relay.entity.email.ClientDetails;
import com.techindna.relay.service.GeoIpService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientDetailsService {

    private final ClientDataConfig clientDataConfig;
    private final GeoIpService geoIpService;

    public ClientDetails clientDetails(HttpServletRequest request) {
        String ipAddress = clientDataConfig.ipAddress(request);
        return new ClientDetails(ipAddress, clientDataConfig.userAgent(request), geoIpService.lookup(ipAddress));
    }
}
