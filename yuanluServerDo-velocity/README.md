# yuanluServerDo-velocity 模块介绍

## 模块文件概览

| 文件路径 | 类型 | 说明 |
|---|---|---|
| `pom.xml` | Maven 配置 | 依赖 velocity-api、yuanluServerDo-common、bungeecord-config、bstats-velocity |
| `src/main/resources/velocity-plugin.json` | Velocity 插件描述 | 定义插件 ID、主类、版本等 |
| `src/main/resources/proxy-config.yml` | 代理层配置 | 服务器分组、禁用服务器、Tab 替换前缀、时间校准间隔、保存延时 |
| `src/main/java/.../Main.java` | 主入口 / 消息路由器 | Velocity 插件生命周期 + 插件消息分发中心 |
| `src/main/java/.../Core.java` | 核心逻辑 | 跨服传送校验、隐身、时间校准、坐标传送执行 |
| `src/main/java/.../TransHandler.java` | 数据迁移处理器 | 批量接收并迁移外部 Home/Warp 数据 |
| `src/main/java/.../TabHandler.java` | Tab 补全处理器 | 玩家名、Home、Warp、TP 等代理层 Tab 补全 |
| `src/main/java/.../ConfigManager.java` | 配置管理器 | WARPS/HOMES 存储、服务器分组、文件读写 |
| `src/main/java/.../CmdProxy.java` | 命令代理 | 将代理端命令转发到后端子服务器实际执行 |

## 模块用途

**yuanluServerDo-velocity** 是 Minecraft 跨服插件套件的 **Velocity 代理端模块**，承担以下核心职责：

1. **跨服通信中枢**：在多个 Minecraft 子服务器之间转发插件消息（PluginMessage）
2. **集中数据存储**：集中管理跨服 Home（家）、Warp（地标）、Back（返回点）数据
3. **跨服传送协调**：处理 TPA、TP、TPHere、Back、Home、Warp 等跨服传送请求
4. **权限校验转发**：将子服务器的权限检查请求代理到 Velocity 层校验
5. **隐身模式（Vanish）管理**：跨服隐身状态同步
6. **冷却时间同步**：跨服传播传送冷却时间
7. **命令代理**：在代理层注册后端命令占位符，并转发到后端执行
8. **数据迁移**：支持从其他服务器批量导入 Home/Warp 数据

## 核心类分析

### Main.java —— 入口与消息路由器

这是整个模块的枢纽，职责包括：

- **生命周期管理**：注册 `ProxyInitializeEvent`、`ProxyShutdownEvent`、`ServerPostConnectEvent`、`PluginMessageEvent`、`TabCompleteEvent`、`CommandExecuteEvent`
- **消息分发中心**：`event_onPluginMessage()` 根据 `Channel` 枚举类型和 `subId` 将消息路由到不同处理逻辑
- **发送工具**：提供 `send()` 多重重载（发给玩家、发给服务器、队列发送）
- **数据包队列**：`PACKET_QUEUE` 在无玩家连接的服务器上缓存消息，待玩家连接后自动发送（兼容 BungeeCord 设计）
- **玩家查找**：`getPlayer()` / `getPlayers()` 支持模糊搜索，并检查服务器组权限

### Core.java —— 跨服核心逻辑

- **传送校验**：`canTp()` 基于服务器分组配置判断两服务器间是否允许传送，同时检查隐身状态
- **坐标传送**：`tpLocation()` 是整个插件最核心的跨服传送方法：
  - 目标服务器 == 当前服务器：直接发送成功响应
  - 目标服务器 != 当前服务器：先向目标服发送传送指令包，再调用 Velocity 的 `player.createConnectionRequest(targetServer).connect()` 异步切服，成功后回调
- **隐身管理**：`switchVanish()` / `autoVanish()` / `hasVanish()` 管理跨服隐身状态
- **时间校准**：`startTimeAmend()` 定期向子服发送校准请求，`timeAmendCallback()` 计算网络延迟偏移

### ConfigManager.java —— 配置与数据管理层

- **服务器分组**：`GROUPS` HashMap 定义哪些服务器之间可以互相传送
- **禁用服务器**：`BAN_SERVER` 黑名单
- **WARPS**：全局地标数据，存储在 `warp.yml`
- **HOMES**：玩家家数据，使用自定义 `HomesLRU`（继承 `LRUCache`）实现懒加载/内存缓存，按 UUID 分目录存储为 `home.yml`
- **配置枚举**：`ConfFile` 和 `PlayerConfFile` 用枚举封装各配置文件的加载/保存逻辑
- **延时保存**：通过 `WaitMaintain` 工具类批量延时保存，减少 IO 压力

