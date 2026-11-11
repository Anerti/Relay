package com.techindna.relay.entity;

public record GeoIp(
        String ipAddress,
        String network,
        String continentCode,
        String continent,
        String countryCode,
        String country,
        String registeredCountryCode,
        String registeredCountry,
        String subdivision1,
        String subdivision2,
        String city,
        String postalCode,
        String timezone,
        Double latitude,
        Double longitude,
        Integer accuracyRadius,
        Long autonomousSystemNumber,
        String autonomousSystemOrganization
) {}
