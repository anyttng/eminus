package com.eminus.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record WorldIdentity(String world, long seed, String dimension) {
    public static final int FOLDER_NAME_LENGTH = 16;

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String HASH_SEPARATOR = ":";

    public String folderName() {
        byte[] hash = hash(seed + HASH_SEPARATOR + dimension);
        return HexFormat.of().formatHex(hash, 0, FOLDER_NAME_LENGTH / 2);
    }

    private static byte[] hash(String source) {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM).digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(HASH_ALGORITHM + " is missing from this Java runtime.", missing);
        }
    }
}
