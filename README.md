<p align="center">
  <img src="assets/panedeck-logo.png" alt="PaneDeck icon" width="180">
</p>

<h2 align="center">PaneDeck</h2>

<p align="center">A ColorOS side workspace with one-tap price comparison.<br>An LSPosed module based on <a href="https://github.com/lujinxin/NeXtep">NeXtep by lujinxin</a> (Apache-2.0).</p>

<p align="center">
  <a href="README_CN.md">中文</a>
</p>

## What it is

PaneDeck adds a OneStep-style side workspace to ColorOS 16 / 17 while keeping the stock launcher. One main window plus three live slots, with drag-and-drop task switching — and a magnifier that searches a keyword across several shopping or food-delivery apps at once, each result in its own window, so you can compare prices on the spot.

**Current version: 1.0.0.** APKs are on [GitHub Releases](https://github.com/JHeyzo/PaneDeck/releases).

## Features

- **Side workspace:** one main window + three live task slots; drag to swap, add from Recents or the app strip.
- **One-tap price comparison:** type once, search JD / Taobao / Pinduoduo / Douyin Mall (shopping) or JD Takeout / Taobao Flash Buy / Meituan (delivery). No scraping, no ordering — each app opens its own search page.
- **Gestures:** bottom-corner swipe and two-finger swipe up/down to open; swipe up to exit. Each has its own switch.
- **Rounded corners:** main 16dp, slots 12dp (square corners optional).
- **Control Center tile, media controls, custom text, dark launcher icon.**

## Requirements

- OnePlus PLK110, ColorOS 16 / 17 (Android 16 / 17). Other devices need adaptation.
- KernelSU (or compatible root) + Zygisk + LSPosed.

## Install

1. Install the APK from [Releases](https://github.com/JHeyzo/PaneDeck/releases).
2. Enable PaneDeck in LSPosed for all packages in the bundled scope list.
3. Reboot. Open PaneDeck Settings to add the Control Center tile.

## Attribution

Based on **NeXtep 2.1.0 by lujinxin** (Apache License 2.0). Upstream project copyright lujinxin; PaneDeck's modifications copyright JHeyzo. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## License

[Apache License 2.0](LICENSE).
