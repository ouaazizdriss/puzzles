# Puzzles

Android jigsaw puzzle app for phones and tablets (Kotlin + Jetpack Compose).

- 24 built-in puzzles in 4 categories (photos from Unsplash, see `CREDITS.md`)
- Turn any photo (gallery or camera) into a puzzle, with cropping
- 9 to 150 pieces, classic tab/blank shapes, drag & snap, pinch-zoom, picture hint, edge-pieces filter
- Auto-save and resume, best times
- Adaptive layout: tray at the bottom in portrait, on the side in landscape / tablets

## Build

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # engine unit tests
```

Requires the Android SDK (compile SDK 36) and JDK 17+. See `PLAN.md` for the design.
