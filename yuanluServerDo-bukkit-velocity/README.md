# yuanluServerDo-bukkit-velocity 模块介绍

## 模块定位

`yuanluServerDo-bukkit-velocity` 是一个**纯 Maven Shade 打包合并模块**，本身**不包含任何 Java 源代码或资源文件**。它的唯一职责是使用 `maven-shade-plugin` 将 `yuanluServerDo-bukkit` 和 `yuanluServerDo-velocity` 两个模块（连同它们共同依赖的 `yuanluServerDo-common`）合并成一个 **uber/fat JAR**。

## 文件清单

该模块极其精简，仅包含一个文件：

| 文件路径 | 说明 |
|----------|------|
| `pom.xml` | Maven 构建配置，定义 shade 合并规则 |

**无任何 `src/main/java` 或 `src/main/resources` 目录。**

## pom.xml 关键内容

```xml
<artifactId>yuanluServerDo-bukkit-velocity</artifactId>
<packaging>jar</packaging>
<name>YuanluServerDo Bukkit &amp; Velocity</name>

<build>
    <plugins>
        <plugin>maven-compiler-plugin</plugin>
        <plugin>maven-shade-plugin</plugin>  <!-- 关键：打包合并 -->
    </plugins>
</build>

<dependencies>
    <dependency>
        <groupId>bid.yuanlu</groupId>
        <artifactId>yuanluServerDo-bukkit</artifactId>
        <scope>compile</scope>
    </dependency>
    <dependency>
        <groupId>bid.yuanlu</groupId>
        <artifactId>yuanluServerDo-velocity</artifactId>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

## 合并内容

该模块构建后生成的 JAR 包含以下内容：

| 来源模块 | 包含内容 |
|----------|----------|
| `yuanluServerDo-common` | `Channel.java` 协议定义、`ShareData` 常量、`ShareLocation` 坐标、`WaitMaintain` 超时管理、`Tool` 工具类、`LRUCache` 缓存、`At` @处理、`ChatColor` 颜色 |
| `yuanluServerDo-bukkit` | Bukkit 插件主类 `Main.java`、事件监听 `Core.java`、命令体系 `cmds/`、消息系统 `MESSAGE.java`、安全坐标 `SafeLoc.java`、资源文件 `plugin.yml` + `config.yml` |
| `yuanluServerDo-velocity` | Velocity 插件主类 `Main.java`、跨服逻辑 `Core.java`、配置管理 `ConfigManager.java`、Tab 处理 `TabHandler.java`、数据迁移 `TransHandler.java`、命令代理 `CmdProxy.java`、资源文件 `velocity-plugin.json` + `proxy-config.yml` |

## 为什么需要这个模块？

方便服主部署：只需放一个 JAR 到所有服务器（代理端 + 子服），无需区分文件。

## 使用场景

| 部署方式 | 需要的 JAR |
|----------|-----------|
| 分离部署 | `yuanluServerDo-bukkit-*.jar`（放子服）+ `yuanluServerDo-velocity-*.jar`（放代理端） |
| 统一部署（推荐） | `yuanluServerDo-bukkit-velocity-*.jar`（同时放子服和代理端即可） |

## 重要约束

- **永远不要在这个模块中添加 Java 源代码** — 它是纯粹的打包工具
- **永远不要在这个模块中添加资源文件** — 所有配置和插件描述符都在各自的子模块中
- 如果修改了 `yuanluServerDo-bukkit` 或 `yuanluServerDo-velocity`，这个模块会自动包含最新版本（因为它们通过 `${project.version}` 引用）

## 与其他模块的关系

```
yuanluServerDo (Parent POM, version 1.2.2)
|
|-- yuanluServerDo-common              <- 共享协议与工具
|-- yuanluServerDo-bukkit              <- Bukkit/Spigot 子服端插件
|-- yuanluServerDo-velocity            <- Velocity 代理端插件
|
+-- yuanluServerDo-bukkit-velocity     <- 【本模块】打包合并
        = shade(bukkit + velocity + common)
        -> 产生一个同时兼容 Bukkit 子服和 Velocity 代理端的 uber JAR
```

## 构建产物

构建命令：
```bash
mvn -B package
```

产物位置：
```
yuanluServerDo-bukkit-velocity/target/yuanluServerDo-bukkit-velocity-{version}.jar
```

这个 JAR 文件同时包含 Bukkit 和 Velocity 的插件入口，可以：
- 放入 Bukkit/Spigot/Paper 服务器的 `plugins/` 目录，作为 Bukkit 插件加载
- 放入 Velocity 代理的 `plugins/` 目录，作为 Velocity 插件加载

**运行时自动识别平台**：Bukkit 端加载 `yuan.plugins.serverDo.bukkit.Main`，Velocity 端加载 `yuan.plugins.serverDo.velocity.Main`，互不干扰。

## BungeeCord vs Velocity 对比

| 特性 | BungeeCord 版本 | Velocity 版本 |
|------|----------------|---------------|
| 组合模块 | `yuanluServerDo-bukkit-bungeecord` | `yuanluServerDo-bukkit-velocity` |
| 代理端主类 | `yuan.plugins.serverDo.bungee.Main` | `yuan.plugins.serverDo.velocity.Main` |
| 代理端 API | BungeeCord API | Velocity API |
| 切服方式 | `player.connect(ServerInfo)` | `player.createConnectionRequest(target).connect()` |
| 事件体系 | BungeeCord `@EventHandler` | Velocity `@Subscribe` |
| 命令代理 | 无（BungeeCord 原生支持后端命令注册） | `CmdProxy.java`（手动注册 `SuggestCommand`） |
| Tab 补全 | `TabCompleteResponseEvent` | `TabCompleteEvent` |
| 配置文件 | `bungee.yml` + `proxy-config.yml` | `velocity-plugin.json` + `proxy-config.yml` |

两个代理端模块共享相同的 `proxy-config.yml` 配置格式，功能完全对等。
