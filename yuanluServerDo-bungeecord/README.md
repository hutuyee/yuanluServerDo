# yuanluServerDo-bungeecord 模块介绍

## 模块定位

`yuanluServerDo-bungeecord` 是 Minecraft 多服务器群组系统的 **BungeeCord 代理端插件**，是 yuanluServerDo 项目的核心跨服协调枢纽。该模块运行在代理层（BungeeCord/Waterfall），负责在多个子服务器之间同步玩家数据、协调跨服传送、管理共享地标与家、处理跨服冷却时间等核心功能。

## 文件清单

| 类型 | 文件路径 |
|------|----------|
| Maven 构建 | `pom.xml` |
| 插件描述 | `src/main/resources/bungee.yml` |
| 代理配置 | `src/main/resources/proxy-config.yml` |
| 主类/入口 | `src/main/java/yuan/plugins/serverDo/bungee/Main.java` |
| 核心逻辑 | `src/main/java/yuan/plugins/serverDo/bungee/Core.java` |
| 配置管理 | `src/main/java/yuan/plugins/serverDo/bungee/ConfigManager.java` |
| 转换处理器 | `src/main/java/yuan/plugins/serverDo/bungee/TransHandler.java` |
| Tab 补全 | `src/main/java/yuan/plugins/serverDo/bungee/TabHandler.java` |
| 包信息 | `src/main/java/yuan/plugins/serverDo/bungee/package-info.java` |

## 模块用途

该模块承担以下关键职责：

1. **跨服传送的代理转发** - 处理 `/tp`、`/tpa`、`/tpahere`、`/tphere` 等命令的跨服中介
2. **共享地标(Warp)管理** - 管理群组内可跨服传送的地标点
3. **玩家家(Home)存储** - 按玩家持久化存储家坐标，支持跨服回家
4. **跨服冷却同步** - 将传送冷却状态广播到所有子服务器
5. **隐身(Vanish)协调** - 管理玩家在跨服环境下的隐身状态
6. **@玩家提醒** - 处理跨服聊天中的 `@玩家名` At 功能，并播放音效提示
7. **Tab补全代理** - 在 BungeeCord 层过滤跨服命令补全候选
8. **时间校准** - 周期性进行代理层和子服务器的时间戳同步
9. **版本兼容性校验** - 确保代理端和子服务器端插件版本一致

## 核心类解析

### Main.java (693行) - 插件主类与事件中枢

| 关键部分 | 说明 |
|----------|------|
| `getPlayer()` / `getPlayers()` | **模糊搜索玩家**，支持按前缀匹配，优先返回同服务器组内玩家 |
| `send(ProxiedPlayer / Server / ServerInfo, byte[])` | **三层数据发送**，负责向特定玩家/服务器/服务器发送数据包 |
| `onEnable()` | 初始化 bStats、注册信道 `bc:yuanlu-SDo`、注册事件监听器、启动时间校准线程 |
| `onDisable()` | 停止时间校准线程、强制保存所有待保存数据 |
| `onPluginMessage()` | **[核心] 信道消息总线** - 接收子服务器消息并分发到各处理器 |
| `onChat()` | 聊天 `@用户` 检测与音效广播 |
| `onServerConnected()` | 玩家连接服务器时发送版本校验和代理信息 |

**主要消息类型处理路由：**

```
PERMISSION -> 代理端权限检查，结果回传
TP -> onPluginTpMessage(TP请求/响应/实际传送)
VERSION_CHECK -> 版本校验
COOLDOWN -> 冷却时间广播到全服
TIME_AMEND -> 时间校准回调
VANISH -> 隐身状态切换
TP_LOC -> onPluginTpLocMessage(坐标传送)
WARP -> onPluginWarpMessage(地标CRUD)
HOME -> onPluginHomeMessage(家CRUD)
BACK -> onPluginBackMessage(返回传送)
TRANS_HOME/TRANS_WARP -> TransHandler(数据转换导入)
```

### Core.java (311行) - 跨服核心逻辑

| 关键方法 | 功能 |
|----------|------|
| `canTp()` | **传送权限校验** - 检查服务器组、隐身状态、是否禁用 |
| `tpLocation()` | **坐标跨服传送** - 判断是否需要切换服务器并执行 `player.connect()` |
| `setHome()` / `getHome()` / `setWarp()` / `getWarp()` | 家/地标 的持久化操作 |
| `startTimeAmend()` / `timeAmendCallback()` | 时间校准的发起与回调 |
| `switchVanish()` / `autoVanish()` | 隐身状态管理 |

