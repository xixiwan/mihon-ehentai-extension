# E-Hentai (Mihon / Tachimanga extension)

A [Mihon](https://mihon.app) (Tachiyomi fork) catalogue source for
[e-hentai.org](https://e-hentai.org). NSFW content — use with the "Show NSFW
sources" option enabled.

- 语言/Language: `en` · 内容/Content: **NSFW (nsfw = 1)**
- APK: `tachiyomi-en.ehentai-v1.4.6.apk`（`ehentai/build/outputs/apk/release/`）
- 基于 extensions-lib **1.4**（经典 Observable API）——兼容 Mihon（1.4/1.6 均支持）、
  Tachimanga 及其他旧版 Tachiyomi 分支；若用 1.6 suspend API 构建，在其他程序会报
  `java.lang.VerifyError`

## 功能 (Features)

| 功能 | 说明 |
|---|---|
| 热门画廊 Popular | `GET /popular`，单页无分页 |
| 搜索 Search | 关键词 + 高级筛选（分类/最低评分/语言/页数范围/包含已删除/要求种子） |
| 画廊详情 | 标题（`#gn`/`#gj`，默认跟随列表显示的那种，可在偏好里强制；没被选中的那个标题以「其他语言标题：…」写在简介开头）、封面、上传者、标签（genre）、描述、发布日期 |
| 章节 | 一个画廊 = 单个 `Full Gallery` 章节（`chapter_number = 1`，`date_upload` = 发布于时间） |
| 阅读/下载 | 惰性解析图片地址（单页 ≈ 1 次查看页请求 + 1 次图片请求）；支持 >20 页大画廊（`?p=N` 翻页收集） |
| 原图 | 查看页 `/fullimg/` 链接，需要有效登录 Cookie，失败自动回退标准图 |
| 域名切换 | e-hentai.org（默认）/ exhentai.org（需登录 Cookie）/ 自定义镜像 |
| 请求节制 | 可选页面请求间隔（0.5s / 1s / 2s），图片下载不受限 |

## 安装 (Install)

1. 构建出 APK（见下），或直接使用 `ehentai/build/outputs/apk/release/tachiyomi-en.ehentai-v1.4.6.apk`。
2. Mihon → 设置 (Settings) → 扩展 (Extensions) → 右上角 `+` → **本地安装 (Local install)** → 选择 APK。
3. 扩展列表出现 **E-Hentai (EN)**。因为 APK 是 debug 签名、不在 Mihon 的信任签名列表里，
   它会显示为「未信任」——**点击该扩展并确认信任**即可（仅首次）。
4. 若列表里看不到：检查 设置 → 浏览 → 「显示 NSFW 源」已开启（默认开启）。
5. 完成后在 浏览 页选择 **E-Hentai (EN)** 源即可使用。

> 也可以把本仓库作为扩展仓库（`index.min.json` + `repo.json`/`index.v2.json` + `apk/` + `icon/` 已附带）
> 添加。仓库地址（Mihon/Tachimanga 均可用）：
> `https://raw.githubusercontent.com/xixiwan/mihon-ehentai-extension/main/index.min.json`

## 偏好设置 (Preferences)

在 Mihon 的扩展详情页打开「设置」：

1. **站点域名 (Domain)** — `e-hentai.org`（默认）/ `exhentai.org`（必须登录 Cookie）/ 自定义镜像；
2. **自定义域名** — 仅在域名选「自定义」时生效；
3. **会员 ID (Member ID)** — `ipb_member_id` 的值；留空则不发送登录 Cookie；
4. **密码哈希 (Pass Hash)** — `ipb_pass_hash` 的值；exhentai.org 必填；
5. **Ignéous Cookie（可选）** — `igneous` 的值；大多数账号无需填写；
   （v1.4.3 起登录 Cookie 拆分为以上三项分别输入；旧版整串 Cookie 会在首次使用时自动迁移。）
   **敏感信息仅存本机 SharedPreferences，不会出现在日志或网络请求之外**（拦截器只对 e-hentai.org / exhentai.org 域名附加）；
6. **User-Agent** — 默认浏览器 UA；被 Cloudflare 拦截（403/503）时可更换；
7. **标题语言** — 详情页/书库用哪种标题：
   - 跟随列表（默认）—— 与搜索/热门列表里显示的那种一致；
   - 英文标题（`#gn`）/ 日语标题（`#gj`）—— 强制指定。

   没被选中的那个标题会以「其他语言标题：…」放在**简介开头**（页面上只有一个标题、或两个
   标题相同时不写），方便对照两种写法，也能在书架上搜到：Mihon 的书架搜索会匹配简介，
   也可用 `desc:关键词` 精确查。

   > 这一项只决定**详情页与书库**的标题。搜索列表显示英文还是日文由站点按**账号设置**渲染，
   > 插件无法通过请求参数改变它（站点搜索参数里没有控制标题语言的项）。
   > 想让列表也是日语，请在 e-hentai 账号设置里打开「日语标题」，然后保持「跟随列表」即可两处一致。
8. **图片质量** — 标准图（默认）/ 原图（需有效 Cookie）；
9. **预解析图片地址** — 默认关；开启后进入阅读前即解析全部图片（大画廊变慢）；
10. **请求间隔** — 页面类请求节流，默认无。

## 构建 (Build)

环境要求：

- JDK 17+（本工程在 JDK 25 上验证）
- Android SDK（`compileSdk 36`、`minSdk 21`、build-tools 36；`local.properties` 或 `ANDROID_HOME` 指定 SDK 路径）
- 网络能访问 `google()` / `mavenCentral()` / `jitpack.io`（依赖 `com.github.tachiyomiorg:extensions-lib` 由 JitPack 构建，首次较慢）
- 国内网络不可直连时请配置代理：`GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"`

```bash
./gradlew :ehentai:assembleRelease
# 产物：ehentai/build/outputs/apk/release/tachiyomi-en.ehentai-v1.4.6.apk
```

运行解析/筛选单元测试（使用实测保存的 HTML 快照，离线可跑）：

```bash
./gradlew :ehentai:testDebugUnitTest
```

> 本仓库构建时用 `GRADLE_USER_HOME=<项目根>/.gradle-home` 把 Gradle 缓存留在项目内，并把调试签名密钥放在
> `keystore/debug.keystore`（仅用于本地安装；发布扩展仓库时应换成正式签名）。

## 已知限制 (Known limitations)

- **Cloudflare**：对数据中心 IP / 异常 UA 会返回 403/503；遇到时先换 UA 偏好，或检查网络环境（部分地区需代理访问）。
- **exhentai.org 必须登录 Cookie**，未填写时给出明确报错（而不是裸 403）；原图同样依赖登录态。
- **图片 CDN 校验 Referer**：图片请求带查看页 Referer；图片 URL 带 `keystamp` 时效参数，过期（通常几小时）后需重新解析，属站点正常行为。
- **搜索分页为游标制**：新版站点忽略 `page=N`，改用 `next=`/`prev=` 游标；本扩展在会话内记住上一页游标。
  若应用进程被杀后直接翻到第 2 页，会回退到第 1 页（不会崩溃）。
- **大画廊**：`getPageList` 串行抓取 `?p=N` 缩略图页（每页 20 张），网络差时较慢；建议开请求间隔避免 429。
- **副标题的写入时机**：另一个标题是在**解析详情页时**写进简介的，所以已经躺在书架里的条目
  要点进详情页刷新一次才会出现；插件无法批量改写已入库的记录。
- 站点会改版：所有选择器集中在 `Constants.kt`，更新时改一处即可；改版对照方法见 `VERIFICATION.md`。

## 代码结构

```
ehentai/src/main/kotlin/eu/kanade/tachiyomi/extension/en/ehentai/
├── Ehentai.kt            # 主类（HttpSource + ConfigurableSource，Observable 写法，lib 1.4 兼容 API）
├── EhentaiParsers.kt     # 纯解析函数（Jsoup），可单测
├── EhentaiFilters.kt     # 筛选定义 + buildSearchParams 纯函数
├── EhentaiPreferences.kt # 偏好读写 + setupPreferenceScreen
├── EhentaiInterceptor.kt # UA / 登录 Cookie 拦截器（仅本站域名）
└── Constants.kt          # 域名/偏好键/分类位掩码/选择器集中管理
```

## 站点实测记录

站点结构核对、参数语义（`f_cats` 排除掩码、游标分页、新日期格式等）与文档差异，见
[`VERIFICATION.md`](VERIFICATION.md)。

## 致谢 / Credits

本项目在开发过程中使用了 **DeepSeek AI** 对话式人工智能辅助完成代码编写、调试与文档整理
（`PROMPT.md`/`SELF-TEST.md` 记录了相关开发过程，未随仓库发布）。

## License / 免责

仅实现站点公开允许的浏览/搜索/阅读能力；exhentai 内容需要用户自行提供账号 Cookie，
不包含任何绕过登录/付费机制的代码。Cookie 属敏感信息，请勿分享。
