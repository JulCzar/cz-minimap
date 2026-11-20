# AGENTS.md

# Standalone 3D Minimap — Project Guidelines

## 1. Project Overview

This project is a standalone Minecraft minimap mod developed for **Minecraft 1.21.1 using NeoForge**.

The goal is to provide a lightweight, configurable 2D minimap combined with an interactive 3D voxel world map.

The project must work independently, without requiring JourneyMap, Xaero's Minimap, or any other mapping mod.

JourneyMap is a behavioral and UX reference for the traditional minimap experience. Its positioning, customization, navigation, and exploration workflows can inspire this project, but its source code, textures, assets, and proprietary implementation must not be copied.

### 1.1 Core Product Concept

The mod provides two complementary interfaces backed by the same persistent cartographic data.

**A. HUD Minimap**

A traditional 2D minimap displayed over the Minecraft HUD.

**B. Interactive Full-Screen 3D Map**

A rotatable, zoomable, three-dimensional voxel representation of the explored world.

The 3D map must support terrain inspection, underground visualization, vertical layer selection, and different levels of geometric detail.

The map should behave like an interactive 3D model viewer rather than a conventional flat map.

### 1.2 Visual Direction

The 3D map must use a hybrid visualization approach.

Its default appearance should resemble a miniature voxel terrain model, preserving recognizable Minecraft block textures and the actual spatial arrangement of the terrain.

The user must also be able to:

- Inspect the terrain from any supported camera angle.
- Hide or isolate vertical ranges.
- Reveal underground structures using cutaway visualization.
- Inspect different vertical intervals independently.
- Display multiple vertical intervals simultaneously.
- View the terrain as a complete volumetric model.
- Reduce distant terrain detail through aggressive LOD.

Do not implement the 3D map as a collection of flat textures.

The geometry must preserve the three-dimensional structure of the discovered terrain.

### 1.3 Main Features

**HUD Minimap**

- Configurable screen positioning.
- Configurable dimensions and scale.
- Configurable square and circular layouts.
- North-up and player-heading orientation.
- Zoom controls.
- Player position and direction indicator.
- Optional compass and coordinates.
- Configurable opacity and background.
- Optional grid and navigation overlays.
- Optional waypoints and entity indicators in future versions.
- Independent visibility and presentation settings.

**Full-Screen 3D Map**

- Independent orbital camera.
- Free rotation.
- Camera panning.
- Zoom in and out.
- Player position and heading.
- Configurable viewing angle.
- Vertical layer selection.
- Vertical cutaway visualization.
- Progressive exploration.
- World change synchronization.
- Hybrid texture representation.
- Aggressive LOD.
- Region-based rendering.
- Persistent map data.

---

## 2. Technical Requirements

### 2.1 Target Environment

- Minecraft: 1.21.1.
- Mod loader: NeoForge.
- NeoForge version: compatible with Minecraft 1.21.1.
- Java: 21.
- Language: Java.
- Build system: Gradle.
- Mapping system: use the mappings configured by the project.
- Distribution: standalone mod.

Do not upgrade Minecraft, NeoForge, Java, or mappings without explicit authorization.

Use the official NeoForge 1.21.1 documentation as the primary technical reference.

### 2.2 Standalone Architecture

The mod must not depend on other minimap, world-map, or rendering mods.

Prefer Minecraft and NeoForge native APIs whenever they provide the required functionality.

Any third-party library must have a clear technical justification.

### 2.3 Client-Side Behavior

The initial implementation must be client-side and must not require installation on a dedicated server.

The client must collect and persist cartographic information available through normal gameplay.

In multiplayer environments:

- Only information received by the client can be mapped.
- Never access server-side world data directly.
- Never request arbitrary chunk loading.
- Changes received through normal chunk and block synchronization must update the map.
- Changes outside the client's known or loaded area cannot be guaranteed to appear until the corresponding information is received again.

Keep the architecture open to a future optional server-side synchronization component, but do not make it a prerequisite.

---

## 3. Architectural Principles

Maintain strict separation between:

1. World data acquisition.
2. Surface discovery.
3. Underground discovery.
4. Exploration state management.
5. Cartographic data storage.
6. Terrain representation.
7. Vertical range processing.
8. Cutaway and clipping logic.
9. LOD generation.
10. 2D minimap rendering.
11. 3D map rendering.
12. User interface and interaction.
13. Configuration and persistence.

