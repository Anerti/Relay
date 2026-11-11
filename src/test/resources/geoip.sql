CREATE SCHEMA geoip;

CREATE TABLE geoip.geoip2_network (
    network cidr not null,
    geoname_id int,
    registered_country_geoname_id int,
    represented_country_geoname_id int,
    is_anonymous_proxy bool,
    is_satellite_provider bool,
    postal_code text,
    latitude numeric,
    longitude numeric,
    accuracy_radius int,
    is_anycast bool
);

CREATE TABLE geoip.geoip2_location (
    geoname_id int not null,
    locale_code text not null,
    continent_code text,
    continent_name text,
    country_iso_code text,
    country_name text,
    subdivision_1_iso_code text,
    subdivision_1_name text,
    subdivision_2_iso_code text,
    subdivision_2_name text,
    city_name text,
    metro_code int,
    time_zone text,
    is_in_european_union bool not null,
    primary key (geoname_id, locale_code)
);

CREATE TABLE geoip.geoip2_asn_network (
    network cidr not null,
    autonomous_system_number int,
    autonomous_system_organization text
);

INSERT INTO geoip.geoip2_location VALUES
    (2267057, 'en', 'EU', 'Europe', 'PT', 'Portugal', '11', 'Lisbon', null, null, 'Lisbon', null, 'Europe/Lisbon', true),
    (2267057, 'fr', 'EU', 'Europe', 'PT', 'Portugal', '11', 'Lisbonne', null, null, 'Lisbonne', null, 'Europe/Lisbon', true);

INSERT INTO geoip.geoip2_network VALUES
    ('203.0.113.0/24', 2267057, 2264397, null, false, false, '1000-001', 38.7167, -9.1333, 20, false),
    ('2001:db8::/32', 2267057, 2264397, null, false, false, '1000-001', 38.7167, -9.1333, 100, false);

INSERT INTO geoip.geoip2_asn_network VALUES
    ('203.0.113.0/24', 3243, 'MEO - SERVICOS DE COMUNICACOES E MULTIMEDIA S.A.');
