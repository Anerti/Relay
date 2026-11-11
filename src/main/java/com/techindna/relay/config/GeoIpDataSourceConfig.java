package com.techindna.relay.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GeoIpDataSourceConfig {
    @Bean(defaultCandidate = false)
    @Qualifier("geoip")
    @ConfigurationProperties("app.geoip.datasource")
    public HikariDataSource geoIpDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