The HUD minimap and the full-screen map must consume the same cartographic data source.

Neither interface should independently scan the Minecraft world or maintain its own conflicting representation of explored terrain.

### 3.1 Suggested Logical Architecture

Use the following structure as a conceptual reference.

```text
mod/
├── core/
│   ├── MapManager
│   ├── MapContext
│   └── DimensionKey
│
├── discovery/
│   ├── ExplorationTracker
│   ├── SurfaceDiscovery
│   ├── UndergroundDiscovery
│   ├── VisibilityResolver
│   ├── ProximityResolver
│   └── DiscoveryPolicy
│
├── world/
│   ├── ChunkDataCollector
│   ├── BlockChangeListener
│   └── WorldDataSnapshot
│
├── storage/
│   ├── MapStorage
│   ├── MapSerializer
│   ├── RegionStorage
│   └── StorageMigration
│
├── terrain/
│   ├── VoxelData
│   ├── TerrainBuilder
│   ├── MeshBuilder
│   ├── MaterialResolver
│   ├── TerrainAggregator
│   └── TerrainRegion
│
├── layers/
│   ├── LayerResolver
│   ├── LayerRange
│   ├── LayerConfiguration
│   └── LayerVisibility
│
├── cutaway/
│   ├── CutawayController
│   ├── CutawaySettings
│   └── ClipResolver
│
├── lod/
│   ├── LodManager
│   ├── LodBuilder
│   ├── LodLevel
│   └── LodCache
│
├── rendering/
│   ├── minimap/
│   └── worldmap/
│       ├── WorldMapRenderer
│       ├── OrbitalCamera
│       ├── MeshRenderer
│       └── RenderResources
│
├── gui/
│   ├── WorldMapScreen
│   ├── MapControls
│   ├── LayerSelector
│   └── CutawayControls
│
├── config/
│   ├── ClientConfig
│   └── MapConfig
│
└── input/
    └── KeyBindings
```

This structure is illustrative.

Do not create empty packages or classes merely to match the diagram.

Create components when their responsibilities become necessary.

### 3.2 Source of Truth

The persistent cartographic model is the source of truth.

All visual representations must be derived from it.

The following must never become independent sources of world information:

- HUD texture caches.
- 3D mesh caches.
- LOD representations.
- Camera state.
- Temporary cutaway meshes.

These are disposable presentation or performance structures.

A cache can always be rebuilt from the underlying cartographic data.

---

## 4. World Exploration and Data Acquisition

### 4.1 Progressive Discovery

The map must reveal terrain progressively as the player explores.

Unexplored regions must remain hidden or visually obscured.

Loading a chunk does not automatically mean every block inside it has been discovered.

Implement an explicit discovery policy.

The discovery system must:

- Track player position.
- Track player dimension.
- Detect relevant terrain surfaces.
- Identify underground areas discovered by the player.
- Preserve previously discovered information.
- Support all dimensions.
- Avoid revealing unknown terrain simply because its chunk became available.

### 4.2 Hybrid Discovery Model

The exploration system must combine two independent discovery strategies.

**Surface Discovery — Visibility Based**

Surface terrain should be revealed through visibility and exposure analysis.

The goal is to progressively reveal the terrain the player can reasonably observe while exploring.

Consider:

- Terrain height.
- Obstructions.
- Exposed surfaces.
- Terrain visibility.
- Player position.
- Configurable discovery radius.

The system must avoid expensive full-world raycasting.

Use bounded visibility calculations and incremental updates.

**Underground Discovery — Hybrid Visibility and Proximity**

Underground discovery must combine visual exposure with proximity.

The system should reveal:

1. Underground surfaces directly visible to the player.
2. Cave walls and floors within a configurable proximity radius.
3. Previously discovered underground spaces.
4. Newly exposed underground areas when the player enters caves or tunnels.

Proximity discovery must not indiscriminately reveal every underground block in a loaded chunk.

It should operate around relevant player positions and discovered underground spaces.

Prefer discovering meaningful exposed surfaces rather than arbitrary solid blocks hidden inside mountains.

### 4.3 Discovery States

The data model must distinguish:

- Unknown terrain.
- Known air.
- Discovered solid blocks.
- Discovered exposed surfaces.
- Discovered underground spaces.
- Previously discovered but currently unobserved regions.

