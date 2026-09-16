package com.eminus.work;

@FunctionalInterface
public interface Job<C> {
    void run(C scratch);
}
