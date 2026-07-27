package com.voxelbuilder.client.voxel;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified STL loader.
 *
 * - Supports ASCII and Binary STL
 * - Always returns triangles or throws
 * - Canonical triangle type: STLAsciiLoader.Triangle
 *
 * Printer-mode safe.
 */
public final class STLLoader {

    private STLLoader() {}

    public static List<STLAsciiLoader.Triangle> load(File file) throws Exception {
        if (isBinarySTL(file)) {
            return loadBinary(file);
        } else {
            return STLAsciiLoader.load(file);
        }
    }

    /* =========================================================
     * Binary STL
     * ========================================================= */

    private static List<STLAsciiLoader.Triangle> loadBinary(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {

            // Skip 80-byte header
            in.skipNBytes(80);

            byte[] countBytes = in.readNBytes(4);
            ByteBuffer countBuf = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN);
            int triCount = countBuf.getInt();

            List<STLAsciiLoader.Triangle> tris = new ArrayList<>(triCount);

            byte[] triBuf = new byte[50]; // one triangle record

            for (int i = 0; i < triCount; i++) {
                if (in.read(triBuf) != 50) break;

                ByteBuffer b = ByteBuffer.wrap(triBuf).order(ByteOrder.LITTLE_ENDIAN);

                // Skip normal (3 floats)
                b.getFloat(); b.getFloat(); b.getFloat();

                // Vertex A
                float ax = b.getFloat();
                float ay = b.getFloat();
                float az = b.getFloat();

                // Vertex B
                float bx = b.getFloat();
                float by = b.getFloat();
                float bz = b.getFloat();

                // Vertex C
                float cx = b.getFloat();
                float cy = b.getFloat();
                float cz = b.getFloat();

                // Skip attribute byte count
                b.getShort();

                tris.add(new STLAsciiLoader.Triangle(
                        new STLAsciiLoader.Vec3f(ax, ay, az),
                        new STLAsciiLoader.Vec3f(bx, by, bz),
                        new STLAsciiLoader.Vec3f(cx, cy, cz)
                ));
            }

            return tris;
        }
    }

    /* =========================================================
     * Detection
     * ========================================================= */

    private static boolean isBinarySTL(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = in.readNBytes(80);
            byte[] countBytes = in.readNBytes(4);
            if (countBytes.length < 4) return false;

            ByteBuffer b = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN);
            long triCount = Integer.toUnsignedLong(b.getInt());

            long expectedSize = 84L + triCount * 50L;
            return expectedSize == file.length();
        } catch (Exception e) {
            return false;
        }
    }
}
