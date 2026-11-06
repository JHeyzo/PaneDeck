# PaneDeck — NeXtep 比价版设计

> 2026-11-05 更名：仓库 JHeyzo/PaneDeck，包名 io.github.jheyzo.panedeck，模块名 PaneDeck（module.prop id=panedeck，作者 JHeyzo）。上游仍为 lujinxin/NeXtep 2.1.0（Apache-2.0），署名保留。

# 比价版设计（基于 NeXtep 2.1.0，不抄格窗代码）

底子：上游 NeXtep 2.1.0，提交 358bcdd（2026-10-29），Apache-2.0。
2026-11-05 已并入 xingmihai fork 的改动（用户确认可用，保留署名）：
底角手势独立开关（新增 BottomCornerGestureDetector /
SystemUiBottomCornerGestureMonitor，状态栏、四指、底角各走各的门）、
ColorOS 17 COUI 桌面菜单锚点/根视图修补、状态栏手势根视图按窗口类型
区分（Android 17 的 getRootViews 兜底）。未并入其签名发布工作流。
圆角开关（paneRounded）合并时保留，未被覆盖。
注意：底角手势监视器在 xingmihai 源码里注明系从格窗移植而来，
属手势触发方式，非付费功能。
ColorOS 适配是上游原生的：ColorOS 16/17、主力机 OnePlus PLK110，
作用域含 SystemUI、系统框架、oplus 桌面、分身服务、负一屏。
本版不引入格窗的解锁逻辑，不复制格窗的解包代码。

## 1. 比价功能（只借鉴行为，不照抄实现）

行为定义（公开行为层面）：
- 用户输一次商品名，多个购物应用同时搜，各开一个窗口并排看。
- 不抓价格、不下单、不读账号，只负责打开各应用的搜索结果页。

实现路径（用上游已有能力，不新造窗口系统）：
- 入口：设置页新增一个比价入口（第一版先放设置页，不动顶栏工具体系）。
- 启动顺序：先确保工作台已打开；第一个应用走
  `WorkspaceController.openFromLauncher(intent)` 进大窗，
  其余走 `WorkspaceController.launchInSlot(intent)` 依次进小窗。
- 上限：上游是 1 大窗 + 3 小窗（`WORKSPACE_SLOT_COUNT = 3`），
  所以一轮比价最多 4 个应用，超过要先提示用户删减，不要硬塞第 5 个。
- 回退规则（按顺序）：
  1. 应用没装：跳过，最后统一提示缺了哪几个；
  2. 小窗没准备好：等一小会儿再试，仍失败就提示稍后再试；
  3. 该应用版本不支持直接搜：退回打开应用首页，不伪造搜索结果。

待用户确认后才能落地的部分（不要凭空编）：
- 比价应用列表：淘宝、京东、拼多多、抖音商城等，到底是哪几个、顺序如何。
- 每个应用的搜索跳转方式：各家搜索页的 Intent / 链接格式不同，
  必须在目标机上逐个实测，实测前不在代码里写死未经验证的跳转格式。
- 主力机型与 ColorOS 版本：用于确认上游适配范围是否覆盖。

## 2. 窗口样式开关（默认小圆角，可切直角）

约定：
- 默认：大窗 16dp、小窗 12dp；
- 直角档：0dp；
- 设置里一个开关即可，不做多档半径选择。

已落地（2026-11-05，工作副本内，未编译验证）：
- `SettingsRepository.paneRounded`（`nextep_settings` 的 `pane_rounded`，
  默认 true）+ 设置页开关 + 中英文文案；
- `WorkspaceTopConfig.paneRounded` + `EXTRA_PANE_ROUNDED`，
  经 `WorkspaceConfigReceiver.readConfig` / `WorkspaceConfigClient`
  透传到 SystemUI 侧，走上游现有配置通道，未另起通道。

仍待验证的关键点（不要假设已解决）：
- 上游的窗口是任务 Surface，不是普通 View 的圆角背景，
  所以圆角不能只在设置页画个圆角框了事，必须在任务 Surface 呈现层
  （`TaskSurfaceCompat` / `SlotTaskCoordinator` 的几何与呈现路径）找
  到可用的裁剪/圆角手段，并确认它在 ColorOS 的 SystemUI 进程里生效。
  上面这一步还没做：开关目前只把选择传过去，窗口本身还不会变圆角。
- 2026-11-05 排查到的落点（未实测）：
  `TaskSurfaceCompat.writeFrame()` 目前只写 crop、矩阵/缩放、位置，
  没有圆角；`appendOwnedTransforms()` 里小窗路径也是直接调
  `writeFrame()`。所以圆角最可能的落点就是在 `writeFrame()` 里，
  按上游现有的反射写法（`setCrop` 那套 `invokeOptional` 模式），
  对同一 leash 再写一个圆角半径（候选：`SurfaceControl.Transaction`
  的 `setCornerRadius`，需在 ColorOS SystemUI 进程里实测它是否存在、
  是否对任务 leash 生效、动画帧重写时会不会被冲掉）。
  半径换算：dp 转 px 后按当前缩放比例处理，大窗 16dp、小窗 12dp
  是两个档，不是同一个半径值。
- 2026-11-05 已在工作副本里加了钩子（未编译、未实测）：
  `TaskSurfaceCompat.paneCornerRadiusPx`（默认 0，即不变），
  `writeFrame()` 里 >0 时用 `invokeOptional` 写 `setCornerRadius`，
  方法不存在时安全空操作，不会打断原有帧提交。
  还没做的：把 `WorkspaceTopConfig.paneRounded` 的值换算成 px
  写进这个字段，以及大窗/小窗两个半径的区分。
- 2026-11-05 接线已做（未编译、未实测）：
  `SystemUiRuntime.refreshWorkspaceConfig()` 里，拿到配置后按
  `paneRounded` 写 `TaskSurfaceCompat.paneCornerRadiusPx`：
  开 = 12dp × density，关 = 0。先用小窗的 12dp 统一当半径，
  大窗的 16dp 还要等按帧区分主窗/小窗才能做，不要把这版
  当成“大窗 16dp 已实现”。
- 视频/游戏铺满时圆角会裁内容，切换到直角档时应立即生效，
  不要等重启工作台。

## 3. 测试前置条件

- 本版与格窗不能同时在 LSPosed 里启用，两者都会钩 SystemUI，
  测本版前先在 LSPosed 里停用格窗并重启手机。
- 上游要求：Root（KernelSU 等）、Zygisk + LSPosed（新 libxposed API）、
  作用域按内置列表全勾，保留可恢复路径（LSPosed 停用 + 重启可恢复）。

## 4. 工具链实测（2026-11-05）

- 沙盒现状：只有 Java 17，无 Android SDK、无 gradle/kotlinc；
  上游 BUILDING.md 要求 JDK 21 + SDK 36.1 + Gradle 9.5。
  因此沙盒内直接编译不可行，需在用户 PC 上编译，
  或另行搭 JDK 21 + SDK 36 环境（下载量大，暂未动手）。

## 5. 圆角分窗半径（2026-11-05 已落地，未编译）

- SurfaceFrame 新增 cornerRadiusPx（默认 0，保持旧构造点不变）；
  writeFrame 优先用帧半径，否则回落到全局 paneCornerRadiusPx。
- 主窗路径（applyPresentation）用 mainPaneCornerRadiusPx=16dp，
  小窗两条重绘路径用 slotPaneCornerRadiusPx=12dp，
  SystemUiRuntime 在圆角开启时同时设置三者，关闭时全 0。
- 仍有缺口：ActivePresentation 初始帧仍带 0，走回落值 12dp，
  即主窗在某些动画帧上可能是 12dp 而非 16dp，需实机看效果后定。

## 6. 比价来源定案（2026-11-05 用户纠正）

- 2 号（zealler9560，格窗 3.0.5）确为 Apache 2.0 开源，
  README 明写“本仓库沿用 Apache License 2.0”，该版本比价
  未上锁、无解锁代码，因此可直接用、需保留署名（上游
  lujinxin NeXtep + 格窗 yishisanren），不得抹掉版权。
- 与 3.4.1 反编译付费版区分：后者是绕过现行付费锁，
  不属于本条；3.0.5 是作者当时公开免费发布的开源版本。
- 实际移植仍需适配：3.0.5 的比价依赖其 Workbench 场景/
  WorkbenchIntents/WorkspaceSearchPlacement，上游 2.1.0
  没有这些，需按上游 WorkspaceController 的 launchInSlot
  路径重接；默认应用列表沿用 3.0.5：购物拼多多、抖音商城、
  京东、淘宝，外卖京东外卖、淘宝闪购、美团外卖。

## 7. 比价 v1（2026-11-05 已写代码，未编译、未实机）

- 新文件 compare/CompareIntents.kt（购物/外卖两档，沿用 3.0.5
  应用列表与搜索入口，署名见上）、compare/CompareRunner.kt
  （主窗先开、随后小窗 0/1/2 依次开，间隔 900ms，单应用失败
  只记日志不中断整轮，未装应用跳过，美团主入口不通时
  回落独立外卖包）。
- 新增过载：NeXtepWindowController/WorkspaceController 的
  launchInSlot(intent, slotIndex) 与 openExternalInMain，
  原有无下标过载（恒走小窗 0）不动。
- 触发：TriggerBroadcastContract.ACTION_COMPARE，带
  EXTRA_COMPARE_QUERY / EXTRA_COMPARE_MODE（SHOPPING/DELIVERY），
  SystemUiRuntime 的 controlReceiver 已注册该动作并在工作区
  未激活时先激活再跑。注意该广播受 signature 级
  CONTROL_PERMISSION 保护，只有同签名模块应用能发。
- 界面入口（2026-11-05 已补）：设置页在圆角开关后新增
  “比价”行，点开弹窗输商品名、选购物/外卖，发送
  ACTION_COMPARE 有序广播（同签名 CONTROL_PERMISSION）
  给 SystemUi。工作区内直接弹窗未做，先用设置页入口。