Do not treat unknown and empty space as equivalent.

A region can be available in memory without being eligible for display.

### 4.4 Discovery Persistence

Once a region has been discovered, its cartographic information must remain available after the player leaves.

Returning to an explored area must not require rediscovering the same terrain.

### 4.5 World Data Collection

Collect relevant information from chunks already available to the client.

Prefer compact, immutable snapshots.

Do not retain live references to mutable `Level`, `ChunkAccess`, or `BlockEntity` instances in long-lived map data.

Do not force chunk loading or generation.

---

## 5. Cartographic Data Model

### 5.1 Spatial Organization

Use Minecraft chunk coordinates as the primary spatial partition.

Each chunk contains 16 × 16 horizontal block positions and multiple vertical sections.

Use sparse storage and avoid allocating complete data structures for unexplored regions.

Organize data by:

- World identity.
- Dimension identifier.
- Chunk coordinates.
- Vertical section.
- Block representation.
- Exploration state.

### 5.2 Block Representation

Store the information required to identify and visually represent Minecraft blocks.

Prefer registry identifiers and relevant block-state properties over live block object references.

Preserve where applicable:

- Block identifier.
- Relevant block-state properties.
- Biome information.
- Tint information.
- Exploration state.
- Relevant visual metadata.

Avoid storing inventories, unnecessary block entity data, or unrelated gameplay information.

### 5.3 Unknown and Empty Space

Unknown space must remain distinguishable from known air.

This distinction is essential for:

- Underground discovery.
- Cave visualization.
- Terrain boundaries.
- Cutaway rendering.
- Exploration fog.

### 5.4 Dimensions

Support dimension-specific map data.

Never assume that all dimensions share the same height limits.

Use the actual minimum and maximum build heights of each dimension.

The initial implementation should support:

- Overworld.
- Nether.
- End.

Other registered dimensions should work whenever their available data and height limits allow it.

---

## 6. Data Persistence

### 6.1 Storage Requirements

Use a dedicated client-side storage system.

Do not use server-side `SavedData` as the primary persistence mechanism.

The storage implementation must:

- Persist explored regions.
- Persist relevant block representation data.
- Persist dimension information.
- Preserve discovery state.
- Support incremental updates.
- Avoid rewriting the entire map for small changes.
- Support versioned serialization.
- Handle corrupted data gracefully.
- Avoid blocking the render thread.

A region-based storage approach is preferred.

### 6.2 World Identity

Isolate data between different worlds and multiplayer servers.

Never combine maps from unrelated worlds.

For multiplayer, derive a stable local identifier from server identity.

Do not store connection credentials or unnecessary sensitive information.

### 6.3 Saving Strategy

Use asynchronous storage operations with bounded queues.

Implement:

- Dirty-region tracking.
- Periodic flushing.
- Save on world disconnect.
- Save on game shutdown.
- Atomic file replacement where possible.
- Recovery from interrupted writes.

---

## 7. 3D Terrain Representation

### 7.1 General Approach

The full-screen map must render an independent voxel representation.

Do not instantiate Minecraft blocks or create a secondary world.

The renderer must consume compact cartographic data and generate optimized geometry.

### 7.2 Hybrid Visual Representation

The default visualization should combine:

- Original Minecraft block textures at close distances.
- Original block appearance for recognizable terrain features.
- Simplified colors and aggregated materials at distant LOD levels.
- Biome-aware tinting when available.
- Consistent representation of unknown areas.

The visual transition between detailed and simplified terrain should preserve recognizable geographic characteristics.

Do not introduce arbitrary colors that make different materials indistinguishable.

### 7.3 Texture Reuse

Reuse Minecraft's block textures and appropriate texture atlas resources whenever practical.

Account for:

- Texture UV coordinates.
- Block-state-dependent models.
- Biome tinting.
- Transparent materials.
- Resource reloads.
- Missing textures.

The renderer must not permanently retain invalid GPU texture references after a resource reload.

### 7.4 Material Resolution

Create a dedicated material resolution stage.

It must convert stored block information into rendering information without requiring live world access.

For each supported block representation, resolve:

- Visual material.
- Texture reference.
- Relevant tint.
- Transparency behavior.
- LOD simplification behavior.

Unsupported complex models must have a consistent fallback.

### 7.5 Geometry Generation

