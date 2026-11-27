package com.techindna.relay.service.event.auth;

import com.techindna.relay.entity.email.EmailDetails;
import com.techindna.relay.entity.email.EmailTemplate;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.enums.VerificationFlow;
import com.techindna.relay.service.mail.EmailService;
import com.techindna.relay.service.redis.VerificationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationEmailService {

    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;
    private final ClientDetailsService clientDetailsService;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendRegistrationVerification(JUser user, HttpServletRequest request) {
        Map<String, Object> variables = clientVariables(user, request);
        variables.put("time", DateTimeFormatter.ISO_INSTANT.format(user.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)));

        sendVerification(
                user,
                VerificationFlow.REGISTER,
                EmailTemplate.REGISTRATION_VERIFICATION,
                "Verify your account",
                variables);
    }

    public void sendLoginVerification(JUser user, HttpServletRequest request) {
        Map<String, Object> variables = clientVariables(user, request);
        variables.put("time", DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)));

        sendVerification(
                user,
                VerificationFlow.LOGIN,
                EmailTemplate.LOGIN_VERIFICATION,
                "Sign in to your account",
                variables);
    }

    private Map<String, Object> clientVariables(JUser user, HttpServletRequest request) {
        Map<String, Object> variables = clientDetailsService.clientVariables(request);
        variables.put("firstName", user.getFirstName());
        variables.put("lastName", user.getLastName());
        variables.put("username", user.getUsername());
        variables.put("email", user.getEmail());
        return variables;
    }

    private void sendVerification(
            JUser user,
            VerificationFlow flow,
            EmailTemplate template,
            String subject,
            Map<String, Object> variables) {
        String token = verificationTokenService.createForUser(user.getId(), flow);
        variables.put("verificationUrl", baseUrl.replaceAll("/+$", "") + "/auth/verify/" + token);

        try {
            emailService.sendMail(
                    new EmailDetails(template, user.getEmail(), subject, variables));
        } catch (RuntimeException e) {
            try {
                verificationTokenService.delete(token);
            } catch (RuntimeException deleteFailure) {
                e.addSuppressed(deleteFailure);
            }
            throw e;
        }
    }
}
