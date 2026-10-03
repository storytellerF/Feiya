# Development

Use JDK 21 or later and an Android SDK with platform 37. Set `ANDROID_HOME` to the SDK
directory (or configure `sdk.dir` in your local `local.properties`). Install Node.js
24 and npm on `PATH` as well; Android builds compile the shared browser UI automatically.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleAlpha :app:assembleRelease
./gradlew :app:lintDebug
```

Lint dependency resolution succeeds, but the current source has two existing
`LocalContextGetResourceValueCall` errors in `MainActivity.kt` and `Messages.kt`.

## Dependency alignment

Declare external library and plugin coordinates and versions in
`gradle/libs.versions.toml`, and reference their `libs` aliases from build scripts.
Local project dependencies remain project references.

AGP aligns dependencies shared by the app and its Android tests to the app's runtime
versions. The app declares an `implementation` dependency constraint for
`androidx.concurrent:concurrent-futures:1.2.0`, matching the requirement of AndroidX
Test JUnit 1.3.0. This raises the older transitive requirement from ProfileInstaller
without forcing an exact version or removing ProfileInstaller. Keep the constraint's
version in `gradle/libs.versions.toml`; revisit it when upgrading AndroidX Test or when
the app's other dependencies already require a compatible version.

## APK resource packaging

Netty native QUIC platform jars contain identical third-party license files under
`META-INF/license/` and identical GraalVM configuration under
`META-INF/native-image/io.netty/netty-codec-native-quic/`. The app uses scoped
`packaging.resources.pickFirsts` rules to retain one copy of these resources in the
APK. Keep the license notices; do not exclude all `META-INF` resources to resolve
merge conflicts. Recheck the duplicate contents when upgrading Netty.

## HID keyboard

`HidKeyboardHost` owns target layout, calibration selection, and a sequential
send queue with observable task status, key progress, failure reasons, and cancellation.
The HID screen owns a saveable `TextFieldValue` draft and updates it synchronously
in `onValueChange`; submission passes an immutable text snapshot to the Host.
Background task updates never replace the draft, cursor, or IME composition. The activity creates and closes it, observes state with lifecycle-aware
Compose collection, and consumes its completion/error effects while started. The application
injects a serial background coroutine dispatcher; host tests inject a test dispatcher.

Text is fully mapped before enqueueing, so unsupported characters cannot produce a
partial send. Each queued message captures its selected layout. The connection handle is captured
synchronously at submission, before dispatching queue work. Handle identity and
validity are checked before and after every key; reconnecting to the same device
creates a new handle. Finished task history is bounded to ten entries. `KeyboardLayout`
maps characters directly to `HidKey(usage, modifier)` using physical US key positions.
There are no string interceptors. `sendText` on the Host is the convenience entry
point for both the HID screen and shared URLs.

All output, including calibration and modifier combinations, calls
a bound `HidKeyboardSession`. The controller publishes connection handles and
invalidates them on disconnect, service loss, unregistration, Bluetooth shutdown,
permission loss, and activity destruction. `RawHidKeyboard` serializes press/release pairs
and attempts a release even on failure or cancellation. Bluetooth calls run on the
IO dispatcher; each session captures one Bluetooth device and profile on the main
thread. Validity is checked again after acquiring the keyboard mutex; key release
always uses the original target. Keyboard report ID 2
has exactly two bytes: modifier mask and one key usage, as defined in
`app/src/main/assets/hid`. Do not replace this with an eight-byte boot keyboard report.
Shortcuts can combine modifiers with one key; multiple simultaneous non-modifier keys
would require a descriptor change.

Mapping references: [USB HID Usage Tables](https://www.usb.org/hid) and the
[XKB US layout definitions](https://github.com/xkbcommon/libxkbcommon/blob/master/test/data/symbols/us).

The JVM tests cover ASCII/layout translation, unsupported input, queued-message
ordering, failure recovery, layout-independent calibration, report bytes, and
cancellation cleanup, session replacement, queue cancellation, and task progress.
Robolectric Compose tests run locally with Android resources and verify immediate
draft edits across task updates/reconnection, plus visible progress and cancellation
controls after disconnecting. They run as part of `:app:testDebugUnitTest` and do not
require an emulator. Real-device HID end-to-end testing is currently deferred.
When resumed, it requires a Bluetooth HID-capable Android device meeting the minimum
version in [README.md](README.md) and a target computer. The validation checklist is:

- Verify mixed-case ASCII and punctuation with each matching target layout.
- Verify unsupported text produces no output and a subsequent valid send succeeds.
- In macOS Keyboard Setup Assistant, verify ANSI and ISO recognition with each text
  layout selected and with a Bluetooth name that does not contain `Mac`.
- Disconnect during a send and verify a new send works after reconnecting.

## HTTP downloads and port changes

`respondUri` reads optional provider metadata on the IO dispatcher. Unknown or
negative sizes remain unknown; they must not become `Content-Length: 0`. The response
opens a fresh input stream for each body/range and closes it on completion or
cancellation. HEAD requests return metadata without opening a stream. Providers
need not supply DocumentsContract MIME/last-modified columns or seekable descriptors.

`ServerBinding` handles port changes serialized by the service event collector.
`AppServer` uses an injected, application-owned serial background dispatcher; its
coroutine scope is owned by the service lifecycle. Blocking start/stop and file IO
run on IO workers. A replacement server and its self WebSocket must start before
the previous server and client are closed. Successful changes release the old port;
no retired listeners, HTTP port redirects, or SSE port-migration events are retained.
Switching back binds a fresh server. Stop/restart/service shutdown closes the current
listener and client.

Port-switch failures preserve the active `ServerState.Started` instance, keeping its
self WebSocket, message cache, and refresh events usable. A separate failure event
updates the foreground notification with the requested and still-active ports. With
no active listener, startup failure remains `ServerState.Error`.

Service commands use a service-owned, non-conflating channel closed on destruction, consumed by `collectServerEvents`
alongside port changes. Each Stop/Restart is processed once, even for consecutive
identical commands. A port change never replays a previous command. Regression tests
exercise Stop → change port → Stop and real WebSocket messaging/cache/refresh after
a failed port switch.

The file-list page retains SSE for refresh events. The chat page uses WebSocket.
After a successful port change, users must open the new address themselves.

Local tests cover provider metadata, full/range/HEAD responses, cancellation cleanup,
port-switch failures, switching back, old-port release, and real loopback HTTP listener
shutdown. These are JVM/server integration tests, not Android device or Windows
browser end-to-end tests. Recheck downloads in a Windows browser with the provider
and file that originally reproduced issue #6 before claiming platform coverage.

## Boot startup

`BootSettingsHost` owns loading/saving/error state on the application-owned serial
settings dispatcher. The activity closes the Host and observes it with lifecycle-aware
collection. `DataStoreBootSettings` stores `start_on_boot` in the existing settings
DataStore; an absent key means disabled. Failed writes preserve the last saved UI value.

The non-exported `BootReceiver` handles only `BOOT_COMPLETED`, after credential storage
is available. It uses `goAsync` with an eight-second timeout and always finishes the
pending broadcast. Enabled startup uses the existing `remoteMessaging` foreground
service; Android 17 local-network permission must already be granted. It does not
launch a permission dialog or activity. Denied startup is logged without retrying.
The application initializes the shared-list path, and the service reloads shared
files before starting its listener. Foreground promotion happens immediately, including
when notification permission is denied.

Local tests cover preference persistence, Host errors/cancellation, switch rendering,
boot gating, timeout and denied-start cleanup, manifest receiver delivery, and foreground
promotion without notification permission. Actual reboot and manufacturer autostart
restrictions require device testing and remain unverified in this environment.

## Minified builds

PR CI assembles both Alpha and Release without signing secrets, in addition to tests.
It then signs disposable APK copies and verifies HTTP startup and WebSocket echo on
an Android 16 / API 36 x86_64 emulator. Production signing keys are never used.
Debug does not run R8 and cannot detect missing-class failures in these variants.
Local builds without the release signing environment variables produce unsigned APKs.

Netty 4.2.17 references desktop-only JFR classes and LDAP name parsers. The narrow
`-dontwarn` rules in `app/proguard-rules.pro` cover only the reported classes:
Netty detects unavailable JFR at runtime, and its LDAP references belong to TLS
certificate-error diagnostics unused by Feiya's plain HTTP Netty listeners. These
rules do not add desktop APIs to Android or disable certificate verification. Recheck
them when upgrading Netty or adding TLS listeners; do not suppress all R8 warnings.

Netty's `ReflectiveChannelFactory` invokes public no-argument Channel constructors.
Keep these constructors for live `io.netty.channel` implementations; `-dontwarn` does
not preserve reflective entry points. Class names and unrelated members can still
be optimized. Assemble success alone is not evidence that a minified app starts.
`ResourceLeakDetector.addExclusions` also checks declared method names during class
initialization. Keep the named allocator/leak-aware buffer/reference-count utility
methods in the rules; renaming or removing them causes an initialization crash.
Netty's concurrent skip-list also resolves `acquireFenceFallback` through a method
handle on Android; retain that fallback method for the non-VarHandle path.
Its field updaters similarly require the `head`, `next`, `val`, and `right` fields
with their original names, types, and volatility. R8 cannot infer those targets
through Netty's class helper; keep the specific fields rather than all Netty code.
Logback's XML configuration also requires the named Logcat appender and reflected
bean/converter members. Keep them so minified builds retain server diagnostics.
Netty's `TypeParameterMatcher` walks generic superclass signatures. Preserve the
class structure of its generic handler/codec/resolver bases and subclasses as well
as `Signature`; Ktor constructs `NettyDirectEncoder` during WebSocket upgrade, so
removing its encoder superclass can break the handshake after HTTP has started.
Preserve `@Sharable` handler classes and the `@Skip` callback metadata/names, too:
Netty checks them reflectively to decide whether handlers can be reused and which
callbacks must run. Removing `@Sharable` prevents subsequent connections from
installing the shared channel initializer.

The runtime check is `scripts/smoke-minified.sh SERIAL SIGNED_APK PACKAGE OUTPUT_DIR`.
Use a disposable emulator: it refuses to overwrite existing installations or test
against an already-running HTTP listener on port 8080. The shell entry point
forwards arguments directly to `scripts/smoke-minified.py`; CI invokes that Python
script directly on its dedicated emulator. Both paths remove only their own
installation and port forwarding. Results and startup logs go
to the output directory; CI uploads them even if the smoke check fails. Use
`scripts/sign-smoke-apks.sh OUTPUT_DIR ALPHA_UNSIGNED_APK RELEASE_UNSIGNED_APK` to
create disposable signed copies for this check. This covers startup, HTTP, and
WebSocket traffic, not Bluetooth HID or every production-device configuration.

## Shared URI integrity and device-test fixtures

Shared URI updates are serialized on IO workers, ignore repeat additions, and repair
legacy duplicate entries when loading. The combined file list also deduplicates by
URI, which is the Compose list key. Instrumented tests create unique temporary file
fixtures, wait for asynchronous additions, and remove only their own entries/files
in `@After`; they must not append permanent `file:///test.zip` entries. SSE assertions
have a timeout and propagate failures instead of swallowing them.