Prioritize:

- Hidden-face removal.
- Face culling.
- Greedy meshing where compatible.
- Geometry batching.
- Region-based meshes.
- Mesh caching.
- Incremental invalidation.

Never create one render object per Minecraft block.

Do not regenerate all geometry whenever the player moves.

### 7.6 Volumetric Integrity

The 3D representation must preserve the actual spatial relationships of discovered blocks.

Do not flatten caves or vertical terrain into 2D textures.

The user must be able to rotate the camera and inspect the volume from different angles.

---

## 8. Hybrid Cutaway and Terrain Inspection

This subsystem is a core feature, not an optional visual effect.

### 8.1 Default View

The default map should display a complete miniature terrain model.

The player must be able to inspect the surface and rotate the terrain to understand its shape.

### 8.2 Cutaway Visualization

Provide mechanisms for selectively hiding terrain.

Support:

- Horizontal clipping.
- Vertical clipping.
- Layer isolation.
- Selected-range visibility.
- Cumulative vertical visibility.
- Full terrain visibility.

The cutaway system must operate on the 3D representation rather than flattening the map.

### 8.3 Cutaway Behavior

The system should allow the user to remove upper terrain layers to inspect caves and underground structures.

It should also allow inspection of a selected vertical interval without requiring all other terrain to be rendered.

The selected range must preserve original world coordinates.

Do not reposition individual layers to fill gaps.

For example, selecting Y=-48 to Y=-32 must display terrain at its actual vertical position, not move it to the surface.

### 8.4 Unknown Terrain Boundaries

Cutaway rendering must preserve exploration restrictions.

Removing an upper layer must not reveal unexplored blocks below it.

Unknown areas must remain visually distinct from known air.

The renderer must never infer underground terrain from the geometry of neighboring discovered blocks.

### 8.5 Performance

Prefer clipping and visibility filtering during mesh generation or rendering.

Avoid rebuilding the entire cartographic model whenever the user changes the cutaway configuration.

Presentation changes should invalidate only the affected rendering data.

---

## 9. Vertical Layer System

### 9.1 Range-Based Selection

The map must support vertical intervals rather than requiring individual Y-level selection.

Default interval size: 16 blocks.

Example:

| Layer | Minimum Y | Maximum Y |
|---|---:|---:|
| 1 | -64 | -48 |
| 2 | -48 | -32 |
| 3 | -32 | -16 |
| 4 | -16 | 0 |
| 5 | 0 | 16 |
| 6 | 16 | 32 |
| 7 | 32 | 48 |
| ... | ... | ... |

The minimum boundary is inclusive and the maximum boundary is exclusive.

For example, `[-64, -48)` includes Y levels -64 through -49.

### 9.2 Layer Configuration

Provide:

- Configurable interval size.
- Configurable starting height.
- Automatic generation of available intervals.
- Dimension-aware limits.
- Display of actual Y ranges.
- Future support for custom ranges.

Do not hardcode Overworld height values as universal limits.

### 9.3 Visualization Modes

**Selected Range**

Display only the blocks inside the selected interval.

**Cumulative Range**

Display the selected interval together with intervals above or below it.

The direction must be configurable.

**Full Explored Terrain**

Display all discovered vertical information.

**Cutaway View**

Allow the user to hide parts of the terrain while preserving the remaining 3D structure.

### 9.4 Cave Visualization

Underground layers must preserve their real spatial relationships.

The player must be able to rotate the camera and inspect cave geometry from different angles.

Unknown underground terrain must remain hidden in every visualization mode.

---

## 10. Level of Detail (LOD)

The 3D map must use aggressive LOD to support large explored regions.

### 10.1 LOD Strategy

Use multiple levels of detail.

Conceptual examples:

- LOD 0: Individual block representation with original textures.
- LOD 1: Simplified block groups with reduced geometry.
- LOD 2: Larger terrain aggregates with simplified materials.
- LOD 3: Coarse regional representation using dominant terrain characteristics.

The exact implementation may evolve based on profiling.

### 10.2 Hybrid Texture and Geometry Strategy

The visual representation must adapt to distance.

**Near camera**

- Preserve block-level appearance.
- Use original textures where possible.
- Preserve recognizable small terrain features.
- Maintain relevant material differences.

**Medium distance**

