package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * E-mail over SMTP. It is live only when {@code SMTP_HOST} is set; without it the channel falls back
 * to a stub that logs a masked line and reports {@link Mode#STUB}, so the record says honestly that
 * nothing left the platform. The routing decision (which channel, to whom) is real either way.
 *
 * The SMTP host comes from configuration, never from a request, and the only address written to is
 * the one preferences resolved from the customer's own profile.
 */
@Slf4j
@Component
class EmailChannelSender implements ChannelSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String smtpHost;
    private final String from;

    EmailChannelSender(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${spring.mail.host:}") String smtpHost,
                       @Value("${app.notifications.mail.from:no-reply@quorum.local}") String from) {
        this.mailSender = mailSender;
        this.smtpHost = smtpHost;
        this.from = from;
    }

    @Override
    public AlertChannel channel() {
        return AlertChannel.EMAIL;
    }

    @Override
    public Mode send(OutboundMessage message) throws DeliveryException {
        JavaMailSender sender = smtpHost == null || smtpHost.isBlank() ? null : mailSender.getIfAvailable();
        if (sender == null) {
            log.info("[NOTIFY-STUB:EMAIL] no SMTP host configured; not sent. to={}", message.masked());
            return Mode.STUB;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(message.destination());
            mail.setSubject(message.subject());
            mail.setText(message.body());
            sender.send(mail);
            log.info("[NOTIFY] e-mail sent to={}", message.masked());
            return Mode.LIVE;
        } catch (MailException e) {
            // The exception text can quote the address; keep only its type.
            throw new DeliveryException("E-mail provider failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
