# Shattered Pixel Dungeon on Pebble

Play [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon) (v4.0.2) on a Pebble watch (Emery and Gabbro). The phone runs the game; the watch shows it and takes your presses and taps.

Moved here from [TomBolger/PixelDungeon-](https://github.com/TomBolger/PixelDungeon-) (its `shattered/` folder, history kept). That repo still holds Pebble PD, the original Pixel Dungeon port that runs entirely on the watch.

## Layout
- `android-watch/`: the phone app's service (Android). It runs the game headless and talks to the watch.
- `common/`: the watch link (`Link`), controls, touch, the assistant and the watch-sized windows (`ui/`).
- `server/`: the desktop test tools (headless server, button scripts, tests).
- `watch/`: the Pebble app (C), a thin renderer.
- `patches/spd.patch`: our changes to Shattered's own source.
- `tools/`: art conversion, the emulator bridge, and helpers.

## Building
1. `./fetch-spd.sh` gets Shattered's source into `spd/` and applies the patch.
2. `./build-server.sh` builds the desktop tools.
3. `(cd watch && pebble build)` builds the watch app.
4. `./build-apk.sh` builds the phone app into `dist/`.
