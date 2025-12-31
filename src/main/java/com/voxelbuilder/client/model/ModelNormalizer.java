package com.voxelbuilder.client.model;

import com.voxelbuilder.client.voxel.STLAsciiLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * ModelNormalizer
 *
 * SINGLE RESPONSIBILITY:
 * - Take arbitrary triangle data
 * - Produce a clean, deterministic, normalized model
 *
 * NO voxel logic
 * NO rendering logic
 * NO file IO (yet)
 *
 * This is a foundation class. Keep it boring.
 */
public final class ModelNormalizer {

    private ModelNormalizer() {}

    /**
     * Normalized immutable model container.
     */
    public static final class NormalizedModel {
        public final List<STLAsciiLoader.Triangle> triangles;
        public final float sizeX;
        public final float sizeY;
        public final float sizeZ;

        public NormalizedModel(
                List<STLAsciiLoader.Triangle> triangles,
                float sizeX,
                float sizeY,
                float sizeZ
        ) {
            this.triangles = triangles;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }
    }

    /**
     * Normalize triangles to:
     * - origin at (0,0,0)
     * - uniform scale so max dimension == targetSize
     */
    public static NormalizedModel normalize(
            List<STLAsciiLoader.Triangle> input,
            float targetSize
    ) {
        if (input == null || input.isEmpty()) {
            return new NormalizedModel(List.of(), 0, 0, 0);
        }

        // --- Compute bounds ---
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (STLAsciiLoader.Triangle t : input) {
            minX = Math.min(minX, Math.min(t.a.x, Math.min(t.b.x, t.c.x)));
            minY = Math.min(minY, Math.min(t.a.y, Math.min(t.b.y, t.c.y)));
            minZ = Math.min(minZ, Math.min(t.a.z, Math.min(t.b.z, t.c.z)));
            maxX = Math.max(maxX, Math.max(t.a.x, Math.max(t.b.x, t.c.x)));
            maxY = Math.max(maxY, Math.max(t.a.y, Math.max(t.b.y, t.c.y)));
            maxZ = Math.max(maxZ, Math.max(t.a.z, Math.max(t.b.z, t.c.z)));
        }

        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;

        float maxDim = Math.max(sizeX, Math.max(sizeY, sizeZ));
        if (maxDim <= 0.000001f) {
            return new NormalizedModel(List.of(), 0, 0, 0);
        }

        float scale = targetSize / maxDim;

        // --- Normalize triangles ---
        List<STLAsciiLoader.Triangle> out = new ArrayList<>(input.size());

        for (STLAsciiLoader.Triangle t : input) {
            out.add(new STLAsciiLoader.Triangle(
                    new STLAsciiLoader.Vec3f(
                            (t.a.x - minX) * scale,
                            (t.a.y - minY) * scale,
                            (t.a.z - minZ) * scale
                    ),
                    new STLAsciiLoader.Vec3f(
                            (t.b.x - minX) * scale,
                            (t.b.y - minY) * scale,
                            (t.b.z - minZ) * scale
                    ),
                    new STLAsciiLoader.Vec3f(
                            (t.c.x - minX) * scale,
                            (t.c.y - minY) * scale,
                            (t.c.z - minZ) * scale
                    )
            ));
        }

        return new NormalizedModel(
                out,
                sizeX * scale,
                sizeY * scale,
                sizeZ * scale
        );
    }
}