- Aggregate compatible geometry.
- Reduce unnecessary faces.
- Preserve major material and biome differences.
- Simplify small terrain details.

**Far distance**

- Use coarse terrain representations.
- Prefer dominant colors and materials.
- Reduce geometry aggressively.
- Preserve the overall terrain silhouette.

Do not simply scale down detailed geometry indefinitely.

### 10.3 LOD Requirements

- Select LOD using camera distance and projected screen size.
- Cache generated LOD data.
- Rebuild only affected regions.
- Avoid render-thread geometry generation.
- Preserve spatial continuity.
- Prevent obvious gaps between adjacent LOD regions.
- Keep unknown regions visually consistent.
- Support vertical layer filtering.

### 10.4 LOD and Cutaway Compatibility

LOD generation must account for vertical ranges and cutaway visualization.

A simplified region must not incorrectly fill an area that contains known air or unknown terrain.

Do not aggregate across boundaries where doing so would reveal undiscovered terrain or destroy important cave structures.

Prioritize correctness over simplification when an area contains relevant underground geometry.

---

## 11. HUD Minimap

### 11.1 Layout

Allow configuration of:

- Top-left.
- Top-center.
- Top-right.
- Bottom-left.
- Bottom-center.
- Bottom-right.
- Custom position.

Support mouse-based dragging for custom positioning.

### 11.2 Appearance

Provide:

- Width and height.
- Scale.
- Shape.
- Frame visibility.
- Frame opacity.
- Background opacity.
- Map opacity.
- Compass visibility.
- Player indicator.
- Orientation.
- Zoom level.

### 11.3 Orientation

**North Up**

North remains fixed at the top.

**Player Heading Up**

The map rotates to keep the player's current heading at the top.

Keep the player indicator consistent with the selected orientation.

### 11.4 Rendering

The minimap must use preprocessed cartographic data.

Do not perform chunk scanning, block-state extraction, or expensive geometry generation during HUD rendering.

Use cached representations and invalidate them only when the underlying data changes.

The HUD must remain independent of the full-screen map's camera and cutaway settings.

---

## 12. Interactive Full-Screen Map

### 12.1 Camera Controls

Implement an independent orbital camera.

Expected controls:

- Left mouse drag: rotate.
- Middle mouse drag: pan.
- Mouse wheel: zoom.
- Optional keyboard camera navigation.
- Configurable sensitivity.
- Reset camera action.

The camera must rotate around a meaningful focal point.

Support horizontal and vertical rotation with sensible limits.

### 12.2 Navigation

Provide:

- Navigation across discovered regions.
- Player position.
- Player heading.
- Coordinates.
- Zoom controls.
- Layer selection.
- Cutaway controls.
- Return-to-player navigation.

Do not automatically move the camera whenever the player moves.

The user must retain control of the camera.

### 12.3 Interface Controls

Keep camera interaction independent of UI interaction.

Dragging over selectors, buttons, or other interface elements must not unexpectedly rotate or move the terrain.

### 12.4 Suggested Interface Organization

Organize controls into distinct groups:

**Navigation**

- Zoom.
- Camera rotation reset.
- Return to player.
- Coordinate information.

**Vertical Inspection**

- Layer selector.
- Range information.
- Selected-range mode.
- Cumulative mode.
- Full-terrain mode.

**Terrain Visibility**

- Horizontal cutaway.
- Vertical cutaway.
- Cutaway boundaries.
- Underground visibility.

**Rendering**

- LOD quality.
- Texture detail.
- Terrain display options.

Avoid overcrowding the map viewport.

Controls should be accessible without obstructing the terrain.

---

## 13. Configuration

Use NeoForge's configuration system for appropriate client settings.

Keep presentation configuration separate from persistent map data.

Configuration should include:

### HUD

- Visibility.
- Position.
- Dimensions.
- Shape.
- Zoom.
- Orientation.
- Opacity.

### Discovery

- Surface discovery radius.
- Underground proximity radius.
- Discovery policy.
- Visibility sampling limits.

### 3D Map

- LOD quality.
- Maximum render distance.
- Texture detail.
- Camera sensitivity.
- Default camera position.
- Default cutaway mode.

### Vertical Layers

- Interval size.
- Starting height.
- Default visualization mode.
- Cumulative direction.

### Storage and Performance

- Cache limits.
- Background processing limits.
- Storage behavior.
- Mesh memory budget.

