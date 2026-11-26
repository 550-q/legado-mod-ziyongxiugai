# 改动说明（Relative to 上游 rimvoid/legado v15 `3.26.09242230`）

本仓库是 [rimvoid/legado](https://github.com/rimvoid/legado) 的个人定制版（Archive 分支）。
本文档中的改动，都来自本地源码快照与上游 `e111bf66`（v15，`3.26.09242230`）的逐文件比对，
不是从某个 git 历史里导出的，所以下面的提交是按功能重新整理的归档提交，**不是原始开发过程的时间线**。

- 比对基准：上游 `e111bf66 Release Archive v15 (3.26.09242230)`
- 规模：53 个文件，+3562 / −550
- 新增文件 5 个：`ReadJumpRecord.kt`、`SelectionMagnifierTouchHandler.kt`、`FastScrollerBubblePolicy.kt`、`FastScrollBubble.kt`、`FastScrollerConfig.kt`
- 快照打包时 `app/src/main/assets/bg/*.jpg` 的文件名被转码成了乱码，内容与上游一致，本仓库保留上游原名，不把乱码名提交进来

---

## 1. 快速滚动条与气泡手柄

提交：`[快速滚动条]` + `[配置]`

上游只有一根可调「触摸区域」的滑块；现在改成「细滑块 + 胶囊气泡手柄」：

- 滑块宽度 8dp → 5dp，圆角 2.5dp，高度 40dp → 44dp（`res/values/dimens.xml`）
- 气泡是新自绘控件 `FastScrollBubble`：胶囊底色 + 三条抓握纹理，颜色跟随主题，亮色底自动反色
- 触发策略抽成 `FastScrollerBubblePolicy`，View 与 Compose 两套实现共用：
  - 不足 3 屏的列表永不弹气泡
  - 一次手势累计滑 1/3 屏（夹 48~160dp）或速度 ≥ 2 屏/秒且已滑 24dp 才弹
  - fling 期间不新弹，停下后整段手势累计 1 屏才补弹
  - 一次手势只弹一次，松手后停留 1.5 秒，期间手指碰列表立即收起
  - 滚动条 1 秒淡出、气泡 1.5 秒淡出，两条独立
- 设置项从「快速滚动条触摸区域（32/36/44/52/60dp）」改为「快速滚动条气泡大小」五档：10/18/26/34/42dp
  （`AppConfig.FAST_SCROLLER_BUBBLE_SIZE_DP`、`pref_config_other.xml`、`arrays.xml`、四语言文案）
- 新增 `FastScrollerConfig`：把偏好同步到 Compose `mutableStateOf`，改设置后不用重启即时生效
- 涉及文件：`ui/widget/compose/ComposeFastScroller.kt`（重写，+504）、`ui/widget/recycler/scroller/FastScroller.kt`（重写）、
  `FastScrollBubble.kt`、`FastScrollerBubblePolicy.kt`、`help/config/FastScrollerConfig.kt`、
  `res/layout/view_fastscroller.xml`、`ui/config/OtherConfigFragment.kt`、`ui/widget/compose/AppSettingComponents.kt`

对应 `updateLog`：35500025（快滚条重做）、35500027（按需触发/五档/弹出动画）、35500028（停留 1.5 秒）。

## 2. 书架顶栏与分组栏

提交：`[书架]`

- `MainTopBarView.onTouchEvent` 恒定返回 true，吃掉顶栏范围内的触摸：修复「搜索栏与分组栏之间的空白处」拖拽穿透到书架列表
- `RoundedTagBarView` 改为布局完成后居中（`pendingCenterIndex` + `addOnLayoutChangeListener`），启动即把当前分组滚到中间；
  `submitItems` 增加 `smooth` 参数，手动滑动分组栏后不再被刷新拽回
  （`MainTopBarView` / `RoundedTagBarView` / `BookshelfFragment1.renderGroupSelector`）
- 书架记住当前分组：`BookshelfFragment1` 在点击/左右切换/`onPause` 时保存 `AppConfig.saveTabPosition`，
  分组列表重建期间暂停写回，恢复到目标分组后再开放

对应 `updateLog`：35500029（分组栏居中）、35500030（顶栏空白穿透修复）。

## 3. 书架添加新书后回顶

提交：`[书架]`

`BooksFragment` 在列表停在顶部时检测「有新条目插到最前面」，用 `scrollToItem(0)` 回到顶部，
避免 LazyColumn 按 key 锚定使新书被挤到屏幕上方看不见（Compose 列表与网格两条路径都加了）。

对应 `updateLog`：35500032。

## 4. 添加本地书籍先选分组

提交：`[添加本地]`

`ImportBookActivity` 选中文件后不再直接导入，先弹 `GroupSelectDialog`（默认勾选当前书架分组，无分组归「未分组」），
确认后带 `groupId` 导入；`LocalBook.importFile/importFiles/importArchiveFile`、`ImportBookViewModel.addToBookshelfWithProgress`
增加 `groupId` 参数（-1 = 不改分组）；`BaseBookshelfFragment` 打开导入页时把当前分组 id 传进去。

对应 `updateLog`：35500032。

## 5. 书籍详情页

提交：`[书籍详情]`

- 「查看目录」改为自建全屏 `Dialog`：从状态栏下沿铺到导航栏上沿，左右 16dp；目录列表换成 `FastScrollRecyclerView`，
  父容器包一层 `FrameLayout`（`FastScroller` 只认 Frame/Relative/Coordinator/Constraint）
  （`BookInfoActivity.showCatalogDialog()`、`res/layout/activity_book_info.xml` 与 `layout-land/` 两份）
- 新增「目录打开方式」：滑动（占一页，原有行为）/ 按钮（详情页改为单页纵向滚动，目录走弹窗）
  （`BookInfoComponentConfig.BookInfoCatalogOpenMode`、`PreferKey.bookInfoCatalogOpenMode`、`BookInfoManageScreen`）
- 详情组件管理支持拖拽排序，且改为按组件类型定位（高度不同的行不会来回跳）
  （`BookInfoManageScreen.kt`、`BookInfoManageActivity.kt`）

对应 `updateLog`：35500031（目录弹窗全屏 + 快滚条）。目录打开方式、组件拖拽排序未在 `updateLog` 中记录，在此补记。

## 6. 阅读跳转历史

提交：`[阅读]`

上游只有一条 `lastBookProgress`；现在改成最多 30 条的跳转历史：

- 新增 `ReadJumpRecord` / `ReadPageSnapshot`，记录章节、页码、章节进度、首句摘要、时间
- `ReadBook`：目录/全文搜索/进度条跳转前 `saveCurrentBookProgress()`；书籍详情页跳转时若阅读界面不在，
  用 `stageJumpFromStoredProgress()` 暂存，阅读界面启动后 `adoptPendingJumpRecords()` 接收；退出阅读清空
- `ReadBookActivity`：返回书架时若存在跳转记录，弹出列表让用户选择恢复到哪一页（时间、章节进度、首句），
  底部「退出」才是真正返回书架；系统返回键只负责弹出/关闭这个框
- `ReadView` 提供当前页快照（普通分页/EPUB 直接模式页码来源不同）

对应 `updateLog`：未记录，在此补记。

## 7. 朗读定时与设置弹窗

提交：`[朗读]`

- 定时从「只能按分钟」改为秒级：`ttsTimerSeconds` / `ttsTimerBySecond` 保存剩余秒数，
  `timeMinute` 作为兼容用的向上取整值；暂停期间不倒计时，倒计时轮询 60s → 1s（暂停时 10s）
- 播放面板新增「自定义」档：输入 1~9999，可切分/秒，记住上次输入
- 新增「定时结束暂停朗读」开关（默认关 = 到点停止朗读）
- `ReadAloudConfigDialog` 顶部选项卡选中色 `primaryColor` → `accentColor`，深色主题下不再发灰

涉及：`BaseReadAloudService.kt`、`ReadAloud.kt`、`ReadAloudPlayerPanel.kt`、`ReadAloudConfigDialog.kt`、`AppConfig.kt`、`PreferKey.kt`。

对应 `updateLog`：35500035（选项卡配色）；定时按秒/自定义/结束时暂停未记录，在此补记。

## 8. 正文长按条（选区菜单）

提交：`[正文长按条]`

- 长按条顺序可拖动调整并保存（`PreferKey.contentSelectActionsOrder`，按 actionId 排序）
- 接入系统「文字处理」应用动作（`ACTION_PROCESS_TEXT`，翻译/记事本/浏览器等），可在配置里勾选；
  每次保存配置后重新枚举，新装应用不用重启阅读页
- 「搜索」短按改为本书全文搜索，长按为网页搜索（长按触发 `menu_web_search`）；朗读项文案在未朗读时显示「朗读所选」
- 网页搜索弹窗新增「用浏览器打开」
- 修掉长按动作后 `removeView` 造成的手势协程 "Already resumed" 崩溃：延后到下一轮主循环再收起菜单
- 输入框选区放大镜：新增 `SelectionMagnifierTouchHandler`（大圆角、160% 放大，接管左右手柄拖动），
  `ThemeEditText` 接入

涉及：`TextActionMenu.kt`、`ContentSelectMenuConfigDialog.kt`、`ContentSelectConfig.kt`、`SelectionWebSearchDialog.kt`、
`SelectionMagnifierTouchHandler.kt`、`ScrollMultiAutoCompleteTextView.kt`、`ThemeEditText.kt`。

## 9. 其他修复

提交：`[其他修复]`

- 定时任务新增/编辑页未保存退出：文案统一为公共 `exit_no_save`，「是」= 继续编辑（只关弹窗）、「否」= 放弃并退出
  （`AutoTaskEditActivity.kt`）
- 外观方案编辑（`AppearanceKitEditActivity.kt`）输入框的文字/边框/光标跟随主题配色，夜间主题下不再深底深字
- RSS 从新版订阅切回经典订阅时清掉顶部占位，避免列表上方大片空白（`RssFragment.kt`）

对应 `updateLog`：35500036 / 35500037（定时任务退出提示）。

## 10. 资源与文案

提交：`[配置]`、`[文档]`

- 四语言 `strings.xml` 同步新增：定时按秒/自定义、定时结束暂停、长按条系统动作、目录打开方式、跳转历史提示、浏览器打开等
- `AppConfig` 新增偏好项；`PreferKey` 删除 `fastScrollerTouchTargetDp`，新增 `fastScrollerBubbleSize` 等
- `updateLog.md` 顶部追加 `3.26.11240300 ~ 3.26.11251249`（35500025~35500037）条目

---

## 未在仓库中的内容

- 构建产物、APK、签名文件、手机路径（`/sdcard/...`、`/root/workspace/...`）均未提交
- 交接文档（`HANDOFF-*.md`）含设备路径与签名口令，未提交

## 已知未修问题

App 后台挂 1~2 小时后回到前台，偶发部分书架分组/「我的」页上下滑无反应（能点击、左右切分组正常、连滚动条气泡都不出现），
重启可恢复。已排除顶栏吞触摸、ViewPager 脏状态、系统冻结等；判断要么是列表视口/测量状态坏了，要么是手势被父容器吃掉，
需要带 `ScrollProbe` 日志的探针包复现取证，尚未修复。
