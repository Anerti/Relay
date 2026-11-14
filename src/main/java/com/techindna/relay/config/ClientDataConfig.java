package com.techindna.relay.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientDataConfig {

    private static final int USER_AGENT_MAX_LENGTH = 512;

    public String ipAddress(HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        int zoneIndex = ipAddress.indexOf('%');
        return zoneIndex < 0 ? ipAddress : ipAddress.substring(0, zoneIndex);
    }

    public String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (!StringUtils.hasText(userAgent)) {
            return "Unknown";
        }
        return userAgent.length() > USER_AGENT_MAX_LENGTH
                ? userAgent.substring(0, USER_AGENT_MAX_LENGTH)
                : userAgent;
    }
}
