# Сторонние компоненты / Third-party notices

<p align="center"><a href="#русский">Русский</a> | <a href="#english">English</a></p>

# Русский

Lumvane распространяется по лицензии [GNU GPL v3](LICENSE). В нём используется чужой код и ресурсы. Ниже указано, откуда они взяты и на каких условиях. Тексты лицензий лежат рядом с кодом (файлы `LICENSE` в каталогах и заголовки файлов) и в каталоге [licenses](licenses). Готовые сборки содержат этот файл, каталог `licenses` и список Go-модулей ядра с их лицензиями (`licenses/Go-modules.txt`).

## Изменённый код под GPL

Windows-версия включает изменённый код других проектов, распространяемых по GNU GPL v3. Авторские права на этот код принадлежат его авторам, изменения видны в истории git.

## Ядро (Go)

| Компонент | Где | Лицензия |
|---|---|---|
| [sing-box](https://github.com/SagerNet/sing-box), SagerNet | `core/third_party/sing-box`, изменён (список правок: `core/third_party/diff_upstream.ps1`) | GPL-3.0-or-later с дополнительным условием: не использовать название sing-box и не создавать впечатление связи с ним |
| [wireguard-go](https://github.com/SagerNet/wireguard-go), WireGuard LLC и SagerNet | `core/third_party/wireguard-go`, изменён (обфускация AmneziaWG) | MIT |
| [Xray-core](https://github.com/XTLS/Xray-core), XTLS | модуль Go без изменений | MPL-2.0 |
| winipcfg из [wireguard-windows](https://git.zx2c4.com/wireguard-windows), WireGuard LLC | `core/windows/internal/boxdns/winipcfg` | MIT |
| [speedtest-go](https://github.com/showwin/speedtest-go), ITO Shogo | `core/windows/internal/speedtest` | MIT |
| [cronet-go](https://github.com/SagerNet/cronet-go), SagerNet | модуль Go; `libcronet.dll` рядом с ядром собрана из [Chromium](https://www.chromium.org/) | GPL-3.0-or-later (привязки), BSD-3-Clause (Chromium, [licenses/Chromium-BSD-3-Clause.txt](licenses/Chromium-BSD-3-Clause.txt)) |
| Остальные Go-модули и стандартная библиотека Go | зависимости из `go.mod` | у каждого своя; полный список с текстами собирает `core/go_licenses.ps1` |

## Windows-версия (C++)

| Компонент | Где | Лицензия |
|---|---|---|
| [Qt 6](https://www.qt.io/), The Qt Company | подключается динамически (DLL рядом с `Lumvane.exe`), исходники Qt: https://download.qt.io/ | LGPL-3.0 ([licenses/LGPL-3.0.txt](licenses/LGPL-3.0.txt)) |
| [SQLiteCpp](https://github.com/SRombauts/SQLiteCpp) и [SQLite](https://sqlite.org/) | `windows/3rdparty/SQLiteCpp` | MIT; SQLite в общественном достоянии |
| [simple-protobuf](https://github.com/tonda-kriz/simple-protobuf), Antonin Kriz | `windows/3rdparty/simple-protobuf` | MIT |
| [fkYAML](https://github.com/fktn-k/fkYAML), Kensuke Fukutani | `windows/3rdparty/fkYAML` | MIT |
| [quirc](https://github.com/dlbeer/quirc), Daniel Beer | `windows/3rdparty/quirc` | ISC |
| [QHotkey](https://github.com/Skycoder42/QHotkey), Felix Barz | `windows/3rdparty/QHotkey` | BSD-3-Clause |
| [QR Code generator](https://www.nayuki.io/page/qr-code-generator-library), Project Nayuki | `windows/3rdparty/qrcodegen.*` | MIT |
| WinCommander, UpdateNode UG | `windows/3rdparty/WinCommander.*` | GPL-3.0 (или Apache-2.0 по коммерческой лицензии) |
| qscopeguard.h из Qt, KDAB | `windows/3rdparty/qscopeguard.h` | LGPL-3.0 |
| QtExtKeySequenceEdit, QThreadCreateThread, QrDecoder | `windows/3rdparty` | GPL-3.0 |
| Примеры Qt (автодополнение, подсветка), The Qt Company | `AutoCompleteTextEdit`, `LogHighlighter` | BSD-3-Clause |
| [QJsonModel](https://github.com/dridk/QJsonModel), Sacha Schutz | `JsonModel` | MIT |
| SpeedWidget из [qBittorrent](https://github.com/qbittorrent/qBittorrent), Anton Lashkov | `SpeedWidget` | GPL-2.0-or-later |
| Windows Version Detection, Shao Voon Wong | `WinVersion` | MIT |
| [Noto Color Emoji](https://github.com/googlefonts/noto-emoji), Google | `windows/res/font/NotoColorEmoji.ttf` | SIL OFL 1.1 ([licenses/OFL-1.1.txt](licenses/OFL-1.1.txt)) |
| [Twemoji](https://github.com/jdecked/twemoji), Twitter, Inc и другие участники | `windows/res/font/TwemojiCOLR.ttf` (шрифт COLR из графики Twemoji) | графика CC-BY 4.0 (https://creativecommons.org/licenses/by/4.0/) |
| [Material Design Icons](https://pictogrammers.com/library/mdi/), Pictogrammers | `windows/res/icon/material` | Apache-2.0 ([licenses/Apache-2.0.txt](licenses/Apache-2.0.txt)) |
| Библиотеки среды выполнения Microsoft Visual C++ (`msvcp140*.dll`, `vcruntime140*.dll`) | только в готовой сборке | распространяемые компоненты Microsoft по условиям Visual Studio |

## Android-версия

| Компонент | Лицензия |
|---|---|
| AndroidX, Jetpack Compose, Room, WorkManager, Navigation, Material Icons (Google) | Apache-2.0 |
| Kotlin, kotlinx.coroutines, kotlinx.serialization (JetBrains) | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) (Square) | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing) и [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded) (JourneyApps) | Apache-2.0 |
| [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) | Apache-2.0 |
| Ядро `lumcore.aar`: те же sing-box, wireguard-go, Xray-core и Go-модули, что в таблице ядра | см. выше |

Текст Apache-2.0: [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).

# English

Lumvane is distributed under the [GNU GPL v3](LICENSE). It uses code and assets of other authors. Below is where they come from and under which terms. License texts sit next to the code (`LICENSE` files in the directories and file headers) and in the [licenses](licenses) directory. Release builds include this file, the `licenses` directory and the list of Go modules of the core with their licenses (`licenses/Go-modules.txt`).

## Modified GPL code

The Windows version includes modified code of other projects distributed under the GNU GPL v3. The copyright in that code belongs to its authors; the changes are visible in the git history.

## Core (Go)

| Component | Where | License |
|---|---|---|
| [sing-box](https://github.com/SagerNet/sing-box), SagerNet | `core/third_party/sing-box`, modified (list of patches: `core/third_party/diff_upstream.ps1`) | GPL-3.0-or-later with an additional term: do not use the sing-box name or imply association with it |
| [wireguard-go](https://github.com/SagerNet/wireguard-go), WireGuard LLC and SagerNet | `core/third_party/wireguard-go`, modified (AmneziaWG obfuscation) | MIT |
| [Xray-core](https://github.com/XTLS/Xray-core), XTLS | Go module, unmodified | MPL-2.0 |
| winipcfg from [wireguard-windows](https://git.zx2c4.com/wireguard-windows), WireGuard LLC | `core/windows/internal/boxdns/winipcfg` | MIT |
| [speedtest-go](https://github.com/showwin/speedtest-go), ITO Shogo | `core/windows/internal/speedtest` | MIT |
| [cronet-go](https://github.com/SagerNet/cronet-go), SagerNet | Go module; `libcronet.dll` next to the core is built from [Chromium](https://www.chromium.org/) | GPL-3.0-or-later (bindings), BSD-3-Clause (Chromium, [licenses/Chromium-BSD-3-Clause.txt](licenses/Chromium-BSD-3-Clause.txt)) |
| Other Go modules and the Go standard library | dependencies from `go.mod` | each has its own; `core/go_licenses.ps1` collects the full list with texts |

## Windows version (C++)

| Component | Where | License |
|---|---|---|
| [Qt 6](https://www.qt.io/), The Qt Company | linked dynamically (DLLs next to `Lumvane.exe`), Qt sources: https://download.qt.io/ | LGPL-3.0 ([licenses/LGPL-3.0.txt](licenses/LGPL-3.0.txt)) |
| [SQLiteCpp](https://github.com/SRombauts/SQLiteCpp) and [SQLite](https://sqlite.org/) | `windows/3rdparty/SQLiteCpp` | MIT; SQLite is in the public domain |
| [simple-protobuf](https://github.com/tonda-kriz/simple-protobuf), Antonin Kriz | `windows/3rdparty/simple-protobuf` | MIT |
| [fkYAML](https://github.com/fktn-k/fkYAML), Kensuke Fukutani | `windows/3rdparty/fkYAML` | MIT |
| [quirc](https://github.com/dlbeer/quirc), Daniel Beer | `windows/3rdparty/quirc` | ISC |
| [QHotkey](https://github.com/Skycoder42/QHotkey), Felix Barz | `windows/3rdparty/QHotkey` | BSD-3-Clause |
| [QR Code generator](https://www.nayuki.io/page/qr-code-generator-library), Project Nayuki | `windows/3rdparty/qrcodegen.*` | MIT |
| WinCommander, UpdateNode UG | `windows/3rdparty/WinCommander.*` | GPL-3.0 (or Apache-2.0 under a commercial license) |
| qscopeguard.h from Qt, KDAB | `windows/3rdparty/qscopeguard.h` | LGPL-3.0 |
| QtExtKeySequenceEdit, QThreadCreateThread, QrDecoder | `windows/3rdparty` | GPL-3.0 |
| Qt examples (completer, highlighter), The Qt Company | `AutoCompleteTextEdit`, `LogHighlighter` | BSD-3-Clause |
| [QJsonModel](https://github.com/dridk/QJsonModel), Sacha Schutz | `JsonModel` | MIT |
| SpeedWidget from [qBittorrent](https://github.com/qbittorrent/qBittorrent), Anton Lashkov | `SpeedWidget` | GPL-2.0-or-later |
| Windows Version Detection, Shao Voon Wong | `WinVersion` | MIT |
| [Noto Color Emoji](https://github.com/googlefonts/noto-emoji), Google | `windows/res/font/NotoColorEmoji.ttf` | SIL OFL 1.1 ([licenses/OFL-1.1.txt](licenses/OFL-1.1.txt)) |
| [Twemoji](https://github.com/jdecked/twemoji), Twitter, Inc and other contributors | `windows/res/font/TwemojiCOLR.ttf` (a COLR font built from Twemoji graphics) | graphics CC-BY 4.0 (https://creativecommons.org/licenses/by/4.0/) |
| [Material Design Icons](https://pictogrammers.com/library/mdi/), Pictogrammers | `windows/res/icon/material` | Apache-2.0 ([licenses/Apache-2.0.txt](licenses/Apache-2.0.txt)) |
| Microsoft Visual C++ runtime libraries (`msvcp140*.dll`, `vcruntime140*.dll`) | release build only | Microsoft redistributable components under the Visual Studio terms |

## Android version

| Component | License |
|---|---|
| AndroidX, Jetpack Compose, Room, WorkManager, Navigation, Material Icons (Google) | Apache-2.0 |
| Kotlin, kotlinx.coroutines, kotlinx.serialization (JetBrains) | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) (Square) | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing) and [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded) (JourneyApps) | Apache-2.0 |
| [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) | Apache-2.0 |
| The `lumcore.aar` core: the same sing-box, wireguard-go, Xray-core and Go modules as in the core table | see above |

Apache-2.0 text: [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).
