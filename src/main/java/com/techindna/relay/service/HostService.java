package com.techindna.relay.service;

import com.techindna.relay.dto.PageResponse;
import com.techindna.relay.entity.Host;
import com.techindna.relay.exception.http.NotFoundException;
import com.techindna.relay.repository.HostRepository;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.RBACRules;
import com.techindna.relay.service.mapper.HostMapper;
import com.techindna.relay.validator.DataValidator;
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
    private final HostMapper hostMapper;

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
}
