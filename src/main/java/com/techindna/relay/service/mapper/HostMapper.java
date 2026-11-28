package com.techindna.relay.service.mapper;

import com.techindna.relay.dto.Meta;
import com.techindna.relay.dto.PageResponse;
import com.techindna.relay.entity.Host;
import com.techindna.relay.repository.model.JHost;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class HostMapper {

    public Host toDomain(JHost host) {
        return new Host(
                host.getId(),
                host.getIpAddress().getHostAddress(),
                host.getStatus(),
                host.getLastSeenAt(),
                host.getUpdatedAt());
    }

    public PageResponse<Host> toPageResponse(Page<JHost> hosts, int page, int size) {
        List<Host> data = hosts.getContent().stream().map(this::toDomain).toList();

        return new PageResponse<>(
                data.isEmpty() ? null : data,
                new Meta(page, size, hosts.getTotalElements())
        );
    }
}
