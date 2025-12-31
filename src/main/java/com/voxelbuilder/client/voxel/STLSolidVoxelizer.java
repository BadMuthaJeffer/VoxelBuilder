package com.voxelbuilder.client.voxel;

import com.voxelbuilder.client.voxel.STLAsciiLoader.Triangle;
import com.voxelbuilder.client.voxel.STLAsciiLoader.Vec3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Solid-fill voxelizer using ray casting.
 *
 * Used as a fallback when surface sampling fails
 * (e.g. Benchy, thin walls, non-watertight meshes).
 */
public final class STLSolidVoxelizer {

    private STLSolidVoxelizer() {}

    public static STLVoxelizer.Result voxelizeSolid(
            List<Triangle> tris,
            int gridSize
    ) {
        if (tris.isEmpty()) {
            return new STLVoxelizer.Result(List.of(), 0, 0, 0);
        }

        boolean[][][] grid = new boolean[gridSize][gridSize][gridSize];

        // For each XZ column, cast a ray along Y
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {

                List<Float> intersections = new ArrayList<>();

                float rx = x + 0.5f;
                float rz = z + 0.5f;

                for (Triangle t : tris) {
                    Float hitY = rayIntersectTriangle(rx, rz, t);
                    if (hitY != null) {
                        intersections.add(hitY);
                    }
                }

                intersections.sort(Float::compare);

                // Fill between intersection pairs
                for (int i = 0; i + 1 < intersections.size(); i += 2) {
                    int y0 = clamp((int)Math.floor(intersections.get(i)), 0, gridSize - 1);
                    int y1 = clamp((int)Math.ceil(intersections.get(i + 1)), 0, gridSize - 1);

                    for (int y = y0; y <= y1; y++) {
                        grid[x][y][z] = true;
                    }
                }
            }
        }

        List<STLVoxelizer.Voxel> out = new ArrayList<>();
        int maxX = 0, maxY = 0, maxZ = 0;

        for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
                for (int z = 0; z < gridSize; z++) {
                    if (grid[x][y][z]) {
                        out.add(new STLVoxelizer.Voxel(x, y, z));
                        if (x > maxX) maxX = x;
                        if (y > maxY) maxY = y;
                        if (z > maxZ) maxZ = z;
                    }
                }
            }
        }

        return new STLVoxelizer.Result(out, maxX + 1, maxY + 1, maxZ + 1);
    }

    /**
     * Ray cast along +Y through a triangle.
     * Returns Y of intersection or null.
     */
    private static Float rayIntersectTriangle(float x, float z, Triangle t) {
        Vec3f a = t.a;
        Vec3f b = t.b;
        Vec3f c = t.c;

        // Project triangle onto XZ plane
        float denom =
                (b.z - c.z) * (a.x - c.x) +
                (c.x - b.x) * (a.z - c.z);

        if (Math.abs(denom) < 1e-6f) return null;

        float u =
                ((b.z - c.z) * (x - c.x) +
                 (c.x - b.x) * (z - c.z)) / denom;

        float v =
                ((c.z - a.z) * (x - c.x) +
                 (a.x - c.x) * (z - c.z)) / denom;

        float w = 1f - u - v;

        if (u < 0 || v < 0 || w < 0) return null;

        return u * a.y + v * b.y + w * c.y;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