### ConfigManager.java (492行) - 配置与持久化

| 组件 | 说明 |
|------|------|
| `ConfFile` 枚举 | 通用配置文件管理（alwaysvanish.uid, warp.yml） |
| `PlayerConfFile` 枚举 | 玩家专属配置（home.yml 分级目录存储） |
| `HomesLRU` 内部类 | 基于 LRU 缓存的玩家家数据系统，惰性加载 |
| `WARPS/HOMES` | 内存存储的地标和家数据 |
| `GROUPS` | 服务器组分群，控制跨服传送范围 |
| `BAN_SERVER` | 禁用服务器黑名单 |
| `allowServer()` | 判断服务器是否启用插件 |

### TransHandler.java (90行) - 数据迁移处理器

用于从其他服务器迁移 Home/Warp 数据。接收其他子服务器发来的大规模数据线，批量写入代理端存储。

### TabHandler.java (106行) - 命令补全

代理 BungeeCord 层的 `TabCompleteResponseEvent`，将 Tab 补全候选替换为：
- `@玩家名` (跨服 At)
- `warp名` (过滤可跨服的warp)
- `home名` (过滤可跨服的home)
- `玩家名` (TP 命令)

## 关键设计模式

| 设计模式 | 应用位置 | 说明 |
|----------|----------|------|
| **单例模式** | `Main.main` | 静态单例插件引用 |
| **策略模式** | `Channel` 枚举 | 每个消息类型通过枚举 + 类绑定，实现统一路由 |
| **对象池模式** | `DataIn/DataOut` | 使用固定大小对象池复用数据流包装类 |
| **LRU 缓存** | `HomesLRU` | 玩家数据惰性加载 + 容量控制 |
| **延迟保存** | `WaitMaintain` | `ConfFile.SAVE_DELAY` 集延时批量写入IO |
| **命令模式** | `Channel.Back/Warp/Home/*` | 每个子包通过 `s*s_xxx/send/p*` 函数封装打包/解包 |

## 与其他模块的交互关系

```
yuanluServerDo-bungeecord (代理层)
         |
         v Plugin Message Channel: "bc:yuanlu-SDo"
    +----+----+
    |         |
bukkit端    bukkit端    ... (各子服务器)
|         |         |
+---------+---------+
```

**交互模块：**

| 模块 | 关系 | 职责分工 |
|------|------|----------|
| **yuanluServerDo-common** | 编译依赖 | 提供共享常量（`ShareData`、`Channel`/`ShareLocation`/`At`/`Tool`/`LRUCache`/`WaitMaintain`） |
| **yuanluServerDo-bukkit** | 跨服通信 | Bukkit 子服务器端，发送 chord 请求到 BungeeCord |
| **yuanluServerDo-velocity** | 功能对等 | Velocity 代理实现，与 BungeeCord 端共用 common 的协议 |
| **yuanluServerDo-bukkit-bungeecord** | 桥接 | Bukkit 端 + BungeeCord 特定桥接代码 |
| **yuanluServerDo-bukkit-velocity** | 桥接 | Bukkit 端 + Velocity 特定桥接代码 |

## 代理层跨服转发逻辑（重点分析）

### 通信架构

**信道注册：** BungeeCord 通过 `getProxy().registerChannel("bc:yuanlu-SDo")` 注册自定义插件消息通道。

**双向通信模型：**

```
子服务器  --sendPluginMessage-->  BungeeCord 代理
              (Bukkit API)         (PluginMessageEvent)
                                   
子服务器  <--sendData-----------  BungeeCord 代理
            (Response Channel)      (Server.sendData)
```

### 典型跨服传送流程（以 `/tpa` 为例）

