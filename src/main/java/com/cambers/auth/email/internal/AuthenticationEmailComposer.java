package com.cambers.auth.email.internal;

import com.cambers.auth.email.internal.config.AuthenticationEmailProperties;
import com.cambers.auth.email.internal.config.PasswordResetDeliveryProperties;
import com.cambers.auth.email.internal.config.VerificationDeliveryProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
public class AuthenticationEmailComposer {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final AuthenticationEmailProperties emailProperties;
    private final VerificationDeliveryProperties verificationProperties;
    private final PasswordResetDeliveryProperties passwordResetProperties;

    public AuthenticationEmailComposer(
            AuthenticationEmailProperties emailProperties,
            VerificationDeliveryProperties verificationProperties,
            PasswordResetDeliveryProperties passwordResetProperties) {
        this.emailProperties = emailProperties;
        this.verificationProperties = verificationProperties;
        this.passwordResetProperties = passwordResetProperties;
    }

    public TransactionalEmail verification(
            String recipient,
            String rawToken,
            Instant expiresAt,
            UUID issuanceId) {
        String actionUrl = actionUrl(verificationProperties.publicUrl().toString(), rawToken);
        String productName = emailProperties.productName();
        return new TransactionalEmail(
                recipient,
                "Verify your email address",
                htmlTemplate(
                        productName,
                        "Verify your email address",
                        "Confirm that this email address belongs to you.",
                        "Verify email",
                        actionUrl,
                        expiresAt,
                        "If you did not create this account, you can ignore this email."
                ),
                textTemplate(
                        productName,
                        "Verify your email address",
                        "Confirm that this email address belongs to you.",
                        actionUrl,
                        expiresAt,
                        "If you did not create this account, you can ignore this email."
                ),
                "auth/email-verification/" + issuanceId,
                List.of(new EmailTag("category", "email_verification"))
        );
    }

    public TransactionalEmail passwordReset(
            String recipient,
            String rawToken,
            Instant expiresAt,
            UUID issuanceId) {
        String actionUrl = actionUrl(passwordResetProperties.publicUrl().toString(), rawToken);
        String productName = emailProperties.productName();
        return new TransactionalEmail(
                recipient,
                "Reset your password",
                htmlTemplate(
                        productName,
                        "Reset your password",
                        "Use the secure link below to choose a new password.",
                        "Reset password",
                        actionUrl,
                        expiresAt,
                        "If you did not request a password reset, you can ignore this email. Your password will not change."
                ),
                textTemplate(
                        productName,
                        "Reset your password",
                        "Use the secure link below to choose a new password.",
                        actionUrl,
                        expiresAt,
                        "If you did not request a password reset, you can ignore this email. Your password will not change."
                ),
                "auth/password-reset/" + issuanceId,
                List.of(new EmailTag("category", "password_reset"))
        );
    }

    private String actionUrl(String publicUrl, String rawToken) {
        return UriComponentsBuilder.fromUriString(publicUrl)
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }

    private String htmlTemplate(
            String productName,
            String heading,
            String description,
            String buttonLabel,
            String actionUrl,
            Instant expiresAt,
            String safetyMessage) {
        String safeProductName = HtmlUtils.htmlEscape(productName);
        String safeHeading = HtmlUtils.htmlEscape(heading);
        String safeDescription = HtmlUtils.htmlEscape(description);
        String safeButtonLabel = HtmlUtils.htmlEscape(buttonLabel);
        String safeActionUrl = HtmlUtils.htmlEscape(actionUrl);
        String safeExpiry = HtmlUtils.htmlEscape(TIMESTAMP_FORMATTER.format(expiresAt));
        String safeSafetyMessage = HtmlUtils.htmlEscape(safetyMessage);

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f6f7f9;color:#111827;font-family:Arial,Helvetica,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background:#f6f7f9;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:560px;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;">
                          <tr>
                            <td style="padding:32px;">
                              <div style="font-size:14px;font-weight:700;color:#374151;margin-bottom:24px;">%s</div>
                              <h1 style="margin:0 0 16px;font-size:26px;line-height:1.25;color:#111827;">%s</h1>
                              <p style="margin:0 0 24px;font-size:16px;line-height:1.6;color:#4b5563;">%s</p>
                              <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 24px;">
                                <tr>
                                  <td style="border-radius:8px;background:#111827;">
                                    <a href="%s" style="display:inline-block;padding:12px 20px;color:#ffffff;text-decoration:none;font-size:15px;font-weight:700;">%s</a>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:0 0 12px;font-size:13px;line-height:1.5;color:#6b7280;">This link expires at %s.</p>
                              <p style="margin:0 0 8px;font-size:13px;line-height:1.5;color:#6b7280;">If the button does not work, copy and paste this URL into your browser:</p>
                              <p style="margin:0 0 24px;font-size:12px;line-height:1.5;word-break:break-all;color:#4b5563;">%s</p>
                              <p style="margin:0;font-size:13px;line-height:1.5;color:#6b7280;">%s</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                safeHeading,
                safeProductName,
                safeHeading,
                safeDescription,
                safeActionUrl,
                safeButtonLabel,
                safeExpiry,
                safeActionUrl,
                safeSafetyMessage
        );
    }

    private String textTemplate(
            String productName,
            String heading,
            String description,
            String actionUrl,
            Instant expiresAt,
            String safetyMessage) {
        return productName + "\n\n"
                + heading + "\n\n"
                + description + "\n\n"
                + actionUrl + "\n\n"
                + "This link expires at " + TIMESTAMP_FORMATTER.format(expiresAt) + ".\n\n"
                + safetyMessage;
    }
}