### TransHandler.java —— 数据迁移

- 处理从旧服务器或其他插件迁移 Home/Warp 数据
- 接收 `TransHome` / `TransWarp` 数据包，收集完成后一次性写入配置
- 自动设置数据所在服务器为发送者当前服务器

### TabHandler.java —— Tab 补全

- 拦截 Velocity 的 `TabCompleteEvent`
- 支持五种补全类型：`TP_ALL`、`TP_NORMAL`、`WARP`、`HOME`、`AT`
- 结果按服务器分组过滤（只显示本组内可传送的玩家/地标/家）

### CmdProxy.java —— 命令代理

- 接收子服通过 `SERVER_INFO` 通道上报的命令列表
- 在 Velocity 端动态注册 `SuggestCommand`，将命令映射到代理层
- 执行时通过 `CommandExecuteEvent` 将命令 `forwardToServer()`
- Tab 补全通过异步方式转发到后端服务器处理

## 关键逻辑详解

### 跨服 TPA 传送状态机

```
阶段1: 请求
玩家A(子服1) --TP.p0C_tpReq--> Velocity(查找玩家B) --TP.p2S_tpReq--> 玩家B(子服2)

阶段2: 响应
玩家B(子服2) --TP.p3C_tpResp--> Velocity --TP.p5S_tpResp--> 玩家A(子服1)

阶段3: 执行传送
玩家A(子服1) --TP.p6C_tpThird--> Velocity:
  1. 通知目标服(子服2): TP.p8S_tpThird
  2. player.createConnectionRequest(targetServer).connect() 切服
  3. 回调发送 TP.p7S_tpThirdReceive 给玩家A
```

### 跨服坐标传送（Home/Warp/Back/TpLoc）

```
玩家(子服A) 请求 Home/Warp:
  Velocity 查询目标坐标和目标服务器
  if 目标 == 当前:
    直接返回成功
  else:
    向目标服发送 TpLoc.p1S_tpLoc (准备传送)
    player.createConnectionRequest(targetServer).connect() 异步切服
    回调返回成功/失败给原子服
```

### 权限校验流程

```
子服 --Permission.sendS(权限节点)--> Velocity
      Velocity: player.hasPermission(节点)
      --Permission.sendC(节点, 结果)--> 子服
```

### 时间校准算法

```
Velocity 定时发送 TimeAmend.sendC() 给有玩家的子服
子服回传 TimeAmend.sendS(本地时间戳)
Velocity 计算:
  shift = velocityTime - (serverTime + roundTripTime/2)
存储到 TIME_AMEND[serverName]
后续跨服冷却广播时统一修正
```

## 设计模式

| 模式 | 应用位置 | 说明 |
|---|---|---|
| **单例模式** | `Main.main` | 静态单例持有插件实例 |
| **策略模式** | `Channel` 枚举 | 每种消息类型对应一个处理策略 |
| **工厂+对象池** | `DataIn.pool()` / `DataOut.pool()` | 内部缓存池（16/128个）复用 IO 流对象 |
| **枚举单例** | `ConfFile` / `PlayerConfFile` | 配置文件类型用枚举封装加载/保存 |
| **模板方法** | `ConfFile.load()` / `save()` | 定义骨架，子类实现 `load0()` / `save0()` |
| **观察者模式** | Velocity `@Subscribe` 事件体系 | 事件驱动架构 |
| **回调模式** | `tpLocation()` 的 `Function<Boolean, byte[]>` | 异步切服完成后回调通知 |
| **LRU 缓存** | `HomesLRU` 继承 `LRUCache` | 玩家家数据懒加载、缓存、自动卸载保存 |

## 与其他模块的交互关系

### 项目架构

```
yuanluServerDo (Parent POM)
|-- yuanluServerDo-common              <- 共享协议与工具
|   |-- Channel.java                   <- 所有数据包定义与编解码
|   |-- ShareData.java                  <- 共享常量、日志、Tab 类型
|   |-- ShareLocation.java              <- 跨服坐标对象
|   +-- LRUCache.java / WaitMaintain... <- 工具类
|-- yuanluServerDo-bukkit              <- Bukkit 后端（基础）
|-- yuanluServerDo-bukkit-bungeecord   <- Bukkit 适配 BungeeCord
|-- yuanluServerDo-bukkit-velocity     <- Bukkit 适配 Velocity
|-- yuanluServerDo-bungeecord          <- BungeeCord 代理端
+-- yuanluServerDo-velocity            <- Velocity 代理端（本模块）
```

