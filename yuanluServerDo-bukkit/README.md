# yuanluServerDo-bukkit 模块介绍

## 模块定位

`yuanluServerDo-bukkit` 是 Minecraft **Bukkit/Spigot/Paper 服务端插件**的核心实现模块，作为跨服操作系统的**客户端侧**。它通过 BungeeCord/Velocity 代理服务器的插件消息通道（Plugin Message Channel）与其他服务器通信，提供跨服传送、跨服地标、跨服家、隐身、回退等一体化功能。

## 文件清单

```
yuanluServerDo-bukkit/
├── pom.xml                                      # Maven 构建配置
├── src/main/resources/
│   ├── plugin.yml                               # 插件入口：yuan.plugins.serverDo.bukkit.Main
│   └── config.yml                               # 完整配置（命令、权限、消息等）
└── src/main/java/yuan/plugins/serverDo/bukkit/
    ├── Main.java                                # 插件主类（入口、配置加载、消息系统）
    ├── Core.java                                # 核心类（事件监听、跨服通信、传送逻辑）
    ├── MESSAGE.java                             # 国际化消息系统
    ├── SafeLoc.java                             # 安全坐标查找器
    ├── PackageUtil.java                         # 包/类加载工具
    ├── cmds/                                    # 命令实现包
    │   ├── Cmd.java                             # 抽象命令基类
    │   ├── CommandManager.java                  # 命令注册管理器
    │   ├── CmdTp.java                           # /tp 命令
    │   ├── CmdTpa.java, CmdTpahere.java         # /tpa, /tpahere 请求传送
    │   ├── CmdTphere.java                       # /tphere 拉人
    │   ├── CmdTpaccept.java, CmdTpdeny.java     # 接受/拒绝传送
    │   ├── CmdTpcancel.java                     # 取消传送请求
    │   ├── CmdWarp.java, CmdHome.java           # /warp, /home 传送
    │   ├── CmdSetWarp.java, CmdSetHome.java     # 设置地标/家
    │   ├── CmdDelWarp.java, CmdDelHome.java     # 删除地标/家
    │   ├── CmdSpawn.java, CmdSetSpawn.java      # 出生点管理
    │   ├── CmdDelSpawn.java
    │   ├── CmdBack.java                         # /back 返回
    │   ├── CmdVanish.java                       # /ysd-v 隐身
    │   ├── CmdReload.java                       # 重载
    │   ├── CmdTrans.java                        # 数据迁移
    │   ├── TabTp.java, TabWarp.java, TabHome.java# Tab补全
    │   └── package-info.java
    └── third/
        ├── Third.java                           # 第三方插件数据迁移框架
        └── package-info.java
```

## 核心类详解

### Main.java —— 插件主类

- **角色**：Bukkit 插件入口，继承 `JavaPlugin` 实现 `Listener`
- **核心职责**：
  - `onLoad()`：加载配置、预注册命令（`preload` 模式）
  - `onEnable()`：注册事件监听器、插件消息通道、初始化 Core
  - 管理多语言消息系统（支持普通字符串、JSON、tellraw）
  - `send(Player, byte[])`：通过 `ShareData.BC_CHANNEL` 向代理端发送数据
- **配置管理**：加载 `config.yml`、`blocks.yml`，支持 `force-override-file`
- **调试**：通过 `yuanlu/config.yml` 的 `debug` 和 `force-override-file` 控制

### Core.java —— 核心大脑（1227行）

最复杂的类，承担以下职责：

**事件监听**（`Listener`）：
- `PlayerMoveEvent`：检测传送等待期间是否移动，移动则取消传送（`BAN_MOVE` 集合）
- `PlayerQuitEvent`：玩家下线时触发清理所有临时数据
- `PlayerJoinEvent`：处理加入服务器后的待传送（`WAIT_JOIN_TP`）
- `PlayerTeleportEvent`：记录 back 位置（当 `use-tp-event` 启用时）
- `PlayerDeathEvent`：死亡时记录位置用于 back
- `AsyncPlayerChatEvent`：实现 @玩家 的彩色高亮（At 功能）

**跨服通信**（`PluginMessageListener`）：
- 监听 `bc:yuanlu-sdo` 通道
- 处理所有 `Channel` 枚举类型的数据包分发
- 维护版本检查白名单：`ALLOW_PLAYERS`（未通过版本检查的玩家只接收 VERSION_CHECK 包）

**内部子系统**：
- `TpHandler`：传送逻辑（本地/远程传送、冷却检查、延迟传送）
- `BackHandler`：back 位置记录与恢复
- `WarpHandler`：地标/家的数据包解析（封装为 Channel.Warp/Home 协议调用）
- `TabHandler`：Tab 补全内容通过 BC 端代理处理
- `SoundHandler`：播放音效枚举映射
- `Permissions`：权限管理（包括 `PerAmount` 数量权限体系）
- `CallbackQueue`：回调队列，按顺序执行多个异步任务

