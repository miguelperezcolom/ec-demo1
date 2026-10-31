package io.mateu.ecdemo1.contenthub.infra.out.mail;

import io.mateu.ecdemo1.contenthub.application.out.Mailer;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Through the environment's relay (postfix). In the demo the employees are made up, so when
 * {@code content-hub.newsletter.redirect-to} is set every message goes there instead, saying in its body
 * whom it was for.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmtpMailer implements Mailer {

    final JavaMailSender sender;

    @Value("${content-hub.newsletter.from:intranet@ec1.mateu.io}")
    String from;

    @Value("${content-hub.newsletter.redirect-to:}")
    String redirectTo;

    @Override
    public boolean redirected() {
        return redirectTo != null && !redirectTo.isBlank();
    }

    @Override
    public void send(List<String> recipients, String subject, String html) {
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            var body = html;
            if (redirectTo != null && !redirectTo.isBlank()) {
                helper.setTo(redirectTo);
                body = "<p style=\"background:#fff3cd;padding:8px\">Demo: este envío era para " + recipients.size()
                        + " destinatarios" + (recipients.size() <= 10 ? ": " + String.join(", ", recipients) : "")
                        + "</p>" + html;
            } else {
                helper.setTo(from);
                helper.setBcc(recipients.toArray(String[]::new));
            }
            helper.setSubject(subject);
            helper.setText(body, true);
            sender.send(message);
            log.info("Newsletter '{}' sent to {} recipients{}", subject, recipients.size(),
                    redirectTo == null || redirectTo.isBlank() ? "" : " (redirected to " + redirectTo + ")");
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build the message: " + e.getMessage(), e);
        }
    }
}
