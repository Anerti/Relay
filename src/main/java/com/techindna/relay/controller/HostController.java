package com.techindna.relay.controller;

import com.techindna.relay.dto.PageResponse;
import com.techindna.relay.entity.Host;
import com.techindna.relay.service.HostService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/{userId}/hosts")
public class HostController {

    private final HostService hostService;

    @GetMapping
    public PageResponse<Host> listHosts(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return hostService.listHosts(userId, order, page, size, authentication);
    }
}
