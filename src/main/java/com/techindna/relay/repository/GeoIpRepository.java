package com.techindna.relay.repository;

import com.techindna.relay.entity.GeoIp;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GeoIpRepository {

    private static final String LOOKUP = """
            SELECT location.city_name          AS city,
                   location.country_name       AS country,
                   location.country_iso_code   AS country_code,
                   location.continent_name     AS continent,
                   location.subdivision_1_name AS subdivision,
                   net.postal_code,
                   location.time_zone          AS timezone,
                   net.latitude,
                   net.longitude,
                   asn.autonomous_system_number,
                   asn.autonomous_system_organization
            FROM (SELECT CAST(? AS inet) AS ip) address
            LEFT JOIN geoip.geoip2_network net ON net.network >>= address.ip
            LEFT JOIN geoip.geoip2_location location
                   ON location.geoname_id = net.geoname_id AND location.locale_code = 'en'
            LEFT JOIN geoip.geoip2_asn_network asn ON asn.network >>= address.ip
            """;

    private final JdbcClient jdbcClient;

    public GeoIpRepository(@Qualifier("geoip") DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public GeoIp lookup(String ipAddress) {
        return jdbcClient.sql(LOOKUP).param(ipAddress).query(GeoIp.class).single();
    }
}
