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
