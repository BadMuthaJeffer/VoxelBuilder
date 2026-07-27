package com.voxelbuilder.client.voxel;

import com.voxelbuilder.client.voxel.STLAsciiLoader.Triangle;
import com.voxelbuilder.client.voxel.STLAsciiLoader.Vec3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * STL -> SURFACE voxels voxelizer (Printer Mode).
 *
 * Key behavior:
 * - Normalizes to min=(0,0,0)
 * - Scales so max dimension fits targetMaxDim
 * - Rasterizes TRIANGLE SURFACE onto voxel grid using triangle-vs-voxelAABB overlap
 *
 * This avoids "undersampling = empty" failures (e.g. Benchy thin details).
 * No solid fill. No watertight requirement.
 */
public final class STLVoxelizer {

    public static final class Voxel {
        public final int x, y, z;
        public Voxel(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    public static final class Result {
        public final List<Voxel> voxels;
        public final int sizeX, sizeY, sizeZ;
        public Result(List<Voxel> voxels, int sizeX, int sizeY, int sizeZ) {
            this.voxels = voxels; this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
        }
    }

    private STLVoxelizer() {}

    public static Result voxelizeSurface(List<Triangle> tris, int targetMaxDim) {
        if (tris == null || tris.isEmpty() || targetMaxDim <= 0) {
            return new Result(List.of(), 0, 0, 0);
        }

        // Bounds in model space
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        for (Triangle t : tris) {
            minX = Math.min(minX, Math.min(t.a.x, Math.min(t.b.x, t.c.x)));
            minY = Math.min(minY, Math.min(t.a.y, Math.min(t.b.y, t.c.y)));
            minZ = Math.min(minZ, Math.min(t.a.z, Math.min(t.b.z, t.c.z)));
            maxX = Math.max(maxX, Math.max(t.a.x, Math.max(t.b.x, t.c.x)));
            maxY = Math.max(maxY, Math.max(t.a.y, Math.max(t.b.y, t.c.y)));
            maxZ = Math.max(maxZ, Math.max(t.a.z, Math.max(t.b.z, t.c.z)));
        }

        float spanX = maxX - minX;
        float spanY = maxY - minY;
        float spanZ = maxZ - minZ;
        float maxSpan = Math.max(spanX, Math.max(spanY, spanZ));
        if (maxSpan <= 0.000001f) {
            return new Result(List.of(), 0, 0, 0);
        }

        // Scale model units -> voxel units so the max dimension fits (targetMaxDim - 1)
        float scale = (float) (targetMaxDim - 1) / maxSpan;

        // Packed voxel set (avoid duplicates)
        Set<Long> packed = new HashSet<>();

        // Track extents for resulting size
        int maxVX = 0, maxVY = 0, maxVZ = 0;

        // Triangle rasterization
        for (Triangle t : tris) {
            Vec3f av = toVoxelSpace(t.a, minX, minY, minZ, scale);
            Vec3f bv = toVoxelSpace(t.b, minX, minY, minZ, scale);
            Vec3f cv = toVoxelSpace(t.c, minX, minY, minZ, scale);

            float triMinX = Math.min(av.x, Math.min(bv.x, cv.x));
            float triMinY = Math.min(av.y, Math.min(bv.y, cv.y));
            float triMinZ = Math.min(av.z, Math.min(bv.z, cv.z));

            float triMaxX = Math.max(av.x, Math.max(bv.x, cv.x));
            float triMaxY = Math.max(av.y, Math.max(bv.y, cv.y));
            float triMaxZ = Math.max(av.z, Math.max(bv.z, cv.z));

            int x0 = clamp((int) Math.floor(triMinX), 0, targetMaxDim - 1);
            int y0 = clamp((int) Math.floor(triMinY), 0, targetMaxDim - 1);
            int z0 = clamp((int) Math.floor(triMinZ), 0, targetMaxDim - 1);

            int x1 = clamp((int) Math.floor(triMaxX), 0, targetMaxDim - 1);
            int y1 = clamp((int) Math.floor(triMaxY), 0, targetMaxDim - 1);
            int z1 = clamp((int) Math.floor(triMaxZ), 0, targetMaxDim - 1);

            // SAT triangle-box overlap expects box centered at (cx,cy,cz) with half-size (0.5,0.5,0.5)
            float[] v0 = new float[] { av.x, av.y, av.z };
            float[] v1 = new float[] { bv.x, bv.y, bv.z };
            float[] v2 = new float[] { cv.x, cv.y, cv.z };

            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {

                        float[] boxCenter = new float[] { x + 0.5f, y + 0.5f, z + 0.5f };
                        float[] boxHalf = new float[] { 0.5f, 0.5f, 0.5f };

                        if (triBoxOverlap(boxCenter, boxHalf, v0, v1, v2)) {
                            packed.add(pack(x, y, z));
                            if (x > maxVX) maxVX = x;
                            if (y > maxVY) maxVY = y;
                            if (z > maxVZ) maxVZ = z;
                        }
                    }
                }
            }
        }

        // Unpack
        java.util.ArrayList<Voxel> out = new java.util.ArrayList<>(packed.size());
        for (long key : packed) {
            int x = (int) (key & 0x1FFFFF);
            int y = (int) ((key >>> 21) & 0x1FFFFF);
            int z = (int) ((key >>> 42) & 0x1FFFFF);
            out.add(new Voxel(x, y, z));
        }

