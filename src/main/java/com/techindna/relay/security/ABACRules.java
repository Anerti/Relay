package com.techindna.relay.security;

import com.techindna.relay.entity.enums.HostStatus;
import com.techindna.relay.entity.enums.UserStatus;
import com.techindna.relay.exception.http.ForbiddenException;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;

public final class ABACRules {

    public static void authorizeIpAddress(String tokenIpAddress, String requestIpAddress) {
        if (tokenIpAddress == null
                || tokenIpAddress.isBlank()
                || (requestIpAddress != null && !tokenIpAddress.equals(requestIpAddress))) {
            throw new ForbiddenException("Cannot access this resource from the current IP address.");
        }
    }

    public static void rejectLocked(JUser user, String message, Runnable audit) {
        deny(user.getStatus() == UserStatus.LOCKED, message, audit);
    }

    public static void rejectUnverified(JUser user, String message, Runnable audit) {
        deny(!Boolean.TRUE.equals(user.getVerified()), message, audit);
    }

    public static void rejectBannedHost(JHost host, String ipAddress, Runnable audit) {
        deny(host.getStatus() == HostStatus.BANNED,
                "Host " + ipAddress + " is banned from accessing this account", audit);
    }

    private static void deny(boolean denied, String message, Runnable audit) {
        if (denied) {
            audit.run();
            throw new ForbiddenException(message);
        }
    }
}
