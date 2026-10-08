package com.eminus.gpu;

public record Location(String namespace, String path) {
    private static final char SEPARATOR = ':';

    public Location {
        if (namespace.isEmpty() || path.isEmpty()) {
            throw new IllegalArgumentException("Location " + namespace + SEPARATOR + path + " has an empty part");
        }
    }

    public static Location parse(String text) {
        int separator = text.indexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("Location " + text + " names no namespace");
        }
        return new Location(text.substring(0, separator), text.substring(separator + 1));
    }

    public Location withPrefix(String prefix) {
        return new Location(namespace, prefix + path);
    }

    public Location withSuffix(String suffix) {
        return new Location(namespace, path + suffix);
    }

    @Override
    public String toString() {
        return namespace + SEPARATOR + path;
    }
}
