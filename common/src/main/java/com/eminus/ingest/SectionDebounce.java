package com.eminus.ingest;

import java.util.function.LongConsumer;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public final class SectionDebounce {
    public static final long WINDOW_MILLIS = 500L;

    private final long windowMillis;
    private final Long2LongMap marked = new Long2LongOpenHashMap();

    public SectionDebounce(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public void mark(long sectionNode, long now) {
        marked.put(sectionNode, now);
    }

    public void drain(long now, LongConsumer ready) {
        ObjectIterator<Long2LongMap.Entry> entries = marked.long2LongEntrySet().iterator();

        while (entries.hasNext()) {
            Long2LongMap.Entry entry = entries.next();
            if (now - entry.getLongValue() < windowMillis) {
                continue;
            }

            long sectionNode = entry.getLongKey();
            entries.remove();
            ready.accept(sectionNode);
        }
    }
}
