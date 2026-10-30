# Letv X3-55：Android 5.0.1（API 21）本地分支

这台电视是 Letv X3-55，Android 5.0.1（API 21）。上游启动器的 `minSdk` 是 26，装不上，所以本仓库
（`linychuo/cleanyourtv` 的 fork）在 `main` 之上多了一个提交，把启动器降到 API 21。

这份文档只记录这台电视的分支怎么维护、怎么同步、怎么装。清理电视的步骤仍以 `PROMPT.md` 为准。

## 分支与远程

| 名字 | 是什么 |
|---|---|
| `main` | 与上游 `Gathin23/cleanyourtv` 的 `main` 完全一致，不要在这里提交 |
| `letv-api21` | 只有一个提交：`Launcher: run on Android 5.0 (API 21)`，叠在 `main` 上 |
| `origin` | 你的 fork：`git@github.com:linychuo/cleanyourtv.git` |
| `upstream` | 主仓库：`https://github.com/Gathin23/cleanyourtv.git` |

本机有两个工作树，共用一个仓库：

- `/home/ivan/new/cleanyourtv`：`main`。读上游代码、跑测试、看文档都在这里。
- `/home/ivan/new/cleanyourtv-api21`：`letv-api21`。改 API 21 适配、构建 APK 在这里。

`main` 的拉取跟 `upstream/main`，推送走 `origin`；`letv-api21` 跟 `origin/letv-api21`。

## 同步上游

上游更新后，按顺序执行。

1. 主仓库代码（在 `/home/ivan/new/cleanyourtv`）：

   ```bash
   git fetch upstream
   git merge --ff-only upstream/main
   git push origin main
   ```

   `--ff-only` 保证 `main` 不产生合并提交。如果它失败，说明有人在 `main` 上提交过，先把那些提交
   挪到分支上再同步。

2. API 21 分支（在 `/home/ivan/new/cleanyourtv-api21`）：

   ```bash
   git fetch origin
   git rebase main
   ```

   `git rerere` 已开启：同一个冲突解过一次，以后 rebase 会自动套用。解决冲突后
   `git add <file>`、`git rebase --continue`。

3. 如果 `launcher/` 有变化，重新构建并装到电视上（下一节），确认正常后再推送：

   ```bash
   git push --force-with-lease origin letv-api21
   ```

   rebase 改写了提交，必须 force push；`--force-with-lease` 会在远程有你不知道的提交时拒绝。

## 构建与安装

构建需要 JDK 17 和 Android SDK platform 34、build-tools 34.0.0。在
`/home/ivan/new/cleanyourtv-api21/launcher` 下：

```bash
./gradlew --no-daemon assembleRelease
```

产物：`launcher/app/build/outputs/apk/release/app-release.apk`。

安装到电视（`<tv ip>` 换成电视地址，端口 5555）：

```bash
export TV_HOST=<tv ip>
tools/tv.py am force-stop com.gathin.tvhome
tools/tv.py --push launcher/app/build/outputs/apk/release/app-release.apk /data/local/tmp/tvhome.apk
tools/tv.py pm install -r /data/local/tmp/tvhome.apk
```

顺序不能省：**先 force-stop 再安装**。Android 5 在应用运行时替换 APK，旧 APK 的 dex 缓存删不掉
（`/data/dalvik-cache/... Permission denied`），下次启动会 `ClassNotFoundException` 崩溃；`pm install -r`
会重新生成缓存。已经崩了的话，重装同一个 APK 就能恢复。

装完启动：

```bash
tools/tv.py am start -n com.gathin.tvhome/.MainActivity
```

## 装完要确认的两件事

1. 按 Home 键回到这个启动器，重启后也是。在桌面选择器里选它并勾选“总是”。

   Android 5 没有 `cmd package set-home-activity`，用：

   ```bash
   tools/tv.py am start -a android.settings.HOME_SETTINGS
   ```

   这个 ROM 上点整行只会启动应用，必须点左边的单选圆圈。查默认 Home：

   ```bash
   tools/tv.py dumpsys package com.gathin.tvhome | grep -i mAlways
   ```

   `mAlways=true` 就是默认。