- 仍缺：小窗 deep link 能否扛过上游 launchInSlot 的
  launcher-category 归一化未实机验证，900ms 间隔只是
  启发值；编译未做。

## 8. 当前阻塞

- 缺：比价应用列表、主力机型、ColorOS 版本。
- 有了这三项，才能写各应用的搜索跳转表并开始改代码；
  在此之前只做设计，不编造跳转格式、不出包。

## 9. 比价 v2（2026-11-05，格窗 3.0.5 启动路移植，用户拍板）

路线定案：主路用格窗 3.0.5 的 openSearches + WorkspaceIntentResolver，
https 兜底，复制跳转（流体云）不当主路，上游 launchInSlot 的
launcher-category 路不当主路（只留给顶栏/拖拽开应用首页）。

移植来源：~/workspace/forks_detail/zealler9560（格窗 3.0.5，
Apache-2.0，署名保留在各文件头与本节）。上游 2.1.0 没有以下
文件，均为格窗新增后移植：
- framework/WorkspaceIntentResolver.kt：原样移植，只改包名。
  VIEW 意图解析到目标 App 自己的路由 Activity，不加
  CATEGORY_LAUNCHER、不动 data，只加 NEW_TASK；解析不到
  （或解析到系统选择器）即不可打开。
- workspace/WorkspaceSearchPlacement.kt：原样移植，只改包名
  与上限：1..4（1 大窗 + 3 小窗），格窗是 1..5。另有一处
  行为改动：格窗在没有空槽时回落到已占用的槽（可替换），
  本版不回落——PaneDeck 的槽启动不能替换已占用的槽，
  塞不下就报 NoSpace，不静默替换用户正在看的应用。
- workspace/WorkspaceLaunchSequence.kt：原样移植，只改包名。
  通用定序器：先 prepare（窗口就绪），再逐个 start，
  每个在目标位置观察到就绪后才启动下一个，失败即停并
  报失败下标与已完成数。
- workspace/WorkspaceSearchReadiness.kt：原样移植，只改包名。
  路由 Activity（京东 InterfaceActivity、抖音 DeepLinkHandler、
  淘宝 MainSearchRoute、淘宝 BrowserActivity）短暂出现不算
  就绪，同一非路由任务在目标位置稳定 600ms 才算就绪。
- display/SlotTaskCoordinator.openSearches()：按格窗的
  openSearches 改写，未全盘照搬格窗的 appLaunch 机器
  （上游 2.1.0 没有那套 LaunchRequest/start/observe/commit
  机器，重写它等于重构整个槽启动器，风险远大于收益）。
  本版用已移植的 WorkspaceLaunchSequence 驱动：start =
  大窗走现有 openExternalInMain、小窗走新增的
  launchExternalInSlot；ready = 用 WorkspaceSearchReadiness
  查 TaskRepository；commit = 空操作（openExternalInMain /
  assignIntent 本身已提交落位）；prepare = 激活目标槽的
  display 并 reconcile。
- display/SlotTaskCoordinator.launchExternalInSlot()：新增。
  复用 assignIntent 的全部落位逻辑（已存在任务、已占
  用槽、pending、swap），只把归一化从 normalizeLaunchIntent
  换成 WorkspaceIntentResolver。原 assignIntent（launcher
  路）不动，其他调用方（拖拽、顶栏）行为不变。
- compare/CompareRunner.kt：重写。不再用 900ms 固定间隔
  逐个调 openExternalInMain / launchInSlot，改为一次调
  workspaceController.openSearches()，失败按
  WorkspaceLaunchSequence.Failure 记日志（下标、已完成数、
  阶段），已完成的保留、不回滚。
- compare/CompareIntents.kt：每家购物目标加 httpsFallback：
  淘宝 https://s.taobao.com/search?q=、京东
  https://search.jd.com/Search?keyword=&enc=utf-8、拼多多
  https://mobile.yangkeduo.com/search_result.html?search_key=、
  抖音 https://www.douyin.com/search/{词}?type=general
  （通用搜索非商城专属，可能需点“商品”tab）。
  以上四条 2026-11-05 用公开资料核对过，不是凭空编的。
  外卖三家无可验证的 https 搜索链接，不设 https 兜底；
  美团保留独立外卖 App 的自定义 scheme 回退。
  https 兜底只在自定义 scheme 不可解析或启动失败时用，
  且限定同一包名（App Links 进 App）；落到浏览器时淘宝/
  拼多多网页搜索可能撞登录墙，这是兜底本身的局限。
- 入口：新增 systemui/CompareDialog.kt（按格窗
  WorkbenchToolsController.showSearch 改写：输词、购物/外卖
  切换、已安装应用行变暗提示；格窗的主题外观与支付 UI
  未移植，用普通 AlertDialog + SystemUiWindowTypeResolver
  的窗口类型）。顶栏 TopAppStripView 的 actionRow 在视频
  按钮与设置按钮之间加了一个放大镜按钮，点开即此弹窗；
  NeXtepWindowController 与 SystemUiRuntime 之间用
  onCompareRequested 回调接线，现有顶栏按钮不动。
  SystemUiRuntime 把 ACTION_COMPARE 的处理抽成 runCompare()，
  设置页广播与工作区内弹窗共用同一条路；设置页入口保留，
  未删除。
- 接线：NeXtepWindowController.openSearches()、
  WorkspaceController.openSearches() 新增，旧的
  launchInSlot / openExternalInMain 不动。

仍未实机验证（不要假设已解决）：
- 每家 deep link 在目标机、目标 App 版本上是否直达搜索页，
  还是被路由 Activity 转走/回首页；https 兜底是否进 App
  还是落浏览器、是否撞登录墙。
- ColorOS 应用间跳转确认框：一轮连跳 4 个 App 时是否逐个
  弹确认、是否会打断定序（失败下标会记在日志里）。
- NoSpace 时的表现：槽已被无关应用占满时，比价是否明确
  报错、用户是否知道要先腾出槽。
- 工作区内弹窗本身：在 SystemUI 进程里弹 AlertDialog 的
  样式、焦点与输入法是否正常；顶栏放大镜按钮是否误触。
- WorkspaceSearchReadiness 的 600ms 稳定等待与
  LaunchSequence 的 5s 观察超时（50 次 × 100ms）在慢设备
  上是否够用，超时后失败下标是否准确。

## 10. 图标定稿（2026-11-05，用户已定，暂不进代码）

- 单概念：一个大窗 + 三个小窗，无顶栏/视频/手机框。几何（1024 画布）：
  主窗 (120,120)-(600,904) r40；小窗 (624,120)-(904,368)、(624,392)-(904,640)、
  (624,664)-(904,904)，各 r32；窗形纯白。
- 启动图标：白天青到靛蓝对角渐变（#0F766E → #1E3A8A），夜间近黑 #111827。
  四季变体（春/夏/秋/冬）全部砍掉，只留默认 + 夜间。
- 状态栏/磁贴与启动图标不一样：透明系——白窗形、透明底、无灰色方块
  （系统磁贴/状态栏规范：单色字形，系统着色）。
- 成品资源已备在 `icon-source/final/`：`ic_panedeck_icon_foreground.xml`、
  `ic_panedeck_icon_background.xml`、`ic_panedeck_icon_background_night.xml`、
  `ic_panedeck_tile.xml`（透明矢量）、`ic_panedeck_tile.png`（透明）、
  `webp/{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round}.webp`。
  接入时：前景/背景进 `mipmap-anydpi/ic_launcher.xml` 与 `ic_launcher_round.xml`，
  夜间版进 `ic_launcher_night.xml`，磁贴替换 `ic_nextep_tile.xml`（改名
  `ic_panedeck_tile.xml` 并更新 2 处引用），闪屏 `ic_splash_round.xml` 改指新字形，
  删除 4 个季节变体（样式/别名/自适应 XML/前景 XML/原图）与旧 NeXtep 原图。
- 状态：用户 2026-11-05 点了“就按这个换”，但同时要求“实际的代码部分先不要动”——
  设计已定稿，资源已备好，`app/` 代码一律未动，不编译、不打包、不推库，等他点头再接。
  实际小窗显示布局问题另议（顶部 16:9 宽视频 + 竖屏主窗等比缩小 + 2/3/4 个小窗）。

## 11. 磁贴灰色与圆角问题（2026-11-05，用户实机反馈）

### 磁贴灰色、点不了（已修，v3）
- 机制：`NeXtepTileService.onStartListening` → `TriggerCoordinator.queryState`，
  必须桌面 + SystemUI 两边都回 Ready 才亮，否则 `STATE_UNAVAILABLE`（灰色）。
- 排除：协议版本 v1/v2 都是 39（对过 GitHub 上 afc5cb1 的文件），不是版本问题；
  作用域里 systemui、`com.android.launcher`、`com.oplus.multiapp` 都勾着。
- 根因：用户默认桌面是 Niagara（`bitpit.launcher`，已设默认），而
  `ColorOsLauncherHook` 只接两个系统桌面（`com.android.launcher` /
  `com.oplus.launcher`），Niagara 进程里没有接收器，问过去没人回。
- 修法：`TriggerBroadcastContract.isSupportedLauncher()`（新增，
  `SUPPORTED_LAUNCHER_PACKAGES`）；默认桌面非系统桌面时，
  `TriggerCoordinator` 的 queryState / requestToggle / requestSet 三处跳过
  桌面 leg，只问、只设 SystemUI。工作台本身不受影响（它在 SystemUI 里）。
- v3 构建：`panedeck-v3-release-signed.apk`（2.1.0/vc17，SHA-256
  `2cbd727fbad5666ad521637a6bc47a2d6dafb89deccc2928a9a6c0d915202bde`），
  含圆角磁贴图标（NeXtep 原版字形，每角 r=1，`ic_panedeck_tile.xml`，
  旧 `ic_nextep_tile.xml` 已删，2 处引用已改）。未推库。

