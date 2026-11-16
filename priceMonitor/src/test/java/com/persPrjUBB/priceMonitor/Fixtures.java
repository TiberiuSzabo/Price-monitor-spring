package com.persPrjUBB.priceMonitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Saved HTML pages under src/test/resources/fixtures, so no test ever calls eMAG. */
final class Fixtures {

    private Fixtures() {
    }

    static String html(String name) {
        String path = "/fixtures/" + name;
        try (InputStream in = Fixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture at " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
