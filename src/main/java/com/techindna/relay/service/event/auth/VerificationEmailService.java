package com.techindna.relay.service.event.auth;

import com.techindna.relay.entity.email.ClientDetails;
import com.techindna.relay.entity.email.EmailDetails;
import com.techindna.relay.entity.email.EmailTemplate;
import com.techindna.relay.entity.email.VerificationEmail;
import com.techindna.relay.repository.model.JUser;
import com.techindna.relay.service.enums.VerificationFlow;
import com.techindna.relay.service.mail.EmailService;
import com.techindna.relay.service.redis.VerificationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
        sendVerification(
                user,
                user.getCreatedAt(),
                VerificationFlow.REGISTER,
                EmailTemplate.REGISTRATION_VERIFICATION,
                "Verify your account",
                request);
    }

    public void sendLoginVerification(JUser user, HttpServletRequest request) {
        sendVerification(
                user,
                Instant.now(),
                VerificationFlow.LOGIN,
                EmailTemplate.LOGIN_VERIFICATION,
                "Sign in to your account",
                request);
    }

    private void sendVerification(
            JUser user,
            Instant time,
            VerificationFlow flow,
            EmailTemplate template,
            String subject,
            HttpServletRequest request) {
        ClientDetails client = clientDetailsService.clientDetails(request);
        String token = verificationTokenService.createForUser(user.getId(), flow);
        VerificationEmail model = new VerificationEmail(
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getEmail(),
                DateTimeFormatter.ISO_INSTANT.format(time.truncatedTo(ChronoUnit.SECONDS)),
                baseUrl.replaceAll("/+$", "") + "/auth/verify/" + token,
                client);

        try {
            emailService.sendMail(
                    new EmailDetails(template, user.getEmail(), subject, model));
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
