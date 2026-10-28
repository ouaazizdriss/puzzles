# Puzzles: Android jigsaw app, implementation plan

## 1. Goal

An Android app for phones and tablets where you can:
- play jigsaw puzzles from a built-in library of images
- pick a photo (gallery or camera) and turn it into a puzzle
- choose a difficulty, pause, and pick up a game later where you left it

Everything runs offline, with no backend and no account.

## 2. Tech stack

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin | Standard for Android |
| UI | Jetpack Compose + Material 3 | Canvas drawing plus gestures in one toolkit, and adaptive layouts |
| Architecture | Single activity, MVVM (ViewModel + StateFlow), Navigation Compose | Simple, and survives rotation and resize |
| DI | Manual `AppContainer` (was Hilt in the first draft) | Three singletons don't justify a DI framework |
| Persistence | JSON files via kotlinx.serialization (saves, photo index), SharedPreferences (settings, best times) (was Room/DataStore in the first draft) | Local only; one small document per game needs no SQL |
| Images | Coil (thumbnails), Android `ImageDecoder` / `BitmapFactory` (game bitmaps) | |
| Photo input | Photo Picker (`PickVisualMedia`) + camera (`TakePicture` + FileProvider) | The Photo Picker needs no storage permission |
| Cropping | Custom Compose crop screen (or the uCrop library to save time) | Lets you pick the framing and aspect ratio |
| Build | Gradle KTS + version catalog, minSdk 26, target/compile SDK 35+ | |

## 3. Features

### MVP (v1)
- **Home / gallery**: three tabs: *Built-in* (by category), *My photos*, *In progress*. Adaptive grid with 2 columns on phones and 4 to 6 on tablets.
- **Create from photo**: pick → crop (square, 4:3, 3:4) → name → saved to "My photos".
- **Puzzle setup**: piece count (9 / 16 / 36 / 64 / 100 / 150), an optional piece-rotation toggle, and the background color.
- **Game screen**:
  - Classic jigsaw-shaped pieces (tabs and blanks)
  - Drag pieces with a finger. A piece snaps when it is dropped near its correct spot.
  - Pinch-zoom and pan on the board (needed for 100+ pieces on a phone)
  - Piece tray: a horizontal strip at the bottom in phone portrait, a vertical strip on the side for tablets and landscape
  - Toolbar: ghost preview of the image, "edges only" filter, scatter/shuffle, timer, pause
  - Haptic and sound feedback when a piece snaps
- **Completion**: animation, time taken, best time saved per puzzle and difficulty.
- **Auto-save and resume**: piece positions are saved on pause, background, or exit.

### Later (v2+)
- Pieces that connect to each other on the loose board and move as a group, like a real jigsaw
- Piece rotation mode (two-finger twist or double-tap)
- Sliding-tile mode (15-puzzle) as a second game type
- Daily puzzle, stats screen, light/dark themes
- Share a finished puzzle as an image

## 4. Core design: the puzzle engine

### 4.1 Piece generation
1. Load the source image and downscale it so the long side is at most 2048 px, applying EXIF rotation.
2. Split the image into a `rows × cols` grid that matches its aspect ratio. For example, 4:3 at about 48 pieces gives 8×6.
3. For every **internal** edge, randomly choose `TAB` or `BLANK`. The neighbouring piece gets the opposite type, so the shapes always fit. Border edges are `FLAT`.
4. Build each piece's outline as a `Path` from cubic Bézier curves (the classic knob shape), with a little random jitter so pieces don't look identical.
5. Render each piece **once** into its own bitmap: clip the source image with the piece path, then add a thin bevel and stroke. The bitmap includes padding of about 25% of the cell size for the tabs.
6. Use a **seed** for the generation, so a saved game can rebuild exactly the same shapes without storing any bitmaps.

### 4.2 Game state (pure Kotlin, unit-testable)
```
PuzzleSpec(imageId, rows, cols, seed, rotationEnabled)
PieceState(id, row, col, x, y, rotation, placed: Boolean, zIndex, inTray: Boolean)
GameState(spec, pieces, elapsedMs, moves, completed)
```
- Snapping: when a piece is dropped, if `distance(piecePos, targetPos) < snapTolerance` (about 30% of the cell size, scaled by zoom) and its rotation is 0, it locks in place and `placed = true`.
- The game is complete when every piece is `placed`.
- Z-order: the piece being dragged moves to the top, and placed pieces sit at the bottom.

