# VoxelBuilder

**Build STL 3D models directly in Minecraft**

Transform your favorite 3D models into magnificent Minecraft structures with precision voxelization and intelligent block placement.

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## ✨ Features

### 🎨 **Professional STL Voxelization**
- **Surface-accurate conversion** using Separating Axis Theorem (SAT)
- Preserves fine details that other voxelizers miss (goodbye disappearing thin walls!)
- Supports both **ASCII and Binary STL** formats with automatic detection
- Smart scaling to fit any model size

### 🏗️ **Intelligent Build System**
- **Server-authoritative placement** - works perfectly in multiplayer
- **Tick-budget management** prevents server lag (configurable performance)
- **Two-phase construction:**
  1. **Building Phase** - Places your model blocks
  2. **Lighting Phase** - Adds interior lighting to prevent mob spawning

### 💡 **Interior Lighting (Unique Feature!)**
- Automatically lights hollow interiors to prevent mob spawning
- Smart enclosed-air detection - only lights true interior spaces
- Surface-first placement on walls/ceilings for natural look
- Fully configurable spacing and search radius

### 🎮 **Easy to Use**
1. Place a **Voxel Builder Controller** block
2. Right-click to open the GUI
3. Select your STL file
4. Preview with real-time rotation and scaling
5. Place the anchor point in your world
6. Watch it build automatically!

