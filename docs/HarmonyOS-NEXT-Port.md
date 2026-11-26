# TomatoFile HarmonyOS NEXT 迁移与开发文档

> 用途：本文件用于在新的 HarmonyOS NEXT 工作区中，从零实现 TomatoFile，并作为功能、交互、架构、验收和发布依据。
>
> 应用信息：
> - 应用名：TomatoFile
> - Bundle Name：`com.cctomato.tv`
> - 目标系统：HarmonyOS NEXT（Stage 模型，建议 API 12/13 及以上，以 DevEco Studio 当前稳定 SDK 为准）
> - 目标设备：手机、平板、PC / 2in1、智慧屏（TV）
> - 开发语言：ArkTS / ArkUI
> - 原生扩展：C/C++ + NAPI（媒体、SMB/FTP 等确有需要时）
>
> 说明：文中涉及的系统 Kit/API 名称以 DevEco Studio 对应 SDK 的 d.ts 为准。HarmonyOS NEXT 版本迭代较快，若某个 Kit 在当前 SDK 中不可用，应在不改变产品行为的前提下选择等价实现，并在交付说明中记录替代方案。

---

## 1. 产品目标

TomatoFile 是一个本地 + 网络文件管理器，核心能力：

1. 管理多个存储来源：
   - 本机目录
   - WebDAV
   - SMB / SMB2 / SMB3
   - FTP / FTPS
2. 分层浏览目录，支持面包屑、返回、排序、列表/网格视图。
3. 常用文件预览：
   - 文本
   - PDF
   - Office（docx / xlsx / pptx）
   - 图片
4. 全屏图片查看器：
   - 左右滑动切换
   - 双指缩放、双击放大
   - 图片元数据
   - 作者/来源链接：仅当值是带协议的 URI 时交给系统打开，绝不使用浏览器搜索兜底
5. 全能视频播放：
   - 常见格式 + WMV / AVI / RMVB / TS / M2TS / FLV 等
   - 4K
   - 手势快进/快退
   - 内置音轨/字幕切换
   - 倍速
6. 本地音乐播放器：
   - 当前目录音乐自动组成播放列表
   - 上一首/下一首
   - 专辑封面、标题、艺术家、专辑
   - 在线歌词匹配
   - 后台播放
7. 远程文件缓存与清理。
8. Android TV 风格的遥控器焦点导航；TV 添加存储支持手机扫码填写。
9. 设置、缓存管理、TV 大屏布局开关。

---

## 2. 设备与形态适配

### 2.1 `module.json5` 设备类型

```json5
{
  "module": {
    "name": "entry",
    "type": "entry",
    "deviceTypes": [
      "phone",
      "tablet",
      "2in1",
      "tv"
    ]
  }
}
```

### 2.2 断点与布局

统一使用 vp/fp，不使用 px。建议断点：

| 形态 | 宽度 | 导航 | 文件列表 |
|---|---|---|---|
| 手机 | < 600vp | 单栏 | 列表默认，可切换网格 |
| 平板 / 折叠屏 | 600–840vp | 单栏或双栏 | 2–3 列网格 |
| PC / 2in1 | > 840vp | 左侧来源导航 + 右侧内容 | 3–5 列网格 |
| 智慧屏 TV | 横屏 16:9 | 顶部/侧边大卡片 | 大卡片 + 焦点框 |

要求：
- 所有页面在窗口尺寸变化时重新计算布局，不依赖固定屏幕方向。
- 视频播放页在手机进入横屏；退出后恢复。
- PC 支持窗口拖动、最大化、鼠标 hover、右键菜单（如实现）。
- TV 支持遥控器方向键、确认键、返回键；所有可交互控件必须有可见焦点态。
- 图片查看器、视频播放器在 TV 上支持方向键切图/快进。
- 使用安全区/窗口避让，不遮挡状态栏、导航栏、PC 标题栏。

### 2.3 输入方式

- 触摸：点击、长按、滑动、双指缩放、双击。
- 鼠标：hover、点击、滚轮滚动、拖拽（如实现）。
- 键盘：Esc 返回、空格播放/暂停、左右方向键快进/快退（播放器）。
- 遥控器：方向键移动焦点，确认键打开，返回键回退。

---

## 3. 技术栈与 Android → HarmonyOS 映射

| Android 现有实现 | HarmonyOS NEXT 建议 |
|---|---|
| Kotlin + Jetpack Compose | ArkTS + ArkUI 声明式 UI |
| Navigation Compose | `Navigation` + `NavPathStack`（避免使用已废弃 router） |
| ViewModel + StateFlow | `@ObservedV2/@Trace` 或 `@State/@Prop/@Link` + 单例 Store |
| Coil | `Image` + 自实现图片缓存/缩略图 |
| Media3 ExoPlayer + libVLC | `AVPlayer`（通用格式）+ FFmpeg/原生播放器（全格式） |
| OkHttp | `@ohos.net.http` / Remote Communication Kit（RCP） |
| NanoHTTPD | `@ohos.net.socket` TCPSocket 服务端，手写极简 HTTP |
| ZXing | ZXing 的 ArkTS/原生移植，或二维码生成库 |
| SharedPreferences | `@ohos.data.preferences` |
| Android Keystore + AES/GCM | HUKS + `@ohos.security.cryptoFramework` |
| WorkManager / Coroutines | `TaskPool` + async/await |
| Android SAF / MANAGE_EXTERNAL_STORAGE | `DocumentViewPicker` + 持久化 URI 授权 |
| ExoPlayer Range / FTP REST / SMB random read | 自实现 Range/offset 读取 + 远程缓存 |
| Room（当前未使用） | 关系型数据库 `@ohos.data.relationalStore`（如需要） |

