package com.example.shop.util;

/** The Luhn checksum every real card number satisfies; catches typos before any payment is attempted. */
public final class LuhnValidator {
    private LuhnValidator() {
    }

    public static boolean isValid(String digits) {
        if (digits == null || digits.length() < 12 || digits.length() > 19) return false;
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') return false;
            int d = c - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
