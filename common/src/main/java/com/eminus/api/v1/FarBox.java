package com.eminus.api.v1;

/**
 * One axis-aligned box a mod hands the far layer, in world coordinates. The constructor throws {@link
 * IllegalArgumentException} when a minimum exceeds its maximum, or a coordinate is not finite or lies outside the
 * range of an {@code int}.
 *
 * @param minX     the west face, in blocks
 * @param minY     the bottom face, in blocks
 * @param minZ     the north face, in blocks
 * @param maxX     the east face, in blocks, no less than {@code minX}
 * @param maxY     the top face, in blocks, no less than {@code minY}
 * @param maxZ     the south face, in blocks, no less than {@code minZ}
 * @param argb     the colour as {@code 0xAARRGGBB}; an alpha below {@code 0xFF} blends the box over what lies behind
 *                 it
 * @param emissive whether the box keeps its colour in the dark; otherwise it is lit as daylight lights the open
 *                 terrain, so it dims at night
 */
public record FarBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int argb,
        boolean emissive) {
    public FarBox {
        if (!(minX <= maxX && minY <= maxY && minZ <= maxZ)) {
            throw new IllegalArgumentException("A box's minimum exceeds its maximum: " + bounds(minX, minY, minZ,
                    maxX, maxY, maxZ));
        }
        if (!inRange(minX) || !inRange(minY) || !inRange(minZ) || !inRange(maxX) || !inRange(maxY)
                || !inRange(maxZ)) {
            throw new IllegalArgumentException("A box's coordinate lies outside the range of an int: "
                    + bounds(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    private static boolean inRange(double coordinate) {
        return coordinate >= Integer.MIN_VALUE && coordinate < Integer.MAX_VALUE;
    }

    private static String bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return "(" + minX + ", " + minY + ", " + minZ + ") to (" + maxX + ", " + maxY + ", " + maxZ + ")";
    }
}