        return new Result(out, maxVX + 1, maxVY + 1, maxVZ + 1);
    }

    private static Vec3f toVoxelSpace(Vec3f v, float minX, float minY, float minZ, float scale) {
        return new Vec3f((v.x - minX) * scale, (v.y - minY) * scale, (v.z - minZ) * scale);
    }

    // pack x,y,z into 63-bit (each up to ~2 million, way above our needs)
    private static long pack(int x, int y, int z) {
        return ((long) x) | ((long) y << 21) | ((long) z << 42);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /* ============================================================
     * Triangle-box overlap test (SAT)
     *
     * Based on Akenine-Möller triBoxOverlap approach.
     * Inputs:
     * - boxCenter: center of AABB
     * - boxHalf: half sizes (0.5,0.5,0.5)
     * - v0,v1,v2: triangle vertices in same space
     * ============================================================ */

    private static boolean triBoxOverlap(float[] boxCenter, float[] boxHalf, float[] tv0, float[] tv1, float[] tv2) {
        // Move triangle into box's local space
        float[] v0 = new float[] { tv0[0] - boxCenter[0], tv0[1] - boxCenter[1], tv0[2] - boxCenter[2] };
        float[] v1 = new float[] { tv1[0] - boxCenter[0], tv1[1] - boxCenter[1], tv1[2] - boxCenter[2] };
        float[] v2 = new float[] { tv2[0] - boxCenter[0], tv2[1] - boxCenter[1], tv2[2] - boxCenter[2] };

        // Compute edges
        float[] e0 = new float[] { v1[0] - v0[0], v1[1] - v0[1], v1[2] - v0[2] };
        float[] e1 = new float[] { v2[0] - v1[0], v2[1] - v1[1], v2[2] - v1[2] };
        float[] e2 = new float[] { v0[0] - v2[0], v0[1] - v2[1], v0[2] - v2[2] };

        // 1) Test the 9 axes from cross products of triangle edges and the box axes
        if (!axisTestEdges(e0, v0, v1, v2, boxHalf)) return false;
        if (!axisTestEdges(e1, v0, v1, v2, boxHalf)) return false;
        if (!axisTestEdges(e2, v0, v1, v2, boxHalf)) return false;

        // 2) Test overlap in the {x,y,z}-directions (box face normals)
        if (!overlapOnAxis(v0[0], v1[0], v2[0], boxHalf[0])) return false;
        if (!overlapOnAxis(v0[1], v1[1], v2[1], boxHalf[1])) return false;
        if (!overlapOnAxis(v0[2], v1[2], v2[2], boxHalf[2])) return false;

        // 3) Test if the box intersects the plane of the triangle
        float[] normal = cross(e0, e1);
        if (!planeBoxOverlap(normal, v0, boxHalf)) return false;

        return true;
    }

    private static boolean overlapOnAxis(float a, float b, float c, float half) {
        float min = Math.min(a, Math.min(b, c));
        float max = Math.max(a, Math.max(b, c));
        return !(min > half || max < -half);
    }

    private static boolean axisTestEdges(float[] e, float[] v0, float[] v1, float[] v2, float[] half) {
        // Axes: e x (1,0,0), e x (0,1,0), e x (0,0,1)
        // Which simplifies to:
        // axisX = (0, -e.z, e.y)
        // axisY = (e.z, 0, -e.x)
        // axisZ = (-e.y, e.x, 0)
        float ex = e[0], ey = e[1], ez = e[2];

        // axisX
        if (!axisTest(0f, -ez, ey, v0, v1, v2, half)) return false;
        // axisY
        if (!axisTest(ez, 0f, -ex, v0, v1, v2, half)) return false;
        // axisZ
        if (!axisTest(-ey, ex, 0f, v0, v1, v2, half)) return false;

        return true;
    }

    private static boolean axisTest(float ax, float ay, float az, float[] v0, float[] v1, float[] v2, float[] half) {
        // If axis length is ~0, skip (degenerate)
        float len2 = ax * ax + ay * ay + az * az;
        if (len2 < 1e-12f) return true;

        float p0 = ax * v0[0] + ay * v0[1] + az * v0[2];
        float p1 = ax * v1[0] + ay * v1[1] + az * v1[2];
        float p2 = ax * v2[0] + ay * v2[1] + az * v2[2];

        float min = Math.min(p0, Math.min(p1, p2));
        float max = Math.max(p0, Math.max(p1, p2));

        // Project box half extents onto axis
        float r = half[0] * Math.abs(ax) + half[1] * Math.abs(ay) + half[2] * Math.abs(az);

        return !(min > r || max < -r);
    }

    private static boolean planeBoxOverlap(float[] normal, float[] vert, float[] half) {
        // Compute projection interval radius of box onto normal
        float r = half[0] * Math.abs(normal[0]) + half[1] * Math.abs(normal[1]) + half[2] * Math.abs(normal[2]);
        float s = normal[0] * vert[0] + normal[1] * vert[1] + normal[2] * vert[2];
        return Math.abs(s) <= r;
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }
}