2. 设置按钮点开的是“简洁桌面设置”。本 fork 把 `config.xml` 里的 `launcher_settings_button` 默认改成了
   `true`，所以设置按钮总能打开启动器设置（主题可以写 `launcher_settings_button: false` 改回去）；
   长按设置按钮始终打开启动器菜单。

## 主题与配置

启动器从电视上的这个目录读配置：

```
/sdcard/Android/data/com.gathin.tvhome/files/
```

- `config.json`：覆盖 `config.xml` 的配置，键见 `themes/README.md`。常用的是 `columns`（列数）、
  `wallpaper`、`launcher_settings_button`。
- `wallpaper.jpg`：单张壁纸；`wallpapers/` 放多张，配 `captions.txt` 显示名字。

配置用 `tools/tv.py --push` 推。`tools/theme.sh` 在这台电视上推壁纸会失败：它依赖的 `base64` 和
`md5sum` 在 Android 5 的 toolbox 里没有，只有 `md5`。壁纸单独用 `--push` 推，再用 `md5` 核对。
配置内容变了以后，启动器会清掉电视上存下的布局选择（列数、壁纸等），以文件里的为准。

## 在电视上切主题

本 fork 在启动器设置里加了一行“主题”，遥控器就能换主题，不用 ADB。主题库要一次性推到电视上：

```bash
adb connect <tv ip>:5555
adb shell mkdir -p /sdcard/Android/data/com.gathin.tvhome/files/themes
adb push themes/. /sdcard/Android/data/com.gathin.tvhome/files/themes/
```

`themes/` 下的每个文件夹就是一个主题。不要用 `tools/tv.py --push` 推这个目录：它推目录时不递归，
带 `wallpapers/` 的主题会失败；单个 `config.json`、单张壁纸用它没问题。

遥控器路径：Home → 简洁桌面设置 → 主题 → 选一个。列表第一项“默认（内置）”会删掉覆盖层，回到
`config.xml` 的内置外观。

切换主题会改写电视上的 `config.json`、`wallpaper.jpg` 和 `wallpapers/`，并写两个文件：

- `theme_name`：刚才选的主题文件夹名，用来在列表里打勾。
- `theme_revision`：切换时间。它的值一变，启动器就会清掉电视端存的列数、行数等布局选择，让主题里的
  值生效；之后在设置里改的列数照常保存。

设置页的“主题：”是按 `config.json` 的内容和主题文件夹逐字节比对出来的。手动改过 `config.json`、
或者主题文件夹改名以后，它可能显示“默认（内置）”，在列表里重选一次就恢复。

删主题：从电视上的 `themes/` 里删掉对应文件夹即可，不用重装启动器。

## 这台电视的现状

- 原厂 `com.stv.launcher` 已禁用；第三方 Luncher 已卸载，APK 备份在 `work/luncher-0.4.0.apk`。
- 当前 Home 是 `com.gathin.tvhome`。
- ADB 保持开启：主人要求不关。正常流程最后会关无线调试和 USB 调试，这台电视跳过。

## 不要做

- 不把 `minSdk` 的改动提给上游：仓库规则里降到 26 以下属于个人 fork，上游只留一行 `SHOWCASE.md`。
- 不往仓库里放 APK、备份、截图和任何标识符（IP、Wi-Fi 名、序列号）。`work/`、`backup/`、`shots/`
  都在 `.gitignore` 里。
- 不 `pm uninstall` 系统包；要停用用 `pm disable-user --user 0`，一次一个。

## 备份与恢复

整条分支（含历史）的离线备份：`/home/ivan/new/cleanyourtv/work/letv-api21.bundle`。

从备份恢复到一个新目录：

```bash
git clone /home/ivan/new/cleanyourtv/work/letv-api21.bundle cleanyourtv-api21
cd cleanyourtv-api21
git checkout -b letv-api21 origin/letv-api21
git remote add upstream https://github.com/Gathin23/cleanyourtv.git
git remote set-url origin git@github.com:linychuo/cleanyourtv.git
```

## 每次同步后的检查

```bash
git log --oneline --graph -5
git rev-list --left-right --count main...upstream/main   # 应该是 0 0
git status --short                                       # 应该是空的
```

再加上电视上的一次重启和 Home 键确认。
