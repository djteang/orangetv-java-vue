package com.orangetv.util;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Bitcoin Base58 decoder.
 * Alphabet: 123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz
 */
public class Base58 {

    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(58);
    private static final int[] INDEXES = new int[128];

    static {
        Arrays.fill(INDEXES, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            INDEXES[ALPHABET.charAt(i)] = i;
        }
    }

    private Base58() {}

    public static byte[] decode(String input) {
        if (input == null || input.isEmpty()) {
            return new byte[0];
        }

        BigInteger num = BigInteger.ZERO;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 128 || INDEXES[c] == -1) {
                throw new IllegalArgumentException("Invalid Base58 character: " + c);
            }
            num = num.multiply(BASE).add(BigInteger.valueOf(INDEXES[c]));
        }

        byte[] bytes = num.toByteArray();

        // Remove leading zero byte from BigInteger's two's complement if present
        boolean stripSign = bytes.length > 1 && bytes[0] == 0;

        // Count leading '1' characters (they represent leading zero bytes)
        int leadingZeros = 0;
        for (int i = 0; i < input.length() && input.charAt(i) == '1'; i++) {
            leadingZeros++;
        }

        byte[] result = new byte[leadingZeros + bytes.length - (stripSign ? 1 : 0)];
        System.arraycopy(bytes, stripSign ? 1 : 0, result, leadingZeros, bytes.length - (stripSign ? 1 : 0));
        return result;
    }

    public static String decodeToString(String input) {
        return new String(decode(input), java.nio.charset.StandardCharsets.UTF_8);
    }
}
