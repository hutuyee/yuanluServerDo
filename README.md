# yuanluerServerDo

__插件暂时或永久停更，作者毕业上班了。无法再分出精力维护，有能力者可以提交维护代码__

让跨服玩家无缝执行命令  
Allow players on the group server to seamlessly execute commands across servers

理论全版本支持  
Theory full version support

## 构建 / Build

本项目使用 **Maven Wrapper** (`mvnw`)，无需全局安装 Maven。

### 常用命令

```bash
# 完整构建（所有模块）
./mvnw -B clean package

# 构建指定模块及其依赖
./mvnw -B clean package -pl yuanluServerDo-bukkit -am

# 跳过测试（更快）
./mvnw -B clean package -DskipTests

# 安装到本地仓库
./mvnw -B clean install
```

> Windows 用户请使用 `mvnw.cmd` 代替 `./mvnw`

### 构建产物

构建完成后，各模块的 JAR 文件位于对应 `target/` 目录下：

| 文件 | 说明 |
|------|------|
| `yuanluServerDo-bukkit/target/*.jar` | Bukkit/Spigot/Paper 插件 |
| `yuanluServerDo-bungeecord/target/*.jar` | BungeeCord 代理插件 |
| `yuanluServerDo-velocity/target/*.jar` | Velocity 代理插件 |
| `yuanluServerDo-bukkit-bungeecord/target/*.jar` | 合并包（Bukkit + BungeeCord） |
| `yuanluServerDo-bukkit-velocity/target/*.jar` | 合并包（Bukkit + Velocity） |

### CI / 自动发布

GitHub Actions 会在发布 (Release) 时自动构建并上传 JAR：

1. 更新根目录 `pom.xml` 中的版本号
2. 提交并推送到 `dev` 分支
3. 在 GitHub 上创建 Release（Tag 与版本号一致）
4. Actions 自动构建并将 5 个 JAR 上传到 Release 附件

## 交流

[discord](https://discord.gg/5SZNhTkqJg)

## 统计数据

- [bstats Bukkit](https://bstats.org/plugin/bukkit/yuanluServerDo/12395)
- [bstats Bungee](https://bstats.org/plugin/bungeecord/yuanluServerDo/12396)
- ![最新版本 badge](https://update.yuanlu.bid/ico/v/mc-bukkit/yuanluServerDo "最新版本")
- ![bStats Players](https://img.shields.io/bstats/players/12396?label=%E7%8E%A9%E5%AE%B6%E6%95%B0%E9%87%8F) ![bStats Servers](https://img.shields.io/bstats/servers/12396?label=%E6%9C%8D%E5%8A%A1%E5%99%A8%E6%95%B0%E9%87%8F)
  ![bStats Count](https://bstats.org/signatures/bungeecord/yuanluServerDo.svg)

## 下载

__支持服务器: Bukkit (Spigot、Paper)__  
__支持代理器: BungeeCord、Velocity__

同一个版本有多个文件可供选择, 文件名中包含Bukkit的即代表可以在Bukkit及其分支（Spigot、Paper）上运行，其它同理  
例如，你选择使用BungeeCord及Paper，你有以下几种选择:

1. 下载`yuanluServerDo-bukkit-[version].jar`及`yuanluServerDo-bungeecord-[version].jar`, 分别放入Paper和BungeeCord
2. 直接下载`yuanluServerDo-bukkit-bungeecord-[version].jar`放入Paper和BungeeCord

## 替代yuanluServerTp

本插件继承了yuanluServerTp思想, 重构后可完全替代此插件

- /tp \<target\>
- /tp \<mover\> \<target\>
- /tpa \<target\>
- /tphere \<target\>
- /tpahere \<target\>
- /tpaccept \[who\]
- /tpdeny \[who\]
- /tpcancel \[who\]

## 实现了列表隐身

使用/ysd-v \[always\] 命令可以隐藏自己的在线状态, 防止被传送请求发现

## 实现跨服Home及Warp

本插件实现了Home及Warp的相关功能, 可以跨服传送至家或地标

- /home
- /home \<home\>
- /sethome \[home\]
- /delhome \<home\>
- /warp
- /warp \<warp\>
- /setwarp \<warp\>
- /delhome \<warp\>

## 实现第三方数据转换

本插件实现了从第三方插件转换数据

命令: /ysd-trans \<plugin\> \<func\>

当前支持的插件及数据:

- CMI
    - Home
    - Warp

## 实现了At功能

在聊天中使用@+玩家名, 可以向对方发送提示音