Changing presentation settings must never erase cartographic data.

Validate numeric values and enforce appropriate limits.

---

## 14. Key Bindings

Register configurable key mappings through NeoForge.

Provide separate controls for:

- Opening and closing the full-screen map.
- Toggling the HUD minimap.
- Zooming the HUD.
- Resetting the 3D camera.
- Returning to player position.
- Optional layer navigation.

Avoid hardcoded key handling that bypasses Minecraft's Controls menu.

Check for conflicts with existing mappings.

---

## 15. Threading and Performance

Performance is a core project requirement.

### 15.1 Main Thread

World access and operations requiring live world state must happen on the appropriate game thread.

Never access mutable world objects from arbitrary background threads.

### 15.2 Background Workers

Move expensive independent operations to background workers:

- Serialization.
- Compression.
- LOD generation.
- Mesh construction from immutable snapshots.
- Map indexing.
- Cache preparation.
- Discovery calculations based on immutable snapshots.

### 15.3 Render Thread

GPU resource creation, upload, modification, and destruction must follow Minecraft's rendering-thread requirements.

Never perform disk operations or expensive terrain generation inside rendering callbacks.

### 15.4 Task Scheduling

Use bounded queues.

Deduplicate pending mesh generation jobs.

Prioritize:

1. Regions currently visible in the 3D map.
2. Regions near the player.
3. Regions visible in the HUD.
4. Other discovered regions.

Cancel obsolete work when its input data becomes outdated.

### 15.5 Memory Management

Implement predictable cache eviction.

Keep compact source data persisted on disk and maintain only relevant decoded data and meshes in memory.

---

## 16. Code Quality and Development Rules

### 16.1 General Rules

- Inspect the repository before modifying it.
- Follow the existing architecture.
- Do not assume packages, classes, or APIs exist.
- Avoid unnecessary abstractions.
- Avoid unrelated refactoring.
- Do not introduce fake or placeholder functionality.
- Do not silently remove existing behavior.
- Avoid unnecessary dependencies.
- Prefer strongly typed code.
- Avoid raw types and unchecked casts.
- Avoid unnecessary static mutable state.
- Keep methods focused.
- Handle errors explicitly.
- Do not add comments that merely repeat the implementation.

### 16.2 NeoForge Compatibility

Before implementing Minecraft or NeoForge integrations:

1. Confirm the API belongs to Minecraft 1.21.1.
2. Consult the corresponding documentation.
3. Verify event names and event buses.
4. Verify client/server execution sides.
5. Verify thread requirements.
6. Check rendering API compatibility.

Never assume examples from Minecraft 1.21.4 or later are compatible.

### 16.3 Client and Server Isolation

Client-only classes must never be loaded through dedicated server code paths.

Keep rendering, GUI, input, and camera code isolated from common initialization.

### 16.4 Error Handling

Handle:

- Missing or invalid map data.
- Unsupported block models.
- Missing textures.
- Corrupted storage regions.
- Interrupted background tasks.
- World disconnections.
- Dimension transitions.
- Resource reloads.
- GPU resource lifecycle changes.

A failed map region must not crash the game.

---

## 17. Testing Strategy

### 17.1 Discovery

Test:

- Surface exploration.
- Underground exploration.
- Cave entrances.
- Narrow tunnels.
- Large caverns.
- Player proximity discovery.
- Visibility-based discovery.
- Previously explored terrain.
- Unknown areas inside loaded chunks.
- Dimension transitions.

Verify that proximity discovery does not reveal arbitrary underground terrain.

### 17.2 Persistence

Test:

- Saving and reopening worlds.
- Reconnecting to multiplayer servers.
- Switching between worlds.
- Switching dimensions.
- Interrupted writes.
- Corrupted files.
- Schema migration.

### 17.3 Terrain Representation

Test:

- Different block materials.
- Different block states.
- Biome tinting.
- Transparent blocks.
- Unsupported block models.
- Original textures at close range.
- Simplified textures at distance.
- Terrain boundaries.
- Unknown regions.

### 17.4 Cutaway and Layers

Test:

- Selected vertical range.
- Cumulative vertical range.
- Full explored terrain.
- Horizontal cutaway.
- Vertical cutaway.
- Multiple simultaneous ranges.
- Cave inspection.
- Terrain above and below selected ranges.
- Unknown blocks below removed terrain.
- Dimension-specific height limits.

