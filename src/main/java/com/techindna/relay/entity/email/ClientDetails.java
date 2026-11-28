package com.techindna.relay.entity.email;

import com.techindna.relay.entity.GeoIp;

public record ClientDetails(
        String ipAddress,
        String userAgent,
        GeoIp geoIp) {}