## Shared browser UI

`web/` is a standalone npm project. HTML lives in `web/src/pages`, and the app,
Worker, Host, Markdown adapter, and stylesheet live in `web/src`. Runtime libraries
(`markdown-it` and `@phosphor-icons/core`) are declared in `web/package.json` and
locked in `web/package-lock.json`. Build/test tools are devDependencies. Dependabot
checks `/web` weekly, including transitive updates through the lockfile.

Use Node.js 24 (`web/.nvmrc`) and npm:

```sh
npm ci --prefix web
npm --prefix web run build
npm --prefix web test
npm --prefix web exec -- playwright install --with-deps chromium
npm --prefix web run test:browser
npm --prefix web run preview
```

`build` bundles JavaScript/CSS with esbuild, copies HTML and only referenced icon
assets, and derives third-party license notices from the actual bundled packages.
The output is `web/dist/feiya/*.html` and `web/dist/web/*`. Neither `dist` nor
`node_modules` is committed. No vendoring step is needed when dependencies change.
Update packages with npm and commit both `package.json` and `package-lock.json`.

### Android packaging

Every Android variant registers generated Java resources through the AGP variant
API. Gradle runs `:app:installWebDependencies` (`npm ci --include=dev`), then
`:app:buildWeb`, then copies the distribution into its variant-specific generated
resource directory. HTML, JS, CSS, icons, and notices enter the APK on the classpath;
source code, test fixtures, node_modules, and source maps do not. npm installation
and bundling use declared inputs/outputs so unchanged builds skip both tasks.
A deleted node_modules directory or changed lockfile causes a fresh install.
Build failures fail the Android build instead of packaging an old distribution.