Verify that cutaway operations never reveal unexplored terrain.

### 17.5 Camera

Test:

- Rotation.
- Panning.
- Zoom.
- Camera reset.
- Return-to-player.
- Extreme camera angles.
- Camera interaction over UI controls.

### 17.6 LOD

Test:

- All LOD levels.
- Different camera distances.
- Large explored regions.
- LOD transitions.
- Region boundaries.
- Cave structures.
- Layer filtering.
- Cutaway compatibility.
- Mesh invalidation.

### 17.7 HUD

Test:

- All supported positions.
- Custom positioning.
- Different GUI scales.
- Window resizing.
- Different shapes.
- Different orientations.
- Zoom changes.
- Visibility toggling.

### 17.8 Performance

Measure:

- Main-thread impact.
- Render-thread impact.
- Background task throughput.
- Memory consumption.
- Mesh generation time.
- Disk I/O.
- Frame time while moving.
- Frame time while rotating.
- Frame time during LOD transitions.

Avoid relying exclusively on average FPS.

Investigate frame-time spikes and memory growth.

---

## 18. Development Roadmap

Implement incrementally.

Do not attempt to build the entire mod in one step.

### Phase 1 — Project Foundation

- Initialize the NeoForge 1.21.1 project.
- Configure Java 21 and Gradle.
- Establish the initial architecture.
- Register the mod.
- Register key mappings.
- Establish client/server separation.
- Verify development client startup.

**Acceptance criteria:** The mod launches successfully in Minecraft 1.21.1.

### Phase 2 — Cartographic Data Model

- Define world identity.
- Define dimension identity.
- Define region and chunk representation.
- Define voxel data.
- Define exploration states.
- Distinguish known air from unknown space.
- Define immutable data snapshots.

**Acceptance criteria:** The project has a consistent cartographic data model that can represent explored and unexplored terrain without depending on rendering.

### Phase 3 — Exploration and Discovery

- Track player movement.
- Track dimension transitions.
- Implement surface visibility discovery.
- Implement underground visibility discovery.
- Implement underground proximity discovery.
- Combine both underground strategies.
- Implement discovery radius configuration.
- Preserve exploration state.

**Acceptance criteria:** Surface terrain and underground structures are progressively discovered using the hybrid policy without revealing arbitrary unknown areas.

### Phase 4 — Persistence

- Implement region-based storage.
- Implement serialization.
- Implement incremental saving.
- Implement asynchronous I/O.
- Implement world and dimension isolation.
- Implement schema versioning.
- Implement recovery mechanisms.

**Acceptance criteria:** Discovered terrain remains available between sessions and different worlds never share map data accidentally.

### Phase 5 — 2D Minimap

- Implement HUD rendering.
- Implement player positioning.
- Implement north-up orientation.
- Implement player-heading orientation.
- Implement zoom.
- Implement configurable positioning.
- Implement appearance settings.
- Implement cache invalidation.

**Acceptance criteria:** The minimap works continuously with low performance overhead and displays progressively discovered terrain.

### Phase 6 — Initial 3D Renderer

- Implement independent 3D camera.
- Implement voxel terrain geometry.
- Implement texture and material resolution.
- Implement biome tinting where practical.
- Implement face culling.
- Implement region-based geometry.
- Implement mesh caching.

**Acceptance criteria:** The full-screen map renders explored terrain as a rotatable 3D voxel model with recognizable Minecraft block appearance.

### Phase 7 — Vertical Layers and Cutaway

- Implement dimension-aware height limits.
- Implement configurable vertical intervals.
- Implement selected-range mode.
- Implement cumulative-range mode.
- Implement full-terrain mode.
- Implement horizontal cutaway.
- Implement vertical cutaway.
- Implement cutaway controls.
- Preserve exploration restrictions.

**Acceptance criteria:** Players can inspect underground terrain by isolating vertical intervals or cutting away parts of the terrain without revealing unknown areas.

### Phase 8 — Hybrid LOD

- Implement multiple LOD levels.
- Implement distance-based selection.
- Implement block aggregation.
- Implement simplified distant materials.
- Implement asynchronous generation.
- Implement mesh caching.
- Implement cache limits.
- Implement LOD transitions.
- Implement cutaway-compatible LOD.

