package com.techindna.relay.repository;

import com.techindna.relay.entity.GeoIp;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GeoIpRepository {

    private static final String LOOKUP = """
            SELECT host(address.ip)                AS ip_address,
                   CAST(net.network AS text)       AS network,
                   location.continent_code,
                   location.continent_name         AS continent,
                   location.country_iso_code       AS country_code,
                   location.country_name           AS country,
                   registered.country_iso_code     AS registered_country_code,
                   registered.country_name         AS registered_country,
                   location.subdivision_1_name     AS subdivision1,
                   location.subdivision_2_name     AS subdivision2,
                   location.city_name              AS city,
                   net.postal_code,
                   location.time_zone              AS timezone,
                   net.latitude,
                   net.longitude,
                   net.accuracy_radius,
                   asn.autonomous_system_number,
                   asn.autonomous_system_organization
            FROM (SELECT CAST(? AS inet) AS ip) address
            LEFT JOIN geoip_app.geoip2_network net ON net.network >>= address.ip
            LEFT JOIN geoip_app.geoip2_location location
                   ON location.geoname_id = net.geoname_id AND location.locale_code = 'en'
            LEFT JOIN geoip_app.geoip2_location registered
                   ON registered.geoname_id = net.registered_country_geoname_id
                  AND registered.locale_code = 'en'
            LEFT JOIN geoip_app.geoip2_asn asn ON asn.network >>= address.ip
            """;

    private final JdbcClient jdbcClient;

    public GeoIpRepository(@Qualifier("geoip") DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public GeoIp lookup(String ipAddress) {
        return jdbcClient.sql(LOOKUP).param(ipAddress).query(GeoIp.class).single();
    }
}
