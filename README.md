# Feiya

[![Test](https://github.com/storytellerF/Feiya/actions/workflows/test.yml/badge.svg)](https://github.com/storytellerF/Feiya/actions/workflows/test.yml)
[![Build](https://github.com/storytellerF/Feiya/actions/workflows/release.yml/badge.svg)](https://github.com/storytellerF/Feiya/actions/workflows/release.yml)

Feiya shares files and messages on your local network and lets you use your
Android device as a Bluetooth keyboard for a computer, with text sending and
physical-key controls for keyboard setup.

## Requirements

- Android 13 (API 33) or later, on a device that supports Bluetooth HID.
- A computer that can pair with a Bluetooth keyboard.
- Bluetooth enabled and the Bluetooth permissions requested by Feiya granted.

## App navigation and file sharing

On phones, use the bottom navigation to switch between files, messages, keyboard,
security, and settings. Wider windows use a permanent side navigation. Select a
shared file to see its details beside the list on a large screen; on phones, use
**Back to files** or the system Back gesture to return to the list.

Use **Add files** to share documents. File details provide a QR code for that file,
local saving where supported, and **Remove from sharing**, which keeps the original
file. The top bar shows the service port; tap it while the service is running to
open the main QR code. Restart, stop, and clear-sharing actions are in the overflow
menu. Clearing the sharing list requires confirmation.

## Send text

1. Open the HID screen. Use Bluetooth settings to pair your computer if it is not
   listed, then select it in Feiya to connect.
2. Match **Target computer keyboard layout** to the input source enabled on your
   computer: US QWERTY (default), Dvorak, or Colemak. Turn Caps Lock off.
3. Focus the destination text field on your computer.
4. Enter text in **Text to send** and tap **Send**. Check the result on your computer.

The layout selection applies to all text sent by Feiya for the current app activity.
You can keep editing your draft while a task runs; queued text stays unchanged.

### Supported input

Text sending supports printable ASCII, including spaces, digits, and punctuation,
as well as Tab, Enter, Backspace, Escape, and Delete. Windows CRLF line endings send
one Enter. If a message contains unsupported characters, such as Chinese or emoji,
the entire message is rejected before any keys are sent.

Layout selection tells Feiya how to produce characters on your computer. It does
not change the computer's input source or expand the supported character set.

## Track and cancel sends

The HID screen lists each task's target computer, status, and completed-key count.
Tasks run in order.

| Status | Meaning |
| --- | --- |
| Queued | Waiting for an earlier task to finish. |
| Sending | Sending keys to the selected computer. |
| Cancelling | Stopping the task and attempting to release the current key. |
| Sent | Bluetooth accepted the key reports; confirm the input on your computer. |
| Failed | Sending stopped; the task shows the reason. |
| Cancelled | The task was stopped before completing. |

Use **Cancel** to stop one task or **Cancel all** to stop all active and queued work.
Keys already sent cannot be undone. The latest ten finished tasks remain visible
for the current activity, including after disconnecting.

Each task stays bound to the connection present when you submit it. Disconnecting,
switching computers, or reconnecting to the same computer stops old tasks. Check
the destination and any text already entered before sending again. Connection or
permission failures also stop the current message.

Opening Feiya from its running-service notification returns to the existing page
and keeps its draft. When you return to the app, it restores Bluetooth keyboard
registration if needed. Wait for preparation to finish, then select your computer
if disconnected. Registration failures show a **Retry** button; old send tasks are
never replayed after reconnection. Preparation and connection attempts time out with a recoverable error.
You can also cancel a pending connection from its dialog.
If Bluetooth has not confirmed that the previous session was released, turn
Bluetooth off and on before retrying; Feiya will not register another session while
cleanup is unconfirmed.

## macOS keyboard setup

When macOS asks you to identify the keyboard, use the **macOS Keyboard Setup
Assistant** controls in Feiya's HID screen:

1. Choose **ANSI** or **ISO** to match the keyboard type you want macOS to identify.
2. When asked for the key to the right of the left Shift key, tap **Send physical Z
   key** for ANSI or **Send ISO extra key (next to left Shift)** for ISO.
3. When asked for the key to the left of the right Shift key, tap **Send physical /
   key**.

These controls send physical keys independently of the text layout selection and
are available whenever a computer is connected, regardless of its Bluetooth name.
ISO calibration does not add international text layouts.

## Changing the server port

When you change the server port successfully, the previous listener closes. Open
pages do not move automatically; open the new address shown in the app or scan its
QR code again. If the new port cannot start, the previous service stays available.

## Start on boot

Enable **Settings → Start on boot** to start file sharing and messaging after the
phone restarts and is unlocked for the first time. It is off by default. The service
loads your saved port and shared files without opening the app; Bluetooth HID still
requires connecting from the HID screen. Turning the switch off affects future boots
and does not stop a running service.

Open Feiya once after installation. On Android 17 or later, grant local-network
access in the app before relying on boot startup. Some phones also require allowing
Feiya in their battery or autostart settings. Force-stopping or restricting the app
can prevent Android from delivering the boot event; open it again to restore startup.

## Development

See [DEVELOPMENT.md](DEVELOPMENT.md) for building from source, tests, and current
validation limitations.

## Browser file sharing and messages

Open the phone's server address in a browser on the same network. Enter the access
password configured on the phone, or choose **未设置密码，直接进入** if none is set.
The **文件** page lists files selected on the phone; choose **下载** beside a file
to save it. The list refreshes automatically when the phone changes its selection.
Use **消息** to send and copy text between connected devices. Messages render
Markdown headings, emphasis, lists, quotes, code blocks, tables, links, and images.
The copy button preserves the original Markdown. Raw HTML is displayed as text. If the connection
closes, the draft stays in the input; choose **重新连接** before sending again.

The browser pages adapt to desktop and phone screens. Their styles, scripts, and
icons are bundled with Feiya and do not require an external CDN.
