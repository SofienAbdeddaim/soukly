package com.soukly.user.support;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

/**
 * GreenMail SMTP server kept on the same port for the whole test run, so that a test can stop it (SMTP down) and
 * the next test gets it back.
 */
public final class TestSmtpServer {

    static final String HOST = "127.0.0.1";
    private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

    private GreenMail greenMail;
    private int port;

    TestSmtpServer() {
        greenMail = new GreenMail(new ServerSetup(0, HOST, ServerSetup.PROTOCOL_SMTP).dynamicPort());
        greenMail.start();
        port = greenMail.getSmtp().getPort();
    }

    int port() {
        return port;
    }

    public void stop() {
        greenMail.stop();
    }

    void ensureRunning() {
        if (!greenMail.isRunning()) {
            greenMail = new GreenMail(new ServerSetup(port, HOST, ServerSetup.PROTOCOL_SMTP));
            greenMail.start();
        }
    }

    public void purge() throws FolderException {
        greenMail.purgeEmailFromAllMailboxes();
    }

    /** Waits until {@code count} emails have arrived since the start of the test (emails are sent asynchronously). */
    public List<MimeMessage> awaitEmails(int count) {
        return awaitEmails(count, 10_000);
    }

    /** Same, waiting at most {@code timeoutMillis}. */
    public List<MimeMessage> awaitEmails(int count, long timeoutMillis) {
        if (!greenMail.waitForIncomingEmail(timeoutMillis, count)) {
            throw new AssertionError("Expected " + count + " email(s), got " + received().size());
        }
        return received();
    }

    public List<MimeMessage> received() {
        return List.of(greenMail.getReceivedMessages());
    }

    /** Token of the verification link in the text part of an email. */
    public static String token(MimeMessage message) {
        Matcher matcher = TOKEN.matcher(part(message, "text/plain"));
        if (!matcher.find()) {
            throw new AssertionError("No verification link in the email");
        }
        return matcher.group(1);
    }

    /** Decoded content of the first part of this MIME type, for example {@code text/html}. */
    public static String part(MimeMessage message, String mimeType) {
        try {
            String content = find(message, mimeType);
            if (content == null) {
                throw new AssertionError("No " + mimeType + " part in the email");
            }
            return content;
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String find(Part part, String mimeType) throws MessagingException, IOException {
        if (part.isMimeType(mimeType)) {
            return (String) part.getContent();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = find(multipart.getBodyPart(i), mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
