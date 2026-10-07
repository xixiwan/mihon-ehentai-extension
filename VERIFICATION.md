# 站点实测复核记录（VERIFICATION.md）

复核日期：**2026-08-15**，方式：经本机代理（Clash, 127.0.0.1:7890）直连抓取
`e-hentai.org` 首页/搜索/热门/画廊/查看页 HTML 逐项核对；抓取与复测脚本在
`tools/`（`fetch.py` 等）。所有结论以实测为准，并已回写到代码（`Constants.kt` 选择器、
`EhentaiFilters.kt` 参数语义、`EhentaiParsers.kt` 解析逻辑）。

---

## 1. 列表页（搜索结果 / popular / 首页共用同一结构）

实测 `table.itg`（class 为 `itg gltc`），行结构（**与旧版文档不同**，旧 `tr.gtr` /
`td.gl1t` 结构已不存在）：

```
<tr>
  <td class="gl1c glcat"><div class="cn ct2" onclick="...">Doujinshi</div></td>   ← 分类
  <td class="gl2c">                                          ← 封面
    <div class="glcut" id="ic{gid}"></div>
    <div class="glthumb" id="it{gid}">
      <div><img alt=… src=… 或 data-src="https://ehgt.org/w/…webp" /></div>
      <div>… <div id="postedpop_{gid}">2026-08-15 05:47</div> …<div>27 pages</div></div>
    </div>
    <div><div id="posted_{gid}">2026-08-15 05:47</div> …</div>
  </td>
  <td class="gl3c glname" onmouseover=…>
    <a href="https://e-hentai.org/g/{gid}/{token}/">
      <div class="glink">标题</div>
      <div><div class="gt" title="parody:original">original</div>…</div>
    </a>
  </td>
  <td class="gl4c glhide"><div><a href="…/uploader/entor">entor</a></div><div>27 pages</div></td>
</tr>
```

结论：

- 行选择器：`table.itg tr:has(td.gl3c)`（跳过分类切换行 ×2 和表头行）。
- 封面：`td.gl2c img`，懒加载行用 `data-src`，普通行用 `src`；占位图 `data:image/gif…`
  与站点小图标（`/g/t.png`、`/g/td.png`）需过滤。
- 标题/链接：`td.gl3c a[href*="/g/"]` → `div.glink`。
- 标签已直接内联在列表行：`div.gt` 的 `title="namespace:tag"`。
- 日期：`div#posted_{gid}`，**新格式 `yyyy-MM-dd HH:mm`**（旧 `17 September 2024, 12:00`
  已不再出现；解析器两种格式都支持）。
- popular 页无 `nexturl/prevurl`（单页无分页）。

## 2. 搜索分页 —— 游标制（重要差异）

- 实测 `?f_search=original&page=1` 与 `?f_search=original` **内容完全一致（MD5 相同）**
  → **`page=N` 参数已被站点忽略**。
- 分页由页面内嵌脚本提供：

  ```js
  var prevurl="";
  var nexturl="https://e-hentai.org/?f_search=original&next=4120012";
  ```

- `nexturl` 保留全部筛选参数并追加 `next=<gid>`（实测 `f_srdd/f_spf/f_spt/f_cats`
  全部保留）；末页 `nexturl=""`。
- 实现：`hasNextPage` = `nexturl` 非空；下一页请求直接用上一页的 `nexturl`
  （`Ehentai.kt` 内 `requestedPages` 记录「第 N 页实际请求的 URL」，再用 `nextPageCursors`
  按该 URL 取游标——站点只给「当前页 → 下一页」的相对游标，按页记账才不会在第 3 页起重复第 2 页）。

## 3. 画廊详情页 `/g/{gid}/{token}/`

实测结构：

| 数据 | 实测选择器/位置 | 与旧文档差异 |
|---|---|---|
| 英文标题 | `h1#gn`（`#gj` 为日文标题） | `#gd2` 现在**包含** `#gn`/`#gj` 两个 h1；两个标题都在用：主标题按「标题语言」偏好（默认跟随列表显示的那个），另一个以「其他语言标题：…」写进简介 |
| 封面 | `#gd1` 内 `div[style*="url(...)"]` CSS 背景图，正则 `url\((https?://[^)]+)\)` | **不再是 `<img>`**（旧 `img#cover` 已不存在） |
| 元数据 | `#gdd table tr`：`td.gdt1`=字段名，`td.gdt2`=值（Posted / Parent / Visible / Language / File Size / Length / Favorited） | Category/Uploader/Rating 已不在该表内；Rating 在 `#gdr` |
| 上传者 | `#gdn a[href*="/uploader/"]` 文本 | — |
| 分类 | `#gdc` 文本（如 "Doujinshi"） | — |
| 标签 | `#taglist table tr`：`td.tc`=命名空间（含尾冒号），同行 `td:eq(1) a`=标签 | 结构保留 |
| 描述 | `#gd2` 全文去掉 `#gn`/`#gj` 文本；再在最前面加一行「其他语言标题：…」（没被选中的那个标题；只有一个标题或两者相同时不加） | 多数画廊无描述，此时简介就是这一行 |
| 缩略图 | `#gdt a[href*="/s/"]`，查看页 URL 形如 `/s/{hash}/{gid}-{n}` | 旧 `div.gdtm` 包裹已不存在 |
| 缩略图分页 | `?p=N`，**每页 20 张**（`p.gpc`：「Showing 1 - 20 of 27 images」） | 旧版 40 张/页 |
| 页数 | `Length` 元数据「27 pages」；回退 `p.gpc` 的 `of N images` | — |

