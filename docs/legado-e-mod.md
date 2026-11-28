# legado-E 定制版交接说明（mod/legado-e）

本文件是给「接手继续开发的 AI / 人」看的。原始定制版说明见本仓库根目录 `docs/开发说明.md`（对应另一个基座 `mod/archive`，架构不同，**不能直接 cherry-pick**），本文件只讲 `mod/legado-e`。

## 1. 仓库与分支

- 仓库：https://github.com/550-q/legado-mod-ziyongxiugai
- 工作分支：`mod/legado-e`（唯一开发分支，所有改动都提交在这里）
- 上游：https://github.com/Luoyacheng/legado-E（阅读Sigma）
- 远端：`mine` 指向本仓库；**不要 force push，不向上游提 PR**
- 本地工作副本：`/home/user/Doubao/chats/<当前会话>/work/legado-e-mod`（若不存在可按第 2 节重新克隆）

## 2. 环境与构建

- 构建走 GitHub Actions，**不要本地编译**（本机没有 Android SDK / 只有 JDK 11）
- workflow：`.github/workflows/build-apk.yml`，push 到 `mod/legado-e` 自动触发，也可手动 dispatch
- 签名：仓库内 `.github/workflows/legado.jks`，alias `legado`，store/key 口令均 `gedoor_legado`
- 包名：`io.legado.app.release`（可与 legado-E 官方正式版覆盖安装）
- 版本号自动生成：versionName `3.YY.MMDDHH`，versionCode = Unix 分钟 + 10000000，**只增不减**

命令行流程（gh CLI 已登录 550-q）：

```bash
git add -A
git -c user.name="550-q" -c user.email="550-q@users.noreply.github.com" commit -m "改动说明"
git push mine HEAD:mod/legado-e          # 触发 Action
gh run list -R 550-q/legado-mod-ziyongxiugai --limit 1 --json databaseId,status,headSha
gh run view <id> -R 550-q/legado-mod-ziyongxiugai --json status,conclusion
gh run download <id> -R 550-q/legado-mod-ziyongxiugai -D apk/latest
```

构建约 8~12 分钟；轮询用短命令 + 等待，不要跑超长 sleep。`gh run download` 偶发 `unexpected EOF`，重试即可。

发正式版（Release）：

```bash
gh release create e-apk-<versionCode> <apk路径> -R 550-q/legado-mod-ziyongxiugai \
  --title "阅读 legado-E Mod <versionName> (<versionCode>)" --notes-file notes.md
```

发行说明只列新增功能，不列调试修复。

## 3. 架构要点（决定改动方式）

legado-E 与老基座是两个世代的代码：约 849 个 kt、0 个 Compose（全 View）、Room DB version=89；老基座约 1467 个 kt、202 个 Compose、DB 113。同名文件在 E 里普遍小得多（ReadBookActivity 651 行 vs 6513 行）。

因此：**所有移植都是「按老 View 架构重写」**，不能 cherry-pick，只参考 `mod/archive` 的实现思路。

## 4. 已交付功能（6 项，已进入 updateLog 与 Release e-apk-39930787）

1. 添加本地书籍先选分组 —— `ui/book/import/local/ImportBookActivity.kt`
2. 正文长按菜单编辑 —— `help/config/ContentSelectMenuConfig.kt`、`ui/book/read/config/ContentSelectMenuConfigDialog.kt`、`ui/book/read/TextActionMenu.kt`
3. 添加文本选择辅助放大镜（200%）—— `ui/book/read/SelectionMagnifierTouchHandler.kt`、`page/ReadView.kt`
4. 朗读定时（按秒/自定义、可暂停或停止、只对本次朗读生效）—— `service/BaseReadAloudService.kt`、`ui/book/read/config/ReadAloudDialog.kt`
5. 阅读跳转历史 —— `model/ReadJumpRecord.kt`、`model/JumpHistoryStore.kt`、`model/ReadBook.kt`、`ui/book/info/BookInfoActivity.kt`、`ui/book/read/ReadBookActivity.kt`
6. 长按「浏览器」弹网页搜索窗口 —— `help/config/SelectionSearchEngineConfig.kt`、`ui/book/read/SelectionWebSearchDialog.kt`、`res/layout/dialog_selection_web_search.xml`

文案 key 已在 `values` / `values-zh` / `values-zh-rHK` / `values-zh-rTW` 四套同步。

## 5. 硬性约定（沿用）

1. `app/src/main/assets/updateLog.md` 只在顶部追加新条目，不覆盖
2. 新增文案四语言包同步
3. `layout/` 与 `layout-land/` 有同名布局时两边一起改
4. Compose 属性用 `mutableStateOf`；函数类型参数放最后
5. 不提交构建产物、不提交签名口令
6. 不 force push，versionCode 只增不减
7. 只实现用户点名的功能，不顺手改别的

## 6. 已踩过的坑

- legado-E 的 `Magnifier.Builder` 在 compileSdk 36/37 下没有 `setZoom`/`setOffset`，只能用反射兼容
- 一个类只能有一个 `companion object`：给 `ContentTextView` 加静态字段时先看有没有已有 companion（曾因此编译失败）
- 阅读页重新分页会被状态栏显隐触发：用 `ContentTextView.suspendFormat` 在 onPause 挂起、onWindowFocusChanged(true) 先补排版再恢复，并在 onDraw 里对空 `visibleRect` 兜底 return，否则会 `IllegalStateException: visibleRect 为空`
- 长按菜单定位要用窗口坐标 + 真实宽高校正，否则压住选中文字
- BottomSheet 首次弹出只有一条高度：给根布局显式 90% 高度 + `post { behavior.setState(EXPANDED) }`