**Acceptance criteria:** Large explored regions can be inspected without rendering every block at full detail, while nearby terrain retains recognizable Minecraft textures.

### Phase 9 — World Update Synchronization

- Implement block change detection.
- Update discovered terrain.
- Invalidate affected geometry.
- Invalidate affected LOD representations.
- Update the minimap cache.
- Verify multiplayer behavior.

**Acceptance criteria:** Changes received by the client appear in the corresponding discovered regions without rebuilding the entire map.

### Phase 10 — UX and Optimization

- Refine minimap presentation.
- Refine full-screen controls.
- Improve camera behavior.
- Improve layer selection.
- Improve cutaway interaction.
- Improve loading feedback.
- Optimize memory usage.
- Optimize rendering.
- Validate large-world behavior.

**Acceptance criteria:** The mod is stable, responsive, and usable during extended gameplay and exploration sessions.

---

## 19. Agent Workflow

Before implementing any task:

1. Inspect the repository.
2. Read relevant project documentation.
3. Identify affected subsystems.
4. Verify Minecraft and NeoForge APIs.
5. Understand existing data ownership and threading.
6. Propose the smallest coherent implementation.
7. Implement only the requested scope.
8. Run relevant Gradle tasks.
9. Review resulting changes.
10. Report implementation status.

When an architectural decision has not been established, explain the alternatives and request clarification instead of inventing requirements.

### 19.1 Implementation Reports

Every completed task should report:

- What changed.
- Files affected.
- Important architectural decisions.
- Tests executed.
- Build and test results.
- Known limitations.
- Remaining work.

Never claim a feature is complete if only its UI, data model, or placeholder implementation exists.

### 19.2 Documentation Maintenance

Update documentation whenever architecture, data formats, or public behavior changes.

Keep the roadmap aligned with the actual implementation.

---

## 20. Non-Goals

Unless explicitly requested, do not implement:

- A complete replacement for Minecraft's world renderer.
- An independently simulated Minecraft world.
- Automatic generation of unexplored chunks.
- Remote server world scanning.
- A web-based map viewer.
- A server-hosted map synchronization service.
- An external map database service.
- Dependency on JourneyMap or Xaero's Minimap.
- Full reproduction of every special block animation.
- Full block entity rendering.
- Unrestricted underground terrain discovery.

These features may be evaluated separately in the future.

---

## 21. Reference Documentation

Use these resources as primary technical references:

- NeoForge 1.21.1 — Getting Started:
  https://docs.neoforged.net/docs/1.21.1/gettingstarted/

- NeoForge 1.21.1 — Events:
  https://docs.neoforged.net/docs/1.21.1/concepts/events/

- NeoForge 1.21.1 — Networking:
  https://docs.neoforged.net/docs/1.21.1/networking/

- NeoForge 1.21.1 — Screens:
  https://docs.neoforged.net/docs/1.21.1/gui/screens/

- NeoForge 1.21.1 — Saved Data:
  https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata/

- NeoForge 1.21.1 — Configuration:
  https://docs.neoforged.net/docs/1.21.1/misc/config/

- JourneyMap — Basic Usage:
  https://teamjm.github.io/journeymap-docs/latest/client/basic-usage/

- JourneyMap — Minimap Settings:
  https://teamjm.github.io/journeymap-docs/latest/client/settings/minimap/

- JourneyMap — Minimap Position:
  https://teamjm.github.io/journeymap-docs/latest/client/settings/minimap-position/

Always verify API compatibility against the actual Minecraft 1.21.1 environment.

---

## 22. Final Guiding Principles

The project's most important architectural principle is:

**The map is a persistent, progressively discovered, optimized cartographic representation of the Minecraft world — not a second Minecraft world.**

The 2D minimap and interactive 3D map are two visualization interfaces consuming the same source of cartographic truth.

The 3D map must preserve terrain volume and spatial relationships while allowing the user to inspect the world through rotation, vertical layer selection, and cutaway visualization.

The discovery system must combine surface visibility and underground proximity without exposing arbitrary unknown terrain.

The rendering system must combine original Minecraft textures at close distances with simplified materials and aggressive LOD at greater distances.

All implementation decisions must prioritize:

1. Exploration integrity.
2. Spatial accuracy.
3. Rendering performance.
4. Memory efficiency.
5. Data consistency.
6. Maintainability.
7. User experience.
