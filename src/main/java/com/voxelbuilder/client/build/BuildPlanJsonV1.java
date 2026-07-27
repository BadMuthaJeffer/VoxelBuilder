package com.voxelbuilder.client.build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * BuildPlan JSON schema v1.
 *
 * This is a local disk format (NOT networking). It is meant to be stable and versioned.
 *
 * Notes:
 * - Coordinates in blocks[] are the BuildPlan's relative positions (as rendered / built).
 * - Anchor is NOT stored here; you can confirm/build at a new anchor later.
 */
public final class BuildPlanJsonV1 {

    public static final int SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private BuildPlanJsonV1() {}

    public static Path defaultPlansDir() {
        // Uses the instance game directory (works in dev + production).
        Path base = Minecraft.getInstance().gameDirectory.toPath();
        return base.resolve("voxelbuilder").resolve("plans");
    }

    public static Path defaultLastPlanPath() {
        return defaultPlansDir().resolve("last_plan_v1.json");
    }

    public static void write(Path file, BuildPlan plan, ResourceLocation blockId) throws IOException {
        if (plan == null) throw new IOException("No BuildPlan to write.");
        if (blockId == null) blockId = ResourceLocation.fromNamespaceAndPath("minecraft", "stone");

        Root root = new Root();
        root.schemaVersion = SCHEMA_VERSION;
        root.blockId = blockId.toString();
        root.sizeX = plan.getSizeX();
        root.sizeY = plan.getSizeY();
        root.sizeZ = plan.getSizeZ();

        root.blocks = new ArrayList<>(plan.getBlocks().size());
        for (BuildPlan.BlockPos3 p : plan.getBlocks()) {
            int[] a = new int[3];
            a[0] = p.x;
            a[1] = p.y;
            a[2] = p.z;
            root.blocks.add(a);
        }

        Files.createDirectories(file.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        }
    }

    public static Loaded read(Path file) throws IOException {
        if (!Files.exists(file)) throw new IOException("Plan file not found: " + file);
        Root root;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = GSON.fromJson(r, Root.class);
        } catch (JsonParseException e) {
            throw new IOException("Invalid JSON: " + e.getMessage(), e);
        }

        if (root == null) throw new IOException("Empty JSON.");
        if (root.schemaVersion != SCHEMA_VERSION) {
            throw new IOException("Unsupported schemaVersion: " + root.schemaVersion);
        }
        if (root.blocks == null) root.blocks = new ArrayList<>();

        List<BuildPlan.BlockPos3> blocks = new ArrayList<>(root.blocks.size());
        for (int[] a : root.blocks) {
            if (a == null || a.length < 3) continue;
            blocks.add(new BuildPlan.BlockPos3(a[0], a[1], a[2]));
        }

        BuildPlan plan = new BuildPlan(blocks, root.sizeX, root.sizeY, root.sizeZ);
        ResourceLocation blockId;
        try {
            blockId = ResourceLocation.tryParse(root.blockId);
        } catch (Throwable t) {
            blockId = null;
        }

        Loaded loaded = new Loaded();
        loaded.plan = plan;
        loaded.blockId = blockId;
        return loaded;
    }

    public static final class Loaded {
        public BuildPlan plan;
        public ResourceLocation blockId;
    }

    private static final class Root {
        int schemaVersion;
        String blockId;
        int sizeX;
        int sizeY;
        int sizeZ;
        List<int[]> blocks;
    }
}
