package com.voxelbuilder.client.screen;

import com.voxelbuilder.client.build.BuildPlan;
import com.voxelbuilder.client.model.ModelNormalizer;
import com.voxelbuilder.client.render.GhostPreviewDebugRenderer;
import com.voxelbuilder.client.voxel.STLAsciiLoader;
import com.voxelbuilder.client.voxel.STLLoader;
import com.voxelbuilder.client.voxel.STLVoxelizer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 * Voxel Builder Controller Screen
 *
 * PRINTER MODE LOCKED:
 *   STL -> STLLoader
 *       -> ModelNormalizer
 *       -> STLVoxelizer (surface only)
 */
public class VoxelBuilderControllerScreen extends Screen {

    /* =========================
     * Enums
     * ========================= */

    private enum Tab { MODELS, PREVIEW, BUILD, SETTINGS }
    private enum OriginMode { CORNER, CENTER }
    private enum ModelType { STL_FILE, CSV_FILE, CSV_FOLDER }

    private static final class ModelEntry {
        final ModelType type;
        final String displayName;
        final File file;

        ModelEntry(ModelType type, String displayName, File file) {
            this.type = type;
            this.displayName = displayName;
            this.file = file;
        }
    }

    /* =========================
     * State
     * ========================= */

    private Tab activeTab = Tab.MODELS;

    private int panelX;
    private int panelY;

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 180;

    private final List<ModelEntry> modelEntries = new ArrayList<>();
    private int selectedIndex = -1;

    /* =========================
     * Buttons
     * ========================= */

    private Button refreshButton;
    private Button rotateButton;
    private Button originButton;
    private Button hollowButton;
    private Button placeBuildButton;
    private Button confirmButton;
    private Button cancelButton;

    /* =========================
     * Transform
     * ========================= */

    private int rotation = 0;
    private OriginMode originMode = OriginMode.CORNER;
    private boolean hollow = false;

    /* =========================
     * Metadata
     * ========================= */

    private boolean metadataValid = false;
    private int voxelCount = 0;
    private int sizeX = 0, sizeY = 0, sizeZ = 0;

    /* =========================
     * Voxel cache
     * ========================= */

    private static class Voxel {
        final int x, y, z;
        Voxel(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    private final List<Voxel> voxelCache = new ArrayList<>();
    private final List<Voxel> orderedBuildPlan = new ArrayList<>();
    private BuildPlan currentBuildPlan = null;

    public VoxelBuilderControllerScreen() {
        super(Component.literal("Voxel Builder Controller"));
    }

    /* =========================
     * Init
     * ========================= */

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        int tabY = panelY - 24;
        int tabW = 60;
        int tabH = 20;
        int gap = 5;

        int startX = panelX + (PANEL_WIDTH - (tabW * 4 + gap * 3)) / 2;

        addRenderableWidget(Button.builder(Component.literal("Models"), b -> activeTab = Tab.MODELS)
                .bounds(startX, tabY, tabW, tabH).build());
        addRenderableWidget(Button.builder(Component.literal("Preview"), b -> activeTab = Tab.PREVIEW)
                .bounds(startX + (tabW + gap), tabY, tabW, tabH).build());
        addRenderableWidget(Button.builder(Component.literal("Build"), b -> activeTab = Tab.BUILD)
                .bounds(startX + 2 * (tabW + gap), tabY, tabW, tabH).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> activeTab = Tab.SETTINGS)
                .bounds(startX + 3 * (tabW + gap), tabY, tabW, tabH).build());

        refreshButton = addRenderableWidget(
                Button.builder(Component.literal("Refresh"), b -> loadModels())
                        .bounds(panelX + PANEL_WIDTH - 68, panelY + 6, 60, 16)
                        .build()
        );

        int btnY = panelY + PANEL_HEIGHT - 22;
        int btnW = 60;
        int btnH = 16;
        int btnX = panelX + 10;

