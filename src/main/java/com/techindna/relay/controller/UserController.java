package com.techindna.relay.controller;

import com.techindna.relay.dto.PageResponse;
import com.techindna.relay.dto.UpdateUserInput;
import com.techindna.relay.entity.User;
import com.techindna.relay.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @GetMapping
    public PageResponse<User> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(defaultValue = "guest") String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.listUsers(search, order, role, page, size);
    }

    @GetMapping("/{userId}")
    public User getUser(@PathVariable UUID userId, Authentication authentication) {
        return userService.getUser(userId, authentication);
    }

    @PatchMapping(value = "/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public User updateUser(
            @PathVariable UUID userId,
            @RequestBody UpdateUserInput input,
            Authentication authentication) {
        return userService.updateUser(userId, input, authentication);
    }
}
