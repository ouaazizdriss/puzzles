# Puzzles

Android jigsaw puzzle app for phones and tablets (Kotlin + Jetpack Compose).

- 24 built-in puzzles in 4 categories (photos from Unsplash, see `CREDITS.md`)
- Kids tab: 15 puzzles (shapes, numbers, ABC, colors, fruit, scenes, animals) with 4 to 16 big pieces,
  piece outlines on the board, pieces that snap in while dragging, no zoom and no timer
- Turn any photo (gallery or camera) into a puzzle, with cropping
- 9 to 150 pieces, classic tab/blank shapes, pinch-zoom, picture hint, edge-pieces filter
- Pieces join each other on the table and move as a group; groups lock into the frame
- Hint button, tray sorting (edges first), snap sounds and haptics, confetti on completion
- Auto-save and resume, best times
- Adaptive layout: tray at the bottom in portrait, on the side in landscape / tablets

## Build

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # engine unit tests
```

Release builds are signed when a `keystore.properties` file (not committed) points to the release keystore;
see the comment in `app/build.gradle.kts`.

`tools/kids_illustrations.py` regenerates the Kids illustrations and `tools/sounds.py` the sound effects.

Requires the Android SDK (compile SDK 36) and JDK 17+. See `PLAN.md` for the design.