### 4.3 Rendering and input
- One Compose `Canvas` draws the board frame, the optional ghost image, the placed pieces, and then the loose pieces in z-order, all inside a `graphicsLayer` transform for zoom and pan.
- `pointerInput` handles the gestures:
  - a one-finger drag that starts on a piece moves that piece (hit-test from top to bottom against the piece paths)
  - a one-finger drag on empty space pans the board
  - two fingers zoom and pan
- Performance: pre-rendered piece bitmaps plus `drawImage` keep 150 pieces smooth. Avoid recomposing per piece; draw everything from a single state snapshot.

## 5. Built-in puzzle library
- About 24 images in 4 categories (Animals, Nature, Cities, Art/Food). Source them from Unsplash or Pexels (their licences allow use in apps) or from CC0 collections. Keep a `CREDITS.md`.
- Store them as WebP under `assets/puzzles/<category>/` at 2048 px on the long side, about 300 to 500 KB each, so roughly 10 MB in total.
- `assets/puzzles/catalog.json` lists the id, title, category, file, and aspect ratio. It is loaded into the Room DB on first launch.
- Option: if the library grows, move the images into a Play Asset Delivery pack.

## 6. Data model (Room)
- `PuzzleImage(id, source: BUILTIN|USER, title, category, path, width, height, createdAt)`
- `SavedGame(id, imageId, rows, cols, seed, rotation, piecesJson, elapsedMs, moves, updatedAt, completed)`
- `BestTime(imageId, pieceCount, bestMs)`
- User photos are copied into `filesDir/user_puzzles/<uuid>.webp` along with a thumbnail, so the app no longer depends on the original file in the gallery.

## 7. Phone and tablet support
- `WindowSizeClass` drives the layouts: Compact (phone), Medium and Expanded (tablet, foldable, landscape).
- The gallery grid uses `GridCells.Adaptive(160.dp)`.
- On the game screen, the tray position and the default zoom depend on the size class.
- Rotation and resize do not lose state: piece positions are stored in **board coordinates**, not screen pixels.
- Declare `resizeableActivity` and test on these emulators: Pixel phone, Pixel Tablet, and a foldable.

## 8. Project structure
```
app/src/main/java/.../puzzles/
  MainActivity.kt, PuzzlesApp.kt (Hilt)
  ui/
    theme/
    navigation/NavGraph.kt
    home/        HomeScreen, HomeViewModel
    import/      PickPhotoScreen, CropScreen, ImportViewModel
    setup/       SetupScreen
    game/        GameScreen, GameViewModel, BoardCanvas, PieceTray, CompletionDialog
    settings/
  engine/        (pure Kotlin, no Android dependencies apart from Bitmap/Path in the renderer)
    EdgeGenerator.kt, PieceShape.kt, PieceRenderer.kt, SnapLogic.kt, GameState.kt
  data/
    db/          AppDatabase, DAOs, entities
    repo/        PuzzleRepository, SavedGameRepository
    image/       ImageLoader (decode, downscale, EXIF), ImageStore (files)
app/src/main/assets/puzzles/...
```

## 9. Milestones
1. **Project scaffold**: Gradle, Compose, Hilt, Room, theme, navigation, and an empty set of screens. *Result: the app runs.*
2. **Engine**: edge generation, piece paths, piece bitmaps, snap logic, with unit tests. *Result: a debug screen shows the cut pieces.*
3. **Game screen**: canvas, drag, snap, zoom/pan, tray, timer, completion. *Result: one hard-coded image is playable.*
4. **Built-in library**: assets, catalog, gallery, setup screen. *Result: you can play any built-in puzzle.*
5. **Photo import**: Photo Picker, camera, crop, storage, "My photos" tab.
6. **Save and resume**, best times, "In progress" tab.
7. **Adaptive polish**: tablet layouts, sounds and haptics, animations, settings, app icon.
8. **Release prep**: R8, a signed AAB, performance pass on a low-end device, privacy policy (photos never leave the device).

## 10. Testing
- Unit tests:
  - the edge generator: neighbouring edges are always complementary, and the same seed produces the same output
  - the grid calculation for each aspect ratio
  - snap logic and the completion check
- Room DAO tests.
- Compose UI tests for the navigation flows.
- Manual and emulator testing on phone, tablet, and foldable, in portrait and landscape.