---

## 4. 工程结构

建议 Stage 模型单 HAP 工程：

```text
TomatoFileHarmony/
├── AppScope/
│   ├── app.json5
│   └── resources/
├── entry/
│   ├── src/main/
│   │   ├── ets/
│   │   │   ├── entryability/
│   │   │   │   └── EntryAbility.ets
│   │   │   ├── pages/
│   │   │   │   └── Index.ets
│   │   │   ├── navigation/
│   │   │   │   └── AppNav.ets
│   │   │   ├── model/
│   │   │   │   ├── FileSource.ets
│   │   │   │   ├── FileEntry.ets
│   │   │   │   ├── FileKinds.ets
│   │   │   │   └── Settings.ets
│   │   │   ├── data/
│   │   │   │   ├── fs/
│   │   │   │   │   ├── FileSystemClient.ets
│   │   │   │   │   ├── LocalFileSystem.ets
│   │   │   │   │   ├── WebDavFileSystem.ets
│   │   │   │   │   ├── SmbFileSystem.ets
│   │   │   │   │   └── FtpFileSystem.ets
│   │   │   │   ├── repo/
│   │   │   │   │   └── FileRepository.ets
│   │   │   │   ├── cache/
│   │   │   │   │   └── RemoteCache.ets
│   │   │   │   ├── store/
│   │   │   │   │   ├── SourceStore.ets
│   │   │   │   │   └── SettingsStore.ets
│   │   │   │   └── crypto/
│   │   │   │       └── SecretStore.ets
│   │   │   ├── media/
│   │   │   │   ├── MediaTypes.ets
│   │   │   │   ├── VideoEngine.ets
│   │   │   │   ├── AudioEngine.ets
│   │   │   │   ├── MpegTsDuration.ets
│   │   │   │   └── LyricsRepository.ets
│   │   │   ├── view/
│   │   │   │   ├── home/
│   │   │   │   ├── browse/
│   │   │   │   ├── source/
│   │   │   │   ├── preview/
│   │   │   │   ├── image/
│   │   │   │   ├── video/
│   │   │   │   ├── audio/
│   │   │   │   ├── cache/
│   │   │   │   └── settings/
│   │   │   ├── components/
│   │   │   └── utils/
│   │   ├── resources/
│   │   │   ├── base/element/string.json
│   │   │   ├── base/media/
│   │   │   └── rawfile/
│   │   └── module.json5
│   ├── build-profile.json5
│   └── oh-package.json5
├── build-profile.json5
├── hvigorfile.ts
└── oh-package.json5
```

命名建议：
- 页面组件使用 `PascalCase`。
- 数据/服务使用 `XxxStore`、`XxxRepository`、`XxxEngine`。
- 单例容器 `AppContainer`：sourceStore、settings、repository、cache、secretStore。
- 不把网络/文件 IO 写进 UI 组件。

---

## 5. 数据模型

### 5.1 来源类型

```ts
export enum SourceType {
  LOCAL = 'LOCAL',
  WEBDAV = 'WEBDAV',
  SMB = 'SMB',
  FTP = 'FTP'
}
```

### 5.2 FileSource

```ts
export interface FileSource {
  id: string;
  type: SourceType;
  name: string;
  localUri: string;     // LOCAL: DocumentViewPicker 选择的目录 URI
  url: string;          // WEBDAV: 基础 URL
  host: string;         // SMB/FTP
  port: number;         // 0 = 协议默认端口
  share: string;        // SMB 共享名
  rootPath: string;     // 远程根目录，默认 '/'
  domain: string;       // SMB 域
  username: string;
  password: string;     // 仅内存使用；持久化必须加密
  secure: boolean;      // FTP over TLS
  builtIn: boolean;     // 内置本机来源不可删除
}
```

### 5.3 FileEntry

```ts
export enum PreviewKind {
  IMAGE, VIDEO, AUDIO, TEXT, PDF, DOCX, XLSX, PPTX, ARCHIVE, OTHER
}

export interface FileEntry {
  name: string;
  path: string;         // 来源内绝对风格路径，/ 开头，规范化
  isDirectory: boolean;
  size: number;
  lastModified: number;
  mimeType: string;
  kind: PreviewKind;
}
```

### 5.4 浏览状态

```ts
export interface DirectorySnapshot {
  path: string;
  entries: FileEntry[];
  scrollIndex: number;
  scrollOffset: number;
}

export interface BrowseState {
  loading: boolean;
  refreshing: boolean;
  source: FileSource | null;
  path: string;
  entries: FileEntry[];
  error: string | null;
  permissionRequired: boolean;
}
```

