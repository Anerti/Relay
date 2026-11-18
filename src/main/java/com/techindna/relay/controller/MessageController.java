package com.techindna.relay.controller;

import com.techindna.relay.dto.PageResponse;
import com.techindna.relay.dto.SendMessageInput;
import com.techindna.relay.entity.Message;
import com.techindna.relay.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;

    @GetMapping(value = "/users/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<Message> listMessages(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication,
            HttpServletRequest request) {
        return messageService.listMessages(userId, order, page, size, authentication, request);
    }

    @PostMapping(
            value = "/users/{userId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Message sendMessage(
            @PathVariable UUID userId,
            @RequestBody SendMessageInput input,
            Authentication authentication,
            HttpServletRequest request) {
        return messageService.sendMessage(userId, input, authentication, request);
    }
}
