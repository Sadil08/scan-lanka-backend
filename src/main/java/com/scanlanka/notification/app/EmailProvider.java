package com.scanlanka.notification.app;

/** Email port (10, global/09 DIP). Swap a real provider (SMTP/SendGrid/SES — D-Q1) behind this. */
public interface EmailProvider {
    void send(String to, String subject, String body);
}
