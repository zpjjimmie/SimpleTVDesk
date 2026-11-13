# SimpleTVDesk 极简电视桌面

适用于 Android 5.0 及以上的安卓电视。首页是一排自己选的应用。左右键选择，确定键打开。应用和图标均来自电视本机，桌面可离线使用。

## 安装与使用

1. 把 `dist/SimpleTVDesk.apk` 复制到 U 盘，在电视的文件管理器中打开安装包。如果系统询问，允许该文件管理器安装应用。也可以使用下文的 ADB 安装。
2. 从电视的应用列表打开「极简桌面」，按确定进入「添加应用」，选出常用应用。如果列表中有系统自带的「电视」应用，也可以将它固定到首页。
3. 到最右侧的「管理」继续添加应用，或选中已固定的应用，选择「向左移动」「向右移动」或「从桌面移除」。移除快捷方式后仍可再次添加。
4. 按遥控器「主菜单 / HOME」。如果出现主页选择框，选择「极简桌面」和「始终」。部分固件也可在设置中找到「应用 → 默认应用 → 主屏幕应用」。打开常用应用，再按一次 HOME，确认能回到极简桌面。

**开机行为取决于电视固件。** 可通过系统菜单完整重启一次，确认开机后是否进入极简桌面。播放中待机再唤醒时，由电视系统决定恢复哪个画面。

## 电视没有默认主页选项时

ADB 是电脑与安卓设备通信的工具。电脑只在首次配置或恢复时使用。以下命令在 Windows PowerShell 中运行，当前目录为本项目。下载并解压 [Android 官方 Platform-Tools](https://developer.android.com/tools/releases/platform-tools)，将其中的 adb.exe 目录加入本次窗口的 PATH，例如：

```powershell
$env:Path = "C:\platform-tools;$env:Path"
```

1. 电视进入「设置 → 设备偏好设置 → 关于」（部分版本直接显示「关于」），找到「版本号 / Build」，连续按七次确定。返回「开发者选项」，开启固件提供的 ADB 调试。菜单名称会随固件变化。
2. 如果电视支持局域网 ADB，让电脑和电视连接同一局域网，在网络设置查看电视 IP。以下 IP 是示例，请替换为实际地址。传统网络调试通常使用 5555；带配对码的无线调试请使用电视显示的配对端口运行 `adb pair IP:配对端口`，再连接电视显示的连接端口。如果固件只提供 USB 调试，需使用支持调试连接的接口。

```powershell
adb connect 192.168.1.50:5555
adb devices -l
```

3. 在电视上确认电脑的调试授权。`adb devices -l` 应显示电视，状态为 `device`。只连接一台设备后执行：

```powershell
adb install -r .\dist\SimpleTVDesk.apk
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Set-Desktop.ps1
```

4. 脚本打印机型、Android 用户、原主页和最终解析出的主页。出现 `Verified HOME: cn.simpletvdesk/.HomeActivity` 后，用实体 HOME 键和完整重启确认。设置成功后可断开 ADB 并关闭调试。多设备连接时，安装使用 `adb -s 设备序列号 install ...`，脚本使用 `-Serial 设备序列号`。

如果报 `Firmware did not apply HOME`，说明系统仍选择了其他主页。脚本会尝试恢复原设置并报告最终主页。部分电视的原厂桌面有更高优先级，即便系统命令返回 Success，HOME 仍会打开原厂桌面。此时仍可从应用列表使用极简桌面。脚本不禁用或卸载系统应用。若提示原主页不明确，请先在电视上选定原厂默认主页，然后再运行。

## 恢复原厂主页

通过电视设置更改的默认主页，可在同一设置页改回。通过脚本设置时，保留 `scripts/home-backups` 文件夹，重新连接同一台电视并执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Restore-Desktop.ps1
```

按实体 HOME 确认回到原主页后，可从电视设置中卸载极简桌面。

## 从源码构建

使用 JDK 17、Android SDK Platform 35、Build Tools 35.0.0 和 Windows PowerShell。设置 `JAVA_HOME`、`ANDROID_HOME` 后运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

脚本编译 Java 和原生资源，生成 `dist/SimpleTVDesk.apk`，并检查对齐与签名。请私下备份首次构建生成的 `.tools/signing` 目录，用于签署后续覆盖更新。使用其他签名重新构建后，覆盖安装会被 Android 拒绝。

## 验证记录

Android 9 TV（API 28，720p，x86）模拟器上已用方向键和确定键完成添加、移动、打开和移除应用。完整重启后固定项保留，首次进入焦点在第一项；卸载最后一个固定应用后，焦点回到「添加应用」。

该模拟器的原厂 HOME 优先级为 2，普通应用为 0。设置默认主页命令返回 Success，但按 HOME 实际仍回到原厂桌面。已验证恢复脚本可选回原主页。
