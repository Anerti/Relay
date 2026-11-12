package com.techindna.relay.controller;

import com.techindna.relay.dto.MessageBody;
import com.techindna.relay.dto.RegisterInput;
import com.techindna.relay.service.auth.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageBody register(@RequestBody RegisterInput input, HttpServletRequest request) {
        return registrationService.register(input, request);
    }
}
