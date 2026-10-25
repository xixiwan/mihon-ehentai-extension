# offline/ — 不联网的构建与校验

这台机器没有外网（Gradle 拿不到 JitPack / google() / mavenCentral()），但构建所需的物料本地都有：
Android SDK（`local.properties` 的 `sdk.dir`）、Gradle 缓存里的 Kotlin 编译器（2.3.20）、
以及 `offline/libs/` 下的 compileOnly 依赖。下面这些脚本只用它们，不联网、不起 Gradle daemon。

```powershell
pwsh -NoProfile -File offline\build-api16.ps1   # 从 _mihon/mihon-0.21.2 的源码编出 1.6 API jar（compile-only）
pwsh -NoProfile -File offline\build-v14.ps1     # 1.4 版 APK -> apk/tachiyomi-en.ehentai-v1.4.6.apk
pwsh -NoProfile -File offline\build-v16.ps1     # 1.6 版 APK -> apk/tachiyomi-en.ehentai-v1.6.0.apk
pwsh -NoProfile -File offline\verify-repo.ps1   # 校验签名指纹 + 三个索引与 APK 是否一致
```

## 文件

| 路径 | 作用 |
|---|---|
| `env.ps1` | 公共路径与 `kotlinc`/`javac` 调用（从 Gradle 缓存解析 kotlinx 等 jar） |
| `api16/src/**` | 1.6 API 的编译期源码：mihon 0.21.2 的 `source-api` + 少量 `core/common` 网络助手原样复制，另加几个 stub（`HttpException` / `NetworkHelper` / `OkHttpExtensions` / `androidx.compose.runtime.Stable` / `JsonObject.EMPTY`） |
| `build-api16.ps1` | 编出 `offline/build/extensions-lib-1.6.jar`（**不打进 APK**，运行时由 app 提供） |
| `build-v14.ps1` | kotlinc → d8 → aapt2 → zipalign → apksigner，产出 1.4 版 APK |
| `build-v16.ps1` | 同上，产出 1.6 版 APK（编 1.4 模块的共享源码，只排除 1.4 主类） |
| `verify-repo.ps1` | 索引 ↔ APK ↔ 签名指纹 的一致性检查（0.21 安装失败的正是这里） |
| `libs/` | compileOnly 依赖 jar：jsoup / okhttp / okio / rxjava / injekt / extlib-1.4.4 / androidx-preference-stubs |

`_ref/`（extlib-1.4.4 源码）只是兜底：`libs/` 里已有编好的 `extlib-1.4.4.jar` 与
`androidx-preference-stubs.jar`，正常构建不需要它。`_mihon/` 只在重编 1.6 API jar 时需要。

## 说明

- 两个 APK 用同一把 `keystore/debug.keystore`（别名 `androiddebugkey`，口令 `android`）签名，
  签名方案 v1+v2；仓库公布的指纹
  `cf46498d7bf8b712d57b07864dbf46edd42fb5a74da7f5945fc75003620cb044` 对两者都成立。
- `minSdk 21` 让 D8 把 `invokedynamic`（字符串拼接、lambda）desugar 成经典字节码，1.4 版因此能在
  Tachimanga 的 JVM 扩展运行时里加载。
- apksigner 的 `--v4-signing-enabled false`：v4 会额外产出 `.idsig` 文件，这里用不到。
- 中间产物都在 `offline/build/`（已 gitignore）。
- `build-v14.ps1` 最后一步要把签好的 APK 复制成 `apk/tachiyomi-en.ehentai-v1.4.6.apk`。如果 `apk/`
  在当前会话里不可写（权限/完整性标签限制），脚本会打印 `note` 并保留
  `offline/build/v14/signed.apk`——内容与要发布的一致，手动拷过去即可。
