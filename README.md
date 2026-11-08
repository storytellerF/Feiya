<a id="feiya"></a>
# Feiya

[![Test](https://github.com/storytellerF/Feiya/actions/workflows/test.yml/badge.svg)](https://github.com/storytellerF/Feiya/actions/workflows/test.yml)
[![Build](https://github.com/storytellerF/Feiya/actions/workflows/release.yml/badge.svg)](https://github.com/storytellerF/Feiya/actions/workflows/release.yml)

Feiya 可在局域网共享文件与消息，也能将 Android 设备作为电脑的蓝牙键盘，提供文本发送和键盘设置所需的物理按键控制。

<a id="requirements"></a>
## 环境要求

- 支持蓝牙 HID 的 Android 13（API 33）或更新版本设备。
- 能与蓝牙键盘配对的电脑。
- 已开启蓝牙，并授予 Feiya 请求的蓝牙权限。

<a id="app-navigation-and-file-sharing"></a>
## 应用导航与文件共享

手机通过底部导航切换文件、消息、键盘、安全和设置页面，较宽窗口使用常驻侧边导航。大屏选择共享文件后，详情显示在列表旁；手机通过 **Back to files** 或系统返回手势回到列表。

使用 **Add files** 共享文档。文件详情提供该文件的二维码、受支持时的本地保存，以及 **Remove from sharing**；移出共享不会删除原文件。顶部栏显示服务端口，服务运行时点击可打开主二维码。重启、停止和清空共享位于更多菜单，清空列表需要确认。

<a id="send-text"></a>
## 发送文本

1. 打开 HID 页面。电脑未列出时，先通过蓝牙设置配对，再在 Feiya 中选择电脑连接。
2. 将 **Target computer keyboard layout** 与电脑启用的输入源匹配：US QWERTY（默认）、Dvorak 或 Colemak。关闭 Caps Lock。
3. 在电脑上聚焦目标文本框。
4. 在 **Text to send** 输入文本并点击 **Send**，然后检查电脑上的结果。

布局选择适用于当前应用 Activity 中所有由 Feiya 发送的文本。任务运行期间可以继续编辑草稿，已排队文本不会改变。

<a id="supported-input"></a>
### 支持的输入

文本发送支持可打印 ASCII，包括空格、数字和标点，以及 Tab、Enter、Backspace、Escape 和 Delete。Windows CRLF 换行只发送一次 Enter。消息包含中文或 emoji 等不支持字符时，会在发送任何按键之前拒绝整条消息。

布局选择告诉 Feiya 如何在电脑上生成字符，不会改变电脑输入源，也不会扩大支持字符集。

<a id="track-and-cancel-sends"></a>
## 跟踪与取消发送

HID 页面列出每项任务的目标电脑、状态和已完成按键数，任务按顺序运行。

| 状态 | 含义 |
| --- | --- |
| Queued | 等待前一任务完成。 |
| Sending | 正在向所选电脑发送按键。 |
| Cancelling | 正在停止任务，并尝试释放当前按键。 |
| Sent | 蓝牙已接受按键报告；请在电脑上确认输入。 |
| Failed | 发送已停止，任务显示原因。 |
| Cancelled | 任务在完成之前已停止。 |

使用 **Cancel** 停止单项任务，或 **Cancel all** 停止所有活动与排队任务。已发送按键无法撤销。当前 Activity 保留最近十项已结束任务，断开连接后仍可查看。

每项任务始终绑定提交时的连接。断开、切换电脑，或重新连接同一电脑都会停止旧任务。再次发送前，检查目标和已输入文本。连接或权限失败也会停止当前消息。

从运行中服务的通知打开 Feiya，会返回已有页面并保留草稿。返回应用时，如有需要会恢复蓝牙键盘注册。等待准备完成，未连接时再选择电脑。注册失败显示 **Retry**；重新连接后绝不重放旧发送任务。准备与连接尝试超时后显示可恢复错误，也可从对话框取消待完成的连接。若蓝牙尚未确认前一会话已释放，请先关闭再开启蓝牙，然后重试；清理未经确认时，Feiya 不会注册新会话。

<a id="macos-keyboard-setup"></a>
## macOS 键盘设置

macOS 要求识别键盘时，使用 Feiya HID 页面的 **macOS Keyboard Setup Assistant** 控件：

1. 选择 **ANSI** 或 **ISO**，匹配希望 macOS 识别的键盘类型。
2. 要求按左 Shift 右侧按键时，ANSI 点击 **Send physical Z key**，ISO 点击 **Send ISO extra key (next to left Shift)**。
3. 要求按右 Shift 左侧按键时，点击 **Send physical / key**。

这些控件独立于文本布局发送物理按键，只要电脑已连接就可使用，不受蓝牙名称影响。ISO 校准不会新增国际文本布局。

<a id="changing-the-server-port"></a>
## 更改服务器端口

端口更改成功后，原监听器关闭。已打开页面不会自动迁移，须打开应用显示的新地址或重新扫描二维码。新端口无法启动时，原服务保持可用。

<a id="start-on-boot"></a>
## 开机启动

开启 **Settings → Start on boot**，手机重启并首次解锁后会启动文件共享和消息服务。默认关闭。服务加载已保存端口和共享文件，不打开应用；蓝牙 HID 仍需从 HID 页面连接。关闭开关仅影响后续启动，不会停止已运行服务。

安装后至少打开 Feiya 一次。Android 17 或更新版本应先在应用中授予局域网访问权限，再依赖开机启动。部分手机还需要在电池或自启动设置允许 Feiya。强制停止或限制应用可能阻止 Android 投递启动事件；再次打开应用可恢复启动能力。

<a id="development"></a>
## 开发

源码构建、测试和当前验证限制见 [DEVELOPMENT.md](DEVELOPMENT.md)。

<a id="browser-file-sharing-and-messages"></a>
## 浏览器文件共享与消息

在同一网络的浏览器打开手机服务器地址。输入手机配置的访问密码；未设置时选择 **未设置密码，直接进入**。**文件** 页面列出手机选定的文件，点击旁边的 **下载** 保存。手机改变选择后列表自动刷新。通过 **消息** 在已连接设备间发送和复制文本。消息支持 Markdown 标题、强调、列表、引用、代码块、表格、链接和图片；复制按钮保留原始 Markdown。原始 HTML 按文本显示。连接关闭时草稿保留在输入框，重新发送前选择 **重新连接**。

浏览器页面适配桌面和手机。样式、脚本及图标随 Feiya 打包，无需外部 CDN。
