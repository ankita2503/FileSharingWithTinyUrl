package dev.system.tinyurl.shortener;

import java.util.Arrays;

public final class Base62Codec {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    private static final int[] INDEX = new int[128];
    static {
        Arrays.fill(INDEX, -1);
        for (int i = 0; i < BASE; i++) {
            INDEX[ALPHABET.charAt(i)] = i;
        }
    }

    private Base62Codec() {}

    public static String encode(long n) {
        if (n < 0) throw new IllegalArgumentException("negative: " + n);
        if (n == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            sb.append(ALPHABET.charAt((int) (n % BASE)));
            n /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String key) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("empty key");
        long result = 0;
        for (char c : key.toCharArray()) {
            int index = c < 128 ? INDEX[c] : -1;
            if (index < 0) throw new IllegalArgumentException("invalid char: " + c);
            result = result * BASE + index;
        }
        return result;
    }
}