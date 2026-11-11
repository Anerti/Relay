package com.techindna.relay.entity;

public record GeoIp(
        String city,
        String country,
        String countryCode,
        String continent,
        String subdivision,
        String postalCode,
        String timezone,
        Double latitude,
        Double longitude,
        Integer autonomousSystemNumber,
        String autonomousSystemOrganization
) {}
