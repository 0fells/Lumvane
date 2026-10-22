# lumcore: Android core

**Русский** | [English](#english)

Библиотека gomobile (`lumcore.aar`): sing-box (форк из `core/third_party`) и Xray-core в одном процессе под управлением `VpnService`. Пакет Kotlin `com.lumvane.lumcore` (класс `Lumcore`, а также `Platform`, `StartOptions` и др.).

## Устройство

- `lumcore/`: экспортируемый API (`Init`, `Start`/`Stop`, `CheckConfig`, `CheckXrayConfig`, `URLTest`/`StopTests`, `QueryStats`/`TotalUp`/`TotalDown`, `QueryConnections` (соединения с приложением-владельцем), `QueryAutoSelectors`/`AutoSelectorAction`, `QueryUrlTestGroups`/`UrlTestRecheck`, `RecentLogs` и `LogListener`, `GenWgKeyPair`, `Version`). Структурированные результаты отдаются строками JSON.
- `lumcore/platform*.go`: `adapter.PlatformInterface` поверх небольшого интерфейса `Platform`, который реализует Kotlin: `Protect(fd)`, `OpenTun(json) -> fd`, `InterfacesJSON`, монитор сетевого интерфейса по умолчанию.
- Общий с ядром Windows модуль `core/shared` (`lumshared`): `boxbox` запускает sing-box (`Context()`, `CloseWithTimeout`), `xray` запускает Xray, все его сокеты TCP и UDP идут через `Platform.Protect`, `wg` генерирует ключи WireGuard.

## Сборка

```
powershell -File core\android\build_aar.ps1    # -> android\app\libs\lumcore.aar (arm64, arm, amd64, Android API 26)
```

Нужны Go, JDK 21 (`JAVA_HOME`), Android SDK и NDK 27 (`ANDROID_HOME`, `ANDROID_NDK_HOME`). Скрипт сам ставит `sagernet/gomobile@v0.1.12` и `gobind` и выполняет `gomobile init`. Флаг компоновщика `-checklinkname=0` обязателен.

Теги сборки: `with_gvisor,with_quic,with_dhcp,with_wireguard,with_utls,with_clash_api,with_tailscale,badlinkname,tfogo_checklinkname0` (без naive).

Тесты на компьютере, без NDK:

```
cd core/android
go test -tags <теги выше> -ldflags=-checklinkname=0 ./lumcore/
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go vet -tags <теги выше> ./...
```

## Правка форка sing-box для Android

Опция `prefer_first` группы `urltest` (`option/group.go`, `protocol/group/urltest.go`, тест `urltest_prefer_test.go`): пока первый участник жив, выбран он; неудачное соединение сразу запускает перепроверку. Нужна для режима "Авто" в приложении.

## Контракт с Kotlin

- Сначала `Lumcore.init(filesDir, cacheDir, platform, logListener)`. `Platform.protect` должен вызывать `VpnService.protect(fd)`: так защищаются сокеты sing-box (в конфиге нужен `auto_detect_interface`) и все сокеты Xray.
- `OpenTun(json)`: собрать `VpnService.Builder` по JSON (адреса, маршруты, исключения, DNS, MTU, списки приложений, `http_proxy`), вызвать `establish()`, вернуть `detachFd()`.
- `StartDefaultInterfaceMonitor`: по сети по умолчанию из `ConnectivityManager` вызывать `listener.update(ifaceName, ifaceIndex, metered, false)` (индекс -1: сети нет); `InterfacesJSON` перечисляет интерфейсы. Оба обязательны: Android запрещает приложениям netlink.
- Xray рядом с sing-box (VLESS + Reality + XHTTP/xmux): JSON Xray передаётся в `StartOptions.XrayConfig` с входом socks или http на 127.0.0.1, а конфиг sing-box ходит в него исходящим `socks`/`http`.

---

## English

gomobile library (`lumcore.aar`): sing-box (the fork from `core/third_party`) and Xray-core in one process, driven by a `VpnService`. Kotlin package `com.lumvane.lumcore` (class `Lumcore`, plus `Platform`, `StartOptions` and others).

### Layout

- `lumcore/`: the bound API (`Init`, `Start`/`Stop`, `CheckConfig`, `CheckXrayConfig`, `URLTest`/`StopTests`, `QueryStats`/`TotalUp`/`TotalDown`, `QueryConnections` (connections with the owning app), `QueryAutoSelectors`/`AutoSelectorAction`, `QueryUrlTestGroups`/`UrlTestRecheck`, `RecentLogs` with `LogListener`, `GenWgKeyPair`, `Version`). Structured results are JSON strings.
- `lumcore/platform*.go`: `adapter.PlatformInterface` over the small Kotlin-implemented `Platform`: `Protect(fd)`, `OpenTun(json) -> fd`, `InterfacesJSON`, default-interface monitor.
- The `core/shared` module (`lumshared`), shared with the Windows core: `boxbox` starts sing-box (`Context()`, `CloseWithTimeout`), `xray` starts Xray with every TCP and UDP socket going through `Platform.Protect`, `wg` generates WireGuard keys.

### Building

```
powershell -File core\android\build_aar.ps1    # -> android\app\libs\lumcore.aar (arm64, arm, amd64, Android API 26)
```

Needs Go, JDK 21 (`JAVA_HOME`), Android SDK and NDK 27 (`ANDROID_HOME`, `ANDROID_NDK_HOME`). The script installs `sagernet/gomobile@v0.1.12` and `gobind` and runs `gomobile init`. The linker flag `-checklinkname=0` is required.

Build tags: `with_gvisor,with_quic,with_dhcp,with_wireguard,with_utls,with_clash_api,with_tailscale,badlinkname,tfogo_checklinkname0` (no naive).

Host tests, no NDK needed:

```
cd core/android
go test -tags <tags above> -ldflags=-checklinkname=0 ./lumcore/
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go vet -tags <tags above> ./...
```

### Android patch in the sing-box fork

The `prefer_first` option of the `urltest` group (`option/group.go`, `protocol/group/urltest.go`, test `urltest_prefer_test.go`): the first member wins while it is alive, and a failed dial triggers an immediate re-test. The app's Auto mode needs it.

### Kotlin contract

- Call `Lumcore.init(filesDir, cacheDir, platform, logListener)` first. `Platform.protect` must call `VpnService.protect(fd)`: it protects sing-box sockets (the config needs `auto_detect_interface`) and every Xray socket.
- `OpenTun(json)`: build a `VpnService.Builder` from the JSON (addresses, routes, excludes, DNS, MTU, app lists, `http_proxy`), call `establish()`, return `detachFd()`.
- `StartDefaultInterfaceMonitor`: for the default network from `ConnectivityManager` call `listener.update(ifaceName, ifaceIndex, metered, false)` (index -1 means no network); `InterfacesJSON` lists the interfaces. Both are mandatory: Android forbids netlink for apps.
- Xray next to sing-box (VLESS + Reality + XHTTP/xmux): pass the Xray JSON in `StartOptions.XrayConfig` with a socks or http inbound on 127.0.0.1; the sing-box config dials it as a `socks`/`http` outbound.
