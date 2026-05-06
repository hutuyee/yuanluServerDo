# yuanluServerDo-common 模块介绍

## 模块定位

`yuanluServerDo-common` 是整个跨服插件套件的**公共协议与工具层**。它本身**不依赖任何 Minecraft 平台 API**（无 Bukkit/Velocity/Bungee 导入），是一个纯 Java 8 库，被所有平台子模块通过 Maven `compile` scope 共同依赖。

## 文件清单

| 文件 | 行数 | 核心职责 |
|------|------|----------|
| `Channel.java` | 2694 | **协议核心**：枚举定义所有数据包类型、编解码、分发逻辑 |
| `ShareData.java` | 99 | 共享常量（通道名、编码、日志）、Tab 类型枚举 |
| `ShareLocation.java` | 30 | 跨服坐标不可变 POJO（x, y, z, yaw, pitch, world, server） |
| `WaitMaintain.java` | 374 | 基于 `DelayQueue` 的延迟自动清理工具 |
| `Tool.java` | 670 | 通用工具类（集合转换、字符串、序列化、容错执行等） |
| `LRUCache.java` | 167 | 基于数组的 LRU 缓存实现 |
| `At.java` | 143 | 聊天消息中 `@玩家名` 的高亮解析处理 |
| `ChatColor.java` | 187 | **自实现**的 Minecraft 聊天颜色枚举（零 Bukkit 依赖） |

## 核心类详解

### Channel.java —— 跨服通信协议核心

这是整个项目**最核心、最庞大**的类（2694 行）。它是一个 `enum`，每个枚举常量代表一种数据包类型，通过 `ordinal()` 作为包 ID。

**枚举值**：
```java
VERSION_CHECK, PERMISSION, TP, COOLDOWN, TIME_AMEND,
SERVER_INFO, VANISH, WARP, HOME, TP_LOC,
TRANS_HOME, TRANS_WARP, BACK, PLAY_SOUND, TAB_PARSE
```

**关键机制**：
- **协议版本校验**：静态初始化时，将所有枚举名称拼接后计算 MD5，生成 `VERSION` 字段。`VersionCheck` 包用于客户端/服务器端握手校验，**防止不同版本插件混用导致协议错乱**。
- **自动 ID 注入**：枚举构造时通过反射将 `ordinal()` 写入各 `Package` 子类的静态 `ID` 字段，避免手动维护 ID。
- **包计数**：`PACK_COUNT` 统计每种数据包的收发数量。

**内部基础设施**：
- `DataIn` / `DataOut`：带**对象池**（大小分别为 16 / 128）的 `DataInputStream` / `DataOutputStream` 封装，采用 `synchronized` 管理池状态，支持复用以减少 GC。
- `ByteIn`：`ByteArrayInputStream` 子类，处理包数据偏移（跳过前 4 字节包 ID）并支持缓存清空。
- `Package`：抽象基类，内含大量自定义函数式接口（`BoolConsumer`, `ObjBoolConsumer`, `BiBoolConsumer`, `BiObjIntConsumer`, `BiPlayerConsumer`, `LongObjConsumer` 等），用于回调式分发解析结果。

**各业务数据包子类**：

| 子类 | 说明 |
|------|------|
| `Tp` | 传送协议，`tp`/`tpa`/`tphere`/`tpahere`/第三方传送，含 12 个子协议（0x0 ~ 0xc） |
| `Warp` / `Home` | 地标/家的增删查传、列表请求与响应 |
| `Back` | 跨服 `/back` 返回功能，支持服务器/玩家两种目标 |
| `TpLoc` | 直接跨服传送到指定坐标 |
| `TransHome` / `TransWarp` | 数据迁移协议（UUID + 家/地标坐标传输） |
| `Cooldown` | 传送冷却广播（UUID + 结束时间戳） |
| `TimeAmend` | 时间校准（解决跨服时间不同步问题） |
| `Permission` | 跨服权限查询：Bukkit 请求 -> Proxy 转发 -> 目标 Bukkit 检查 -> 返回结果 |
| `Vanish` | 隐身模式状态同步 |
| `ServerInfo` | 交换代理信息：TAB 名称、代理类型（BungeeCord/Velocity）、后端命名空间与命令列表 |
| `PlaySound` | 跨服播放音效（目前仅定义 `AT` 音效） |
| `TabParse` | TAB 补全解析代理 |

