package com.techindna.relay.service.event.auth;

import com.techindna.relay.entity.GeoIp;
import com.techindna.relay.repository.GeoIpRepository;
import com.techindna.relay.security.ClientIpAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientDetailsService {

    private static final Logger log = LoggerFactory.getLogger(ClientDetailsService.class);

    private final ClientIpAddressResolver clientIpAddressResolver;
    private final GeoIpRepository geoIpRepository;

    public Map<String, Object> clientVariables(HttpServletRequest request) {
        String ipAddress = clientIpAddressResolver.resolve(request);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

        Map<String, Object> variables = new HashMap<>();
        variables.put("ipAddress", ipAddress);
        variables.put("userAgent", userAgent == null ? "Unknown" : userAgent);

        GeoIp geoIp = lookup(ipAddress);
        if (geoIp != null) {
            variables.put("ipAddress", geoIp.ipAddress());
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

    private GeoIp lookup(String ipAddress) {
        try {
            return geoIpRepository.lookup(ipAddress);
        } catch (DataAccessException e) {
            log.warn("GeoIP lookup failed for {}", ipAddress, e);
            return null;
        }
    }
}