### 5.5 设置

```ts
export interface AppSettings {
  viewMode: 'list' | 'grid';
  sortMode: 'nameAsc' | 'nameDesc' | 'sizeAsc' | 'sizeDesc' | 'dateAsc' | 'dateDesc';
  forceTvLayout: boolean;
  cacheLimitMb: number;
}
```

---

## 6. 文件类型判定

必须按文件名后缀优先判定，MIME 作为辅助。原因：HarmonyOS/服务端 MIME 经常缺失或错误，而 M2TS/TS/WMV 等经常被标成 `application/octet-stream`。

### 6.1 视频后缀

```text
mp4, mkv, webm, avi, mov, 3gp, 3g2, m4v, ts, m2ts, mts, wmv, rmvb, rm, flv,
mpg, mpeg, m2v, m1v, vob, asf, ogv, divx, f4v, mxf
```

### 6.2 音频后缀

```text
mp3, wav, flac, aac, ogg, oga, m4a, m4b, opus, wma, ape, alac, aiff, aif,
amr, mid, midi, mka, wv
```

### 6.3 图片后缀

```text
jpg, jpeg, png, gif, webp, bmp, heic, heif, avif, ico, jfif
```

### 6.4 文本后缀

```text
txt, text, log, md, markdown, json, json5, xml, html, htm, xhtml, css, scss,
sass, less, js, mjs, cjs, ts, tsx, jsx, kt, kts, java, py, rb, go, rs, c, h,
cpp, cc, hpp, cs, php, sh, bash, zsh, bat, cmd, ps1, yml, yaml, toml, ini,
cfg, conf, properties, gradle, sql, csv, tsv, srt, vtt, tex, diff, patch,
gitignore, editorconfig, dockerfile, makefile, pro, env, lock, rtf
```

### 6.5 路由规则

- 音频 → 音频播放页。
- 视频 → 视频播放页。
- 图片 → 图片查看器。
- PDF/Office/文本 → 预览页。
- 其他 → 文件信息页，提供“用其他应用打开”。

**禁止**：先进入统一播放器再根据加载结果切音频/视频 UI。必须在进入页面前按后缀确定 `mediaType`，避免先闪音频界面。

---

## 7. 本地文件访问

### 7.1 选择目录

使用 `DocumentViewPicker` 选择目录/文件，`@ohos.file.fs` 读取。

要求：
- 首次添加本机来源时弹出目录选择器。
- 保存 URI 后调用持久化授权接口，使应用重启后仍可访问。
- 不申请 `MANAGE_EXTERNAL_STORAGE` 等 Android 式全盘权限；HarmonyOS 普通应用应使用 Picker + 持久化 URI。
- TV/PC 上若系统文件选择器不可用，提供应用沙箱目录和已授权 URI 列表。

### 7.2 路径处理

- `FileSource` 内部路径统一为 `/` 开头、无尾斜杠（根为 `/`）。
- 本地来源的 `localUri` 与相对路径拼接时使用 URI 编码。
- 规范化路径时必须拒绝 `..` 越出根目录，防止路径穿越。
- 目录列表只接受直接子项；不递归读取。

### 7.3 权限失败

统一错误文案：
- “没有访问该目录的权限，请重新选择目录”
- “目录不存在或已被移动”
- “无法读取目录，请检查存储授权”

UI 提供“重新授权/重新选择”按钮。

---

## 8. 网络文件系统

### 8.1 统一接口

```ts
export interface FileSystemClient {
  list(path: string): Promise<FileEntry[]>;
  stat(path: string): Promise<FileEntry>;
  openStream(path: string, position?: number): Promise<InputStreamLike>;
  readRange(path: string, position: number, length: number): Promise<ArrayBuffer>;
  close(): Promise<void>;
}
```

Repository 按 `source.id` 缓存 client 实例；来源编辑/删除时 invalidate。

### 8.2 WebDAV

- 方法：`PROPFIND`，`Depth: 1`。
- 认证：HTTP Basic；用户名/密码来自加密存储。
- 解析 207 Multi-Status XML：`d:response`、`d:href`、`d:getcontentlength`、`d:getlastmodified`、`d:resourcetype`。
- URL 编码：逐段 `encodeURIComponent`，保留 `/`。
- 读取：`GET` + `Range: bytes=start-`；正确处理 206/200/416。
- TLS：HTTPS 默认校验证书；自签名证书需明确产品策略（建议首版不支持忽略证书，避免安全问题）。
- 错误映射：401/403 提示认证失败；404 目录不存在；5xx 服务端错误。

### 8.3 SMB

HarmonyOS NEXT 不保证内置 SMB 客户端。建议：
1. 优先确认当前 SDK 是否有官方 SMB 文件 API；若有，直接封装。
2. 否则使用 C 的 `libsmb2` 或等价 SMB2/3 库，通过 NAPI 暴露：
   - connect
   - list
   - stat
   - open/read/seek/close