**方法命名规范**：严格遵循 `{s|p}{hexId}{C|S}_{name}`：
- `s`=send, `p`=parse
- `C`=client(Bukkit端), `S`=server(代理端)
- 例：`s0C_tpReq` = Bukkit 发送，子包 ID 0，客户端，传送请求

### ShareData.java —— 全局共享常量

```java
SHOW_NAME  = "元路跨服操作插件";
BC_CHANNEL = "bc:yuanlu-sdo";  // 强制小写
CHARSET    = Charset.forName("UTF-8");
```

- `TabType` 枚举：定义 5 种 Tab 补全分类（`TP_ALL`, `TP_NORMAL`, `WARP`, `HOME`, `AT`），自定义基于 `Character.MAX_VALUE` 进制的紧凑编码方式。
- `readInt(byte[], int, int)`：从字节数组安全读取大端 int。

### ShareLocation.java —— 跨服坐标

```java
double x, y, z;
float yaw, pitch;
String world;
String server;  // 可变，记录所属 Bukkit 服务器名
```

纯 POJO，支持 `clone()`，用于在代理层和 Bukkit 层之间传递玩家位置。

### WaitMaintain.java —— 超时自动清理

- 内部维护一个 `DelayQueue<Element>`，由独立的守护线程持续 `take()`，到期即触发清理。
- 支持对多种数据结构设置超时清理：
  - `Collection<K>` → `CElement`
  - `Map<K, V>` → `MapElement`
  - `Map<K, Collection<V>>` → `MutiMapElement`
  - `Map<T, Map<K, V>>` → `MapMapElement`
- 支持 **Number 值监控**：超时后若数值未改变，触发 `clearListener`。
- 预定义两类默认超时：
  - `T_Net = 5000ms`（网络超时）
  - `T_User = 120000ms`（用户操作超时）

**应用场景**：传送请求的等待确认超时、临时状态缓存的过期清理。

### Tool.java —— 通用工具箱

禁止实例化的静态工具类，主要能力：

- **集合转换**：`translate()`, `translateToList()`, `translateToSet()`, `mapToCollection()`, `mutiMapToCollection()`, `setToMap()`
- **序列化**：`serialize(Collection, char, Function)` / `deserializeList(String, String, Function)`
- **字符串**：`humpTrans()` / `humpTransBack()`（驼峰与下划线互转）、`parseVar()`（模板变量解析，支持 `<var>` 语法）、`randomString()`
- **搜索**：`getMatchList()`（前缀匹配列表）、`search()`（找长度最接近的模糊匹配）
- **容错执行**：`tryRun()` / `tryRunNormal()` — 执行代码体并自动捕获 Throwable，通过 `ShareData.getLogger()` 记录警告
- **类加载器**：`load(Class<?>)` / `load(String)` — 安全触发 `Class.forName`
- **特殊相等**：`equals(Object, Object)` — 若均为 Number，则同时比较 long 值和 double 值（误差容限 `0x1.0p-1021`）

### LRUCache.java —— 最近最少使用缓存

- **不使用 LinkedHashMap**，而是基于 `Object[]` 数组 + 顺序移动实现。
- 线程安全：所有 public 方法标记 `synchronized`。
- `check(K)`：只查不更新缓存位置。
- `get(K)`：查并移至队首，若未命中则调用抽象方法 `create(K)` 创建。
- `clearCache()`：清空缓存，并调用 `clearHandle(K, V)` 钩子（子类可重载）。
- `resize(int)`：动态调整缓存容量，自动迁移或截断数据。
- `CACHE_USE`（AtomicInteger）：统计缓存被访问的总次数。

