package com.voxelbuilder.client.voxel;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * STL loader supporting BOTH:
 * - ASCII STL
 * - Binary STL
 *
 * Auto-detects format based on file header.
 *
 * Public API is unchanged to avoid regressions.
 */
public final class STLAsciiLoader {

    public static final class Vec3f {
        public final float x, y, z;
        public Vec3f(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class Triangle {
        public final Vec3f a, b, c;
        public Triangle(Vec3f a, Vec3f b, Vec3f c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    private STLAsciiLoader() {}

    /**
     * Entry point used everywhere else in the mod.
     * Automatically handles ASCII or Binary STL.
     */
    public static List<Triangle> load(File stlFile) throws Exception {
        if (isBinarySTL(stlFile)) {
            return loadBinary(stlFile);
        } else {
            return loadAscii(stlFile);
        }
    }

    // =========================
    // ASCII STL
    // =========================

    private static List<Triangle> loadAscii(File stlFile) throws Exception {
        List<Triangle> tris = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(stlFile))) {
            String line;
            Vec3f[] v = new Vec3f[3];
            int vi = 0;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("vertex")) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 4) {
                        float x = Float.parseFloat(p[1]);
                        float y = Float.parseFloat(p[2]);
                        float z = Float.parseFloat(p[3]);
                        v[vi++] = new Vec3f(x, y, z);
                        if (vi == 3) {
                            tris.add(new Triangle(v[0], v[1], v[2]));
                            vi = 0;
                        }
                    }
                }
            }
        }

        return tris;
    }

    // =========================
    // Binary STL
    // =========================

    private static List<Triangle> loadBinary(File stlFile) throws Exception {
        List<Triangle> tris = new ArrayList<>();

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(stlFile)))) {

            // 80-byte header (ignored)
            byte[] header = new byte[80];
            in.readFully(header);

            // Triangle count (little-endian uint32)
            int triCount = Integer.reverseBytes(in.readInt());

            byte[] triBuf = new byte[50]; // 12 floats + 2-byte attr

            for (int i = 0; i < triCount; i++) {
                in.readFully(triBuf);
                ByteBuffer bb = ByteBuffer.wrap(triBuf).order(ByteOrder.LITTLE_ENDIAN);

                // Skip normal
                bb.getFloat();
                bb.getFloat();
                bb.getFloat();

                Vec3f a = new Vec3f(bb.getFloat(), bb.getFloat(), bb.getFloat());
                Vec3f b = new Vec3f(bb.getFloat(), bb.getFloat(), bb.getFloat());
                Vec3f c = new Vec3f(bb.getFloat(), bb.getFloat(), bb.getFloat());

                tris.add(new Triangle(a, b, c));
                // attribute bytes ignored
            }
        }

        return tris;
    }

    // =========================
    // Format detection
    // =========================

    /**
     * Heuristic STL format detection.
     * Binary STLs may start with "solid", so size check is required.
     */
    private static boolean isBinarySTL(File file) throws IOException {
        if (file.length() < 84) return false;

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[80];
            fis.read(header);

            String headerStr = new String(header).trim().toLowerCase();
            if (!headerStr.startsWith("solid")) {
                return true;
            }

            // If it starts with "solid" but file size matches binary structure, treat as binary
            long fileSize = file.length();
            fis.skip(4);
            byte[] countBuf = new byte[4];
            fis.read(countBuf);
            int triCount = ByteBuffer.wrap(countBuf)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();

            long expectedSize = 84L + (50L * triCount);
            return fileSize == expectedSize;
        }
    }
}
