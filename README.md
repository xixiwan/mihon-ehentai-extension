# E-Hentai (Mihon / Tachimanga extension)

A [Mihon](https://mihon.app) (Tachiyomi fork) catalogue source for
[e-hentai.org](https://e-hentai.org). NSFW content — use with the "Show NSFW
sources" option enabled.

- 语言/Language: `en` · 内容/Content: **NSFW (nsfw = 1)**
- 两个构建，可并存，按你的应用版本选一个（详见 [`MIHON-0.21.md`](MIHON-0.21.md)）：

  | | 1.4 版 | 1.6 版 |
  |---|---|---|
  | APK | `apk/tachiyomi-en.ehentai-v1.4.7.apk` | `apk/tachiyomi-en.ehentai-v1.6.0.apk` |
  | 包名 | `…en.ehentai` | `…en.ehentai.v16` |
  | extensions-lib | **1.4**（经典 Observable API） | **1.6**（suspend API） |
  | 适用 | Mihon、Tachimanga、旧 Tachiyomi 分支 | 仅 Mihon 0.20+ |

  两个 APK 用同一把 `keystore/debug.keystore` 签名，所以仓库里公布的那一个签名指纹对两者都成立；
  源 id 也相同（换版本后书库条目、Cookie 偏好都还在）。

> **Mihon 0.21 装不上扩展的原因与修复**：0.21 起「安装/信任」都要求 APK 签名等于仓库公布的
> `signingKey`，而本仓库这两个字段原本是空字符串。已修正 `index.v2.json` / `repo.json`，
> 细节与发布清单见 [`MIHON-0.21.md`](MIHON-0.21.md)。

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
| 请求节制 | 对站点请求统一节流（默认 0.5s，可选 0 / 0.25 / 1 / 2 秒，所有并发下载共用）；查看页→图片地址缓存 30 分钟；图片 CDN 不受限 |
| 限流识别 | 识别站点封禁页 / 图片配额 / sad panda，429/503/509 自动退避重试，报错里给出可执行的建议 |

## 安装 (Install)

Mihon 0.20+ / Tachimanga 都只从**扩展仓库**安装（0.20.4 起扩展页已经没有「本地安装」入口）：

1. 设置 → 浏览 → 内容警告：打开 **NSFW**。本扩展是 NSFW，不开这一项列表里就看不到它（0.21 起会把
   未启用的内容警告过滤掉）。
2. 扩展 → 右上角 `+` → 添加仓库：
   `https://raw.githubusercontent.com/xixiwan/mihon-ehentai-extension/main/index.min.json`
   （Mihon 0.21 读到 `index.min.json` 后会自动跟到 `repo.json` → `index.v2.json`；Tachimanga 直接读 `index.min.json`）
3. 列表里会出现两条，装**与你应用匹配的那一条**：
   - `E-Hentai (1.4)` —— Mihon / Tachimanga / 旧分支通用；
   - `E-Hentai (1.6)` —— 仅 Mihon 0.20+（suspend API）。
4. 装完即信任：APK 签名与仓库公布的 `signingKey` 一致，不再需要逐个点「信任」。
5. 浏览 → 源列表里出现 **E-Hentai (EN)** 即可使用。

> 在同一个应用里两个版本**装一个就够**（功能相同、源 id 相同，只是扩展列表里会看到两条）。
> Tachimanga 请装 1.4 版：1.6 版用的是它不认识的 suspend API。

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
10. **请求间隔** — 对 e-hentai.org / exhentai.org 的请求统一节流（列表、详情、查看页、原图下载），
    默认 **0.5 秒**，可选 0 / 0.25 / 1 / 2 秒。间隔由所有并发下载共用；图片 CDN 不受影响。
    站点按 IP 限流，下载较多时建议 1–2 秒。

## 已知限制 (Known limitations)

- **1.4 / 1.6 两个构建共用同一个源 id**：换版本后书库与偏好都还在，但**同一个应用里两个都装**时
  它们是同一个源的两种实现，装一个就够（扩展列表里会出现 `E-Hentai` 与 `E-Hentai (1.6)` 两条）。
- **Tachimanga 只能装 1.6 之前的构建**：它不认识 extensions-lib 1.6 的 suspend API，加载 1.6 版会 `VerifyError`。
- **Cloudflare**：对数据中心 IP / 异常 UA 会返回 403/503；遇到时先换 UA 偏好，或检查网络环境（部分地区需代理访问）。
- **exhentai.org 必须登录 Cookie**，未填写时给出明确报错（而不是裸 403）；原图同样依赖登录态。
- **图片 CDN 校验 Referer**：图片请求带查看页 Referer；图片 URL 带 `keystamp` 时效参数，过期（通常几小时）后需重新解析，属站点正常行为。
- **搜索分页为游标制**：新版站点忽略 `page=N`，改用 `next=`/`prev=` 游标；本扩展在会话内记住上一页游标。
  若应用进程被杀后直接翻到第 2 页，会回退到第 1 页（不会崩溃）。
- **站点按 IP 限流**：下载/阅读时每张图都要请求一次查看页，请求过多会触发封禁或图片配额
  （`excessive pageloads` / `sad panda`）。v1.4.7 起默认 0.5 秒间隔、429/503/509 退避重试、查看页地址缓存
  30 分钟；批量下载时建议把间隔调到 1–2 秒，并降低应用里的并发下载数。封禁页/配额页会被识别成明确报错，
  不再表现为「没有图片」。
- **大画廊**：`getPageList` 串行抓取 `?p=N` 缩略图页（每页 20 张），带总耗时预算与重复页保护，网络差时较慢。
- **副标题的写入时机**：另一个标题是在**解析详情页时**写进简介的，所以已经躺在书架里的条目
  要点进详情页刷新一次才会出现；插件无法批量改写已入库的记录。
- 站点会改版：所有选择器集中在 `Constants.kt`，更新时改一处即可。

## 代码结构

```
ehentai/src/main/kotlin/eu/kanade/tachiyomi/extension/en/ehentai/   # 与版本无关的站点逻辑（两个构建共用）
├── Ehentai.kt            # 1.4 主类（HttpSource + ConfigurableSource，Observable 写法）
├── EhentaiParsers.kt     # 纯解析函数（Jsoup），可单测
├── EhentaiFilters.kt     # 筛选定义 + buildSearchParams 纯函数
├── EhentaiPreferences.kt # 偏好读写 + setupPreferenceScreen
├── EhentaiInterceptor.kt # UA / 登录 Cookie / 全局限流拦截器（仅本站域名）
├── EhentaiNetworking.kt  # 全局限流器、封禁/配额页识别、查看页图片地址缓存、错误文案
└── Constants.kt          # 域名/偏好键/分类位掩码/选择器/限流参数集中管理

ehentai-v16/src/main/kotlin/eu/kanade/tachiyomi/extension/en/ehentai/v16/
└── Ehentai.kt            # 1.6 主类（suspend API 接线；上面那批文件原样复用，只排除 1.4 主类）

```

1.6 版把每个 1.4 的 `xxxRequest/xxxParse` 对接到 `suspend` 接口（`getPopularManga` /
`getSearchManga` / `getMangaUpdate` / `getPageList` / `getImageUrl`），解析、筛选、偏好、拦截器
一行都没复制；站点改版仍然只改 `Constants.kt` + `EhentaiParsers.kt`。

## 致谢 / Credits

本项目在开发过程中使用了 **DeepSeek AI** 对话式人工智能辅助完成代码编写、调试与文档整理
（`PROMPT.md`/`SELF-TEST.md` 记录了相关开发过程，未随仓库发布）。

## License / 免责

仅实现站点公开允许的浏览/搜索/阅读能力；exhentai 内容需要用户自行提供账号 Cookie，
不包含任何绕过登录/付费机制的代码。Cookie 属敏感信息，请勿分享。
