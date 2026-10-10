package com.eminus.api.v1;

import java.util.List;

/**
 * A group of boxes registered through {@link EminusApi#registerBoxes}. The far layer draws it in its dimension until
 * {@link #remove} is called or the client leaves the world; after either, the group is gone and its methods do
 * nothing.
 */
public interface FarBoxGroup {
    /**
     * The dimension the group is drawn in, as its identifier.
     */
    String dimension();

    /**
     * Replaces the group's boxes, which are drawn from the next frame on. Call on any thread.
     *
     * @throws NullPointerException when the list or one of its boxes is {@code null}
     */
    void update(List<FarBox> boxes);

    /**
     * Removes the group, which is no longer drawn from the next frame on. Call on any thread.
     */
    void remove();
}
