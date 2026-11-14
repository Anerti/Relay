package com.techindna.relay.controller;

import com.techindna.relay.dto.MessageBody;
import com.techindna.relay.dto.RegisterInput;
import com.techindna.relay.dto.VerifyRegistrationResponse;
import com.techindna.relay.service.auth.RegistrationService;
import com.techindna.relay.service.auth.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final VerificationService verificationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageBody register(@RequestBody RegisterInput input, HttpServletRequest request) {
        return registrationService.register(input, request);
    }

    @GetMapping("/verify/{token}")
    public ModelAndView showVerificationConfirmation(@PathVariable String token, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        return new ModelAndView("auth/auth-redirection", Map.of("token", token));
    }

    @PostMapping("/mfa/confirm/register/{token}")
    public VerifyRegistrationResponse confirmRegistration(@PathVariable String token, HttpServletRequest request) {
        return verificationService.confirmRegistration(token, request);
    }
}
