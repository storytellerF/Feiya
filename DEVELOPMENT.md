# 开发

使用 JDK 21 或更新版本，以及包含 platform 37 的 Android SDK。将 `ANDROID_HOME` 设为 SDK 目录，或在本地 `local.properties` 配置 `sdk.dir`。同时安装 Node.js 24，并确保 npm 在 `PATH` 中；Android 构建会自动编译共享浏览器 UI。

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleAlpha :app:assembleRelease
./gradlew :app:lintDebug
```

定位 Gradle 弃用项时，为构建添加 `--warning-mode all` 和 `-Dorg.gradle.deprecation.trace=true`（PowerShell 中为后者加引号）。项目已迁移废弃的 Groovy 仓库 URL 赋值方式。AGP 9.4.1 仍在 `BasePlugin.createAndroidJdkImageConfiguration` 调用 `Configuration.setVisible`，Gradle 计划在 11 移除。这是插件警告，不是项目脚本调用。升级 AGP 时重新检查；保留警告，不屏蔽诊断。

系统栏使用 AndroidX `enableEdgeToEdge`，图标外观随 `AppTheme`，按钮导航使用平台对比度保护。剪贴板写入在 composition 管理的协程作用域中使用 `LocalClipboard.setClipEntry`。JVM Compose 测试覆盖消息／二维码链接复制内容和系统栏图标变化；发布前还应在设备上目视检查二维码链接复制和手势／按钮导航。

## 依赖对齐

外部库和插件的坐标、版本在 `gradle/libs.versions.toml` 声明，构建脚本引用其 `libs` 别名。本地项目依赖仍使用项目引用。

AGP 将应用和 Android 测试共享的依赖对齐到应用运行时版本。应用为 `androidx.concurrent:concurrent-futures:1.2.0` 声明 `implementation` 依赖约束，匹配 AndroidX Test JUnit 1.3.0 的要求。这会提升 ProfileInstaller 较旧的传递依赖要求，不强制精确版本，也不删除 ProfileInstaller。约束版本保留在 `gradle/libs.versions.toml`；升级 AndroidX Test，或应用其他依赖已要求兼容版本时，重新评估。

## APK 资源打包

Netty 原生 QUIC 平台 JAR 在 `META-INF/license/` 包含相同第三方许可证，在 `META-INF/native-image/io.netty/netty-codec-native-quic/` 包含相同 GraalVM 配置。应用使用限定范围的 `packaging.resources.pickFirsts`，在 APK 中各保留一份。保留许可证声明，不要为解决合并冲突而排除全部 `META-INF` 资源。升级 Netty 时重新核对重复内容。

## HID 键盘

`HidKeyboardHost` 管理目标布局、校准选择和顺序发送队列，提供可观察的任务状态、按键进度、失败原因和取消。HID 页面持有可保存的 `TextFieldValue` 草稿，并在 `onValueChange` 同步更新；提交时将不可变文本快照交给 Host。后台任务更新绝不替换草稿、光标或 IME 组合输入。Activity 创建和关闭 Host，通过感知生命周期的 Compose 收集观察状态，并在 started 期间消费完成／错误副作用。应用注入后台串行协程调度器；Host 测试注入测试调度器。

入队前完整映射文本，避免不支持字符导致部分发送。每条排队消息记录所选布局。提交时、分发队列工作之前，同步捕获连接句柄。每次按键前后检查句柄身份和有效性；重连同一设备会创建新句柄。已结束历史最多十项。`KeyboardLayout` 根据 US 物理按键位置，将字符直接映射到 `HidKey(usage, modifier)`，没有字符串拦截器。Host 的 `sendText` 是 HID 页面与共享 URL 的便捷入口。

所有输出（包括校准和修饰键组合）都调用已绑定的 `HidKeyboardSession`。连接 Host 发布连接句柄；`HidProfileHandle` 管理有效性，在断开、服务丢失、注销、蓝牙关闭、权限丢失和显式 Host 关闭的回调边界直接使句柄失效。Activity 销毁时取消自己的发送队列。`RawHidKeyboard` 串行执行按下／释放对，即使失败或取消也尝试释放。蓝牙调用运行在 IO 调度器；会话发布前捕获一个蓝牙设备和 profile。取得键盘 mutex 后再次检查有效性；释放按键始终使用原目标。键盘报告 ID 2 严格为两个字节：修饰键掩码和一个键 usage，由 `app/src/main/assets/hid` 定义。不要改成八字节 boot keyboard 报告。快捷键可组合修饰键与单个按键；同时按多个非修饰键需要修改描述符。

映射参考：[USB HID Usage Tables](https://www.usb.org/hid) 和 [XKB US 布局定义](https://github.com/xkbcommon/libxkbcommon/blob/master/test/data/symbols/us)。

JVM 测试覆盖 ASCII／布局转换、不支持输入、排队消息顺序、失败恢复、独立于布局的校准、报告字节、取消清理、会话替换、队列取消和任务进度。`HidConnectionHost` 由应用管理，不依赖 Android／Compose。它在注入的协调调度器上串行执行生命周期操作与回调投影；回调使句柄失效不等待该队列。`AndroidHidPlatform` 使用应用 context，平台调用和描述符读取运行在 IO。所有 Activity 实例共享一份注册。只要至少一个 Activity resumed，不透明的前台客户端 token 就允许恢复；暂停或销毁某个 Activity 不会注销另一个客户端的 HID 会话。profile 和蓝牙 receiver 首次使用后随应用存活；蓝牙关闭、权限丢失或 profile 丢失会释放受影响 profile。进程死亡时由系统释放应用资源；`close()` 也支持测试中的显式拆除。键盘发送队列仍由 Activity 管理。

注册请求被接受与 `onAppStatusChanged(true)` 是不同事件。恢复、权限授予、蓝牙恢复和显式重试都会协调注册。尝试失败展示给用户，不自动循环重试。每个 profile 句柄管理一次注册尝试和显式连接代次。获取、注册确认和释放确认各有十秒期限。`HidProfileAcquisition` 在取消后关闭未被接管的迟到代理。注册超时撤销发送能力并请求释放；unregister 成功返回只表示请求已接受，不代表确认。收到 false 注册回调，或服务／蓝牙丢失确认释放前，Host 保留管理责任并阻止新注册，包括显式重试。清理超时会说明如何重启蓝牙。迟到的注册成功不能恢复正在释放的句柄；只有前一资源关闭后，重试才获取新句柄。连接查询使用快照身份，避免并发断开让会话复活。连接尝试（含对端发起的 connecting 回调）也有十秒期限，重复 connecting 回调和 Activity 恢复不会延长。超时或显式取消会原子地退役仍为当前尝试的注册，再通过同一确认释放路径获取新 profile。Android 回调没有连接尝试 ID，只重置 connecting 标志会让迟到成功复活已放弃尝试。赢得原子转换的成功则保留。连接对话框支持 Cancel 和 Back；超时显示连接错误，清理完成后可 Retry。通知用 `CLEAR_TOP | SINGLE_TOP` 复用 Activity 及其导航／草稿状态。Host 测试覆盖这些转换、重叠 Activity 客户端、注册拒绝、回调缺失、清理归属、迟到回调、同步会话撤销、按键释放清理和回调／查询顺序。`HidNotificationTest` 检查真实通知 PendingIntent 契约，不模拟 Android 任务栈执行。Android 15 真机冒烟还验证了三次实际通知返回均复用同一 Activity，保留消息页／草稿，注册成功后进入 HID 设备选择状态。该检查使用隔离的 Debug 安装，没有向目标电脑发送按键。

Robolectric Compose 测试在本地使用 Android 资源，验证任务更新和重连期间草稿即时编辑，以及断开后可见的进度与取消控件。它们属于 `:app:testDebugUnitTest`，无需模拟器。真实设备 HID 端到端测试目前延期。恢复测试时，需要满足 [README.md](README.md) 最低版本、支持蓝牙 HID 的 Android 设备和目标电脑。验证清单：

- 对每种匹配的目标布局验证大小写混合 ASCII 与标点。
- 验证不支持文本完全不产生输出，随后有效发送成功。
- 在 macOS Keyboard Setup Assistant 中，对每种文本布局以及不包含 `Mac` 的蓝牙名称，验证 ANSI 和 ISO 识别。
- 发送中断开，验证重连后可再次发送。
- 反复从通知返回，验证只有一个 MainActivity、页面／草稿不变且 HID 输入可用；也检查桌面图标／最近任务返回、蓝牙开关和权限撤销／授予。真实蓝牙输入需要已配对电脑，JVM 或通知测试不能证明它有效。

## HTTP 下载与端口更改

`respondUri` 在 IO 调度器读取可选提供方元数据。未知或负大小保持未知，不能转换为 `Content-Length: 0`。每次正文或 range 响应打开新输入流，在完成或取消时关闭。HEAD 仅返回元数据，不打开流。提供方不必提供 DocumentsContract MIME／last-modified 列或可 seek 描述符。

`ServerBinding` 处理由服务事件收集器串行化的端口更改。`AppServer` 使用注入、由应用管理的后台串行调度器；协程作用域属于服务生命周期。阻塞的启动／停止和文件 I/O 运行在 IO worker。替换服务器及其自身 WebSocket 必须先启动，再关闭原服务器和客户端。成功更改会释放旧端口，不保留退役监听器、HTTP 端口重定向或 SSE 端口迁移事件。切回时绑定全新服务器。停止、重启及服务关闭都会关闭当前监听器和客户端。

切换端口失败保留活动 `ServerState.Started` 实例，使其自身 WebSocket、消息缓存和刷新事件仍可用。独立失败事件更新前台通知，显示请求端口和仍活动的端口。没有活动监听器时，启动失败仍为 `ServerState.Error`。

服务命令使用由服务管理、不合并事件的 channel，在销毁时关闭，由 `collectServerEvents` 与端口变化一起消费。即使连续命令相同，每次 Stop／Restart 也只处理一次。端口变化绝不重放旧命令。回归测试覆盖 Stop → 更改端口 → Stop，以及端口切换失败后的真实 WebSocket 消息、缓存和刷新。

文件列表页继续使用 SSE 刷新事件，聊天页使用 WebSocket。端口切换成功后，用户须自行打开新地址。

本地测试覆盖提供方元数据、完整／range／HEAD 响应、取消清理、切换端口失败、切回、旧端口释放和真实回环 HTTP 监听器关闭。这些是 JVM／服务器集成测试，不是 Android 设备或 Windows 浏览器端到端测试。声称平台覆盖前，须在 Windows 浏览器中使用最初复现 issue #6 的提供方和文件重新检查下载。

## 开机启动

`BootSettingsHost` 在应用管理的串行设置调度器上管理加载、保存和错误状态。Activity 关闭 Host，通过感知生命周期的收集观察状态。`DataStoreBootSettings` 将 `start_on_boot` 存入现有设置 DataStore，缺失键表示禁用。写入失败保留最近已保存的 UI 值。

不导出的 `BootReceiver` 仅在凭据存储可用后处理 `BOOT_COMPLETED`。它使用 `goAsync`，超时八秒，并始终结束待处理广播。启用启动时使用现有 `remoteMessaging` 前台服务；Android 17 局域网权限必须已获准。不会打开权限对话框或 Activity。启动被拒绝时记录日志，不重试。应用初始化共享列表路径，服务启动监听器前重新加载共享文件。立即提升为前台服务，包括通知权限被拒绝的情况。

本地测试覆盖偏好持久化、Host 错误／取消、开关渲染、启动条件、超时和拒绝启动的清理、manifest receiver 投递，以及没有通知权限时的前台提升。实际重启与厂商自启动限制需要设备测试，在此环境仍未验证。

## CI Gradle 缓存

PR 测试和 main 发布构建使用同一固定版本的 `gradle/actions/setup-gradle`，设置 `cache-provider: enhanced`。保持两个工作流一致，不再并用 `setup-java` Gradle 缓存或其他 Gradle User Home 缓存。构建继续使用 `./gradlew`，由 Wrapper 定义 Gradle 版本。

action 默认仅在默认分支写缓存；PR 只恢复兼容的默认分支条目，不写自身缓存。评估缓存变化时，检查 setup-gradle 日志与作业摘要中的恢复条目，并比较冷／热耗时、传输量和仓库缓存存储。构建成功本身不能证明缓存已复用或性能提升。

## 压缩构建

PR CI 除测试外，还在没有签名机密的情况下组装 Alpha 与 Release，再为一次性 APK 副本签名，在 Android 16 / API 36 x86_64 模拟器验证 HTTP 启动和 WebSocket echo。绝不使用生产签名密钥。Debug 不运行 R8，无法发现这些变体的缺失类失败。本地未提供 release 签名环境变量时生成未签名 APK。

Netty 4.2.17 引用桌面专用 JFR 类和 LDAP 名称解析器。`app/proguard-rules.pro` 中范围很小的 `-dontwarn` 仅覆盖报告的类：Netty 在运行时检测 JFR 不可用；LDAP 引用属于 Feiya 普通 HTTP Netty 监听器未使用的 TLS 证书错误诊断。这些规则不向 Android 添加桌面 API，也不禁用证书验证。升级 Netty 或增加 TLS 监听器时重新检查，不要压制全部 R8 警告。

Netty 的 `ReflectiveChannelFactory` 调用 Channel 的公开无参构造函数。对实际使用的 `io.netty.channel` 实现保留这些构造函数；`-dontwarn` 不会保留反射入口。类名和无关成员仍可优化。仅组装成功不能证明压缩应用能启动。`ResourceLeakDetector.addExclusions` 也会在类初始化时检查声明的方法名，规则中须保留指定的分配器、泄漏感知 buffer 和引用计数工具方法；重命名或删除会导致初始化崩溃。Netty 并发跳表在 Android 上还会通过方法句柄解析 `acquireFenceFallback`，保留此方法供非 VarHandle 路径使用。字段更新器同样要求 `head`、`next`、`val` 和 `right` 保留原名称、类型和 volatile 属性。R8 无法通过 Netty 类辅助方法推断目标，因此保留具体字段，而非全部 Netty 代码。Logback XML 配置也要求指定的 Logcat appender 和反射 bean／converter 成员，保留它们才能让压缩构建仍有服务器诊断。Netty 的 `TypeParameterMatcher` 遍历泛型父类签名，除 `Signature` 外还要保留其泛型 handler、codec、resolver 基类和子类的类结构。Ktor 在 WebSocket 升级时构造 `NettyDirectEncoder`，删除其 encoder 父类可能导致 HTTP 已启动但握手失败。也须保留 `@Sharable` handler 类，以及 `@Skip` 回调元数据和名称；Netty 反射检查它们，判断 handler 能否复用及哪些回调须运行。移除 `@Sharable` 会阻止后续连接安装共享 channel initializer。

运行时检查入口为 `scripts/smoke-minified.sh SERIAL SIGNED_APK PACKAGE OUTPUT_DIR`。使用一次性模拟器：脚本拒绝覆盖已安装应用，也拒绝在端口 8080 已有 HTTP 监听器时测试。Shell 入口直接转发参数给 `scripts/smoke-minified.py`；CI 在专用模拟器直接调用 Python 脚本。两条路径都只移除自己创建的安装和端口转发。结果和启动日志输出到指定目录，CI 即使冒烟失败也上传。使用 `scripts/sign-smoke-apks.sh OUTPUT_DIR ALPHA_UNSIGNED_APK RELEASE_UNSIGNED_APK` 为此检查创建一次性签名副本。该检查覆盖启动、HTTP 和 WebSocket 流量，不覆盖蓝牙 HID 或所有生产设备配置。

## 共享 URI 完整性与设备测试夹具

共享 URI 更新在 IO worker 上串行执行，忽略重复添加，并在加载时修复旧重复项。合并文件列表也按 URI 去重，URI 同时是 Compose 列表键。仪器测试创建唯一临时文件夹具，等待异步添加，并在 `@After` 只移除自己的条目和文件；不能追加永久 `file:///test.zip` 条目。SSE 断言须有超时并传播失败，不能吞掉错误。