        rotateButton = addRenderableWidget(
                Button.builder(Component.literal("Rotate"), b -> {
                    rotation = (rotation + 90) % 360;
                    rebuildBuildPlan();
                }).bounds(btnX, btnY, btnW, btnH).build()
        );

        originButton = addRenderableWidget(
                Button.builder(Component.literal("Origin"), b -> {
                    originMode = (originMode == OriginMode.CORNER ? OriginMode.CENTER : OriginMode.CORNER);
                    rebuildBuildPlan();
                }).bounds(btnX + btnW + 6, btnY, btnW, btnH).build()
        );

        hollowButton = addRenderableWidget(
                Button.builder(Component.literal("Hollow"), b -> {
                    hollow = !hollow;
                    rebuildBuildPlan();
                }).bounds(btnX + (btnW + 6) * 2, btnY, btnW, btnH).build()
        );

        placeBuildButton = addRenderableWidget(
                Button.builder(Component.literal("Place Build"), b -> {
                    GhostPreviewDebugRenderer.armPlacement();
                    minecraft.setScreen(null);
                }).bounds(btnX + (btnW + 6) * 3, btnY, 80, btnH).build()
        );

        confirmButton = addRenderableWidget(
                Button.builder(Component.literal("Confirm"), b -> minecraft.setScreen(null))
                        .bounds(btnX, btnY - 20, 60, 16).build()
        );

        cancelButton = addRenderableWidget(
                Button.builder(Component.literal("Cancel"), b -> minecraft.setScreen(this))
                        .bounds(btnX + 66, btnY - 20, 60, 16).build()
        );

        loadModels();
    }

    /* =========================
     * Blur fix
     * ========================= */

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    /* =========================
     * Rendering
     * ========================= */

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        boolean previewActive = activeTab == Tab.PREVIEW && metadataValid;

        refreshButton.visible = activeTab == Tab.MODELS;

        rotateButton.visible = previewActive;
        originButton.visible = previewActive;
        hollowButton.visible = previewActive;
        placeBuildButton.visible = previewActive;

        confirmButton.visible = previewActive;
        cancelButton.visible = previewActive;

        rotateButton.setMessage(Component.literal("Rot " + rotation));
        originButton.setMessage(Component.literal(originMode == OriginMode.CORNER ? "Corner" : "Center"));
        hollowButton.setMessage(Component.literal(hollow ? "Hollow" : "Solid"));