### 圆角开了仍直角（已修，待实机验证；2026-11-05 用户强行停止重开仍直角，推翻“只须重开”）
- 根因（已确认两层）：
  1. 小窗：小窗内容是虚拟显示器上的 TextureView（`VirtualDisplaySlot`），
     leash 的 `setCornerRadius` 只圆了虚拟显示器缓冲里的任务，方形的
     TextureView 本身照样把方角显示出来——leash 路径永远圆不了小窗。
  2. 主窗：配置可能晚于画窗到达（`refreshWorkspaceConfig` 只在画窗时读
     一次半径值），已画好的窗不重画；另 `setCornerRadius` 反射失败时
     `invokeOptional` 静默吞错，无日志可查。
- 修法（三处）：
  1. `TaskSwitcherView.setCornerRadiusPx()`：给 TextureView 挂
     `ViewOutlineProvider`（圆角矩形）+ `clipToOutline`，新建/替换
     TextureView 时重挂；`NeXtepWindowController.setSlotCornerRadiusPx()`
     在建槽与配置刷新时下发。
  2. `TaskSurfaceCompat.refreshCornerRadii()`（新增）：配置一到就把当前
     半径重写到所有已呈现主窗 + 小窗 leash（含 `presentation.frame` 更新，
     让后续 Shell 提交也沿用新半径）；`SystemUiRuntime` 配置刷新后调用。
  3. `writeFrame` 里 `setCornerRadius` 失败只打一次 warn 日志
     （`cornerRadiusUnsupportedLogged`），下次若仍直角可凭此区分
     “反射不支持”还是“值没到”。
- 作用域推荐：`META-INF/xposed/scope.list` 已含全部 7 个需要项
  （`com.android.systemui`、`android`、`system`、`com.android.launcher`、
  `com.oplus.launcher`、`com.oplus.multiapp`、`com.coloros.assistantscreen`），
  已在 v3/v4 APK 内验证存在；Niagara 不用加——hook 只接两个系统桌面，
  磁贴修法后也不再需要桌面进作用域。
- v4 构建（2026-11-05 16:02，含磁贴修复 + 本圆角修法）：
  `panedeck-v4-release-signed.apk`（2.1.0/vc17，SHA-256
  `b4e0764faaa816904e84e89a03a8d8ff7646e53f3ac431d581b69e36b3d941de`）。
  构建 1m21s 成功。未推库。

### v5（2026-11-05，用户实机反馈的后续三件）
- **启动图标（用户选了 3 小窗镜像版）**：已接进代码。3 小 pills 在左、大窗在右，
  白天青到靛蓝渐变（#0F766E→#1E3A8A，对角线左上青→右下靛），夜间纯色 #111827。
  前景是 `<inset>` 包 artwork PNG，所以直接重画了 artwork PNG：
  `ic_launcher_square_artwork.png`（日）、`ic_launcher_night_artwork.png`（夜）、
  `ic_launcher_artwork.png`（圆形，splash/圆图标用），以及各密度 legacy webp
  （48/72/96/144/192，方+圆）。四季版已砍：`LauncherIconStyle` 只剩 DEFAULT/NIGHT，
  `SolarTermIconCalendar.kt` 已删，`LauncherIconManager` 的自动档回归默认图标，
  设置页选择器只剩“自动/默认”，manifest 的 4 个四季 alias、4 套
  mipmap-anydpi/foreground/artwork、5 处 locale 的四季文案全部删除。
  生成脚本 `/tmp/make_panedeck_icon.py`；新图已验（方版渐变+3 白 pills+大白窗）。
- **状态栏磁贴依旧没圆角（v4 的 r=1 太细，肉眼看不见）**：`ic_panedeck_tile.xml`
  半径加大——顶栏与大窗 r=2，三个小 pills r=1.75（取满其 3.5 高度的一半），
  已渲染预览确认肉眼可见；APK 内验证过新磁贴 drawable 确实在包里（`0x7f0800a9`），
  不是没打进去。
- **默认窗口没圆角（2026-11-05 用户截图反馈）**：根因是
  `MainTaskPresentationCoordinator` 遇 HOME 直接 return（“Launcher owns transform”），
  默认窗口（桌面）从不适配也不圆角，只有打开 app 才圆。修法：HOME 按普通主窗呈现
  （`present`/`presentFromFullscreen` 同路，`reconcileForeground` 的 HOME 分支改呈
  现不再恢复，呈现成功后仍调 `notifyHomePresented()` 刷新壁纸背衬与 home 预览）。
  空槽点按桌面时走近期任务选择器的逻辑未动（按 foreground 包名判断，与呈现状态无关）。
- **圆角空出处直接黑色**：主窗 leash 圆角后露出的是全屏 app 后面被隐藏的壁纸层，
  即黑色。修法：`NeXtepContentPanel`（正好盖住主窗区域）里加
  `MainPaneBackdropView`——用 `WorkspaceWallpaperBackdrop.crop` 的磨砂壁纸
  （与顶栏/侧栏同一套）在圆角外画四个角，中间按主窗圆角半径挖洞让任务透出；
  半径由 `SystemUiRuntime` 配置刷新时下发，壁纸刷新时同步更新。圆角处现显示磨砂壁纸。
- v5 构建（2026-11-05 16:14，含以上全部）：`panedeck-v5-release-signed.apk`
  （2.1.0/vc17，SHA-256 `72c86dd4f1bf44c470b0a8c81e864e10e3bd85df6af4001ff50b0bf134e98903`，
  11,697,621 字节——四季草地图删后从 28MB 降到 11.7MB）。构建 1m10s 成功。
  APK 内验证：无四季资源，`ic_panedeck_tile`、`ic_launcher_square_artwork`、
  `ic_launcher_night_artwork` 均在。未推库。

### v6（2026-11-05，v5 实机反馈：入口消失 + 图标白色太大 + 仍直角）
- **应用入口消失（v5 回归，我的错）**：删四季 alias 时，若当时启用的是四季
  alias（如自动档的秋季），manifest 里没了它，DefaultLauncher 又是被禁用的，
  结果一个可用的启动图标都没剩，桌面找不到 PaneDeck、进不去设置页。
  修法：`LauncherIconManager.update()` 在 `KEY_STARTED` 为假时不再直接返回，
  而是先检查是否还有启用的 alias，一个没有就恢复默认图标；全新安装时默认
  alias 本来就是启用的，所以是空操作。恢复由 `MY_PACKAGE_REPLACED` /
  `BOOT_COMPLETED` 的 receiver 触发，装 v6 或重启后图标自己回来。
- **图标白色太大**：v5 的白字形占 artwork 的 82%（为补自适应图标 16.67%
  内缩而画大），应用详情页等不走内缩的渲染里看着满屏白。v6 缩到 65%
  （字形 180–844，圆形版按比例 0.54），渐变边框露出来了；已看预览确认。
- **仍直角**：v5 截图里主窗仍是方形。v5 是覆盖安装后没重启的话，SystemUI
  还跑的是旧代码，圆角修法不生效；须重启后看。若重启后仍直角，下一步加
  日志确认 HOME 任务是否走到呈现（`isMainFullscreen` 是否通过）。
- v6 构建（2026-11-05 16:20，含以上全部）：`panedeck-v6-release-signed.apk`
  （2.1.0/vc17，SHA-256 `dcaeff5de3a006b48b46e2c637e0be3e29169d4c1d14d90da39567952885c42a`，
  11,709,909 字节）。构建 45s 成功，签名验证通过（jer 证书），APK 内验证：
  新图标 artwork + 磁贴均在，无四季资源。未推库。

### v7（2026-11-05，v6 实机反馈四件）
- **四季没删干净**：桌面图标选择器里还留着“自动随季节”一行（`LauncherIconManager`
  的 AUTOMATIC 概念 + `LauncherIconPreference` 的首选项 + 两处字符串）。
  彻底删掉：AUTOMATIC 常量、`selection()` 默认改为 DEFAULT（旧存档的 "auto"
  自动落到默认）、`select()` 不再接受 auto、失败重试的闹钟调度一并删掉
  （只保留清理旧闹钟）；选择器只剩“默认”；`launcher_icon_automatic` /
  `launcher_icon_automatic_current` 五地字符串全删。夜间 alias 保留（用户
  说留着），但仍不在选择器里。
- **比价弹窗没有确认按钮**：框架 AlertDialog 的按钮栏在 SystemUI 进程里不
  渲染，弹窗只有输入框、没法提交。改法：弹窗自带一排 [取消] [搜索] 按钮
  （画在内容区里），键盘的搜索键也能提交；框架的 positive/negative
  按钮已去掉。附带：应用图标改成可点选（点一下选/不选），工作区只有
  4 个窗所以一次最多选 4 个，多了弹提示；默认选中已装的前 4 个。
- **比价少个阿里巴巴**：购物加 1688（包名 com.alibaba.wireless）。没找到
  可验证的 1688 自定义 scheme，所以主 intent 直接用 https 搜索地址
  `s.1688.com/selloffer/offer_search.htm?keywords=`，包名钉死
  com.alibaba.wireless，应用认这个 host 就在应用内打开；不认则该项在
  弹窗里置灰、跳过。待他实机验证。
- **圆角仍直角（重启后，v6）**：已确认打开的 app 有圆角（leash 的
  setCornerRadius 是生效的），只剩默认窗口（桌面）是方形。根因锁定在
  壁纸背衬：桌面本身是透明的，圆角露出的部分是系统壁纸窗（不归
  leash 管），只能靠 ContentPanel 里的磨砂壁纸背衬盖住；背衬的
  bitmap 拿不到时只画一层很淡的 tint，约等于没画。bitmap 拿不到的
  嫌疑：ColorOS 的预设壁纸走 live wallpaper 服务，`capture()` 原
  来一见 `wallpaperInfo != null` 就直接返回 null（且 screenshotWallpaper
  在 ColorOS 上也可能失败）。修法：`capture()` 的 drawable 兜底不再
  被 wallpaperInfo 否决（拿不到渲染帧也试静态 drawable）；无 bitmap
  时的 tint 加深（强度 50 时深色 alpha 16→32、浅色 10→24），让圆角
  即使没拿到壁纸也能看出磨砂边。另在配置日志里补了圆角 px 值，
  仍直角时一条 logcat 就能定位。
