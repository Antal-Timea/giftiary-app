# Bundled fonts

Gifty bundles its fonts as Android font resources; nothing is downloaded at runtime.
The SIL Open Font License requires the license to accompany the fonts, so the license
texts in this directory ship inside the APK.

| | Lora | Public Sans |
|---|---|---|
| Role | Editorial: headings, empty-state titles | Functional: body, labels, buttons, inputs |
| Bundled file | `app/src/main/res/font/lora.ttf` | `app/src/main/res/font/public_sans.ttf` |
| Source file | `ofl/lora/Lora[wght].ttf` | `ofl/publicsans/PublicSans[wght].ttf` |
| Variable font | Yes, `wght` axis 400–700 (default 400) | Yes, `wght` axis 100–900 (default 100) |
| Font version | 3.008 | 2.001 |
| Source | [google/fonts](https://github.com/google/fonts) | [google/fonts](https://github.com/google/fonts) |
| google/fonts revision | `b4c417dfee46b06751f094f0362b2d7a7e8c5eb3` (2023-12-14) | `37caf657942042c2de8f5f11d9d6866a1b689b84` (2022-05-13) |
| Upstream project | [cyrealtype/Lora-Cyrillic](https://github.com/cyrealtype/Lora-Cyrillic) @ `403b1a66` | [uswds/public-sans](https://github.com/uswds/public-sans) @ `c7923167` |
| Designer | Cyreal | USWDS, Dan Williams, Pablo Impallari, Rodrigo Fuenzalida |
| License | SIL Open Font License 1.1 — [`Lora-OFL.txt`](Lora-OFL.txt) | SIL Open Font License 1.1 — [`PublicSans-OFL.txt`](PublicSans-OFL.txt) |
| SHA-256 | `822a6621ccbe8d97d20ac88c1c41f5615c9c2c202eaa75f272cd452aac6475a7` | `d75a7dc1a27eb9e336d5b33f55489d2ecb5621bf694d5c43b2415bce2ca830a8` |

Only the upright styles are bundled; the italic files were not needed.

Each weight in `GiftyTypography.kt` sets the `wght` axis explicitly. This matters for
Public Sans, whose default instance is Thin (100).