```
[1] 玩家A(服务器A) 发送 tp请求(C0: name=玩家B, type=1)
         |
         v
[2] 代理端Main.onPluginTpMessage() 接收
   - 解析子包 p0C_tpReq
   - 模糊搜索玩家B (getPlayer())
   - 检查服务器组 (Core.canTp)
   - 向玩家B所在服务器转发 s2S_tpReq
         |
         v
[3] 玩家B(服务器B) 收到请求
   [前向路由]
         |
         v
[4] 玩家B 确认(接受/拒绝) 发送 s3C_tpResp
         |
         v
[5] 代理端转发结果给玩家A/玩家B
         |
         v
[6] 玩家A 发起实际传送 s6C_tpThird (mover=自己, target=玩家B)
         |
         v
[7] 代理端 Core.tpLocation()
   a.  movers.connect(targetServer)
   b.  向目标服务器发送坐标信息 s1S_tpLoc
   c.  回调结果 s7S_tpThirdReceive
```

### 实际跨服坐标传送 (Core.tpLocation)

这是最核心的代理层逻辑：

```java
public static void tpLocation(ProxiedPlayer player, ShareLocation loc, Function<Boolean, byte[]> callback) {
    // 当前服务器
    val nowServer = player.getServer();
    // 目标服务器
    val targetServer = ...getServerInfo(loc.getServer());
    
    if (targetServer == null) {
        // 目标无效，直接失败
        Main.send(nowServer, callback.apply(false));
        return;
    }
    
    // 步骤1: 预发到目标服务器，让其准备传送
    Main.sendQueue(targetServer, Channel.TpLoc.s1S_tpLoc(loc, player.getName()));
    
    // 步骤2: 判断是否需要跨服切换
    if (nowServer.getInfo().getName().equals(targetServer.getName())) {
        // 同服务器，直接确认
        Main.send(nowServer, callback.apply(true));
    } else {
        // 跨服务器：用 BungeeCord API 切换玩家连接的服务器
        player.connect(targetServer, (success, e) -> {
            // 步骤3: 回调结果给原服务器
            Main.send(nowServer, callback.apply(success));
            if (e != null) e.printStackTrace();
        }, Reason.COMMAND);
    }
}
```

**关键特征：**
1. **先发坐标再切服** - 先向目标服务器发送 `TpLoc.s1S_tpLoc` 让其准备接收，再触发玩家 connect
2. **BungeeCord API 利用** - 通过 `ProxiedPlayer.connect(ServerInfo)` 实现玩家跨服切换
3. **回调通知** - 通过 `player.connect()` 的回调将结果返回给原服务器
4. **非阻塞** - 整个流程基于事件驱动，通过回调机制异步完成

### 跨服冷却同步

```
[1] 子服务器C 检测到新冷却开始
         |
[2] 发送 Channel.Cooldown.sendS(endTime) -> 代理端
         |
[3] 代理端接收，修正时间偏移 (clientTime + timeAmend)
         |
[4] 向所有服务器进行广播：
         for (ServerInfo server : allServers)
             send(server, Cooldown.broadcast(uuid, endTime - serverAmend))
```

### 时间校准机制

```
代理端线程循环 (默认每5分钟)
         |
         v
[1] 向各子服务器发送 TimeAmend.sendC()（空包请求）
         |
[2] 记录 TIMER_AMEND_WAITER[server] = 当前时间
         |
[3] 子服务器收到立即响应 TimeAmend.sendS（含服务器时间）
         |
[4] 代理端接收，计算偏移量：
     shift = 代理收到时间 - (子服时间 + 往返/2)
         |
[5] 存入 TIMER_AMEND[server] = shift
```

用于修正跨服冷却时间的同步误差。

## 总结

**yuanluServerDo-bungeecord** 是一个功能完善的 BungeeCord 代理层插件，它利用 BungeeCord 的 `PluginMessageChannel` 机制构建了完整的跨服通信协议。其核心设计亮点在于：

1. **集中式路由** - `Main.onPluginMessage()` 作为所有跨服消息的单一入口，通过枚举化 Channel 实现清晰分流
2. **代理层状态管理** - Warp、Home 等共享数据由代理端集中持久化，子服务器只通过请求获取
3. **BungeeCord 原生能力利用** - 直接使用 `player.connect()` 实现跨服无缝传送，简化了传送逻辑
4. **时间校准** - 解决分布式服务器间时间不同步问题
5. **服务器组隔离** - 通过 `server-group` 配置控制跨服交互范围，防止跨群组误操作

该模块与 Bukkit 子服务器端通过统一的 `Channel` 协议在 `bc:yuanlu-SDo` 信道上完成所有数据交互，是整个跨服插件功能得以实现的枢纽层。
