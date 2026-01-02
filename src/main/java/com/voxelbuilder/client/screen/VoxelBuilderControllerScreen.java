package com.voxelbuilder.client.screen;

import com.voxelbuilder.client.build.BuildPlan;
import com.voxelbuilder.client.model.ModelNormalizer;
import com.voxelbuilder.client.render.GhostPreviewDebugRenderer;
import com.voxelbuilder.client.voxel.STLAsciiLoader;
import com.voxelbuilder.client.voxel.STLLoader;
import com.voxelbuilder.client.voxel.STLVoxelizer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

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

    private enum Tab { MODELS, PREVIEW, BLOCKS, SETTINGS }
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
    private Button originButton;
    private Button hollowButton;
    private Button placeBuildButton;
    private Button confirmButton;
    private Button cancelButton;

    // Blocks tab
    private EditBox blockSearchBox;
    private Button blockPrevPageButton;
    private Button blockNextPageButton;
    private Button selectedBlockButton;
    private final Button[] blockEntryButtons = new Button[10];
    private final List<ResourceLocation> allBlockIds = new ArrayList<>();
    private final List<ResourceLocation> filteredBlockIds = new ArrayList<>();
    private int blockPage = 0;
    private ResourceLocation selectedBlockId = null;
    private String selectedBlockLabel = "Default";


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
        addRenderableWidget(Button.builder(Component.literal("Blocks"), b -> activeTab = Tab.BLOCKS)
                .bounds(startX + (tabW + gap) * 2, tabY, tabW, tabH).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> activeTab = Tab.SETTINGS)
                .bounds(startX + (tabW + gap) * 3, tabY, tabW, tabH).build());

        refreshButton = addRenderableWidget(
                Button.builder(Component.literal("Refresh"), b -> loadModels())
                        .bounds(panelX + PANEL_WIDTH - 68, panelY + 6, 60, 16)
                        .build()
        );

        int btnY = panelY + PANEL_HEIGHT - 22;
        int btnH = 16;
        int btnX = panelX + 10;
        int w60 = 60;

        // Preview controls (rotation is hotkey-only now)
        originButton = addRenderableWidget(
                Button.builder(Component.literal(originMode == OriginMode.CORNER ? "Corner" : "Center"), b -> {
                    originMode = (originMode == OriginMode.CORNER) ? OriginMode.CENTER : OriginMode.CORNER;
                    rebuildPreview();
                }).bounds(btnX, btnY, w60, btnH).build()
        );

        hollowButton = addRenderableWidget(
                Button.builder(Component.literal(hollow ? "Hollow" : "Solid"), b -> {
                    hollow = !hollow;
                    rebuildPreview();
                }).bounds(btnX + w60 + 6, btnY, w60, btnH).build()
        );

        placeBuildButton = addRenderableWidget(
                Button.builder(Component.literal("Place Build"), b -> {
                    GhostPreviewDebugRenderer.armPlacement();
                    minecraft.setScreen(null);
                }).bounds(btnX + (w60 + 6) * 2, btnY, 86, btnH).build()
        );

        int confirmY = panelY + PANEL_HEIGHT - 40;
        confirmButton = addRenderableWidget(
                Button.builder(Component.literal("Confirm"), b -> {
                    GhostPreviewDebugRenderer.lockAnchor();
                }).bounds(panelX + 10, confirmY, 60, btnH).build()
        );

        cancelButton = addRenderableWidget(
                Button.builder(Component.literal("Cancel"), b -> {
                    GhostPreviewDebugRenderer.disarmPlacement();
                    minecraft.setScreen(this);
                }).bounds(panelX + 74, confirmY, 60, btnH).build()
        );

        // === Blocks tab widgets ===
        int bx = panelX + 10;
        int by = panelY + 32;

        blockSearchBox = new EditBox(font, bx, by, PANEL_WIDTH - 20, 16, Component.literal("Search"));
        blockSearchBox.setValue("");
        blockSearchBox.setResponder(s -> {
            blockPage = 0;
            refreshBlockFilter();
        });
        addRenderableWidget(blockSearchBox);

        int listY = by + 22;
        int rowH = 16;
        int rowGap = 2;
        for (int i = 0; i < blockEntryButtons.length; i++) {
            final int slot = i;
            int ry = listY + i * (rowH + rowGap);
            blockEntryButtons[i] = addRenderableWidget(
                    Button.builder(Component.literal(""), btn0 -> {
                        int idx = blockPage * blockEntryButtons.length + slot;
                        if (idx < 0 || idx >= filteredBlockIds.size()) return;
                        selectedBlockId = filteredBlockIds.get(idx);
                        selectedBlockLabel = selectedBlockId.toString();
                        selectedBlockButton.setMessage(Component.literal("Block: " + selectedBlockLabel));
                    }).bounds(bx, ry, PANEL_WIDTH - 20, rowH).build()
            );
        }

        int pagerY = panelY + PANEL_HEIGHT - 40;
        blockPrevPageButton = addRenderableWidget(
                Button.builder(Component.literal("<"), btn0 -> {
                    if (blockPage > 0) {
                        blockPage--;
                        updateBlockButtons();
                    }
                }).bounds(bx, pagerY, 20, 16).build()
        );
        blockNextPageButton = addRenderableWidget(
                Button.builder(Component.literal(">"), btn0 -> {
                    int maxPage = Math.max(0, (filteredBlockIds.size() - 1) / blockEntryButtons.length);
                    if (blockPage < maxPage) {
                        blockPage++;
                        updateBlockButtons();
                    }
                }).bounds(bx + 24, pagerY, 20, 16).build()
        );
        selectedBlockButton = addRenderableWidget(
                Button.builder(Component.literal("Block: " + selectedBlockLabel), btn0 -> {
                    selectedBlockId = null;
                    selectedBlockLabel = "Default";
                    selectedBlockButton.setMessage(Component.literal("Block: " + selectedBlockLabel));
                }).bounds(bx + 50, pagerY, PANEL_WIDTH - 20 - 50, 16).build()
        );

        refreshBlockFilter();
        loadModels();
    }

    /* =========================
     * Blur fix
     * ========================= */
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
    /* =========================
     * Rendering
     * ========================= */
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        boolean previewActive = activeTab == Tab.PREVIEW && metadataValid;

        refreshButton.visible = activeTab == Tab.MODELS;

        boolean blocksActive = activeTab == Tab.BLOCKS;
        if (blockSearchBox != null) blockSearchBox.visible = blocksActive;
        if (blockPrevPageButton != null) blockPrevPageButton.visible = blocksActive;
        if (blockNextPageButton != null) blockNextPageButton.visible = blocksActive;
        if (selectedBlockButton != null) selectedBlockButton.visible = blocksActive;
        for (Button b0 : blockEntryButtons) {
            if (b0 != null) b0.visible = blocksActive;
        }

        originButton.visible = previewActive;
        hollowButton.visible = previewActive;
        placeBuildButton.visible = previewActive;

        confirmButton.visible = previewActive;
        cancelButton.visible = previewActive;

        originButton.setMessage(Component.literal(originMode == OriginMode.CORNER ? "Corner" : "Center"));
        hollowButton.setMessage(Component.literal(hollow ? "Hollow" : "Solid"));

        g.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF2B2B2B);
        g.drawString(font, title, panelX + 8, panelY + 8, 0xFFFFFF);
        g.hLine(panelX + 8, panelX + PANEL_WIDTH - 8, panelY + 24, 0xFFFFFFFF);

        if (activeTab == Tab.MODELS) renderModelsTab(g);
        else if (activeTab == Tab.PREVIEW) renderPreviewTab(g);
        else if (activeTab == Tab.BLOCKS) renderBlocksTab(g);

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


    /* =========================
     * Input
     * ========================= */
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

        public boolean isPauseScreen() {
        return false;
    }


    /* =========================
     * Blocks tab helpers
     * ========================= */

    private void loadAllBlocksIfNeeded() {
        if (!allBlockIds.isEmpty()) return;
        // BuiltInRegistries.BLOCK.keySet() gives all registered block IDs.
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            allBlockIds.add(id);
        }
        allBlockIds.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
    }

    private void applyBlockFilter() {
        loadAllBlocksIfNeeded();
        filteredBlockIds.clear();

        String q = "";
        if (blockSearchBox != null) {
            q = blockSearchBox.getValue();
        }
        q = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);

        if (q.isEmpty()) {
            filteredBlockIds.addAll(allBlockIds);
        } else {
            for (ResourceLocation id : allBlockIds) {
                if (id.toString().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    filteredBlockIds.add(id);
                }
            }
        }

        // Clamp page
        int maxPage = Math.max(0, (filteredBlockIds.size() - 1) / blockEntryButtons.length);
        if (blockPage > maxPage) blockPage = maxPage;
        if (blockPage < 0) blockPage = 0;
    }

    private void updateBlockEntryButtons() {
        if (blockSearchBox == null) return;
        applyBlockFilter();

        int start = blockPage * blockEntryButtons.length;
        for (int i = 0; i < blockEntryButtons.length; i++) {
            Button b = blockEntryButtons[i];
            if (b == null) continue;

            int idx = start + i;
            if (idx >= 0 && idx < filteredBlockIds.size()) {
                ResourceLocation id = filteredBlockIds.get(idx);
                String label = id.toString();
                if (label.length() > 42) {
                    label = label.substring(0, 39) + "...";
                }
                b.active = true;
                b.visible = (activeTab == Tab.BLOCKS);
                b.setMessage(Component.literal(label));
            } else {
                b.active = false;
                b.visible = (activeTab == Tab.BLOCKS);
                b.setMessage(Component.literal(""));
            }
        }

        if (blockPrevPageButton != null) {
            blockPrevPageButton.active = blockPage > 0;
        }
        if (blockNextPageButton != null) {
            int maxPage = Math.max(0, (filteredBlockIds.size() - 1) / blockEntryButtons.length);
            blockNextPageButton.active = blockPage < maxPage;
        }

        if (selectedBlockButton != null) {
            selectedBlockButton.setMessage(Component.literal("Block: " + selectedBlockLabel));
        }
    }

    private void syncBlockTabVisibility(boolean alsoUpdateButtons) {
        boolean show = (activeTab == Tab.BLOCKS);

        if (blockSearchBox != null) blockSearchBox.setVisible(show);
        if (blockPrevPageButton != null) blockPrevPageButton.visible = show;
        if (blockNextPageButton != null) blockNextPageButton.visible = show;
        if (selectedBlockButton != null) selectedBlockButton.visible = show;
        for (Button b : blockEntryButtons) {
            if (b != null) b.visible = show;
        }

        if (show && alsoUpdateButtons) {
            updateBlockEntryButtons();
        }
    }

    private void selectBlockFromIndex(int filteredIndex) {
        if (filteredIndex < 0 || filteredIndex >= filteredBlockIds.size()) return;
        selectedBlockId = filteredBlockIds.get(filteredIndex);
        selectedBlockLabel = selectedBlockId.toString();
        if (selectedBlockButton != null) {
            selectedBlockButton.setMessage(Component.literal("Block: " + selectedBlockLabel));
        }
    }

    private void renderBlocksTab(GuiGraphics g) {
        int x = panelX + 10;
        int y = panelY + 8;
        g.drawString(font, "Blocks", x, y, 0xFFFFFF);

        // Small hint row
        int hintY = panelY + 26;
        g.drawString(font, "Search + click a block. This is just selection for now.", x, hintY, 0xAAAAAA);

        int infoY = panelY + PANEL_HEIGHT - 56;
        g.drawString(font, "Matches: " + filteredBlockIds.size(), x, infoY, 0xAAAAAA);
    }

}