Normal commands such as `./gradlew :app:assembleDebug`, `:app:assembleAlpha`,
`:app:assembleRelease`, and `:app:testDebugUnitTest` generate the web resources
automatically. `./gradlew :app:buildWeb` runs just the web build through Gradle.
CI provisions Node.js for both test and release workflows; the PR workflow also
runs npm tests and Chromium checks against the bundled output. Both workflows
compare APK entries against the npm distribution using:

```sh
python3 scripts/check-web-apk.py web/dist app/build/outputs/apk/*/*.apk
```

This checks for missing, stale, unexpected, or duplicate web resources and excludes
node_modules from the APK.

Ktor serves `/login` and `/web/*` before authentication, and `/` and `/messages`
after session validation. The `feiya/` output namespace avoids collisions with
dependency resources such as `index.html`. `/shares`, `/shares/{index}`, `/sse`,
and `/chat` retain their protocols. File downloads still use server list indices.

### State, Markdown, and verification

`web/src/host.js` owns asynchronous state and effects without DOM dependencies.
`web/src/worker.js` injects networking, timers, and the Markdown renderer in a
Worker per page. `web/src/app.js` renders snapshots and owns the synchronous draft.
Page exit terminates the Worker; back/forward cache restore starts a fresh Host.
Messages are not replayed after disconnect. Clipboard copying uses the secure API
when available and a user-gesture fallback for LAN HTTP.