- **底角手势难触发**：不是失灵，是比前一版难。触发区 64dp→96dp，
  双向最小位移 24dp→20dp，平缓方向斜率上限 2.75→3.5（约 16°），
  时限 1200ms→2000ms；陡峭方向保持 1.88 不动（再松会抢系统上滑
  回桌面手势）。
- v7 构建（2026-11-05 16:33，含以上全部）：`panedeck-v7-release-signed.apk`
  （2.1.0/vc17，SHA-256 `6c340476983022c5f8e1bf01c180ace9a83bd19c440799da3fa043cd8580fd56`，
  11,714,005 字节）。构建 49s 成功，签名验证通过（jer 证书），APK 内验证：
  无 `launcher_icon_automatic` 字符串。未推库。

### v8（2026-11-05，v7 圆角仍直角——这次找到真因）
- v7 装完重启仍直角。重新读代码发现 v5–v7 的背衬从一开始就画错了地方：
  `MainPaneBackdropView` 的洞是整个 view 的 bounds，但 ContentPanel 窗口是
  全屏的（`setWindowBounds` 为看全屏 insets 把它设成 screenWidth×screenHeight），
  所以背衬只画屏幕四角；主窗的上两个角在顶栏下面（不在屏幕角上），从来没
  被盖到。且 drawable 也被 `setBounds(0,0,width,height)` 拉伸到全屏，磨砂
  壁纸和真实壁纸错位。另：桌面是透明启动器，磨砂壁纸盖在真实壁纸上几乎
  看不见，所以就算画到了角也读不出圆角。
- 修法：`MainPaneBackdropView` 加 `setPaneRect()`，洞改成主窗矩形
  （`configureWindow` 里随高亮框一起更新）；drawable 的 bounds 也改成
  主窗矩形（和真实壁纸对齐）；角落再加一层浅色磨砂（alpha 120，
  235/241/248，和顶栏一个色系），让圆角在深色壁纸上能读出来。
- v8 构建（2026-11-05 16:52，含以上）：`panedeck-v8-release-signed.apk`
  （2.1.0/vc17，SHA-256 `33387dd31724e912f2515e2c6d8bbedb31013e082d2327afdec0f1a10cd34e94`，
  11,714,005 字节）。构建 43s 成功，签名验证通过（jer 证书），dex 内验证
  含 `setPaneRect`（v7 无）。未推库。

### v9（2026-11-05，v8 圆角仍没有——按格窗开源版移植遮罩，一次性解决）
- 用户明说“不行就看那个格窗开源版”。读了 Panes 3.0.5（zealler9560）：
  上游 NeXtep 2.1.0 没有圆角框，格窗自己加了 `WorkbenchFrameDrawable`：
  **只圆自己的视觉框，绝不碰任务 leash 的原生圆角**——在主窗上盖一层
  遮罩，把方角盖住（磨砂壁纸 + 面板色），圆角是“盖出来”的不是“切出来”的。
  所以格窗的圆角不依赖 leash 的 setCornerRadius、不依赖壁纸 bitmap、
  桌面透明也不影响。这是之前 v4–v8 全部走错路的根因。
- 移植（Apache 2.0，署名保留）：`systemui/WorkbenchAppearance.kt`
  （DARK/LIGHT 面板色 + `WorkbenchFrameDrawable`，只取遮罩相关部分；
  slot/badge 助手未移植）；`NeXtepWindowController` 加 `mainFrame()` /
  `panelBackdrop()` / `refreshMainFrame()` / `resolveAppearance()`（跟随
  系统夜间模式，无偏好项）。`contentHighlightView` 的背景改成主窗框：
  `applyWallpaperBackdrop` 里设置（拖动高亮是 GradientDrawable 时不覆盖，
  拖完由 `highlightInternalDragTarget` 的 else 分支恢复成框）；圆角半径
  取 `TaskSurfaceCompat.mainPaneCornerRadiusPx`（跟随圆角开关，不是格窗
  的固定 14dp）；壁纸未抓到时遮罩退到纯色 `opaquePanel`。
- 删掉了 `MainPaneBackdropView.kt`（背面背衬，格窗没有这东西）及全部引用；
  `setMainPaneCornerRadiusPx` 改成重建框。
- v9 构建（2026-11-05 17:01，含以上）：`panedeck-v9-release-signed.apk`
  （2.1.0/vc17，SHA-256 `7fba899802d9018b9926474139f4c01024e379eef9a1c9fecaa20164044c22ec`，
  11,718,101 字节）。构建 35s 成功，签名验证通过（jer 证书），dex 内验证
  含 `WorkbenchFrameDrawable`。未推库。
- v9 实机确认（2026-11-06 08:02 截图）：主窗口圆角已好（音乐 App 主窗
  四角都圆）。但用户指出“就主窗口有圆角”——小窗还是方的。
### v10（2026-11-06，小窗圆角 + 去虚线，仍按格窗开源版）
- 小窗圆角：`display/TaskSwitcherView.kt` 按格窗的 slot 卡片改——背景换
  成圆角 GradientDrawable（`slotCard` 浅色卡片底 + 1dp `outline` 描边），
  `clipToOutline = true`，空窗和有 App 的窗一起圆；圆角取
  `slotPaneCornerRadiusPx`（跟随圆角开关，不是格窗的固定 14dp）。
  `applyCornerRadius()` 同步更新卡片背景圆角 + 自身 clip + TextureView
  clip。`WorkbenchAppearance` 补了 `slotCard` 与 `resolve(context)`
  （系统夜间模式）；`TaskSwitcherView.setAppearance()` 由控制器在小窗
  创建时和 `applyWallpaperBackdrop` 里调用（夜间模式变化时重着色）。
  构建时曾报 `cornerRadiusPx` 未初始化（init 在声明之前引用）——init
  里改写 0f，由 `applyCornerRadius()` 统一设。
- 去虚线：用户 2026-11-06 08:07 截图指出“虚线太影响观感”——侧栏画的
  分隔线（小窗之间、主窗与侧栏之间，1dp 半透明白）。格窗开源版的
  `sidebarPanel` 就是个普通 FrameLayout，根本不画分隔线；删掉
  `NeXtepWindowController.sidebarPanel` 的 `dispatchDraw` + `dividerPaint`
  （`object : FrameLayout` 改回 `FrameLayout(context).apply`）。
- v10 构建（2026-11-06 08:15，含以上）：`panedeck-v10-release-signed.apk`
  （2.1.0/vc17，SHA-256 `87851c9aca1aa7725f0a5d14ca0f7a5908644ca51c05350b3504e5cb112047c7`，
  11,718,101 字节）。构建 1m56s 成功（首轮曾因 Kotlin 编译错误失败，
  修后重跑通过；另有一轮 Gradle 空转 2m53s 失败，属构建器问题，与代码
  无关）。签名验证通过（jer 证书 `b831bc2b…17fee5`），dex 内验证含
  `WorkbenchFrameDrawable`、`setAppearance`、`slotCard`，`dividerPaint`
  已清零。未推库。待实机验证：小窗圆角（空窗与有 App 的窗）、虚线消失。
### v11（2026-11-06，用户实测 v10 后报的 6 个问题，一起修）
- 空窗依旧直角：v10 的卡片圆角只在小窗创建时设一次，配置到达时机不
  对就被跳过（有 App 的窗靠 leash 圆角所以看着圆了，空窗没 leash 就
  方着）。`NeXtepWindowController.applyWallpaperBackdrop` 里每次显示
  工作台都重设一遍小窗圆角（跟 setAppearance 同一处）。
- 虚线也没去掉：v10 删了侧栏分隔线，但小窗卡片那圈 1dp 描边本身就是
  一圈细线，看着还是虚线。`TaskSwitcherView` 的卡片描边去掉（init 与
  `setAppearance` 里的 `setStroke` 都删），只剩半透明圆角卡片底。
- 比价占窗不工作：`WorkspaceSearchPlacement.plan` 恢复格窗的占窗兜底
  （没空窗时用已占用的窗）；`SlotTaskCoordinator` 移植格窗
  `replaceSearchDestination`（被占的窗先把旧 App 送回默认屏幕、留
  在最近任务里，绝不杀掉，再把要比的 App 放进去），在 `openSearches`
  的启动步里占窗时先调用。格窗的 `wakePane` 没移植（PaneDeck 的槽
  没有 resume，自己的 park 路径也不先唤醒；比价准备步已激活目标屏）。
- 阿里巴巴国际版：比价的阿里巴巴改认国际版（包名
  `com.alibaba.intl.android.apps.poseidon`，搜
  `alibaba.com/trade/search?SearchText=`，2026-11-06 网上核对过包名
  与搜索链接），国内 1688（`com.alibaba.wireless`，s.1688.com）留作
  备选；两个都装优先用国际版。`CompareDialog` 的已安装判断与图标
  也认备选（否则只装 1688 时会显示未安装）。
- 默认顺序：购物改成京东 → 淘宝 → 拼多多 → 阿里巴巴 → 抖音商城；
  外卖本来就是京东外卖 → 淘宝闪购 → 美团外卖，不用改。顺序即优先级
  （第一个进主窗，弹窗默认选前 4 个已安装的）。
- 下拉状态栏在主窗里：`SystemUiRootTransformController` 之前把通知
  栏根视图缩进主窗（上游设计，免得盖住小窗和顶栏），用户要的是正常
  全屏通知栏。`beginShadeTransform` / `beginObservedShadeTransform`
  改成不应用变换（只记录根视图），通知栏恢复全屏；触摸映射随之回
  到屏幕坐标（未变换时不重映射）。
