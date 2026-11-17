package com.techindna.relay.service;

import com.techindna.relay.dto.PageResponse;
import com.techindna.relay.entity.User;
import com.techindna.relay.entity.enums.UserRole;
import com.techindna.relay.exception.http.NotFoundException;
import com.techindna.relay.repository.UserRepository;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.security.RBACRules;
import com.techindna.relay.service.mapper.UserMapper;
import com.techindna.relay.validator.DataValidator;
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
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final DataValidator dataValidator;

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

    @Transactional(readOnly = true)
    public PageResponse<User> listUsers(String search, String order, String role, int page, int size) {
        dataValidator.validateSearchString(search);
        dataValidator.validatePagination(page, size);

        PageRequest pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(
                        "desc".equalsIgnoreCase(order) ?
                                Sort.Direction.DESC :
                                Sort.Direction.ASC,
                        "createdAt", "id"));

        return userMapper.toPageResponse(
                userRepository.findByRoleAndSearch(
                        "superuser".equalsIgnoreCase(role) ? UserRole.SUPERUSER : UserRole.GUEST,
                        (search == null || search.isBlank()) ? null : search.toLowerCase(Locale.ROOT),
                        pageable),
                page,
                size);
    }
}
