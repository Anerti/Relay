package com.techindna.relay.service.event.auth;

import com.techindna.relay.config.ClientDataConfig;
import com.techindna.relay.entity.GeoIp;
import com.techindna.relay.service.GeoIpService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientDetailsService {

    private final ClientDataConfig clientDataConfig;
    private final GeoIpService geoIpService;

    public Map<String, Object> clientVariables(HttpServletRequest request) {
        String ipAddress = clientDataConfig.ipAddress(request);

        Map<String, Object> variables = new HashMap<>();
        variables.put("ipAddress", ipAddress);
        variables.put("userAgent", clientDataConfig.userAgent(request));

        GeoIp geoIp = geoIpService.lookup(ipAddress);
        if (geoIp != null) {
            variables.put("continent", geoIp.continent());
            variables.put("country", geoIp.country());
            variables.put("city", geoIp.city());
            variables.put("timezone", geoIp.timezone());
            variables.put("latitude", geoIp.latitude());
            variables.put("longitude", geoIp.longitude());
            variables.put("accuracyRadius", geoIp.accuracyRadius());
            variables.put("autonomousSystemOrganization", geoIp.autonomousSystemOrganization());
        }
        return variables;
    }
}
