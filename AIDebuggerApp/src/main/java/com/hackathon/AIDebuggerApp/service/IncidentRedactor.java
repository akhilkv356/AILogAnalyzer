package com.hackathon.AIDebuggerApp.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class IncidentRedactor {
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN [^-]*PRIVATE KEY-----[\\s\\S]*?-----END [^-]*PRIVATE KEY-----");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[^\\s,;\"']+");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)([\"']?(?:password|pwd|api[-_]?key|access[-_]?token|secret|authorization)"
                    + "[\"']?\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;]+)");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern URL_CREDENTIALS = Pattern.compile(
            "(?i)(https?://)[^\\s/@]+:[^\\s/@]+@");

    public String redact(String input) {
        String text = PRIVATE_KEY.matcher(input).replaceAll("[REDACTED PRIVATE KEY]");
        text = BEARER.matcher(text).replaceAll("Bearer [REDACTED]");
        text = SECRET.matcher(text).replaceAll("$1[REDACTED]");
        text = URL_CREDENTIALS.matcher(text).replaceAll("$1[REDACTED]@");
        return EMAIL.matcher(text).replaceAll("[REDACTED EMAIL]");
    }
}
