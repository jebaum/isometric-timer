# Launcher icons

Generated from `achilles-icon-final.png` in the repository root and wired into
the app via `AndroidManifest.xml`:

```xml
<application android:icon="@mipmap/ic_launcher" ... >
```

## What's here

| File | Purpose |
| --- | --- |
| `mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon: background + foreground |
| `mipmap-*/ic_launcher_foreground.png` | Foreground layer, 108dp per density (108/162/216/324/432 px) |
| `values/ic_launcher_background.xml` | Background layer colour (`#000000`) |
| `play-store-icon-512.png` | 512×512 listing icon; unused while sideloading |

## What was removed, and why

The original export also carried `mipmap-*/ic_launcher.png` (a square fallback
for API < 26) and `mipmap-anydpi-v26/ic_launcher_round.xml`. Both are gone:

- `minSdk` is 36, so the pre-adaptive-icon fallback can never be selected.
- `android:roundIcon` is a leftover from API 25, which predates adaptive icons.
  Since API 26 the launcher applies the mask — circle, squircle, rounded square,
  teardrop — to the two adaptive layers, so round is just one of those shapes.
  The manifest never declared the attribute, making the file dead weight.

`mipmap-anydpi-v26` keeps its `-v26` qualifier even though `minSdk` is 36 and
lint's `ObsoleteSdkInt` suggests merging it into a plain `mipmap-anydpi`. Taking
that advice makes `aapt2` fail to resolve `@mipmap/ic_launcher` at all. The
warning is cosmetic; the suggested fix does not build.

## Safe zone, and why the icon is cropped

Adaptive icon layers are 108×108dp but only the centre 72dp is guaranteed
visible — the outer 18dp per edge is reserved for masking and the parallax
launchers apply on press. That is a tighter budget than the web manifest's
`maskable` icons, which get 80%.

The full illustration spans 76% × 94% of its frame, so scaling it to fit would
have left the limb small and adrift. Instead the layers are cropped in on the
tendon, ankle and heel, sized so the limb sits inside a 66dp circle, with the
cut edges feathered so sliced annotation strokes dissolve into black rather
than stopping dead. Verified against both circle and squircle masks.

The foreground is stored unpremultiplied — alpha carries the artwork and the
colour is scaled to match — so it composites correctly over whatever background
the launcher draws, and the layers can move independently during parallax.

## Caveats

- **There is deliberately no `<monochrome>` layer**, so lint's
  `MonochromeLauncherIcon` warning is expected. It only feeds Android 13+ themed
  icons, which tint a flat alpha shape — the wireframe cannot survive that, and
  a derived silhouette looked misshapen. Without it, themed home screens simply
  show the normal adaptive icon untinted. Add one only if you start using themed
  icons, and draw it by hand rather than tracing this artwork.
- **The background is flat black** to match the artwork. On a dark wallpaper the
  icon reads mostly as its gold tendon. Change
  `app/src/main/res/values/ic_launcher_background.xml` if you want it to sit
  forward more.
- **These are raster.** A vector foreground would scale better and shrink the
  APK; it would need the artwork redrawn, not traced.
