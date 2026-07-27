package com.voxelbuilder.client.model;

import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ModelFileManager {

    private static final String MODELS_DIR = "voxelbuilder/models";

    public static Path getModelsDirectory() {
        return Minecraft.getInstance()
                .gameDirectory
                .toPath()
                .resolve(MODELS_DIR);
    }

    public static List<String> getModelFiles() {
        Path dir = getModelsDirectory();

        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}

        List<String> files = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.csv")) {
            for (Path path : stream) {
                files.add(path.getFileName().toString());
            }
        } catch (IOException ignored) {}

        files.sort(String::compareTo);
        return files;
    }

    /** STEP 5: read CSV metadata */
    public static ModelMetadata readMetadata(String fileName) {
        Path file = getModelsDirectory().resolve(fileName);

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int count = 0;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split(",");
                if (parts.length < 3) continue;

                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);

                count++;
            }
        } catch (Exception e) {
            return null;
        }

        if (count == 0) return null;

        return new ModelMetadata(
                maxX - minX + 1,
                maxY - minY + 1,
                maxZ - minZ + 1,
                count
        );
    }

    /** Simple data holder */
    public record ModelMetadata(int width, int height, int depth, int voxelCount) {}
}