- v11 构建（2026-11-06 08:35，含以上）：`panedeck-v11-release-signed.apk`
  （2.1.0/vc17，SHA-256 `ceb00617e3637a297b20a7492f9e6a23a5eaaae6ae46f3b44c59d8f218b75fe3`，
  11,718,101 字节）。构建 9m39s 成功（中途服务重启打断一次，代码未丢，
  重建 /tmp 证书库后重跑）。签名验证通过（jer 证书 `b831bc2b…17fee5`），
  dex 内验证含 `replaceSearchDestination`、`poseidon`、
  `alibaba.com/trade/search`、`WorkbenchFrameDrawable`，`dividerPaint`
  仍为 0。未推库。待实机验证：空窗圆角、虚线消失、占窗比价、阿里巴巴
  国际版、比价顺序、通知栏全屏。
- 未解决（格窗开源版也没有现成答案）：主窗口里普通 app 的点按（输入法）
  位置不对——格窗也是 leash-only、只给启动器做点按重映射，所以没有参考
  实现；修法仍是二选一（system_server hook 输入窗口，或任务 bounds 改
  主窗矩形重排），待圆角确认后再动。磁贴在桌面点不开（点开一个 app 就行）：
  查了 toggle 链路（磁贴 → 有序广播 → SystemUI `setActive`，和底角手势
  同一条 `setActive`），静态没看到桌面特有的断点，待他确认现象（磁贴是
  灰的还是点了弹不可用提示）。
- 另：主窗口里 app 的点按（输入法）位置不对——只修了桌面（启动器有
  TouchCoordinateMapper 的点按重映射 hook），主窗口里的普通 app 没有
  任何重映射：任务 leash 是缩放过的，但输入系统按全屏坐标派发点按，
  所以点按会偏。修法二选一（未定）：system_server 里 hook 输入窗口
  加 leash 变换，或把任务 bounds 改成主窗矩形让 app 按主窗尺寸重排
  （改动大，会动呈现模型）。待他确认症状（哪个 app、偏多少、往哪偏）。

## §12 v12（2026-11-06 10:09，一次性修 5 项：镂空底色＋输入法 bounds＋比价 1/2/3）

- **镂空底色**：`NeXtepWindowController.panelBackdrop` 去掉 panelEnd 叠加层，只剩 wallpaper crop（与顶栏同一块底色）。主窗右上角割裂感与上半部那条线，应是 panelEnd 让镂空比顶栏暗一档所致。
- **输入法点按偏移（改 bounds 路线，用户 2026-11-06 09:41 选定）**：非 HOME 主窗任务不再走 leash 缩放，改走 `applyMainBounds` 把任务 bounds 设成主窗矩形（`TaskSurfaceCompat.presentAtBounds`，leash 恒等 scale 1/pos 0/crop null，`reapply`/`stageDisplayRotation` 对 bounds 模式也保持恒等），app 按主窗尺寸重排、输入落点与所见一致。HOME 仍走 leash（启动器自带 TouchCoordinateMapper 且假设 leash 缩放）。退出/换任务时 `clearBoundsForRestore` 把 bounds 清回空矩形恢复全屏；旋转时重算 bounds。代价：进出工作台 app 会重排，不再平滑缩放。
- **问题 1 阿里巴巴没带关键词**：poseidon 主链 URL 简化为规范形式 `https://www.alibaba.com/trade/search?SearchText=<query>`（去掉 fsb/IndexArea/CatId，疑似 App Link 没把带参长链认作搜索）。1688 兜底不变。若国际版仍不带词，说明该 app 不认此链，需再确认它实际打开的是搜索页还是首页。
- **问题 2 换词后只有主窗变**：根因是移植时丢了格窗的一行——`SlotTaskCoordinator.assignIntent` 里 app 已在目标小窗时直接 `return Result.success(Unit)`，新搜索 intent 根本没发出去。按格窗原样补回：external（比价）时 `return slots[slotIndex].launch(normalized)`，把新关键词递给已在窗内的 app 重新搜；普通拖放仍只返回成功。
- **问题 3 上滑后台不是全局（用户 2026-11-06 09:52 定：上滑先退工作台，再看全局后台；09:53 补充：默认上滑退出工作台，要后台再上滑停住）**：两处。① `ColorOsLauncherHook` 的 RecentsView hook：`setOverviewStateEnabled(true)` 且工作台激活时，先经 `TriggerCoordinator.requestSet(false, TriggerSource.RECENTS)` 退出工作台，再让总览继续显示（退出是异步的，这次停住可能仍看到旧视图，再停住一次必是全局后台）。② `MainTaskPresentationCoordinator.reconcileForeground`：前台变 HOME 且上一个主窗任务是 app 时，post 一个 `onHomeFromApp` 回调，`WorkspaceController` 接到后 `setActive(false)` 退出工作台（工作台本来就开在 HOME 上时不触发，HOME 仍正常做主窗；post 而非同步，避免在 reconcile 里重入控制器状态机）。
- **构建教训**：`gradle.properties` 开了 configuration-cache，`clean`+`assembleRelease` 会静默复用旧 APK（SHA 与 v11 完全相同、dex 里无新符号）。v12 是手动 `rm -rf app/build` 后 `--no-daemon --no-configuration-cache` 重建才拿到真新包；另 `pkill -f GradleDaemon` 会把自己的 shell 也杀掉（命令行含该字符串），以后只用 `./gradlew --stop`。
- **v12 包**：`panedeck-v12-release-signed.apk`，11,722,197 字节，SHA-256 `a824cd07dd30a064140507b65030086ea701f034c8449bd77d90dee9ab4f52b6`，2.1.0/vc17，jer 证书 `b831bc2b…17fee5`。dex 验证：`presentAtBounds`×2、`applyMainBounds`×2、`onHomeFromApp`×1、`WorkbenchFrameDrawable`×2、`dividerPaint`=0、`RECENTS`×1、`replaceSearchDestination`×1、`poseidon`×1。未推库。用户 2026-11-06 10:16–10:18 实测 v12（已重启）：QQ 下方留黑、主窗顶上有白线、阿里巴巴仍无关键词且开首页、上滑退出也没生效——四项均未修好，见 §13。

## §13 v13（2026-11-06，v12 实测反馈 + 桌面图标清理 + 双指手势）

- **QQ 下方空白（v12 bounds 回归）**：根因是 `applyMainBounds` 只设了 bounds，没把任务标成可调整尺寸——app 窗口不跟 bounds 走，保持旧尺寸， pane 下面留黑。小窗一直没这问题，因为 `SlotTaskCoordinator` 每次都先 `setTaskResizeable(TASK_RESIZE_MODE_RESIZEABLE)`。修法：`applyMainBounds` 里先 `ActivityTaskManagerCompat.setTaskResizeable(taskId, TASK_RESIZE_MODE_RESIZEABLE)` 再设 bounds；常量 `TASK_RESIZE_MODE_RESIZEABLE = 2` 从 `SlotTaskCoordinator` 私有 companion 提到 `ActivityTaskManagerCompat` 公开（`SlotTaskCoordinator` 仍用自己的 2，不动它）。
- **主窗顶上白线（v12 去 panelEnd 没修掉）**：实测像素：主窗顶边 y=512–513 有 2px 亮带（193,193,193），顶栏灰 158、QQ 白 255 之间——不是 panelEnd 色差，是主窗框 1dp 描边（border 虽是透明，但 `drawRoundRect` 仍留抗锯齿亮带）。修法：`mainFrame` 的 stroke 从 `dp(1)` 改为 0，框只画镂空圆角、不画任何边。
- **上滑退出没生效（v12 只处理了 HOME 前台）**：用户上滑停住时前台变的是 SystemUI（总览/后台），不是 HOME，`reconcileForeground` 里 `SYSTEM_UI_PACKAGE` 分支直接 return，什么都没做。修法：该分支里若上一个主窗任务是 app（非 HOME），同样 post `onHomeFromApp` 退出工作台，于是总览再出现时已是全局后台。RecentsView hook（系统启动器进程）仍保留，但默认桌面是 Niagara 时它不一定触发；SystemUI 分支是主路。
- **桌面图标设置项删掉**：四季已删，但设置页「桌面图标」一行还在，文案仍写「按季节或手动选择显示」。夜间图标是自动跟随系统暗黑/省电的，手动选已无意义，整行连同其后分隔线一起删（`SettingsScreen` 里删 row 与 divider、`launcherIconButton` 创建也删；`LauncherIconManager` 自动切夜间仍保留，`LauncherIconPreference` 成死代码先留着）。
- **双指上下滑激活工作台（底角手势不好用，用户 2026-11-06 10:21 要求）**：格窗 3.0.5 里没有双指手势（只有底角单指），按用户口述实现。新增 `TwoFingerSwipeGestureDetector`：第二指落下开始跟踪，两指重心垂直移动 ≥60dp 且垂直明显大于水平（1.5:1）、两指纵向间距变化 ≤40dp（防捏合/无关触摸）即确认，上滑下滑都触发；水平双指移动（滚动/捏合）提前放弃。接进 `SystemUiBottomCornerGestureMonitor` 同一条原始触摸流，与底角检测器并跑，任一确认即 pilfer + 切工作台；开关独立：`SettingsRepository`/`WorkspaceConfigContract`/`SystemUiRuntime` 新增 `twoFingerSwipeGestureEnabled`（默认开），设置页底角手势下方新增「双指上下滑手势」开关（中英 strings 在 `gestures.xml`，其余语言走默认英文兜底，与底角手势同例）。
- **阿里巴巴仍无关键词、开首页（未修，需 poseidon APK）**：v12 的规范 URL 仍打不开搜索。网搜证实 poseidon 的 intent filter 用通配符把所有 alibaba.com 链都收进 app，却路由到首页/Weex 页（exchangetuts 有同款崩溃报告），不是 URL 格式问题，是它根本不认搜索 deep link。`canOpen` 只证明 intent 能解析，不证明 app 会进搜索页。修法须拿到 poseidon APK 读 manifest 找真正的搜索 intent；v13 不动这条链（仍是 poseidon 主链 + 1688 兜底），等用户提供 APK 或接受 1688 为主。
- **v13 包**：`panedeck-v13-release-signed.apk`，11,730,389 字节，SHA-256 `b9d8f7f1b2f3a57eebe5f6c6397419b2e362f7845ec7fefe8d78703d1a0f47bc`，2.1.0/vc17，jer 证书 `b831bc2b…17fee5`。dex 验证：`TwoFingerSwipeGestureDetector`×5、`twoFingerSwipeGestureEnabled`×2、`setTwoFingerSwipeGestureEnabled`×2、`toggleWorkspaceFromTwoFingerSwipeGesture`×2、`TASK_RESIZE_MODE_RESIZEABLE`×1、`setTaskResizeable`×3、`presentAtBounds`×2、`applyMainBounds`×2、`onHomeFromApp`×1、`WorkbenchFrameDrawable`×2、`replaceSearchDestination`×1、`poseidon`×1；`launcher_icon_title` 资源已不存在（R8 随死代码一起清掉，桌面图标行彻底没了）。构建 11m38s（clean build 较慢，正常）。未推库。用户 2026-11-06 10:46 反馈：不只是 QQ，**所有 app 都是半屏**——主窗里所有 app 只占上半，下半黑。v13 的 resizeable 修法有个洞（见 §14）。

