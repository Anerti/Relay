package com.techindna.relay.service;

import com.techindna.relay.config.ClientDataConfig;
import com.techindna.relay.dto.PageResponse;
import com.techindna.relay.dto.UpdateHostInput;
import com.techindna.relay.entity.Host;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.entity.enums.HostStatus;
import com.techindna.relay.exception.http.NotFoundException;
import com.techindna.relay.exception.http.UnprocessableContentException;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.RBACRules;
import com.techindna.relay.service.event.HostEventService;
import com.techindna.relay.service.mapper.HostMapper;
import com.techindna.relay.validator.DataValidator;
import com.techindna.relay.validator.HostValidator;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HostService {

    private final HostRepository hostRepository;
    private final UserRepository userRepository;
    private final DataValidator dataValidator;
    private final HostValidator hostValidator;
    private final HostMapper hostMapper;
    private final HostEventService hostEventService;
    private final ClientDataConfig clientDataConfig;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public PageResponse<Host> listHosts(UUID userId, String order, int page, int size, Authentication authentication) {
        dataValidator.validatePagination(page, size);

        JUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        RBACRules.authorize(
                RBACRules.roleOf(authentication),
                UUID.fromString(authentication.getName()),
                user.getRole(),
                user.getId());

        PageRequest pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(
                        "asc".equalsIgnoreCase(order) ?
                                Sort.Direction.ASC :
                                Sort.Direction.DESC,
                        "lastSeenAt", "id"));

        return hostMapper.toPageResponse(hostRepository.findByUserId(user.getId(), pageable), page, size);
    }

    @Transactional
    public Host updateHostStatus(UUID userId, UUID hostId, UpdateHostInput input,
            Authentication authentication, HttpServletRequest request) {
        HostStatus status = hostValidator.validateUpdate(input);

        JUser caller = userRepository.findById(UUID.fromString(authentication.getName()))
                .orElseThrow(() -> new NotFoundException("User not found"));

        JUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        RBACRules.authorize(RBACRules.roleOf(authentication), caller.getId(), user.getRole(), user.getId());

        JHost host = hostRepository.findByIdAndUserId(hostId, user.getId())
                .orElseThrow(() -> new NotFoundException("Host not found"));

        if (status == HostStatus.BANNED
                && caller.getId().equals(user.getId())
                && host.getIpAddress().equals(InetAddress.ofLiteral(clientDataConfig.ipAddress(request)))) {
            throw new UnprocessableContentException("You cannot ban the host you are using");
        }

        if (host.getStatus() != status) {
            host.setStatus(status);
            host.setUpdatedAt(Instant.now());
            hostRepository.saveAndFlush(host);

            hostEventService.logEvent(
                    hostEventService.recordHost(caller, request),
                    EventLogSeverity.INFO,
                    "[HOST]: Host " + host.getIpAddress().getHostAddress() + " "
                            + status.name().toLowerCase(Locale.ROOT),
                    request);

            entityManager.refresh(host);
        }

        return hostMapper.toDomain(host);
    }
}