3. 支持 SMB2/2.1/3.x，NTLMv2；域可选；默认端口 445。
4. 认证失败、共享不存在、权限不足分别给中文提示。
5. 大文件必须支持随机读，不允许整文件下载后才能播放。

若首版无法稳定集成 SMB，UI 仍保留 SMB 类型，但需在发布说明明确“暂不支持”，不能静默失败。

### 8.4 FTP / FTPS

- 建议基于 TCP Socket 实现 FTP 客户端，或使用经过验证的 C `libcurl` NAPI 封装。
- 必须支持：
  - PASV 被动模式
  - `USER/PASS`
  - `LIST` / `MLSD`
  - `REST` 断点偏移
  - `RETR`
  - `SIZE` / `MDTM`
- FTPS：显式 TLS（AUTH TLS）；证书校验策略与 WebDAV 一致。
- 默认端口 21；FTPS 通常也是 21。
- 读取大文件时按 Range/offset 流式读取，并进入远程缓存。

### 8.5 凭据安全

- 密码不得明文写入 preferences/JSON/日志。
- 使用 HUKS 生成/保存 AES 密钥，使用 `cryptoFramework` 进行 AES-GCM 加解密。
- 持久化只保存 `passwordCipher`、`iv`、`tag` 或统一加密后的 JSON。
- 日志中只显示来源名称、类型、主机，不显示密码。

---

## 9. 远程缓存

### 9.1 目标

- 远程图片、音频、视频、预览文件读取后进入缓存，避免重复下载。
- 支持缓存上限、LRU 清理、手动清空。
- 视频播放不能强制整文件下载；必须支持 Range/offset 流式读取。

### 9.2 结构

```text
cacheDir/
  remote/
    <sourceId>/
      <sha256(path)>/
        data
        meta.json
  index.json
```

`meta.json` 记录：sourceId、path、size、mime、lastModified、lastAccess、etag（如有）。

### 9.3 策略

- 单文件超过阈值的视频：只缓存已读片段或使用临时文件 + Range，不一次性落完整文件。
- 图片/音频/文档：可完整缓存。
- 清理：按 lastAccess LRU；删除索引与文件；提供“清空缓存”。
- 缓存统计：文件数、总大小；首页不展示统计卡片（按现有 UI 要求），只在缓存管理页显示。

---

## 10. 媒体播放总体方案

### 10.1 分层

1. **通用播放**：HarmonyOS `AVPlayer`（`@ohos.multimedia.media`）负责 MP4/MKV/WebM/MP3/FLAC/M4A 等平台支持的格式。
2. **全格式播放**：WMV/AVI/RMVB/TS/M2TS/FLV 及 RealVideo/WMV 编码，`AVPlayer` 很可能不支持，必须准备原生软件解码方案。
3. **原生方案建议**：
   - 编译 FFmpeg（arm64-v8a / x86_64）为 OHOS 静态库；
   - NAPI 暴露 demux/decode/seek；
   - 视频通过 `XComponent` 的 Surface 渲染；
   - 音频通过 `AudioRenderer` 输出；
   - 或评估将 libVLC 移植到 OHOS。
4. **不要**假设 `AVPlayer` 支持所有容器；必须按扩展名选择引擎，并在 UI 上给出明确错误。

### 10.2 视频/音频页面分离

路由参数必须携带 `mediaType`：

```ts
type MediaType = 'video' | 'audio';
```

- 视频路由 → 视频播放页。
- 音频路由 → 音频播放页。
- 不要先渲染统一播放页再按探测结果切换。
- 新页面首帧就使用正确的背景、控制栏和方向。

### 10.3 视频播放页

功能：
- 全屏、横屏（手机）、16:9 适配。
- 播放/暂停、±10 秒。
- 左右滑动快进/快退：按滑动距离/屏幕宽度 × 总时长计算，松手 seek，拖动时显示“快进/快退 mm:ss”。
- 进度条拖动。
- 倍速：0.5×、0.75×、1×、1.25×、1.5×、2×、3×。
- 音轨列表切换。
- 字幕轨列表切换/关闭。
- 内置字幕渲染；外挂字幕首版可不做，但文档/UI 不要承诺。
- 屏幕常亮。
- 后台/切后台暂停策略：视频默认暂停；音频继续。
- TV：方向键焦点、确认播放、返回退出；播放器控件 4 秒无操作自动隐藏，点击/按键重新显示。

### 10.4 M2TS / TS 时长与 seek（必须实现）

Android 端已验证：BDAV 的 `.m2ts` 使用 192 字节包，`MediaMetadataRetriever`/libVLC 可能返回 duration=0。HarmonyOS 必须自实现 PCR 时长探测。

算法：
1. 打开文件/输入流，读取头部约 8MB。
2. 检测包长：
   - 188 字节：同步字节 `0x47` 位于包首。
   - 192 字节（BDAV）：同步字节位于包首 +4。
   - 按连续同步字节命中数评分选择。
3. 解析 adaptation field：
   - `adaptation_field_control` 为 2 或 3；
   - adaptation_field_length ≥ 7；
   - PCR flag = 0x10；
   - PCR 6 字节：33-bit base + 9-bit extension。