### 🛡️ **Server-Safe & Grief-Protected**
- Distance limits (players can't build too far away)
- Dimension locks (builds cancel if player changes dimension)
- Per-player and global build limits
- Configurable extent caps
- Chunk-loading safety (skips unloaded chunks)
- Admin commands for monitoring and cancellation

---

## 📋 Requirements

- **Minecraft:** 1.21.1
- **Mod Loader:** NeoForge 21.1+
- **Java:** 21 or higher
- **Recommended Server RAM:** 4GB+ (for large builds)

---

## 🚀 Installation

### For Players:
1. Download the latest release from [CurseForge](#) 
2. Install [NeoForge 21.1+](https://neoforged.net/)
3. Place the mod JAR in your `mods` folder
4. Launch Minecraft!

### For Developers:
```bash
git clone https://github.com/BadMuthaJeffer/VoxelBuilder.git
cd VoxelBuilder
./gradlew build
```

---

## 🎯 How to Use

### Basic Workflow:

1. ## 🔨 Crafting

### Voxel Builder Controller
```
  I R I
  R C R
  I R I
```
- I = Iron Ingot
- R = Redstone  
- C = Comparator

Result: 1x Voxel Builder Controller
   ```

2. **Place and Open the Controller**
   - Right-click the Voxel Builder Controller block
   - The GUI will open

3. **Load Your Model**
   - Click the **Models** tab
   - Select an STL file from your `.minecraft/voxelbuilder/models/` folder
   - Click **Refresh** if you just added new files

4. **Customize Your Build**
   - **Preview Tab:** Adjust rotation (use hotkeys: R/T for Y-axis, F/G for X-axis, V/B for Z-axis)
   - **Blocks Tab:** Choose which block type to build with
   - **Settings Tab:** Adjust detail level (resolution)

5. **Place the Build**
   - Click **Place Build**
   - Right-click a block in the world to set the anchor point
   - The preview will appear at that location
   - Click **Confirm** to start building!

### Hotkeys (when preview is active):
- **R / T** - Rotate around Y-axis (yaw)
- **F / G** - Rotate around X-axis (pitch)
- **V / B** - Rotate around Z-axis (roll)
- **ESC** - Cancel placement

---

## ⚙️ Configuration

VoxelBuilder uses server-side configuration for multiplayer safety. Config file location:
```
<world>/serverconfig/voxelbuilder-server.toml
```

### Key Settings:

**Performance:**
- `globalBlocksPerTick` (default: 300) - Total blocks placed per server tick
- `perJobBlocksPerTick` (default: 80) - Max blocks per build per tick
- `maxActiveJobsGlobal` (default: 16) - Max concurrent builds on server
- `maxActiveJobsPerPlayer` (default: 1) - Max builds per player

**Safety:**
- `maxDistanceFromAnchor` (default: 96) - How far player can be from build
- `cancelWhenOutOfRange` (default: true) - Cancel if player moves too far
- `cancelOnDimensionChange` (default: true) - Cancel on dimension change
- `maxExtentXZ` (default: 512) - Max build size in X/Z directions
- `maxExtentY` (default: 256) - Max build size in Y direction

**Interior Lighting:**
- `lightingEnabled` (default: true) - Enable interior lighting pass
- `lightingBlockId` (default: "minecraft:glowstone") - Block used for lighting
- `lightingSpacingXZ` (default: 12) - Horizontal spacing between lights
- `lightingSpacingY` (default: 7) - Vertical spacing between lights

---

## 🎮 Commands

All commands require OP level 2.

### `/voxelbuilder config`
Display current server configuration values.

### `/voxelbuilder status`
Show all active builds and their progress.

### `/voxelbuilder cancel <player>`
Cancel all builds for a specific player.

### `/voxelbuilder cancelall`
**Emergency command** - Cancel all active builds on the server.

---

## 🧠 Technical Details

### Why VoxelBuilder is Different:

Most STL-to-voxel converters use naive ray-casting which:
- ❌ Loses thin walls and fine details
- ❌ Creates gaps in non-watertight meshes
- ❌ Requires perfect model topology

**VoxelBuilder uses Separating Axis Theorem (SAT)** which:
- ✅ Tests triangle-vs-voxel overlap directly
- ✅ Preserves surface details accurately
- ✅ Works on any STL file (watertight or not)
- ✅ No undersampling = no missing geometry

### Architecture:

```
Client Side:
  STL File → STLLoader → STLVoxelizer (SAT) → BuildPlan → Ghost Preview
                                                                ↓
                                                         Submit to Server
Server Side:
  Validate → Create Job → Tick-based Execution:
                           [Building Phase] → [Lighting Phase] → Complete
```

### Performance:

- **10,000 block model** @ 80 blocks/tick = ~6 seconds build time
- **100,000 block model** @ 80 blocks/tick = ~1 minute build time
- Lighting phase adds ~10-30 seconds depending on interior size

---

## 📊 Performance Tips

### For Large Builds:
1. **Increase server tick budget** (if server is dedicated):
   ```toml
   globalBlocksPerTick = 500
   perJobBlocksPerTick = 150
   ```

2. **Disable lighting** for solid models (no interior):
   ```toml
   lightingEnabled = false
   ```

3. **Pre-load chunks** around build area to avoid chunk generation lag

### For Multiplayer Servers:
1. Set reasonable player limits:
   ```toml
   maxActiveJobsPerPlayer = 1
   maxActiveJobsGlobal = 8
   ```

2. Monitor builds with `/voxelbuilder status`

3. Use distance limits to prevent remote griefing:
   ```toml
   maxDistanceFromAnchor = 96
   cancelWhenOutOfRange = true
   ```

---

## 🐛 Known Limitations

- **Maximum model size:** ~2 million blocks (configurable extent caps)
- **File formats:** STL only (OBJ, FBX not supported)
- **Colors:** Single block type per build (no multi-material support yet)
- **Memory:** Large builds (1M+ blocks) use ~12-24MB RAM per active build

---

## 🗺️ Roadmap

**Planned Features:**
- [ ] Multi-material support (different blocks for different parts)
- [ ] Color mapping from texture files
- [ ] OBJ file support
- [ ] Build queue system
- [ ] Undo/rollback functionality
- [ ] In-game model browser with thumbnails
- [ ] Build templates and favorites

---

## 🤝 Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📝 License

**MIT License with Attribution**

Copyright (c) 2026 BadMuthaJeffer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

**The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.**

**Proper credit must be given to the original author (BadMuthaJeffer) in any
derivative works or distributions.**

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

## 📞 Support

- **Issues:** [GitHub Issues](https://github.com/BadMuthaJeffer/VoxelBuilder/issues)
- **Discord:** [Coming Soon]
- **Wiki:** [Coming Soon]

---

## 🙏 Credits

**Created by BadMuthaJeffer**

Special thanks to:
- The NeoForge team for the modding framework
- Akenine-Möller for the triangle-box overlap algorithm
- The Minecraft modding community

---

## 📸 Screenshots

[Add screenshots here once you have some good builds!]

---

**⭐ If you enjoy VoxelBuilder, please star the repository and share it with others!**

*Built with ❤️ for the Minecraft community*