## 共享浏览器 UI

`web/` 是独立 npm 项目。HTML 位于 `web/src/pages`，应用、Worker、Host、Markdown 适配器和样式表位于 `web/src`。运行时库（`markdown-it` 和 `@phosphor-icons/core`）在 `web/package.json` 声明，由 `web/package-lock.json` 锁定。构建与测试工具属于 devDependencies。Dependabot 每周检查 `/web`，包括通过 lockfile 更新传递依赖。

使用 Node.js 24（`web/.nvmrc`）和 npm：

```sh
npm ci --prefix web
npm --prefix web run build
npm --prefix web test
npm --prefix web exec -- playwright install --with-deps chromium
npm --prefix web run test:browser
npm --prefix web run preview
```

`build` 用 esbuild 打包 JavaScript／CSS，复制 HTML 和仅被引用的图标资源，并从实际打包的包生成第三方许可证声明。输出为 `web/dist/feiya/*.html` 和 `web/dist/web/*`。`dist` 与 `node_modules` 都不提交。依赖变化不需要 vendoring；通过 npm 更新，并提交 `package.json` 和 `package-lock.json`。

### Android 打包

每个 Android 变体通过 AGP variant API 注册生成的 Java 资源。Gradle 先运行 `:app:installWebDependencies`（`npm ci --include=dev`），再运行 `:app:buildWeb`，然后将发行内容复制到变体专属生成资源目录。HTML、JS、CSS、图标和声明通过 classpath 进入 APK；源码、测试夹具、node_modules 和 source map 不进入。npm 安装和打包声明输入／输出，未变化时跳过两项任务。删除 node_modules 或改变 lockfile 会触发重新安装。构建失败必须使 Android 构建失败，不能打包旧发行内容。