4. 头部找第一个 PCR；文件尾部向前读取 8MB，找不到则扩大到 16/32/64MB，找最后一个 PCR。
5. `duration = (lastPcr - firstPcr) / 27_000` 毫秒；若为负加 `2^33 * 300` 处理回绕。
6. 有效范围 1 秒–24 小时；否则回退到系统 metadata。
7. PCR 成功时不要再调用耗时的 `MediaMetadataRetriever`。
8. 播放器 length 为 0 时使用 PCR 时长驱动进度条。
9. seek：
   - 引擎支持 seek 时直接 seek；
   - 不支持时使用 `start-time` 重新打开当前媒体，保留播放列表/封面/元数据，不重置 UI。
10. 对 5.78GB 级文件只读头尾，不得全文件扫描。

验收：
- 35 分钟 BDAV M2TS 显示 `35:13`。
- 进度条可拖动，快进/快退可用。
- 5.78GB 文件打开时长探测不阻塞 UI。

### 10.5 音频播放页

布局（手机）：
- 大专辑封面（无封面用品牌占位图）。
- 标题、艺术家 · 专辑。
- 进度条 + 当前/总时长。
- **底部**：上一首 / 播放暂停 / 下一首。
- 底部工具栏：`倍速`、`列表`、`歌词`。
- 列表为底部弹层，显示当前目录全部音频；点击切换。
- 歌词为底部弹层，显示 LRC/纯文本；尽量按时间高亮当前行。

逻辑：
- 当前目录下所有音频自动组成播放列表。
- 上一首/下一首循环。
- 播放结束自动下一首。
- 切换歌曲时立即清空上一首的标题、艺术家、专辑、封面、歌词。
- 异步读取元数据完成后必须校验“当前 entry 是否仍是发起读取的那一首”，防止旧任务覆盖新歌曲。
- 读取内置封面/标题/艺术家/专辑：系统媒体元数据提取器；失败用文件名。
- 在线歌词：LRCLIB `https://lrclib.net/api/search?track_name=...&artist_name=...`，取 `syncedLyrics` 优先，其次 `plainLyrics`。
- 歌词/元数据网络失败不影响播放，显示“暂无歌词/网络不可用”。

后台播放：
- 使用 AVSession/媒体会话；
- 申请后台运行权限；
- 锁屏/控制中心显示标题、封面、上一首/下一首/播放暂停。

### 10.6 播放器状态模型

```ts
export interface PlayerState {
  loading: boolean;
  sourceName: string;
  title: string;
  artist: string;
  album: string;
  isVideo: boolean;
  playing: boolean;
  buffering: boolean;
  durationMs: number;
  positionMs: number;
  speed: number;
  error: string | null;
  audioTracks: TrackOption[];
  subtitleTracks: TrackOption[];
  selectedAudioId: number;
  selectedSubtitleId: number;
  albumArt: image.PixelMap | null;
  playlist: PlaylistItem[];
  playlistIndex: number;
  lyrics: string | null;
  lyricsLoading: boolean;
  lyricsError: string | null;
}
```

---

## 11. 图片查看器

- 全屏黑底。
- 左右滑动切换同目录图片。
- 双指缩放（1×–5×），双击放大/还原。
- 缩放状态大于 1 时，左右滑动用于平移，不切图。
- 加载完成后右下角显示“链接”按钮（仅当元数据中存在作者/来源链接时显示）。
- 图片信息弹窗显示：尺寸、格式、大小、拍摄时间、作者、版权。
- 移除底部“手势提示”文案（按现有 UI 要求）。
- 作者链接规则：
  - 仅接受带协议的 URI：`http:`, `https:`, `mailto:` 等安全协议；
  - 拒绝 `file:`, `content:`, `javascript:`, `data:`, `intent:`, 应用私有 scheme；
  - 普通作者名、域名、邮箱不得被当作 URL 搜索；
  - 系统打开成功不弹 toast/snackbar；失败才提示“没有应用可以响应此链接”。

图片缩放使用 `PinchGesture` + `PanGesture`，或 `Image` 外层 `Stack` 手动矩阵变换；注意 TV 方向键翻页。

---

## 12. 文本 / PDF / Office 预览

### 12.1 文本

- 读取前 N MB（建议 2–5MB），避免超大文件卡死。
- 编码检测：UTF-8、UTF-8 BOM、GBK、UTF-16；失败回退 UTF-8。
- 等宽字体、可滚动、可选择复制（如系统支持）。
- TV 支持方向键滚动。

### 12.2 PDF

- 优先使用 HarmonyOS PDF Kit/系统 PDF 组件（若 SDK 提供）。
- 否则使用 Web 组件加载 PDF.js（本地 rawfile 资源），注意大文件内存。
- 支持翻页、缩放、TV 方向键滚动。

### 12.3 Office

- docx / xlsx / pptx：
  - 优先系统 Office 预览 Kit（若提供）；
  - 否则 Web 组件 + 本地 JS 渲染库（docx-preview / SheetJS / pptxjs 等）；
  - 只读预览，不编辑。
- 失败时提供“用其他应用打开”。
- 大文件限制与加载状态必须明确。