另：页面内嵌 `var gid=…; var token=…`（构造 URL 用，未依赖）。

## 4. 查看页 `/s/{hash}/{gid}-{n}`

实测：

- 标准图：`<img id="img" src="https://{sub}.hath.network:2333/h/…/keystamp=…;fileindex=…/1.webp">`
  —— 图片 URL 带 **`keystamp` 时效参数**（过期后需重新解析查看页，站点正常行为）。
- **`imgurl` 脚本变量已不存在**（旧文档的正则失效）。
- 原图：`<a href="https://e-hentai.org/fullimg/{gid}/{n}/{showkey}/{file}">Download original …</a>`
  —— 未登录访问实测 **302 → `bounce_login.php`**，即原图需要登录 Cookie。
- 页数指示 `<span>1</span> / <span>27</span>`；`a#next`/`a#prev` 存在（未依赖）。
- 标准图直连实测 **200 image/webp**（带 Referer 即可）。

## 5. 高级搜索参数（3.3 差异——旧参数大部分已失效）

实测新版搜索表单（内嵌于 `ehg_index.c.js`，`advdiv` 动态注入）：

- 存活参数：
  - `f_search`（关键词）；
  - `f_cats` —— **语义变为排除掩码**：JS `toggle_category(a)` 把点击分类的位
    OR 进 `f_cats`（界面里被点掉的分类变灰）；实测 `f_cats=2` 返回不含 Doujinshi 的结果，
    `f_cats=1021`（=1023^2）只返回 Doujinshi，`f_cats=1019` 只返回 Manga。
    分类位值按界面 id：Misc=1, Doujinshi=2, Manga=4, Artist CG=8, Game CG=16,
    Image Set=32, Cosplay=64, Asian Porn=128, Non-H=256, Western=512；
  - `f_spf`/`f_spt` 页数范围（实测 100–200 生效、5–10 返回 0 结果）；
  - `f_srdd` 最低评分（0/2/3/4/5，实测 5 生效）；
  - `f_sh` 包含已删除、`f_sto` 要求种子（checkbox `on`）；
  - `advsearch=1`（可选，`search_presubmit()` 会把空值参数 disabled 掉，等价于省略）。
- **已失效参数**（旧文档 3.3 所列，站点不再支持）：`f_sname`/`f_stags`/`f_sdesc`、
  `f_sr`、`f_sp`、`f_sfl`(大小)、`f_sdd`/`f_sd`/`f_sdm`/`f_sdy`、`f_si`/`f_sil`/`f_siu`、
  `f_apply`、`f_sfu`/`f_sft`/`f_sfl`(禁用筛选)——实测 `f_sname=on` 与基线结果完全一致。
- 语言筛选：站点表单无独立语言字段，但 `language:xxx` 关键词搜索有效
  （实测 `f_search=language:chinese` 返回中文画廊）。

> 因此本扩展的筛选只实现上述存活参数（分类/评分/语言/页数/已删除/种子），
> 旧文档里的文件大小、日期、图片尺寸筛选未实现（站点已移除）。

## 6. 其他实测事实

- 分类位掩码合计 1023；「全部」= 不发送 `f_cats`（与 `f_cats=0` 结果一致）。
- 封面缩略图 CDN：`ehgt.org`（`/w/` 路径 = 按宽度缩放）。
- 图片标准尺寸 CDN：`*.hath.network:2333`（H@H 网络），要求 Referer。
- exhentai.org 未带 Cookie 的访问行为未实测（无账号），按站点公开规则实现：
  未填 Cookie 时直接给出可读报错。
- `f_cats` 未带 `f_search` 时（仅筛选浏览）可用：实测 `?f_cats=1019` 返回纯 Manga 结果。

## 7. 复测方法

```bash
# 需代理时：
python tools/fetch.py "https://e-hentai.org/?f_search=original" tools/search.html
python tools/inspect_list.py tools/search.html     # 列表结构
python tools/inspect_gallery.py tools/gallery.html # 画廊结构
python tools/inspect_viewer.py tools/viewer.html   # 查看页结构
python tools/test_params.py                        # 筛选参数行为
python tools/verify_cats.py                        # f_cats 语义验证
```