## §14 v14（2026-11-06，v13 的 resizeable 修法补洞 + 1688 manifest 实测）

- **所有 app 半屏（v13 修法有洞）**：v13 把 `setTaskResizeable` 放在 `applyMainBounds` 里，但放在 `if (task.bounds == bounds) return` 之后。v12 已把 bounds 设成主窗矩形的任务，再进 `applyMainBounds` 时 bounds 已相等，直接 return，永远拿不到 resizeable 标志——app 仍不重排，全是半屏。修法：把 `setTaskResizeable` 移到 bounds 相等判断之前，无条件先标 resizeable 再比 bounds。
- **1688 manifest 实测（用户 2026-11-06 10:47 发来 `_____12.16.1.0_2_cavz.apk`）**：注意这是 **1688 国内版（`com.alibaba.wireless`，12.16.1.0）**，不是国际版 poseidon（`com.alibaba.intl.android.apps.poseidon`）。`aapt2 dump xmltree` 读 manifest（5834 行）：搜索结果页 `QuickSearchResultActivity`（`com.alibaba.wireless.newsearch.result.view.QuickSearchResultActivity`）`exported=false`，只有内部 action `android.alibaba.action.search.quick.search`，**无 URL intent filter，不能 deep link**；manifest 里也没有 `s.1688.com` / `m.1688.com` 的 host（只有 `ma.m.1688.com`、`shortvideo.m.1688.com`），所以现用的 1688 兜底链 `https://s.1688.com/selloffer/offer_search.htm?keywords=` 不会进 1688 app，会落到浏览器（浏览器里能看到关键词和结果，不是在 app 内）。1688 自定义 scheme 是 `wireless1688://` / `1688://`，但搜索页不导出，scheme 也到不了。**结论：1688 搜索页无法 deep link；poseidon 仍需它自己的 APK（国际版）才能找真搜索 intent。** v14 不动阿里巴巴链，等用户发 poseidon APK 或定 1688/浏览器方案。
- **v14 包**：`panedeck-v14-release-signed.apk`，11,730,389 字节，SHA-256 `92aff3059396b8aa2766280102a48b0231ea3eebf93cf209251b45c14ec9e1f9`，2.1.0/vc17，jer 证书 `b831bc2b…17fee5`。dex 验证：`TwoFingerSwipeGestureDetector`×5、`setTaskResizeable`×3、`applyMainBounds`×2、`onHomeFromApp`×1、`WorkbenchFrameDrawable`×2。构建 5m13s。未推库。用户 2026-11-06 11:15 实测 v14：**一个没改**，主窗 app 仍半屏（截图 190303，聊天 app 只占上半、下半黑）。bounds 路线三版（v12/v13/v14）全败。

## §15 v15（2026-11-06，bounds 路线撤回 leash + 阿里巴巴改 1688 浏览器 + 比价弹窗 UI 待定）

- **主窗半屏，bounds 路线撤回（用户 2026-11-06 11:15：一个没改）**：v12 起 bounds 模式让所有 app 半屏，v13（补 resizeable）、v14（resizeable 移到 bounds 判断前）都没修好。根因未完全定位（bounds+resizeable+恒等 leash 三件套齐了 app 仍只填 2/3，疑似 ColorOS 对主窗任务的 bounds 不认，或 `reapply` 走了旧 leash 路径），不再继续磨。修法：主窗任务撤回 leash 路径（v11 行为）——`presentTask` 非 HOME 分支改回 `present`/`presentFromFullscreen` + 清 bounds，`presentedTaskId` 已在屏分支也不再调 `applyMainBounds`（残留 bounds 的先清掉再 `reapply`）。`applyMainBounds`/`presentAtBounds` 函数留着不动（死代码，以备后用）。代价：输入法点按偏移回来（leash 缩放的老问题，用户 09:41 选 bounds 就是为修它），半屏比点按偏移更伤，先保证 app 能填满主窗。
- **阿里巴巴改 1688 浏览器（用户 2026-11-06 11:16 纠正：发来的就是他装的阿里巴巴）**：他装的是 1688 国内版（`com.alibaba.wireless`），没有 poseidon 国际版。1688 搜索页不导出、无 URL intent filter、`s.1688.com` 无 host，包内 deep link 走不通。修法：阿里巴巴主链改成 `https://s.1688.com/selloffer/offer_search.htm?keywords=` **不 pin 包**（新增 `httpsUnpinned`，直接走浏览器，浏览器里能看到关键词和结果）；poseidon 的 `alibaba.com/trade/search?SearchText=` 留作 fallback（pin 包，万一他以后装国际版）。`CompareIntents` 头注释同步改掉“国际版优先”的说法。
- **比价弹窗 UI 太潦草（用户 2026-11-06 11:19，截图 190304）**：现弹窗是原生 `AlertDialog` + 默认 Button/RadioButton，白底方角、灰按钮、与深色圆角工作台完全割裂。用户 2026-11-06 11:38 选了 **B 白色圆角卡片**，并指出 v15 不该在弹窗没改完就先打包（“全改完了再打包”的老规矩）。v16 一次性把弹窗也改了。
- **v15 包**：`panedeck-v15-release-signed.apk`，11,730,389 字节，SHA-256 `bffb2226518b331257581cc704be70da3580adca270f4ff93bbd4032336ee922`，2.1.0/vc17，jer 证书 `b831bc2b…17fee5`。dex 验证：`TwoFingerSwipeGestureDetector`×5、`httpsUnpinned`×1、`presentAtBounds`×2（函数还在，主窗已不走它）。未推库。用户没装 v15（等 v16 一次性包）。

## §16 v16（2026-11-06，比价弹窗改白色圆角卡片 + v15 全部内容，一次性包）

- **比价弹窗重做（用户选 B 白色圆角卡片）**：`CompareDialog` 整版重写，只换 UI 不动行为（购物/外卖切换、app 多选上限 4、空查询 toast、键盘搜索键提交、取消/搜索均保留）。白底圆角卡片 20dp（`GradientDrawable`，窗口背景改透明以免系统底把圆角切方），标题 20sp 加粗深色、说明 13sp 灰；购物/外卖从 RadioButton 改成 pill 分段控件（浅灰底 12dp 圆角，选中段白底+加粗）；app 图标 48dp，选中时青色描边（2dp）+ 浅青底色，未装 30% 透明、未选 55%；搜索框改圆角浅灰底（12dp，无下划线，16sp）；取消改描边按钮、搜索改实心青色（#0F766E，跟启动图标同色）加粗白字，搜索按钮 flex 1、高度 48dp。`FrameLayout` 导入未用上（写时顺手带了，编译只警告不报错）。
- **v16 包**：`panedeck-v16-release-signed.apk`，11,734,485 字节，SHA-256 `a4aaf54074928b68125fd49fc71dba7548def07c4a15b224ff0757fdb7c5f495`，2.1.0/vc17，jer 证书 `b831bc2b…17fee5`。dex 验证：`CompareDialog`×18、`TwoFingerSwipeGestureDetector`×5、`httpsUnpinned`×1。构建 5m06s。未推库。用户实测（2026-11-06 11:50，三张截图）：半屏已好（主窗 app 填满）、比价能跑（京东主窗+3 小窗出“笔记本”结果）、弹窗白卡也出来了；但**横线还在**，且阿里巴巴在弹窗里灰着（没图标）——用户：1. 横线还是没修；2. 阿里巴巴不行就删了。
- **横线根因终于找到（2026-11-06 11:53，像素实测）**：v10–v13 猜的全错（小窗描边、panelEnd、主框描边都不是）。两张新截图像素扫描：横线在 y=512–513（2px），颜色 RGB≈192，上方顶栏灰 157、下方 app 白 240，位置固定在 contentTop。192 = 35% 白叠在 157 灰上（157×0.65+255×0.35≈192）。源头是 `ControlAreaPager` 里的 **`boundaryDivider`**：1dp 高、贴顶栏底部（Gravity.BOTTOM）、背景 `argb(90,255,255,255)`（35% 白），NeXtep 2.1.0 上游自带、**格窗 3.0.5 里根本没有**。v17 把它删了（字段 + addView 两处）。
- **阿里巴巴已删（用户 2026-11-06 11:50）**：`CompareIntents` 购物只剩京东→淘宝→拼多多→抖音商城，`httpsUnpinned`  helper 一并删掉。

