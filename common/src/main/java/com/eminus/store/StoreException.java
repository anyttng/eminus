package com.eminus.store;

import org.jspecify.annotations.Nullable;

public final class StoreException extends RuntimeException {
    public StoreException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