The Worker parses each message once, ignoring any sender-supplied HTML. The page
renders locally generated HTML; copying uses the original text. Raw HTML stays
disabled, and markdown-it's dangerous-URL validation remains enabled. Links open
separately with `noopener noreferrer`; message images can load their referenced
URLs. Run unsafe-input fixtures when changing parser options or adding plugins.

The preview runs on `127.0.0.1:4173` with mock HTTP/SSE/WebSocket data and serves
the same distribution as the APK. It is not an Android end-to-end test. Browser
tests save screenshots to ignored `web/test/artifacts/`. `WebPagesTest` separately
checks real Ktor routes, session login, and generated JS/CSS/icon/notice resources
using a JVM server. The web build preserves local assets; no CDN is needed.

## Adaptive Android UI

`AppShell` uses the available window width: bottom navigation below 600 dp, a
compact side rail from 600 dp, and a labeled sidebar from 1000 dp (224 dp, increasing to 280 dp in windows
at least 1200 dp wide). Large font scales use icon navigation with accessibility
labels instead of clipping bottom/rail labels.
`SharedFiles` switches to a list/detail layout when its own content area reaches
720 dp, after navigation has taken its space. Selection is saved by URI, survives
reordering and saved-state restoration, and clears when the file disappears.
Compact detail handles system Back before leaving the screen.

Messages use a separate Markdown draft preview when their content area reaches
800 dp; compact windows keep the composer below the timeline. Settings, security,
and HID forms have an 840 dp maximum width. HID moves its task history into a
280 dp supporting pane at 720 dp of content width; compact windows cap that
scrollable history to 45% of their content height. Theme colors remain dynamic and
support dark mode. File rows show actual names and URIs, not placeholder sizes
or modification dates from the design concept.

The edge-to-edge activity explicitly uses `adjustResize` so keyboard avoidance is
handled by Compose IME insets rather than automatic window panning. Message and
HID pages apply `imePadding()` after the shell padding has been consumed. When
checking keyboard layout on a device, open and dismiss the keyboard in compact
and wide windows and verify that the composer stays above it without excess space.

`QrCodeHost` owns address discovery and QR encoding, using the application serial
coordination dispatcher and background encoding. The composable observes state
with lifecycle awareness and closes the Host when its dialog leaves composition.
Changing file or port creates a new Host; switching address cancels prior encoding.

Native file/navigation icons are Phosphor Core 2.1.1 paths converted to Android
VectorDrawable resources; the MIT license is in `res/raw/phosphor_license.txt`.
After updating the npm package, run `python3 scripts/update-android-icons.py`
and commit the regenerated native drawables and license.

Run `./gradlew :app:testDebugUnitTest --tests '*AdaptiveFilesTest'` for adaptive selection,
saved-state, and rendering checks. Rendering captures are written under
`app/build/outputs/design/`; these use Robolectric native graphics, not a device.

## Brand icons

The Feiya mark combines an F with two swept wings, using plum, off-white, and lime.
`res/drawable/ic_launcher_foreground.xml` is the color vector source; keep its shape
within the adaptive safe circle. `ic_launcher_monochrome.xml` supplies themed icons,
and `ic_notification.xml` uses an opaque white silhouette on transparency at 24 dp.
Both foreground-service notification builders must use the notification drawable.
The splash screen shares the adaptive foreground and background color.

After editing the vectors or `values/ic_launcher_background.xml`, regenerate legacy
launcher density PNGs and the in-app logo:

```sh
python3 -m venv /tmp/feiya-icons-venv
/tmp/feiya-icons-venv/bin/pip install CairoSVG==2.9.1
/tmp/feiya-icons-venv/bin/python scripts/update-brand-icons.py
./gradlew :app:processDebugResources
```

CairoSVG requires the native Cairo library.