## §17 v17（2026-11-06，横线根因修 + 阿里巴巴删 + 比价弹窗按格窗原样重做，一次性包）

- **比价弹窗按格窗原样改（用户 2026-11-06 11:52 选定）**：`CompareDialog` 按格窗 3.0.5 `WorkbenchToolsController.showSearch` 整版重写（只换 UI 与行为，不动比价启动链路）。跟工作台同款磨砂面板底色（`opaquePanel`，跟随夜间模式，28dp 圆角、1dp 描边、左右 16dp 内缩），不再是白色卡片——整体 UI 不割裂；`isForceDarkAllowed=false`，并去掉 ROM 自带圆角内板（不留第二层框）。标题 20sp 加粗、说明 13sp 灰；购物/外卖分段控件：卡片色底 13dp + 1dp 描边，选中段主题色 pill（深色浅蓝/浅色深钢蓝）白字加粗，**默认外卖在左**（格窗枚举顺序）；app 图标只展示不点选（40dp，装了亮、没装 35% 透明），**不再多选**，点搜索搜所有已装（`selectedNames=null`，`CompareRunner` 本就支持）；搜索框 50dp、卡片底+1dp 描边、左边系统放大镜图标；取消/搜索 46dp 文字按钮，取消卡片底+描边、搜索主题色实心，宽 1:1.5。搜的时候按钮变“搜索中…”变灰、控件禁用；起不来哪个 app，框下出一行小字（哪个 app、已完成几/共几、原因：窗口超时/没空窗/启动超时/被拒绝/放不进窗/启动失败），弹窗不关。为此 `CompareRunner.run` 与 `SystemUiRuntime.runCompare` 各加可选 `onComplete` 回调，`onSearch` 签名改成 `(Mode, String, (Result<Unit>) -> Unit) -> Boolean`；新增字符串 `compare_missing/compare_search_running/compare_search_failed_detail/compare_window_timeout/compare_no_space/compare_launch_timeout/compare_launch_denied/compare_place_failed/compare_launch_failed`（中英），`compare_max_four` 已删（不再多选）。格窗的购物车字形 PaneDeck 没有（无 `WorkbenchIcon`），搜索框用系统放大镜图标代替。
- **v17 包**：`panedeck-v17-release-signed.apk`，11,738,581 字节，SHA-256 `1cc6d8da4f83642382b21bb4d468eb6ee30152ba2baa5f98398e9d35bd265db2`，2.1.0/vc17，jer 证书 `b831bc2b…17fee5`。dex 验证：`CompareDialog`×18、`boundaryDivider`×0（已删）、`httpsUnpinned`×0、`阿里巴巴`×0（已删）。构建 5m50s。未推库。用户 2026-11-06 13:29 实测：弹窗格窗同款已对（外卖默认在左、3 家外卖图标、搜索框放大镜、取消/搜索 1:1.5）；两点新要求：①要点透明度；②点了搜索要三个 app 全打开弹窗才跳掉，应该是点了搜索就隐藏。

## §18 v18（2026-11-06，弹窗磨砂透明 + 点搜索立即隐藏）

- **弹窗磨砂透明（用户 2026-11-06 13:29“是不是要点透明度好看点”）**：工作台本身就是磨砂玻璃，弹窗全不透明显得悬空。窗口底色从 `opaquePanel` 改成 `WorkbenchAppearance.dialogPanel`（同款面板底色、80% 不透明，`0xCC` alpha），壁纸 faintly 透出来，字照样清楚；分段/搜索框/取消按钮仍用不透明卡片色，层次还在。
- **点搜索立即隐藏（用户 2026-11-06 13:30）**：格窗是等整轮跑完才关弹窗（busy“搜索中…”+失败状态行），用户觉得等三个 app 全开完才跳掉太慢。v18 改成：校验通过（词非空、有已装目标）就立即 `dismiss()`，再调 `onSearch` 起轮次；起不来才 toast“启动失败”。为此格窗的 busy/状态行整套撤掉：`CompareDialog.onSearch` 签名回落到 `(Mode, String) -> Boolean`，`CompareRunner.run` 与 `SystemUiRuntime.runCompare` 的可选 `onComplete` 回调一并撤掉，新增的状态行字符串（`compare_search_running/compare_search_failed_detail/compare_window_timeout/compare_no_space/compare_launch_timeout/compare_launch_denied/compare_place_failed`）也删了，只留 `compare_missing` 与 `compare_launch_failed` 两个 toast 用。
- **v18 包**：`panedeck-v18-release-signed.apk`，11,730,389 字节，SHA-256 `12f5c3347977ed3313650a30885e9efc9b860652dc07a44bd2fc682f8fddfa53`，2.1.0/vc17，jer 证书 `b831bc2b…17fee5`。dex 验证：`CompareDialog`×16、`dialogPanel`×1、`boundaryDivider`×0。构建 8m46s（比平时慢）。未推库。待实机验证：弹窗磨砂透出一点壁纸、点搜索弹窗立刻消失、app 在后面依次打开、其余 v17 项同前。

## §19 v19（2026-11-06，状态栏+四指手势全删 + 双指放宽 + 关于页加比价文案）

- **状态栏触发手势与四指手势全删（用户 2026-11-06 13:59，设置页截图佐证：两项开关都是关的、“也没用，删了”）**：设置页只剩底角手势与双指上下滑两个入口。删掉的东西：设置行（状态栏、四指、游戏中屏蔽、四指黑名单）与 `createFourFingerBlacklistButton` 整函数；`SettingsRepository` 的 4 个字段/读写/KEY；`WorkspaceConfigContract` 的 4 个字段/EXTRA/读写；`SystemUiRuntime` 的 `isStatusBarGestureEnabled`、`toggleWorkspaceFromGesture`、`fourFingerGestures` 控制器、`setWorkspaceFromFourFingerGesture`、`ACTION_FOUR_FINGER_WORKSPACE` 处理；`SystemServerHook` 的四指 hook 安装；`TriggerBroadcastContract.ACTION_FOUR_FINGER_WORKSPACE`；6 个实现文件全删（`SystemUiStatusBarGestureInstaller`、`TopCornerGestureDetector`、`SystemUiGestureCoordinator`、`FourFingerGestureController`、`FourFingerGestureDetector`、`FourFingerGestureHook`）；5 个 locale 的 `gestures.xml` 四指字符串与 `strings.xml` 的 `gesture_title/gesture_summary`。`SystemUiRootTouchHooker` 不能整删——它还管通知栏下拉变全屏的 shade transform，只把手势 coordinator 部分拆掉（`consumeUntilEnd`/`targetReference`/`coordinator` 全撤，`dispatch` 只剩 shade 跟踪 + 永远 `proceed()`）。`ControlAreaPager` 的状态栏触摸透传保留（那是让通知栏能被拉下来，不是触发手势）；`closeWorkspaceFromGesture` 保留（那是工作台退出口）。**注意**：v18 之前“状态栏触发”是唯一能从桌面外唤起工作区的入口之一，删后只剩底角+双指+磁贴/控制中心入口。
- **双指上下滑桌面触发不了（用户 2026-11-06 13:58）**：设置里开关是开的，但桌面上划不出来。根因没在代码里定位到——同一条 `monitorGestureInput` 事件流，底角手势能触发（只是不好用），说明 monitor 在桌面收得到事件，config 也同步到了 SystemUI（`workspaceConfigReady` 与 `twoFingerSwipeGestureEnabled` 均有值）。最可能的是 detector 阈值对自然手势太严：60dp 太长、竖直必须压过水平 1.5 倍（稍一斜滑就不认）、两指间距变化只容 40dp。修法（未验证，先放宽再看）：最小距离 60dp→45dp、竖直压水平 1.5→1.25、间距变化 40dp→60dp，2 秒窗口不变。若 v19 仍不触发，下一步是加 abandon 原因日志（当前只记 confirmed），让用户抓 log 看是哪一关拦的——不要再猜。
- **关于页文案（用户 2026-11-06 13:59“整体文案修改”，没给新稿）**：先按最明显的缺口改了一版草稿——`about_features` 末尾加比价（“点放大镜输入关键词，一次搜遍京东、淘宝、拼多多、抖音商城（或京东外卖、淘宝闪购、美团外卖），结果各占一窗，当场比价”，中英同步；其余 locale 未动）。标语/致敬/主窗标题/兼容性/致谢/ credits 均未动。**这版文案是 Muse 代拟的草稿，等用户纠正**，他若给新稿再整版换。
- **v19 包**：`panedeck-v19-release-signed.apk`，11,677,141 字节，SHA-256 `470b2bf8b052dce608c6571b9985dc2fefa80c54cca4e40bc07ee291b03f53fe`，2.1.0/vc18（versionCode 17→18，否则覆盖安装不升级），jer 证书 `b831bc2b…17fee5`。dex 验证：删除的手势类 ×0（`SystemUiStatusBarGestureInstaller`/`TopCornerGestureDetector`/`SystemUiGestureCoordinator`/`FourFingerGesture*` 全无），`TwoFingerSwipeGestureDetector`×5、`BottomCornerGestureDetector`×6、`CompareDialog`×16、`dialogPanel`×1；资源含“当场比价”×1。构建 4m33s（`/tmp/cacerts-hatch` 被 VM 重启清掉，重建了一份）。未推库。待实机验证：设置页只剩底角+双指两行、双指桌面上下滑能开工作区、关于页多一行比价文案。

## §20 v20（2026-11-06，关于页按“咱修改版”重写引言）