### 模块依赖

- `yuanluServerDo-velocity` 以 **compile** 方式依赖 `yuanluServerDo-common`
- 不直接依赖任何 Bukkit 模块，通过 **Velocity PluginMessage** 协议与后端通信

### 通信协议

- **通道名**：`bc:yuanlu-sdo`（转小写，Legacy Channel Identifier）
- **数据格式**：`[4 字节 int: Channel 类型 ID][1 字节: SubId][payload...]`
- **流向**：
  - 子服 -> Velocity：`PluginMessageEvent`（Source=ServerConnection, Target=Player）
  - Velocity -> 子服：`ServerConnection.sendPluginMessage(channel, data)`

## 代理层跨服转发逻辑（重点分析）

所有跨服转发逻辑集中在 `Main.event_onPluginMessage()` 方法中，以下是主要转发场景：

### 玩家传送（TP）转发 —— 最复杂的状态机

这是整个模块业务复杂度最高的部分，支持多种传送类型：

| 类型 | 说明 |
|---|---|
| `0` | `/tp` 直接传送 |
| `1` | `/tpa` 请求传送 |
| `2` | `/tphere` 拉人 |
| `3` | `/tpahere` 请求拉人 |
| `4/5` | 第三方传送（mover/target） |

**核心逻辑**：
1. Velocity 接收源服务器请求包，解析目标玩家名
2. 使用 `Main.getPlayer()` 在**全局**模糊搜索目标玩家
3. 通过 `Core.canTp()` 检查服务器分组权限和隐身状态
4. 将请求转发到目标玩家所在服务器
5. 等待目标玩家响应后，再回传给源玩家
6. 最终执行时，使用 Velocity API 的 `createConnectionRequest()` 完成服务器切换

### 坐标传送转发（Home/Warp/Back）

Velocity 作为**数据持有层**和**协调层**：

- **数据层**：Velocity 持有全局 WARPS 和 HOMES 配置
- **协调层**：当玩家请求传送到某 Home/Warp 时：
  - 查询目标坐标和目标服务器名
  - 向目标服务器发送 `TpLoc.s1S_tpLoc(loc, playerName)`（通知准备传送）
  - 调用 `player.createConnectionRequest(targetServer).connect()` 切服
  - 异步回调返回结果

### 权限校验转发

```
子服发送 Permission.sendS(权限节点)
  v
Velocity 调用 player.hasPermission(节点)
  v
Velocity 发送 Permission.sendC(节点, 结果) 回子服
```

这是典型的代理端权限委托模型，因为 Velocity 才是持有实际玩家连接和权限的地方。

### 冷却广播

```
某子服触发传送冷却
  v
子服发送 Cooldown.sendS(冷却结束时间)
  v
Velocity 遍历所有服务器（带时间修正）
  v
向每个服务器发送 Cooldown.broadcast(UUID, 修正后的结束时间)
```

### 命令代理转发

```
子服上线时上报: ServerInfo.sendC(namespace, [cmd1, cmd2, ...])
  v
Velocity 注册 SuggestCommand 占位符
  v
玩家执行命令 -> CommandExecuteEvent -> forwardToServer()
  v
Tab 补全 -> 异步请求后端 -> 后端返回 TabParse.sendC -> 合并代理层数据返回
```

### 数据包队列机制（兼容设计）

```java
private static final Map<ServerInfo, Queue<byte[]>> PACKET_QUEUE = new HashMap<>();
```

- 当向某服务器发送消息但该服当前无玩家时，消息缓存到队列
- 玩家首次连接到该服务器时（`ServerPostConnectEvent`），触发队列发送
- 这是为了兼容 BungeeCord 时代的无连接发送模式

## 总体评价

yuanluServerDo-velocity 是一个设计成熟、逻辑清晰的 Minecraft Velocity 代理端插件：

- **协议层**：定义了完整的二进制通信协议（`Channel` 枚举），通过 PluginMessage 实现代理层与后端的双向通信
- **数据层**：集中管理全局数据（WARPS、HOMES），使用 LRU 缓存减轻内存压力，延时保存减少 IO
- **传送层**：实现了完整的跨服传送状态机，覆盖 TPA、TPHere、Home、Warp、Back 等全部场景
- **架构层**：事件驱动 + 函数式回调处理异步操作，对象池优化 IO 性能，代码结构清晰

该模块与后端子服的 bukkit-velocity 模块配对工作，共同构成完整的跨服解决方案。与 BungeeCord 版本共享 common 模块的协议定义，确保了协议层面的统一性。