        g.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF2B2B2B);
        g.drawString(font, title, panelX + 8, panelY + 8, 0xFFFFFF);
        g.hLine(panelX + 8, panelX + PANEL_WIDTH - 8, panelY + 24, 0xFFFFFFFF);

        if (activeTab == Tab.MODELS) renderModelsTab(g);
        else if (activeTab == Tab.PREVIEW) renderPreviewTab(g);
        else if (activeTab == Tab.BUILD) renderBuildTab(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /* =========================
     * Tabs
     * ========================= */

    private void renderModelsTab(GuiGraphics g) {
        int x = panelX + 10;
        int y = panelY + 32;

        for (int i = 0; i < modelEntries.size(); i++) {
            if (i == selectedIndex) {
                g.fill(x - 2, y - 1, x + PANEL_WIDTH - 18, y + 11, 0xFF3A3A3A);
            }
            g.drawString(font, modelEntries.get(i).displayName, x, y, 0xFFFFFF);
            y += 12;
        }
    }

    private void renderPreviewTab(GuiGraphics g) {
        int x = panelX + 10;
        int y = panelY + 32;

        if (!metadataValid) {
            g.drawString(font, "No preview data.", x, y, 0xAAAAAA);
            return;
        }

        g.drawString(font, "Voxels: " + voxelCount, x, y, 0xFFFFFF); y += 12;
        g.drawString(font, "Size: " + getRotatedSizeX() + " x " + sizeY + " x " + getRotatedSizeZ(),
                x, y, 0xFFFFFF);
    }

    private void renderBuildTab(GuiGraphics g) {
        int x = panelX + 10;
        int y = panelY + 32;

        if (currentBuildPlan == null) {
            g.drawString(font, "No build data.", x, y, 0xAAAAAA);
            return;
        }

        g.drawString(font, "Build-ready: YES", x, y, 0x55FF55);
        y += 12;
        g.drawString(font, "Blocks: " + currentBuildPlan.getBlockCount(), x, y, 0xFFFFFF);
    }

    /* =========================
     * Input
     * ========================= */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeTab == Tab.MODELS && button == 0) {
            int x = panelX + 10;
            int y = panelY + 32;

            for (int i = 0; i < modelEntries.size(); i++) {
                if (mouseX >= x && mouseX <= x + PANEL_WIDTH - 20 &&
                        mouseY >= y && mouseY <= y + 12) {
                    selectedIndex = i;
                    parseSelectedModel();
                    return true;
                }
                y += 12;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /* =========================
     * Model loading
     * ========================= */

    private void loadModels() {
        modelEntries.clear();
        selectedIndex = -1;
        clearParsedData();

        File dir = new File("voxelbuilder/models");
        if (!dir.exists()) dir.mkdirs();

        File[] dirs = dir.listFiles(File::isDirectory);
        if (dirs != null) {
            Arrays.sort(dirs);
            for (File d : dirs) {
                if (folderHasCsv(d)) {
                    modelEntries.add(new ModelEntry(ModelType.CSV_FOLDER, d.getName(), d));
                }
            }
        }

        File[] stls = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".stl"));
        if (stls != null) {
            Arrays.sort(stls);
            for (File f : stls) {
                modelEntries.add(new ModelEntry(ModelType.STL_FILE, f.getName(), f));
            }
        }

        File[] csvs = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".csv"));
        if (csvs != null) {
            Arrays.sort(csvs);
            for (File f : csvs) {
                modelEntries.add(new ModelEntry(ModelType.CSV_FILE, f.getName(), f));
            }
        }

        if (!modelEntries.isEmpty()) {
            selectedIndex = 0;
            parseSelectedModel();
        }
    }

    private boolean folderHasCsv(File folder) {
        File[] csvs = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".csv"));
        return csvs != null && csvs.length > 0;
    }

    private void clearParsedData() {
        metadataValid = false;
        voxelCount = 0;
        sizeX = sizeY = sizeZ = 0;
        voxelCache.clear();
        orderedBuildPlan.clear();
        currentBuildPlan = null;
    }

    /* =========================
     * Parsing
     * ========================= */

    private void parseSelectedModel() {
        clearParsedData();
        if (selectedIndex < 0 || selectedIndex >= modelEntries.size()) return;

        ModelEntry e = modelEntries.get(selectedIndex);

        if (e.type == ModelType.STL_FILE) parseSTL(e.file);
        else if (e.type == ModelType.CSV_FILE) parseCSVSingleFile(e.file);
        else if (e.type == ModelType.CSV_FOLDER) parseCSVFolder(e.file);

        rebuildBuildPlan();
    }

    private void parseSTL(File file) {
        try {
            List<STLAsciiLoader.Triangle> tris = STLLoader.load(file);
            ModelNormalizer.NormalizedModel model =
                    ModelNormalizer.normalize(tris, 64.0f);

            STLVoxelizer.Result result =
                    STLVoxelizer.voxelizeSurface(model.triangles, 64);

            voxelCache.clear();
            for (STLVoxelizer.Voxel v : result.voxels) {
                voxelCache.add(new Voxel(v.x, v.y, v.z));
            }

            voxelCount = voxelCache.size();
            sizeX = result.sizeX;
            sizeY = result.sizeY;
            sizeZ = result.sizeZ;
            metadataValid = voxelCount > 0;

        } catch (Exception ignored) {
            metadataValid = false;
        }
    }

    /* =========================
     * CSV
     * ========================= */

    private void parseCSVSingleFile(File file) {
        loadCsvIntoCache(file);
        finalizeCsvBoundsFromCache();
    }

    private void parseCSVFolder(File folder) {
        File[] csvs = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".csv"));
        if (csvs == null || csvs.length == 0) return;
        Arrays.sort(csvs);
        for (File f : csvs) loadCsvIntoCache(f);
        finalizeCsvBoundsFromCache();
    }

    private void loadCsvIntoCache(File file) {
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.split(",");
                if (p.length < 3) continue;
                voxelCache.add(new Voxel(
                        Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim())
                ));
            }
        } catch (Exception ignored) {}
    }

    private void finalizeCsvBoundsFromCache() {
        voxelCount = voxelCache.size();
        metadataValid = voxelCount > 0;
        if (!metadataValid) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Voxel v : voxelCache) {
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            minZ = Math.min(minZ, v.z);
            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
            maxZ = Math.max(maxZ, v.z);
        }

        sizeX = (maxX - minX) + 1;
        sizeY = (maxY - minY) + 1;
        sizeZ = (maxZ - minZ) + 1;
    }

    /* =========================
     * Build plan
     * ========================= */

    private void rebuildBuildPlan() {
        orderedBuildPlan.clear();
        currentBuildPlan = null;
        if (!metadataValid || voxelCache.isEmpty()) return;

        orderedBuildPlan.addAll(voxelCache);
        orderedBuildPlan.sort(Comparator
                .comparingInt((Voxel v) -> v.y)
                .thenComparingInt(v -> v.z)
                .thenComparingInt(v -> v.x));

        int dispX = getRotatedSizeX();
        int dispZ = getRotatedSizeZ();
        int shiftX = originMode == OriginMode.CENTER ? dispX / 2 : 0;
        int shiftZ = originMode == OriginMode.CENTER ? dispZ / 2 : 0;

        Set<String> occupied = new HashSet<>();
        for (Voxel v : orderedBuildPlan) {
            occupied.add(v.x + "," + v.y + "," + v.z);
        }

        List<BuildPlan.BlockPos3> blocks = new ArrayList<>();

        for (Voxel v : orderedBuildPlan) {
            if (hollow && isFullySurrounded(v, occupied)) continue;

            int rx = v.x;
            int rz = v.z;

            if (rotation == 90) {
                rx = v.z;
                rz = sizeX - 1 - v.x;
            } else if (rotation == 180) {
                rx = sizeX - 1 - v.x;
                rz = sizeZ - 1 - v.z;
            } else if (rotation == 270) {
                rx = sizeZ - 1 - v.z;
                rz = v.x;
            }

            blocks.add(new BuildPlan.BlockPos3(
                    rx - shiftX,
                    v.y,
                    rz - shiftZ
            ));
        }

        currentBuildPlan = new BuildPlan(blocks, dispX, sizeY, dispZ);
        GhostPreviewDebugRenderer.setPreview(currentBuildPlan);
    }

    private boolean isFullySurrounded(Voxel v, Set<String> set) {
        return set.contains((v.x + 1) + "," + v.y + "," + v.z) &&
               set.contains((v.x - 1) + "," + v.y + "," + v.z) &&
               set.contains(v.x + "," + (v.y + 1) + "," + v.z) &&
               set.contains(v.x + "," + (v.y - 1) + "," + v.z) &&
               set.contains(v.x + "," + v.y + "," + (v.z + 1)) &&
               set.contains(v.x + "," + v.y + "," + (v.z - 1));
    }

    private int getRotatedSizeX() {
        return (rotation == 90 || rotation == 270) ? sizeZ : sizeX;
    }

    private int getRotatedSizeZ() {
        return (rotation == 90 || rotation == 270) ? sizeX : sizeZ;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