- **关于页引言重写（用户 2026-11-06 14:20 纠正：“毕竟是咱修改版了”）**：v19 只在 features 末尾加了比价一句，引言仍是 NeXtep 原文“向 OneStep 致敬……”，读起来像 PaneDeck 就是 NeXtep。用户的意思是：这是咱基于 NeXtep 改出来的版本，关于页得这么写。修法：`about_tribute` 整句换成“PaneDeck 是基于 NeXtep 的修改版：在原生 ColorOS 桌面上延续 OneStep 的侧边多任务，并加入一键比价。”（5 个 locale 同步：zh-Hans/zh-Hant/en/ja/ko；ja/ko 为 Muse 代译）。features 段的比价细则（v19 加的）保留，引言是定性、features 是细则，不重复。标语“一步，再进一步。”、主窗标题、兼容性、致谢、credits（作者 JHeyzo、基于 lujinxin 的 NeXtep）均未动——credits 本来就写对了。
- **v20 包**：`panedeck-v20-release-signed.apk`，11,677,141 字节，SHA-256 `dabe1f3c45ad6b6ac38608229002c529c68077f79d51ee261a31d7e78a335296`，2.1.0/vc19，jer 证书 `b831bc2b…17fee5`。资源验证：“基于 NeXtep 的修改版”×1；删除的手势类仍 ×0。构建 4m34s。未推库。v19 的待验证项同前，另加：关于页引言是新句。

## §21 v21（2026-11-06，双指桌面根因：窗口相对坐标 + 触发更快 + 震动反馈）

- **桌面双指上下滑根因（用户 2026-11-06 14:27 实测定性：桌面上下都不行、进 app 就行、三方桌面 Niagara）**：上下都不行排除了快捷设置冲突（上滑没有默认手势抢）；app 里行、桌面不行，且底角手势（同一条 monitor）在桌面上能用，说明 monitor 在桌面收得到事件，问题在双指检测器本身。两检测器的唯一区别是坐标源：底角用 `rawX/rawY`（屏幕原始坐标），双指用 `getX()/getY()`（相对窗口）。进 app 时窗口全屏，两者相等；三方桌面窗口有偏移，`getX()` 错位，重心/边界校验失效，检测器直接判无效。修法：双指改用 raw 坐标（`rawX = getX(i) + (rawX - getX(0))`，窗口偏移对所有指头相同，1 号指 raw 坐标由此算出），与底角同一套。**这是强推论，未实机证实**——v21 桌面能划开即实锤，还不行就加 abandon 原因日志抓现场。
- **触发延迟 + 无震动（用户 2026-11-06 14:30）**：触发距离 45dp→30dp（划更短就开）；确认时震一下（`VibrationEffect.createOneShot(20ms)`，monitor 确认分支里触发，底角+双指都有）。用户 14:30 还说了“别急着打包”，v21 第一版编到一半被叫停（kill 掉），等延迟+震动都加上才重编这版。
- **v21 包**：`panedeck-v21-release-signed.apk`，11,677,141 字节，SHA-256 `4a1bfbf024fa59334b5003dcdc9b96507b9bae2720890518a740e9b205213d6f`，2.1.0/vc20，jer 证书 `b831bc2b…17fee5`。dex 验证：`TwoFingerSwipeGestureDetector`×5、`VibrationEffect`×1、删除的手势类 ×0。构建 4m11s。未推库。待实机验证：**桌面（Niagara）双指上下滑能开工作区**、触发比以前快、确认时震一下。

## §22 v22（2026-11-06，桌面双指根因：HOME leash 查不到 + 关于页比价文案收进顶部列表）

- **桌面双指震动但进不了分窗（用户 2026-11-06 14:49）**：v21 桌面划了会震，说明检测已通（raw 坐标修好了），震就是确认信号；但震完进不去，卡在 `enter()` 的 `presentForeground`。根因：桌面时前台任务是 HOME（Niagara），ColorOS 只把 HOME 的根放进 ShellTaskOrganizer，launcher 子任务不在里面；`TaskSurfaceCompat.present` 查 leash 时 `findTaskLeash(taskId)` 没传 `allowHomeRoot`，查不到就抛“Task absent from ShellTaskOrganizer”，`enter()` 失败，工作区打不开。进 app 时前台是普通 app，organizer 里有它的 leash，所以能开。代码里 `findHomeRoot` 兜底本来就存在（只认 activityType=2 的 HOME 任务，不会把普通 app 指到别的任务上），但只有截图预览 `capturePreview` 在用，`present` 没用。修法：`present` 与 `reapply` 查 leash 时都传 `allowHomeRoot = true`（`presentAtBounds` 是已死的 bounds 路线，没动）。**仍是强推论，未实机证实**——桌面双指能开工作区即实锤。用户还补了一句“记得一开始的版本没有这个问题”，没钉到具体版本；HOME 呈现路径在 v12–v15 改过几轮（leash/bounds 来回），可能是那时埋下的回归。
- **关于页比价文案（用户 2026-11-06 14:57–14:59）**：v19 加的那句比价（括号里塞 7 个应用名）他直说“还不如第一版”，截图圈了 features 段；他给的修法是“顶部还提供里加上比价不就行了”。照办：`about_features` 删掉整句比价细则，`顶部还提供` 列表改成“媒体控制、常用应用、自定义文字和一键比价等功能”（5 个 locale 同步）。引言保持 v20（“PaneDeck 是基于 NeXtep 的修改版：在原生 ColorOS 桌面上延续 OneStep 的侧边多任务，并加入一键比价。”）——我预览里那版更口语的引言（“把 OneStep 的侧边多任务带到……”）只存在于预览 widget，没进代码。
- **v22 包**：`panedeck-v22-release-signed.apk`，11,677,141 字节，SHA-256 `3a98b9edb1e54b1a7c51ca8513ed0a8b5be4e55bb071d2f3972e5a82b9c6a159`，2.1.0/vc21，jer 证书 `b831bc2b…17fee5`。dex 验证：`allowHomeRoot`×1、删除的手势类 ×0；资源验证：“一键比价等功能”×1、“一次搜遍京东”×0、“在原生 ColorOS 桌面上延续”×1。构建 8m40s（`/tmp/cacerts-hatch` 又被清掉，重建了一份）。**未推库**——用户 15:00 改定：先打包看效果，实机验证后再决定推不推仓库。待实机验证：**桌面（Niagara）双指上下滑能开工作区**（核心）、关于页 features 段是新句、其余 v21 项同前。

## §23 v23（2026-11-06，1.0.0 + PaneDeck 改名 + 推库，浏览器分批上传）

- **桌面双指实机确认（用户 2026-11-06 15:20“重启可以了”）**：v22 覆盖安装后第一次报“还是不行”是旧代码在跑——LSPosed 日志显示手机 14:38 开机（v21），15:12 装 v22 后没重启也没重启 SystemUI；重启后桌面双指能开工作区。**HOME leash 推论实锤**。教训：报“还是不行”先查版本号/日志确认新代码已加载，别直接重诊。
- **改名（用户 2026-11-06 15:20 发现日志还写 NeXtep）**：入口类 `NeXtepHookEntry`→`PaneDeckHookEntry`（`java_init.list` 同步），`NeXtepLog` TAG 与各处 `log()` 标签 → "PaneDeck"。`NeXtepLog` 对象名未改（74 处引用，纯外观）。
- **版本号（用户 2026-11-06 15:23–15:26 定）**：不是 fork，版本号不跟上游 2.1.0 走；**PaneDeck 1.0.0**，versionCode 22（`app/build.gradle.kts` 与 `module.prop` 同步）。
- **README 重写（用户 2026-11-06 15:25“仓库的 about 和 readme 可得好好写，不能把上游摘干净了”）**：`README.md`/`README_CN.md` 重写，头图与引言保留“基于 lujinxin 的 NeXtep 2.1.0（Apache-2.0）”，新增“PaneDeck 的改动”一节，license 节写明上游版权 lujinxin、改动版权 JHeyzo；已删功能（状态栏手势、四指、游戏排除、黑名单、季节图标）从文档里去掉；入口写实（底角、双指、控制中心磁贴、外部命令、上滑退出）；上游 QQ 反馈群已删。截图/logo 仍指向上游 assets，原样保留。
- **v23 包**：`panedeck-v23-release-signed.apk`，11,677,141 字节，SHA-256 `28b7050a887b950d50ef836faf295975c51cb5a763ba22812d955780c61b9888`，**1.0.0/vc22**，jer 证书。dex 验证：`PaneDeckHookEntry`×3、`NeXtepHookEntry`×0、`allowHomeRoot`×1；资源：“一键比价等功能”×1、“基于 NeXtep 的修改版”×1。已发给用户，待实机验证（桌面双指仍能开 + LSPosed 日志显示 PaneDeck）。
- **推库（用户 2026-11-06 15:35 定：浏览器直接改，别要 PAT）**：本地提交 `f15bd6c`（91 文件，+1779/−1531，已清掉混入的预览图/图标中间件/签名文件）。`git push` 没凭证、connector 推不了删除与二进制，改走浏览器（JHeyzo 登录态）网页上传。过程：首轮只上了 25 个文件（README、21 删、2 版本字段，HEAD `7c382cf`），续传任务中途掉线；之后按每批 17 个文件分 4 个独立浏览器任务补齐——batch 1（DESIGN/比价/设置/圆角，HEAD `cf54caa`）、batch 2（systemui/task/trigger，HEAD `fcaab2a`）、batch 3（workspace/入口/图标，HEAD `80f6c05`）、batch 4（多语言文案/入口清单/gradlew/图标源，HEAD `d325327`）。网页上传的坑：一次只能往当前目录传，根目录批量传会把子目录扁平化，必须按目录分批传；一次授权文件上限约 20 个，67 个文件得分批；删除无法批量合成一个提交，最终是约 40 次提交叠在 `73f54fd` 之上（单提交的要求在网页 UI 下做不到）。**远程已与本地 `f15bd6c` 内容完全一致**（`git diff pane/main HEAD` 仅剩 `gradlew` 一个模式位：本地 100755、远程 100644，内容同一 blob——网页上传丢了可执行位；新 clone 需 `chmod +x gradlew` 才能 `./gradlew`，本地构建不受影响）。
