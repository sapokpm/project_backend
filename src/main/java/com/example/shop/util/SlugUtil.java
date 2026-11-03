package com.example.shop.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

public final class SlugUtil {
    private SlugUtil() {
    }

    public static String slugify(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return s.isEmpty() ? "item" : s;
    }

    /** slugify, then append -2, -3 ... until the slug is not taken. */
    public static String uniqueSlug(String text, Predicate<String> exists) {
        String base = slugify(text);
        String candidate = base;
        for (int i = 2; exists.test(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }
}
