package com.efremandrei.statusupdater;

import java.net.URI;
import java.net.URISyntaxException;

/** Validated, immutable snapshot. The widget and activity use the same settings. */
public final class MatrixConfig {
    public static final int DEFAULT_MINUTES = 60;
    public static final String DEFAULT_COLOR = "#FFBD66";
    public final String address;
    public final String text;
    public final int minutes;
    public final String color;

    private MatrixConfig(String address, String text, int minutes, String color) {
        this.address = address;
        this.text = text;
        this.minutes = minutes;
        this.color = color;
    }

    public static MatrixConfig create(String address, String text, String minutes) {
        return create(address, text, minutes, DEFAULT_COLOR);
    }

    public static MatrixConfig create(String address, String text, String minutes, String color) {
        String normalized = normalizeAddress(address);
        String message = text == null ? "" : text.trim();
        if (message.isEmpty()) throw new ValidationException("message", "Enter the text to show on your matrix.");
        if (message.length() > 500) throw new ValidationException("message", "Keep your message to 500 characters or fewer.");
        int duration;
        try { duration = Integer.parseInt(minutes.trim()); }
        catch (Exception e) { throw new ValidationException("duration", "Enter a whole number of minutes (1–1440)."); }
        if (duration < 1 || duration > 1440) throw new ValidationException("duration", "Choose between 1 minute and 24 hours (1440 minutes).");
        return new MatrixConfig(normalized, message, duration, normalizeColor(color));
    }

    public static String normalizeColor(String value) {
        String hex = value == null ? "" : value.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}"))
            throw new ValidationException("color", "Enter six hex digits, for example #FFBD66.");
        return "#" + hex.toUpperCase(java.util.Locale.ROOT);
    }

    public static String normalizeAddress(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) throw new ValidationException("address", "Enter your matrix IP address, for example 192.168.1.50.");
        if (!raw.contains("://")) raw = "http://" + raw;
        try {
            URI uri = new URI(raw);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())) || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new URISyntaxException(raw, "Use a host and optional port only");
            }
            String host = uri.getHost();
            if (host.matches("[0-9.]+")) {
                String[] parts = host.split("\\.", -1);
                if (parts.length != 4) throw new URISyntaxException(raw, "Invalid IPv4 address");
                for (String part : parts) {
                    if (part.isEmpty() || part.length() > 3 || Integer.parseInt(part) > 255)
                        throw new URISyntaxException(raw, "Invalid IPv4 address");
                }
            }
            return new URI(uri.getScheme().toLowerCase(java.util.Locale.ROOT), null,
                    host.toLowerCase(java.util.Locale.ROOT), uri.getPort(), null, null, null).toString();
        } catch (URISyntaxException | NumberFormatException e) {
            throw new ValidationException("address", "Use an IP or hostname, optionally with http(s):// and a port. Leave out paths and passwords.");
        }
    }

    public int seconds() { return minutes * 60; }
    public static String durationLabel(int minutes) {
        if (minutes % 60 == 0) return (minutes / 60) + " h";
        if (minutes > 60) return (minutes / 60) + " h " + (minutes % 60) + " min";
        return minutes + " min";
    }

    public static final class ValidationException extends IllegalArgumentException {
        public final String field;
        ValidationException(String field, String message) { super(message); this.field = field; }
    }
}