`./gradlew :app:assembleDebug`、`:app:assembleAlpha`、`:app:assembleRelease` 和 `:app:testDebugUnitTest` 等普通命令自动生成 Web 资源。`./gradlew :app:buildWeb` 仅通过 Gradle 执行 Web 构建。CI 的测试与发布工作流均准备 Node.js；PR 工作流还针对打包输出运行 npm 测试和 Chromium 检查。两个工作流均使用以下命令比较 APK 条目与 npm 发行内容：

```sh
python3 scripts/check-web-apk.py web/dist app/build/outputs/apk/*/*.apk
```

它检查缺失、过时、意外或重复 Web 资源，并确保 APK 不含 node_modules。

Ktor 在认证前提供 `/login` 和 `/web/*`，会话验证后提供 `/` 和 `/messages`。`feiya/` 输出命名空间避免与依赖资源（如 `index.html`）冲突。`/shares`、`/shares/{index}`、`/sse` 和 `/chat` 保留协议。文件下载仍使用服务器列表索引。

### 状态、Markdown 与验证

`web/src/host.js` 管理异步状态与副作用，不依赖 DOM。`web/src/worker.js` 在每页一个 Worker 中注入网络、计时器和 Markdown 渲染器。`web/src/app.js` 渲染快照并管理同步草稿。退出页面终止 Worker；从前进／后退缓存恢复时启动新 Host。断开后不重放消息。剪贴板可用时使用安全 API，局域网 HTTP 则使用用户手势回退方式。

