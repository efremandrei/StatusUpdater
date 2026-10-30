package com.efremandrei.statusupdater;

import org.junit.Test;
import static org.junit.Assert.*;

public class MatrixConfigTest {
    @Test public void defaultDurationIsOneHour() { assertEquals(60, MatrixConfig.DEFAULT_MINUTES); assertEquals(3600, MatrixConfig.create("192.168.1.50", "Meeting", "60").seconds()); }
    @Test public void normalizesHostAndPort() {
        assertEquals("http://192.168.1.50", MatrixConfig.normalizeAddress(" 192.168.1.50/ "));
        assertEquals("http://awtrix.local:8080", MatrixConfig.normalizeAddress("awtrix.local:8080"));
        assertEquals("https://awtrix.local", MatrixConfig.normalizeAddress("https://AWTRIX.local/"));
        assertEquals("http://[::1]:8080", MatrixConfig.normalizeAddress("http://[::1]:8080"));
    }
    @Test public void rejectsDangerousOrMistypedAddresses() {
        String[] invalid = {"", "999.168.1.1", "192.168.1", "ftp://awtrix.local", "http://user:password@awtrix.local", "http://awtrix.local/api/notify", "http://awtrix.local?x=y", "http://awtrix.local#x", "http://awtrix.local:65536", "awtrix.local:0", "192.168.1.4:-1", "http://my matrix"};
        for (String value : invalid) assertThrows(value, MatrixConfig.ValidationException.class, () -> MatrixConfig.normalizeAddress(value));
    }
    @Test public void rejectsInvalidDurationsAndEmptyMessage() {
        for (String value : new String[]{"0", "-1", "1441", "1.5", "", "9999999999"})
            assertThrows(MatrixConfig.ValidationException.class, () -> MatrixConfig.create("192.168.1.50", "Meeting", value));
        assertThrows(MatrixConfig.ValidationException.class, () -> MatrixConfig.create("192.168.1.50", "  ", "60"));
        assertThrows(MatrixConfig.ValidationException.class, () -> MatrixConfig.create("192.168.1.50", "A".repeat(501), "60"));
        assertEquals(86400, MatrixConfig.create("192.168.1.50", "Valid", "1440").seconds());
    }
    @Test public void preservesUnicodeAndQuotes() { assertEquals("פגישה \"Meeting\"\nBack soon ☕", MatrixConfig.create("awtrix.local", " פגישה \"Meeting\"\nBack soon ☕ ", "60").text); }
    @Test public void defaultsToAmberAndNormalizesRgbHexColors() {
        assertEquals("#FFBD66", MatrixConfig.create("awtrix.local", "Legacy", "60").color);
        assertEquals("#12ABEF", MatrixConfig.create("awtrix.local", "Custom", "60", " 12abef ").color);
        assertEquals("#000000", MatrixConfig.normalizeColor("#000000"));
        assertEquals("#FFFFFF", MatrixConfig.normalizeColor("#ffffff"));
    }
    @Test public void rejectsMalformedAndAlphaColors() {
        for (String color : new String[]{null, "", "#FFF", "#12345", "#12345678", "12345G", "red", "##123456"}) {
            MatrixConfig.ValidationException error = assertThrows(MatrixConfig.ValidationException.class,
                    () -> MatrixConfig.create("awtrix.local", "Color", "60", color));
            assertEquals("color", error.field);
        }
    }
}