---

## 13. 首页

要求（沿用当前 Android 版 UI 决策）：
- 顶部仅：`TomatoFile` 标题 + 设置按钮。
- 不显示统计卡片、缓存卡片、促销文案。
- 来源列表卡片：类型图标、名称、类型标签、地址/路径、编辑按钮。
- 右下角 FAB“添加存储”。
- 不显示列表末尾“添加新存储”卡片。
- 平板/PC/TV 使用多列大卡片。
- TV 焦点：卡片可聚焦，焦点框明显。

空状态：
- 无来源时显示引导 + “添加存储”。

---

## 14. 浏览页

- 顶部：返回、来源名、当前路径面包屑、切换视图、刷新、更多。
- 面包屑每一级可点击跳转。
- 进入目录时立即清空旧列表，显示“正在读取目录…”。
- 刷新时保留旧列表并显示进度条。
- 支持排序：名称、大小、日期，升降序。
- 列表/网格切换。
- 返回上一级不重新加载（使用目录快照栈），并恢复滚动位置。
- 目录历史栈最多 32 层。
- 进入子目录前保存当前 `entries + scrollIndex + scrollOffset`。
- 从面包屑/返回命中缓存时恢复列表与滚动位置。
- 根目录返回键交给系统。
- 错误/空目录/权限不足分别展示。
- TV 方向键滚动、焦点移动；PC 滚轮。

滚动恢复建议：
- 使用官方 `Scroller`/`List` 的 `scrollToIndex`；
- 不要依赖手动创建列表状态对象；
- 每个目录保存 index/offset，恢复时在 `onAppear`/`aboutToAppear` 后执行一次。

---

## 15. 添加存储 + TV 手机扫码

### 15.1 表单字段

- 类型：WebDAV / SMB / FTP / 本机目录。
- 名称。
- WebDAV：URL。
- SMB/FTP：host、port。
- SMB：share、rootPath、domain。
- FTP：rootPath、FTPS 开关。
- LOCAL：目录 URI（Picker）。
- 用户名、密码。
- 测试连接、保存、删除（编辑时）、取消。

### 15.2 TV 手机扫码填写

仅在 TV（或 `forceTvLayout`）且非编辑模式显示“手机扫码填写参数”。

流程：
1. TV 启动局域网 HTTP 服务，端口从 8765 起自动选择空闲端口。
2. 获取本机局域网 IPv4（非 loopback、site-local）。
3. 生成随机 token，URL：`http://<ip>:<port>/?token=<token>`。
4. 用二维码组件展示 URL，同时显示 URL 文本和“手机与电视需在同一局域网”。
5. 手机打开网页，填写与 TV 表单一致的字段。
6. 提交后 TV 表单实时回显；显示“已收到手机提交，参数已回显，请核对后保存”。
7. 关闭二维码弹窗立即停止服务，链接失效。
8. token 错误返回 403。

HTTP 服务要求：
- 只监听局域网，不暴露公网。
- 极简 HTTP/1.1：GET `/` 返回表单；POST `/submit` 解析 `application/x-www-form-urlencoded`。
- 表单字段：type/name/localPath/url/host/port/share/rootPath/domain/username/password/secure。
- POST 解析必须兼容字段在 query 或 body 的情况。
- 返回 UTF-8 HTML；成功后提示回电视保存。
- 二维码使用 ZXing 或等价库生成；TV 只展示，不调用相机。

TV 弹窗：
- 二维码 220–280vp。
- URL 小字可读。
- 关闭按钮可遥控器聚焦。
- 服务启动失败显示明确错误。

---

## 16. 缓存管理 / 设置

缓存管理：
- 显示文件数、总大小。
- 清理全部缓存。
- 清理后刷新统计。
- 不影响已保存来源配置。

设置：
- 列表/网格默认视图。
- 默认排序。
- TV 大屏布局开关（`forceTvLayout`）。
- 缓存上限。
- 关于/版本/开源许可。
- 可增加主题跟随系统（可选）。

---

## 17. 权限

在 `module.json5` 中按需声明：

```json5
"requestPermissions": [
  { "name": "ohos.permission.INTERNET" },
  { "name": "ohos.permission.GET_NETWORK_INFO" },
  { "name": "ohos.permission.KEEP_BACKGROUND_RUNNING" }
]
```

说明：
- 文件访问优先使用 Picker + 持久化 URI，不申请全盘文件权限。
- 读取媒体库如使用 `photoAccessHelper`，按 SDK 要求申请媒体读权限；否则用 Picker。
- 后台音频需要 `KEEP_BACKGROUND_RUNNING` 并配置 ContinuousTask/AVSession。
- 不申请相机权限（二维码只展示）。
- 局域网 HTTP 服务需要网络权限；部分系统版本可能还有本地网络访问开关，需在真机验证。
- 所有权限拒绝场景给出可操作提示，不崩溃。

---

## 18. 安全要求

