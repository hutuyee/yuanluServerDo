# H_aaa

我更改了Bukkit 以及Velocity文件夹

具体内容为

[ + ] 修复设置home的bug

[ + ] 添加死亡点Back

[ + ] 修复tab补全

[ - ] 减去检测版本一致



# yuanluerServerDo

__插件暂时或永久停更，作者毕业上班了。无法再分出精力维护，有能力者可以提交维护代码__

让跨服玩家无缝执行命令  
Allow players on the group server to seamlessly execute commands across servers

理论全版本支持  
Theory full version support

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