Worker 对每条消息只解析一次，忽略发送方提供的任何 HTML。页面渲染本地生成的 HTML，复制使用原文本。原始 HTML 保持禁用，markdown-it 的危险 URL 验证保持启用。链接以 `noopener noreferrer` 单独打开；消息图片可以加载其引用 URL。修改解析器选项或添加插件时，运行不安全输入夹具。

预览在 `127.0.0.1:4173` 使用模拟 HTTP／SSE／WebSocket 数据，提供与 APK 相同的发行内容，不是 Android 端到端测试。浏览器测试截图保存在已忽略的 `web/test/artifacts/`。`WebPagesTest` 另行通过 JVM 服务器检查真实 Ktor 路由、会话登录及生成的 JS／CSS／图标／声明资源。Web 构建保留本地资源，无需 CDN。

## 自适应 Android UI

`AppShell` 根据可用窗口宽度调整：小于 600 dp 使用底部导航，600 dp 起使用紧凑侧边 rail，1000 dp 起使用带标签侧栏（宽 224 dp，窗口至少 1200 dp 时增至 280 dp）。大字体使用带无障碍标签的图标导航，避免底部或 rail 标签裁切。导航占用空间后，`SharedFiles` 自身内容区达到 720 dp 时切换为列表／详情布局。选择按 URI 保存，可跨重排和状态恢复，文件消失时清空。紧凑详情在离开页面前优先处理系统返回。