1. 来源密码加密存储，禁止明文。
2. 日志脱敏：不打印密码、token、完整 URL 查询参数。
3. 二维码 token 单次会话有效；关闭弹窗即停止服务。
4. 路径穿越防护：规范化后必须仍在来源根目录内。
5. TLS 默认校验证书；不提供“忽略所有证书”开关，除非产品明确要求并单独提示风险。
6. 作者链接 scheme 白名单；禁止 `javascript:`/`data:`/`file:`/`intent:`。
7. 远程内容只读预览；不执行下载文件中的脚本（Office/PDF 用隔离 Web 容器时关闭不必要的 JSBridge）。
8. 缓存目录只存应用私有目录。

---

## 19. 性能要求

- 目录列表首屏 < 1s（局域网正常时）。
- 大目录（1000+ 项）使用虚拟列表，不一次性创建全部组件。
- 图片缩略图异步加载、内存缓存 + 磁盘缓存，列表快速滚动不卡顿。
- 视频 4K 播放优先硬解；软解时监控掉帧/温度。
- 远程视频使用 Range/offset，不整文件下载。
- M2TS 时长探测只读头尾；不得扫描 5.78GB。
- 所有文件/网络 IO 放到 TaskPool/异步任务，不阻塞 UI 线程。
- 页面销毁时释放播放器、图片 PixelMap、HTTP 请求、TCP 服务。

---

## 20. 错误与空状态文案

统一中文文案，避免直接展示系统异常堆栈：

- 目录不存在：`目录不存在或已被移动`
- 无权限：`没有访问该目录的权限，请重新选择目录`
- 网络失败：`连接失败，请检查地址和网络`
- 认证失败：`用户名或密码不正确`
- 超时：`连接超时`
- 播放失败：`这个媒体无法解码或格式不受支持`
- 缓存失败：`缓存读写失败`
- 空目录：`这个目录是空的`
- 搜索无结果：`没有找到匹配的文件`

---

## 21. 构建与发布

### 21.1 环境

- DevEco Studio 5.0+（以支持 API 12/13 的稳定版为准）
- HarmonyOS NEXT SDK
- Node.js（DevEco 自带 hvigor 环境）
- 签名：AppGallery Connect 创建应用 `com.cctomato.tv`，下载证书/Profile

### 21.2 工程配置

`AppScope/app.json5`：

```json5
{
  "app": {
    "bundleName": "com.cctomato.tv",
    "vendor": "cctomato",
    "versionCode": 1000000,
    "versionName": "1.0.0",
    "icon": "$media:app_icon",
    "label": "$string:app_name"
  }
}
```

`module.json5` 的 `deviceTypes` 必须包含 `phone`、`tablet`、`2in1`、`tv`。

### 21.3 构建命令

```bash
# 安装依赖
ohpm install

# 构建 HAP
hvigorw assembleHap --mode module -p product=default

# 构建 APP
hvigorw assembleApp
```

实际任务名以工程 `hvigorfile.ts` 与 DevEco 版本为准。

### 21.4 CI

- HarmonyOS 命令行构建依赖 DevEco/SDK 与签名材料，GitHub Actions 无官方公开托管 SDK。
- 建议：
  1. 使用华为云/AppGallery Connect 的构建服务；
  2. 或在自建 Windows/macOS Runner 安装 DevEco Studio + SDK，缓存 `~/.ohpm` 与 SDK；
  3. 签名证书/Profile 放 CI Secret，不提交仓库。
- 产物：`.hap` / `.app`；上传 AppGallery Connect 审核。
- 版本号与 Android 版本无需一致；建议 `versionName` 从 `1.0.0` 起。

### 21.5 应用图标 / TV 资源

- 手机/平板/PC 图标：分层图标或普通图标。
- TV 需要横版 banner/大图标，比例按智慧屏要求。
- 应用名称统一 `TomatoFile`。
- 资源使用 `resources/base/media`，不要硬编码路径。

---

## 22. 测试矩阵

### 22.1 设备

- 手机：直板、折叠屏（展开/折叠）。
- 平板。
- PC / 2in1：窗口缩放、鼠标、键盘。
- 智慧屏/TV：遥控器、16:9、焦点。
- 至少一台 API 12 设备/模拟器；如支持更低 API，补充测试。

### 22.2 来源

- 本机目录（Picker 授权、重启后仍可访问）。
- WebDAV（HTTP/HTTPS、Basic Auth、中文路径、空格）。
- SMB（SMB2/3、域、错误密码）。
- FTP/FTPS（PASV、REST、断线）。
- 远程大文件 Range 播放。

### 22.3 文件

- 图片：jpg/png/webp/heic，超大图，EXIF 作者链接。
- 文本：UTF-8/GBK/UTF-16，大文件。
- PDF、docx、xlsx、pptx。
- 视频：MP4 4K、MKV、WebM、WMV、AVI、TS、M2TS（BDAV 192 字节、起始时间非 0）、RMVB、FLV。
- 音频：MP3/FLAC/M4A/OGG，带/不带封面，带/不带标签。
- 目录：1000+ 文件、中文名、空格、特殊字符、符号链接/越界路径。

### 22.4 关键验收

