package com.techindna.relay.service.auth;

import com.techindna.relay.config.ClientDataConfig;
import com.techindna.relay.dto.VerifyRegistrationResponse;
import com.techindna.relay.entity.enums.EventLogSeverity;
import com.techindna.relay.exception.http.UnauthorizedException;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JHost;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.jwt.JwtTokenProvider;
import com.techindna.relay.service.enums.VerificationFlow;
import com.techindna.relay.service.event.HostEventService;
import com.techindna.relay.service.mapper.UserMapper;
import com.techindna.relay.service.redis.VerificationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

    private final VerificationTokenService verificationTokenService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final HostEventService hostEventService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ClientDataConfig clientDataConfig;

    @Transactional
    public VerifyRegistrationResponse confirmRegistration(String token, HttpServletRequest request) {
        UUID userId = verificationTokenService.consume(token, VerificationFlow.REGISTER);
        if (userId == null) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }

        JUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN_MESSAGE));

        user.setVerified(true);
        user = userRepository.saveAndFlush(user);

        JHost host = hostEventService.recordHost(user, request);
        hostEventService.logEvent(host, EventLogSeverity.INFO, "[REGISTRATION]: Account verified", request);

        String jwt = jwtTokenProvider.generateToken(
                user.getId().toString(), user.getRole().name(), clientDataConfig.ipAddress(request));

        return new VerifyRegistrationResponse(jwt, userMapper.toDomain(user));
    }
}