消息内容区达到 800 dp 时显示独立 Markdown 草稿预览；紧凑窗口将输入器放在时间线下。设置、安全和 HID 表单最大宽 840 dp。HID 在内容宽 720 dp 时将任务历史移入 280 dp 辅助面板；紧凑窗口中，可滚动历史最高占内容高度 45%。主题保持动态颜色并支持深色模式。文件行显示真实名称和 URI，不显示设计概念中的占位大小或修改日期。

edge-to-edge Activity 显式使用 `adjustResize`，由 Compose IME inset 处理键盘避让，不使用自动窗口平移。消息与 HID 页面在 shell padding 已消费后应用 `imePadding()`。在设备检查键盘布局时，在紧凑和宽窗口打开、关闭键盘，验证输入器始终位于键盘上方且无多余空间。

`QrCodeHost` 管理地址发现与二维码编码，使用应用串行协调调度器和后台编码。composable 以感知生命周期的方式观察状态，对话框离开 composition 时关闭 Host。更换文件或端口创建新 Host；切换地址取消先前编码。

原生文件和导航图标由 Phosphor Core 2.1.1 路径转换为 Android VectorDrawable；MIT 许可证位于 `res/raw/phosphor_license.txt`。更新 npm 包后，运行 `python3 scripts/update-android-icons.py`，提交重新生成的原生 drawable 和许可证。

运行 `./gradlew :app:testDebugUnitTest --tests '*AdaptiveFilesTest'` 检查自适应选择、状态保存与渲染。渲染截图输出到 `app/build/outputs/design/`，使用 Robolectric 原生图形，不是设备截图。

## 品牌图标

Feiya 标记将 F 与两片后掠翼结合，使用梅紫、米白和青柠色。`res/drawable/ic_launcher_foreground.xml` 是彩色矢量源，形状保持在 adaptive 安全圆内。`ic_launcher_monochrome.xml` 提供主题图标；`ic_notification.xml` 为 24 dp 透明背景上的不透明白色轮廓。两个前台服务通知构建器都必须使用该通知 drawable。启动页共享 adaptive 前景和背景色。

修改矢量或 `values/ic_launcher_background.xml` 后，重新生成旧式各密度启动 PNG 和应用内标志：

```sh
python3 -m venv /tmp/feiya-icons-venv
/tmp/feiya-icons-venv/bin/pip install CairoSVG==2.9.1
/tmp/feiya-icons-venv/bin/python scripts/update-brand-icons.py
./gradlew :app:processDebugResources
```

CairoSVG 需要原生 Cairo 库。
