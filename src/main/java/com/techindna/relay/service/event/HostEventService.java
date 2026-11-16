package com.techindna.relay.service.event;

import com.techindna.relay.config.ClientDataConfig;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.repository.EventLogRepository;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.model.JEventLog;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HostEventService {

    private final HostRepository hostRepository;
    private final EventLogRepository eventLogRepository;
    private final ClientDataConfig clientDataConfig;

    public JHost recordHost(JUser user, HttpServletRequest request) {
        InetAddress ipAddress = InetAddress.ofLiteral(clientDataConfig.ipAddress(request));

        hostRepository.upsertLastSeen(user.getId(), ipAddress.getHostAddress(), Instant.now());

        return hostRepository.findByUserAndIpAddress(user, ipAddress).orElseThrow();
    }

    public void logEvent(
            JHost host, EventLogSeverity severity, String description, HttpServletRequest request) {
        eventLogRepository.saveAndFlush(JEventLog.builder()
                .host(host)
                .userAgent(clientDataConfig.userAgent(request))
                .severity(severity)
                .description(description)
                .build());
    }
}
