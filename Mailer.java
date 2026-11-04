package com.projects.tracker;

import java.util.List;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/** Sends through Gmail SMTP with an app password. Call from a background thread. */
final class Mailer {
    private Mailer() {}

    static void send(String from, String appPassword, List<String> to, String subject, String text, String html) throws Exception {
        Properties p = new Properties();
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.host", "smtp.gmail.com");
        p.put("mail.smtp.port", "587");
        p.put("mail.smtp.connectiontimeout", "20000");
        p.put("mail.smtp.timeout", "20000");

        Session session = Session.getInstance(p, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, appPassword);
            }
        });

        MimeMessage m = new MimeMessage(session);
        m.setFrom(new InternetAddress(from, "מעקב פרויקטים", "UTF-8"));
        m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(String.join(",", to)));
        m.setSubject(subject, "UTF-8");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(text, "UTF-8");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");
        MimeMultipart alt = new MimeMultipart("alternative");
        alt.addBodyPart(textPart);
        alt.addBodyPart(htmlPart);
        m.setContent(alt);
        Transport.send(m);
    }
}