### MESSAGE.java —— 消息系统（275行）

多层次消息体系：
- `Msg`：消息包装类，缓存式管理，支持 `reload()`
- `StrMsg`：普通字符串消息（`sender.sendMessage`）
- `JsonMsg`：单条 JSON 消息（通过 `tellraw` 命令发送）
- `MultiJsonMsg`：多条 JSON 消息
- `EmptyMsg`：空消息（用于禁用消息的场景）

支持变量替换（`Tool.parseVar`）和格式化参数。

### SafeLoc.java —— 安全坐标查找器（555行）

- **目标**：防止玩家被传送到岩浆、虚空、窒息方块等危险位置
- **策略**：在目标位置周围 5 格球形范围内搜索安全坐标
- **搜索算法**：
  1. 检查当前位置是否安全
  2. 垂直搜索（上下）
  3. 水平近邻搜索（按距离排序的 `SEARCH_POS[]` 数组）
- **版本适配**：通过 `@LimitImpl` 注解 + `VersionCmp` 版本比较，自动选择 `Impl`（>1.7.10）或 `Impl_ge1_7_10`（<=1.7.10，不检查 Spectator 模式）
- **方块分类**：`DANGERS`（危险）、`CAN_STAND`（可站立）、`CAN_BREATH`（可呼吸），支持反射加载（如 `!!isSolid`）

### Cmd.java + CommandManager.java —— 命令框架

- `Cmd` 抽象基类：
  - 封装 `Command` 标准流程：检查玩家 -> 检查权限（可委托 BC） -> 执行
  - 使用 `CallbackQueue` 串联异步验证步骤
  - 消息缓存：`msg(String type)` 自动从 `Main.mes()` 取配置
  - bstats 统计：`EXECUTE_COUNT` 记录命令执行次数
- `CommandManager`：
  - 通过反射扫描 `cmds` 包下所有命令类
  - 从 `config.yml` 的 `cmd` 节点读取命令配置（名称、权限、描述等）
  - 通过反射调用 `Bukkit.getServer().getCommandMap()` 动态注册命令
  - 支持 `preload` 在 `onLoad()` 阶段注册（用于覆盖 Essentials/CMI 的同名命令）

## 命令注册模式

```java
// Main.onLoad()
if (config.getBoolean("setting.preload")) {
    CommandManager.init(config.getConfigurationSection("cmd"));
}

// Main.onEnable()
getServer().getMessenger().registerOutgoingPluginChannel(this, ShareData.BC_CHANNEL);
getServer().getMessenger().registerIncomingPluginChannel(this, ShareData.BC_CHANNEL, Core.INSTANCE);
```

**设计特点**：
- 每个命令类继承 `Cmd`，类名格式 `CmdXxx`
- 命令名从类名自动推断：`CmdTpAccept` -> `tpaccept`（驼峰转短横线）
- 注册信息来自 `config.yml` 的 `cmd.xxx` 节点，删除节点即可禁用命令
- `preload: true` 可在 `onLoad()` 阶段注册，确保覆盖其他插件

## 典型命令模式（以 CmdWarp 为例）

```java
Core.listenCallBack(player, Channel.WARP, 2, (name, server) -> {
    // 收到搜索结果回调后...
    msg("tp", sender, name, server);
    Core.BackHandler.recordLocation(player, server);
    Main.send(player, Channel.Warp.s3C_tpWarp(name));
});
Main.send(player, Channel.Warp.s2C_searchWarp(arg)); // 发送搜索请求
```

所有需要跨服交互的命令都遵循：**发送请求 -> 注册回调等待响应 -> 回调中执行本地逻辑**。

## TPA 请求流程（关键交互）

1. **A执行 `/tpa B`**：
   - A 发送 `Tp.s0C_tpReq("B", 1)` 到 BC
   - 监听回调 `Channel.TP` id=1，接收 B 的真实名字
   - A 添加本地等待记录 `CmdTpa.addTpReq()`
   - 监听回调 `Channel.TP` id="5-B"，等待 B 的接受/拒绝响应

2. **BC转发请求给 B**：
   - B 收到 `Tp.p2S_tpReq`，显示请求消息
   - B 的 `CmdTpaccept.addTpReq()` 被调用

3. **B执行 `/tpaccept`**：
   - B 发送 `Tp.s3C_tpResp("A", true)` 给 BC
   - 监听回调 `Channel.TP` id=4，确认处理成功
   - BC 转发给 A：`Tp.s5S_tpResp("B", true)`

4. **A收到接受响应**：
   - 触发回调，A 调用 `Core.tpTo(player, "B", waitTime, true)`
   - 实际传送通过 `Tp.s6C_tpThird(A, B)` -> BC -> `Tp.p8S_tpThird` 到目标服务器

