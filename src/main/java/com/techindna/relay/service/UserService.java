package com.techindna.relay.service;

import com.techindna.relay.entity.User;
import com.techindna.relay.exception.http.NotFoundException;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.RBACRules;
import com.techindna.relay.service.mapper.UserMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public User getUser(UUID userId, Authentication authentication) {
        JUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        RBACRules.authorize(
                RBACRules.roleOf(authentication),
                UUID.fromString(authentication.getName()),
                user.getRole(),
                user.getId());

        return userMapper.toDomain(user);
    }
}
