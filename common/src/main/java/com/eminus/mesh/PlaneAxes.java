package com.eminus.mesh;

import net.minecraft.core.Direction;

public final class PlaneAxes {
    public static Direction.Axis width(Direction.Axis normal) {
        return normal == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    public static Direction.Axis height(Direction.Axis normal) {
        return normal == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    public static int x(Direction.Axis normal, int u, int v, int plane) {
        return along(Direction.Axis.X, normal, u, v, plane);
    }

    public static int y(Direction.Axis normal, int u, int v, int plane) {
        return along(Direction.Axis.Y, normal, u, v, plane);
    }

    public static int z(Direction.Axis normal, int u, int v, int plane) {
        return along(Direction.Axis.Z, normal, u, v, plane);
    }

    private static int along(Direction.Axis axis, Direction.Axis normal, int u, int v, int plane) {
        if (axis == normal) {
            return plane;
        }

        return axis == width(normal) ? u : v;
    }

    private PlaneAxes() {
    }
}