## 事件监听设计

Core 是一个 **Singleton + 多重角色类**：
- `implements PluginMessageListener` — 跨服消息接收
- `implements Listener` — Bukkit 事件监听

**清理机制**：
```java
private static final ArrayList<Consumer<Player>> CLEAR_LISTENER = new ArrayList<>();
static void registerClearListener(Consumer<Player> c);
static void callClear(Player player);
```

在玩家退出时，`onPlayerQuit` 调用 `callClear` 统一清理所有临时状态：
- `TabHandler.TAB_REPLACE` — Tab 替换内容
- `TpHandler.BAN_MOVE` — 禁止移动标记
- `CALL_BACK_WAITER` — 所有回调等待
- `ALLOW_PLAYERS` — 版本通过标记

## 跨服通信设计

### 通信协议（依赖 common 模块）

**Channel 枚举** 定义了所有数据包类型：

| 类型 | 说明 |
|------|------|
| `VERSION_CHECK` | 版本校验（基于 MD5） |
| `PERMISSION` | 权限检查委托 BC 端 |
| `TP` | 传送请求/响应全流程 |
| `TP_LOC` | 坐标类传送（warp/home/spawn） |
| `WARP` | 地标搜索/设置/删除 |
| `HOME` | 家搜索/设置/删除 |
| `BACK` | Back 位置同步 |
| `COOLDOWN` | 传送冷却同步 |
| `SERVER_INFO` | 服务器信息交换 |
| `VANISH` | 隐身状态 |
| `TAB_PARSE` | BC 端 Tab 补全代理 |
| `PLAY_SOUND` | 跨服播放音效 |
| `TRANS_HOME/TRANS_WARP` | 第三方数据迁移 |
| `TIME_AMEND` | 时间同步 |

### 数据包格式

```
[0-3] int: Channel ID (枚举序号)
[4]   byte: 子包 ID
[5+]  实际数据（UTF/Double/Boolean/Location等）
```

### 回调等待机制

```java
// Core.java
private static final EnumMap<Channel, Map<UUID, ArrayList<ListenCallBackObj>>> CALL_BACK_WAITER;

public static void listenCallBack(Player, Channel, Object checker, long maxTime, Object handler);
public static boolean callBack(Player, Channel, Object checker, Consumer handler);
```

- 每个 `Channel` 有一个按玩家 UUID 组织的回调列表
- `checker` 用于匹配特定回调（通常是数据包子 ID 或特定标识）
- 超时自动清理（使用 `WaitMaintain` 延迟队列）

## 与其他模块交互关系

```
yuanluServerDo-bukkit
    │ 依赖（compile）
    ▼
yuanluServerDo-common
    ├── Channel.java          # 所有数据包协议定义
    ├── ShareData.java        # 共享数据（通道名、编码、TabType）
    ├── ShareLocation.java    # 跨服坐标封装
    ├── WaitMaintain.java     # 延迟清理工具
    ├── Tool.java             # 通用工具
    └── ...                   # 其他辅助类
    │
    │ 通过 Plugin Message Channel 通信
    ▼
yuanluServerDo-bungee (BC代理端)
    └── 处理路由、数据存储、群服逻辑
```

## 设计模式总结

| 模式 | 应用位置 | 说明 |
|------|----------|------|
| **单例模式** | `Core.INSTANCE` | 单例持有所有处理器 |
| **策略模式** | `SafeLoc` 多版本实现 | `Impl`/`Impl_ge1_7_10` 自动适配 |
| **模板方法** | `Cmd.execute()` | 固定流程：玩家检查 -> 权限检查 -> `execute0()` |
| **回调/观察者** | `CALL_BACK_WAITER` | 异步请求-响应回调注册 |
| **工厂+反射** | `CommandManager` | 反射扫描包内类自动注册命令 |
| **对象池** | `DataIn`/`DataOut` | 带缓存池的 ByteArray 流 |
| **延迟队列** | `WaitMaintain` | 统一管理超时清理 |

## 重难点总结

1. **传送延迟与移动检测**：执行命令后玩家需站立不动等待，期间 `PlayerMoveEvent` 监控 `BAN_MOVE` 集合，移动即取消

2. **跨服传送回退记录**（Back）：每次传送前记录当前位置，死亡/传送均自动记录，支持跨服 back（通过 BC 转发 back 坐标）

3. **权限委托**：Bukkit 端没有的其他服务器玩家的权限信息，通过 `PERMISSION` 通道委托 BC 端检查

4. **Tab 补全代理**：由于 BC 端才知道所有在线玩家/群服数据，Tab 补全通过 `TAB_PARSE` 通道将输入发给 BC，BC 返回补全列表

5. **Velocity 兼容**：当检测到 Velocity 代理时，Bukkit 端需要额外发送命令名列表给代理端用于命令注册（`ServerInfo.sendC`）
