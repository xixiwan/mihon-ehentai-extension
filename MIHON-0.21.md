# Mihon 0.21 装不上扩展：原因、修复、以及 1.4 / 1.6 双版本

对应 issue：[xixiwan/mihon-ehentai-extension#6](https://github.com/xixiwan/mihon-ehentai-extension/issues/6)

## 一、为什么装不上（根因，已核对 mihon-0.21.2 源码）

Mihon 0.21.0 起，扩展的**安装**和**信任**都绑定到「扩展仓库的签名密钥」：

| 环节 | 代码 | 判定 |
|---|---|---|
| 安装 | `ExtensionInstaller.downloadAndInstall()` → `ExtensionInstaller.kt:92` | 下载到的 APK 的签名证书 SHA-256 必须出现在 `extension.store.signingKey` 里，否则抛 `Extension isn't signed with the key of <仓库名>`，状态变 `InstallStep.Error` |
| 加载 | `TrustExtension.isTrusted()` → `ExtensionLoader` | 已安装的 APK 签名必须等于某个仓库的 `signingKey`，或用户在应用里逐版本手动信任过；否则 `NotLoaded(Untrusted)`，源不出现 |

而本仓库的两个元数据文件里签名字段都是**空字符串**：

```
index.v2.json : "signingKey": ""
repo.json     : "meta".signingKeyFingerprint: ""
```

空串不可能等于任何证书摘要 ⇒ 从仓库安装必然失败；即便手动装了 APK，也会因为「未信任」而不加载。**APK 本身没问题**（debug 密钥签名，证书 SHA-256 =
`cf46498d7bf8b712d57b07864dbf46edd42fb5a74da7f5945fc75003620cb044`）。

另外两个会让人误以为「装不上」的点：

1. **内容警告过滤**：`GetExtensionsByType` 只把 `contentWarning ∈ 已启用的内容警告` 的可用扩展放进列表。
   本扩展是 NSFW，没在 设置 → 浏览 → 内容警告 里打开 NSFW 时，列表里**根本看不到它**。
2. **没有本地安装入口**：0.20.4 / 0.21.2 的扩展页只有「从仓库安装」，旧 README 里那条
   「本地安装 (Local install)」对本版本已经不适用。

## 二、修复（已改好，需要你 push）

同一个证书指纹写进两个文件（小写十六进制，与 `Hash.sha256(签名证书字节)` 的输出格式一致）：

- `index.v2.json` → `signingKey`
- `repo.json` → `meta.signingKeyFingerprint`

这样 0.21 安装与加载都以「签名 = 本仓库密钥」为准：从仓库装能装、装完即信任，用户不用再逐个点「信任」。
老版本应用（Tachimanga、旧 Mihon）不读这两个字段，行为不变。

> 已经装过 1.4.6、只是显示「未信任」的用户：仓库信息刷新后（打开扩展页 / 重启应用），
> 密钥被更新，扩展会自动重新加载，不用重装。

## 三、1.4 和 1.6 两个版本

| | 1.4 版（原有） | 1.6 版（新增） |
|---|---|---|
| 包名 | `eu.kanade.tachiyomi.extension.en.ehentai` | `eu.kanade.tachiyomi.extension.en.ehentai.v16` |
| extensions-lib | 1.4（经典 RxJava `Observable` API） | 1.6（suspend API，`tachiyomix.extensionLib = 1.6`） |
| 适用 | Mihon、Tachimanga、旧 Tachiyomi 分支 | 仅 Mihon 0.20+（0.21.2 已核对） |
| versionCode / Name | 6 / `1.4.6` | 1 / `1.6.0` |
| APK | `apk/tachiyomi-en.ehentai-v1.4.6.apk` | `apk/tachiyomi-en.ehentai-v1.6.0.apk` |
| 源 id | `57122881048805941`（lang+name+versionId 生成） | **相同**，见下 |

**为什么包名必须不同**：Android 靠包名区分应用，包名相同就是「同一扩展的两个版本」，后装的会覆盖先装的。
1.6 版用独立包名，因此可以和 1.4 版并存。

**为什么源 id 保持相同**：源 id 由 `lang + name + versionId` 生成，两者完全一致，所以
书库里的条目、源偏好（Cookie 等存在同一个 `source_<id>` 文件里）在换版本后仍然有效。
代价是：**同一个应用里两个版本同时安装时**，它们共用同一个源 id，功能也完全一样，装一个就够
（扩展列表里会看到 `E-Hentai` 与 `E-Hentai (1.6)` 两条）。

1.6 版的 API 接线（`ehentai-v16/src/main/kotlin/.../v16/Ehentai.kt`）：

| 1.6 接口 | 对应原 1.4 实现 |
|---|---|
| `getPopularManga(page)` | `popularMangaRequest/Parse` |
| `getSearchManga(page, query, filters)` | `searchMangaRequest/Parse`（游标分页逻辑原样保留） |
| `getMangaUpdate(manga, chapters, fetchDetails, fetchChapters)` | `mangaDetailsParse` + `chapterListParse` |
| `getPageList(chapter)` | `fetchPageList` |
| `getImageUrl(page)` | `imageUrlRequest/Parse` |
| `imageRequest(page)` | 同名（图片请求带查看页 Referer） |

解析、筛选、偏好、拦截器代码**完全不重复**：1.6 版直接编 1.4 模块里的同一批文件
（只排除 1.4 的主类），站点改版只改 `Constants.kt` / `EhentaiParsers.kt` 一处。

## 四、你要做的（发布清单）

1. 提交并 push 这些改动（新增 `ehentai-v16/`、`offline/`，更新三个 json、`icon/…v16.png`、README）。
2. `index.v2.json` 里 1.6 那条的 `apkUrl` 现在指向
   `https://raw.githubusercontent.com/xixiwan/mihon-ehentai-extension/main/apk/tachiyomi-en.ehentai-v1.6.0.apk`
   —— push 完立刻可用。若你想像 1.4.6 那样走 Release 资产，建一个 `v1.6.0` Release、上传这个 APK，
   再把 `apkUrl` 换成 `.../releases/download/v1.6.0/tachiyomi-en.ehentai-v1.6.0.apk`。
3. 应用里：
   - 设置 → 浏览 → 内容警告：打开 **NSFW**（否则列表里看不到本扩展）；
   - 扩展 → 右上角 `+` → 添加仓库：`https://raw.githubusercontent.com/xixiwan/mihon-ehentai-extension/main/index.min.json`
     （0.21 会自动跟到 `repo.json` → `index.v2.json`；Tachimanga 则直接读 `index.min.json`）；
   - 列表里应出现 `E-Hentai (1.4)` 和 `E-Hentai (1.6)`，按你的应用版本装对应的那个。
4. 老仓库条目不用删：刷新一次扩展仓库，密钥会更新，已装的扩展自动恢复可信。

## 五、自检

```powershell
pwsh -NoProfile -File offline\verify-repo.ps1
```

会核对这些（任何一条不满足就返回 1）：

- `index.v2.json` 的 `signingKey` 是 64 位小写十六进制，且与 `repo.json` 的 `signingKeyFingerprint` 一致；
- `apk/` 下每个 APK 的**实际签名证书 SHA-256** 等于这个密钥（安装/信任的关键）；
- 索引里的 `packageName` / `versionCode` / `versionName` / `extensionLib` 与 APK 的清单一致；
- APK 的 `tachiyomix.contentWarning = 2`（NSFW），否则会被内容警告过滤掉；
- `index.min.json` 的旧格式条目指向的文件存在且版本对得上。

### 本机已机器验证

- 两版都能从源码离线编出 APK：1.4 → dex `47,940 B`，1.6 → dex `54,440 B`（d8 `--min-api 21`）；
- 签名：两个 APK 的 v2 证书 SHA-256 都是 `cf46498d…0cb044`，等于仓库 `signingKey`
  ——`ExtensionInstaller.kt:92` 的 `extension.store.signingKey in getSignatures(...)` 因此为真；
- 清单：`tachiyomix.extensionLib` 存成**数值** 1.4 / 1.6（`ExtensionLoader.kt:356` 的 `getFloat` 直接命中，
  不依赖 versionName 回退）、`tachiyomix.contentWarning = 2`、`tachiyomi.extension` feature 齐备；
- dex 的外部引用只有 mihon 提供的类型（`eu/kanade/tachiyomi/source/*`、`network/NetworkHelper`、
  `network/RequestsKt`）加 kotlinx-coroutines：API jar 与第三方依赖都没被打进 APK；
- 索引链路能走通：`index.min.json` → `repo.json`（`meta` 四个字段齐全）→ `index_v2` → `index.v2.json`
  （按 `NetworkExtensionStore` 的形状解析，`extensionList` 非空、`signingKey` 为 64 位小写十六进制）；
- `verify-repo.ps1` 全绿（签名、包名、版本、lib 版本、内容警告、旧索引条目）。

### 还没法在本机验证的（需要你在设备上点一下）

这台机器没有 adb 设备，「点安装 → 打开源 → 读到列表」只能由你确认：

1. 0.21 里添加仓库后列表出现两条，点 `E-Hentai (1.6)` 安装应**不再**报
   `Extension isn't signed with the key of …`；
2. 装完在 浏览 → 源 里出现 `E-Hentai (EN)`，热门/搜索能出结果、能翻页；
3. 若某次请求报 `Failed to fetch … HTTP 403`，那是 Cloudflare/UA 的问题（不是签名问题），换 UA 偏好再试。