### At.java —— 聊天 @ 处理

处理聊天消息中的 `@玩家名` 语法：
- `At.format(format, msg, names)`：将消息按 `@` 分割为多个 `At` 段，通过前一段的尾部颜色传播到下一段，保证颜色连续性。如果某段的 `first` 字段（空格前的部分）匹配真实玩家名，则将其高亮为 `AQUA` 色。
- `At.at(msg, names)`：提取消息中所有被 @ 的玩家名流。
- 依赖自实现的 `ChatColor.getLastColors()` 而不是 Bukkit API。

### ChatColor.java —— 自实现聊天颜色

- **完全独立**，复制了 Bukkit 的 `ChatColor` 枚举实现。
- 16 种颜色 + 5 种格式（MAGIC/BOLD/STRIKETHROUGH/UNDERLINE/ITALIC）+ RESET。
- `COLOR_CHAR = '\u00A7'`。
- `getLastColors(String)`：从字符串末尾逆向扫描，提取需要传递给下一段文本的颜色/格式代码序列。

## 设计模式

| 设计模式 | 应用 |
|----------|------|
| **枚举单例 + 策略** | `Channel` 枚举每个值对应一种协议策略，通过 `byId(int)` 统一分发 |
| **对象池** | `DataIn`（池 16）、`DataOut`（池 128）复用字节流对象 |
| **模板方法** | `LRUCache` 定义算法骨架，`create()` 由子类实现 |
| **函数式回调 / Observer** | `Channel` 各解析方法均使用 `Consumer` / `BiConsumer` 回调分发数据 |
| **静态工厂 + 资源池** | `DataIn.pool(byte[])` / `DataOut.pool(int)` |
| **MD5 协议指纹** | `Channel.VERSION` 通过枚举名称 MD5 生成，作为协议兼容性校验依据 |

## 协议字节结构

```
[0-3] int    : 包类型 ID (Channel.ordinal(), 大端)
[4]   byte   : 子包 ID (SubId)
[5..] bytes  : 业务数据 (UTF/double/float/boolean/UUID/int 等)
```

## 与其他模块的关系

```
yuanluServerDo (parent POM, 版本 1.2.2)
    ├── yuanluServerDo-common              ← 本模块 (pure Java, 零平台依赖)
    ├── yuanluServerDo-bukkit              ← Bukkit/Spigot 服务端 (依赖 common)
    ├── yuanluServerDo-bungeecord          ← BungeeCord 代理端 (依赖 common)
    ├── yuanluServerDo-velocity            ← Velocity 代理端 (依赖 common)
    ├── yuanluServerDo-bukkit-bungeecord   ← Bukkit + BungeeCord 组合包
    └── yuanluServerDo-bukkit-velocity     ← Bukkit + Velocity 组合包
```

**运行期交互**：
- **Bukkit -> Proxy**：Bukkit 端调用 `Channel.Tp.s0C_tpReq()` 等方法构建数据包，通过 Bukkit PluginMessage API 发送到代理通道 `bc:yuanlu-sdo`。
- **Proxy -> Bukkit**：BungeeCord/Velocity 端接收数据包后，通过 `Channel.byId()` 识别包类型，再调用各 `pXX_xxx()` 解析方法，将数据以回调方式传递给业务处理逻辑。
- **跨服聊天**：Bukkit 端通过 `At.format()` 处理 `@玩家`，通过自实现的 `ChatColor` 解析颜色，不依赖平台 API，保证 common 的纯粹性。
- **超时管理**：`WaitMaintain` 被代理端用于管理传送请求的 120 秒超时、网络请求的 5 秒超时。
- **缓存加速**：`LRUCache` 可被各端用于缓存玩家查询结果、权限检查结果等高频数据。