1. 进入第二级目录不崩溃（重点回归 HarmonyOS 不同 API 版本）。
2. 视频/音频进入各自界面，不先闪错误界面。
3. 音频切歌后标题/封面/歌词立即更新，旧异步任务不能覆盖。
4. 音频底部有上一首/播放/下一首 + 倍速/列表/歌词工具栏。
5. M2TS 显示时长、可拖动、可快进快退。
6. TV 二维码表单提交后字段自动回显，关闭弹窗后链接失效。
7. 图片作者链接仅协议 URL 走系统打开，不搜索。
8. 目录返回恢复列表与滚动位置。
9. 缓存清理后远程文件可重新加载。
10. 后台音频锁屏控制正常。

---

## 23. 实施顺序建议

### Phase 1：工程骨架与设备适配
- 创建 Stage 工程，bundleName `com.cctomato.tv`。
- `deviceTypes` 四形态。
- Navigation、主题、资源、断点布局。
- 首页 + 设置空壳。

### Phase 2：本机来源与浏览
- Picker 目录授权 + 持久化。
- FileKinds、FileEntry、LocalFileSystem。
- 首页来源卡片、添加本机来源。
- 浏览页列表/网格、排序、面包屑、加载/空/错误。
- 目录历史栈 + 滚动恢复。

### Phase 3：远程来源
- WebDAV 完整。
- FTP/FTPS。
- SMB（按 SDK/原生库可行性）。
- 加密凭据、测试连接、编辑/删除。
- 远程缓存与清理。

### Phase 4：预览
- 文本。
- 图片查看器 + 元数据 + 链接规则。
- PDF。
- Office。
- 文件信息/用其他应用打开。

### Phase 5：媒体
- 媒体类型与路由分离。
- AVPlayer 通用视频/音频。
- 全格式原生引擎（FFmpeg/VLC 方案选型）。
- M2TS PCR 时长/seek。
- 视频手势、音轨/字幕、倍速。
- 音频播放列表、封面、歌词、后台。
- TV 遥控器播放器。

### Phase 6：TV / 跨端
- TV 焦点、布局、banner。
- 手机扫码填写服务 + 二维码。
- PC 窗口/鼠标/键盘。
- 平板多列。

### Phase 7：稳定性与发布
- 大文件/大目录/弱网/异常。
- 生命周期释放、内存。
- 签名、HAP/APP、AppGallery 上架材料。
- 回归测试矩阵。

---

## 24. 已知风险与决策点

1. **全格式媒体**：HarmonyOS AVPlayer 不支持 WMV/AVI/RMVB/M2TS 等所有容器/编码。必须提前验证 FFmpeg/libVLC 在 OHOS 的编译与 Surface 渲染；这是最大技术风险。
2. **SMB**：若 SDK 无官方 SMB API，需要 NAPI + libsmb2；需评估包体积、许可证、线程模型。
3. **FTP/FTPS**：可能需要自实现协议或 libcurl；需处理 PASV、TLS、超时。
4. **PDF/Office**：优先系统 Kit；没有则 Web + JS 渲染，注意安全与内存。
5. **文件访问**：HarmonyOS 无 Android 式全盘权限；电视/PC 上目录选择器体验需真机验证。
6. **后台音频**：ContinuousTask/AVSession 配置与权限需按目标 API 版本确认。
7. **PC/2in1**：窗口尺寸变化、键盘/鼠标事件、菜单行为需单独测试。
8. **TV**：焦点管理、遥控器按键码、banner 尺寸、应用市场上架要求。
9. **二维码 HTTP 服务**：部分系统可能限制局域网监听/本地网络；需真机验证，失败时给出替代方案（手动输入 TV 显示的地址）。
10. **API 版本差异**：所有系统 Kit 以 DevEco 当前 SDK 为准，禁止照搬 Android API。

---

## 25. 交付物清单

新工作区完成时至少包含：

- 可编译的 HarmonyOS NEXT 工程。
- `com.cctomato.tv` 配置。
- 四形态可运行。
- 本机/WebDAV 来源完整；SMB/FTP 按风险决策交付。
- 浏览、预览、图片、视频、音频、缓存、设置、TV 扫码。
- 单元测试/关键路径测试。
- 构建说明与签名说明。
- 已知限制列表（尤其全格式媒体、SMB、外挂字幕）。
- 发布包 `.hap`/`.app` 或 AppGallery 构建产物。

---

## 26. 给新工作区开发的执行约定

1. 先读本文件，再检查 DevEco 当前 SDK 的实际 API，不凭记忆调用不存在的 Kit。
2. 每个阶段先做可运行最小闭环，再扩展；不要一次性搭完所有页面。
3. 所有用户可见文案使用中文；代码标识符使用英文。
4. 所有文件/网络操作必须异步，UI 线程不做 IO。
5. 播放器、图片、TCP 服务、HTTP 请求必须在页面销毁时释放。
6. 修改数据模型/路由/权限时同步更新本文档。
7. 每个阶段结束提供：实现说明、已验证设备/API、未完成项、风险。
8. 迁移目标是“行为等价”，不是逐行翻译 Android 代码；允许用 HarmonyOS 更合适的组件，但必须保留本文件定义的交互和验收标准。
