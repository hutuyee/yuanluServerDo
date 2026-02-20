/**
 *
 */
package yuan.plugins.serverDo.velocity;

import com.google.common.base.Objects;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import lombok.*;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import yuan.plugins.serverDo.*;
import yuan.plugins.serverDo.Tool.ThrowableFunction;
import yuan.plugins.serverDo.Tool.ThrowableRunnable;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置管理器
 *
 * @author yuanlu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigManager {
	/** 地标点 */
	static final           HashMap<String, ShareLocation>   WARPS      = new LinkedHashMap<>();
	/** 家点 */
	static final           HomesLRU                         HOMES      = new HomesLRU(32);
	/** 服务器组 */
	private static final   HashMap<String, HashSet<String>> GROUPS     = new HashMap<>();
	/** 禁用的服务器 */
	private static final   HashSet<String>                  BAN_SERVER = new HashSet<>();
	/** 配置文件 */
	private static @Getter ConfigurationNode                    config;
	/** tab替换 */
	private static @Getter
	@Setter                String                           tabReplace;
	/** 服务器信息 */
	private static         byte[]                           serverInfo;
	/** 出错 */
	private static @Getter boolean                          errorGroup;
	/** 自动保存延时 */
	private @Getter
	@Setter
	static                 long                             saveDelay  = 1000 * 60;
	/** 是否使用AT功能 */
	private @Getter
	@Setter
	static                 boolean                          useAt      = true;

	/**
	 * 检测是否启用服务器
	 *
	 * @param server 服务器
	 *
	 * @return 此服务器是否启用本插件
	 */
	public static boolean allowServer(String server) {
		return !BAN_SERVER.contains(server);
	}

	/**
	 * 检测是否可以传送
	 *
	 * @param s1 服务器1
	 * @param s2 服务器2
	 *
	 * @return 是否可以传送
	 */
	public static boolean canTp(String s1, String s2) {
		if (BAN_SERVER.contains(s1) || BAN_SERVER.contains(s2)) return false;
		if (Objects.equal(s1, s2)) return true;
		if (errorGroup) {
			if (Main.isDEBUG()) Main.getMain().getLogger().warning("error: 服务器组不存在");
			return false;
		}
		val group = GROUPS.get(s1);
		return group != null && group.contains(s2);
	}

	/**
	 * 关闭时保存
	 */
	public static void closeSave() {
		val list = new ArrayList<>(ConfFile.SAVE_DELAY.keySet());
		ConfFile.SAVE_DELAY.clear();
		list.forEach(ConfFile::save);
		for (val v : PlayerConfFile.values()) {
			if (v.SAVE_DELAY.isEmpty()) continue;
			val l = new ArrayList<>(v.SAVE_DELAY.keySet());
			v.SAVE_DELAY.clear();
			l.forEach(v::save);
		}
	}

	/**
	 * 初始化
	 *
	 * @param config config
	 */
	public static void init(ConfigurationNode config) {
		ConfigManager.config = config;

		val tabReplace = config.node("player-tab-replace")
				.getString("yl★:" + Tool.randomString(8));

		setTabReplace(tabReplace);

		setSaveDelay(config.node("save-delay")
				.getLong(getSaveDelay()));

		setUseAt(config.node("use-at")
				.getBoolean(isUseAt()));

		loadGroup(config);

		serverInfo = Channel.ServerInfo.sendS(
				tabReplace,
				Main.getPluginContainer()
						.getDescription()
						.getVersion()
						.orElse("Unknown"),
				Channel.ServerInfo.ServerPkg.ProxyType.Velocity
		);

		Arrays.stream(ConfFile.values()).forEach(ConfFile::load);
	}

	/**
	 * 加载组
	 *
	 * @param config 配置文件
	 */
	private static void loadGroup(ConfigurationNode config) {

		val sg = config.node("server-group");

		if (sg.empty()) {
			errorGroup = true;
			Main.getMain().getLogger().warning("[SERVER GROUP] config error!");
			return;
		}

		for (val entry : sg.childrenMap().entrySet()) {

			val key = entry.getKey().toString();
            List<String> group = null;
            try {
                group = entry.getValue().getList(String.class, Collections.emptyList());
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }

            for (val server : group) {
				HashSet<String> canTp = GROUPS.computeIfAbsent(server, k -> new HashSet<>());
				canTp.addAll(group);
			}

			if (ShareData.isDEBUG())
				ShareData.getLogger().info("加载组 " + key + ": " + group);
		}

		BAN_SERVER.clear();
        try {
            BAN_SERVER.addAll(
                    config.node("server-ban")
                            .getList(String.class, Collections.emptyList())
            );
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

	/**
	 * 保存配置<br>
	 * 将会延时保存
	 *
	 * @param f 配置类型
	 */
	public static void saveConf(ConfFile f) {
		WaitMaintain.put(ConfFile.SAVE_DELAY, f, System.currentTimeMillis(), saveDelay, f::save);
	}

	/**
	 * 保存配置<br>
	 * 将会延时保存
	 *
	 * @param f      配置类型
	 * @param player 对应玩家
	 *
	 * @see #saveConf(PlayerConfFile, UUID)
	 */
	public static void saveConf(PlayerConfFile f, Player player) {
		saveConf(f, player.getUniqueId());
	}

	/**
	 * 保存配置<br>
	 * 将会延时保存
	 *
	 * @param f 配置类型
	 * @param u 对应玩家UUID
	 */
	public static void saveConf(PlayerConfFile f, UUID u) {
		f.needSave.add(u);
		WaitMaintain.put(f.SAVE_DELAY, u, System.currentTimeMillis(), saveDelay, () -> f.save(u));
	}

	/**
	 * 发送BC信息给子服务器
	 *
	 * @param server 服务器
	 */
	public static void sendBungeeInfoToServer(ServerConnection server) {
		Main.send(server, serverInfo);
	}

	/**
	 * 配置文件
	 *
	 * @author yuanlu
	 */
	@Getter
	@AllArgsConstructor
	public enum ConfFile {
		/** 自动隐身 */
		ALWAYS_VANISH("alwaysvanish.uid") {
			@Override
			protected void load0() throws IOException {
				try (BufferedReader in = new BufferedReader(new FileReader(getFile()))) {
					in.lines().forEach(s -> {
						try {
							Core.alwaysVanish.add(UUID.fromString(s));
						} catch (IllegalArgumentException e) {
							ShareData.getLogger().warning("[Conf] " + fname + ": Bad UUID: " + s);
							e.printStackTrace();
						}
					});
				}
			}

			@Override
			protected void save0() throws IOException {
				try (BufferedWriter out = new BufferedWriter(new FileWriter(getFile()))) {
					for (UUID u : Core.alwaysVanish) {
						out.write(u.toString());
						out.write('\n');
					}
				}
			}

		},
		/** 传送地标 */
		WARP("warp.yml") {
			@Override
			protected void load0() throws IOException {
				ConfigurationNode warps = YamlUtil.load(getFile().toPath());

				for (ConfigurationNode child : warps.childrenMap().values()) {
					String name = child.key().toString();
					// Configuration warp = warps.getSection(name);

					String world = child.node("world").getString();
					String server = child.node("server").getString();
					double x = child.node("x").getDouble();
					double y = child.node('y').getDouble();
					double z = child.node("z").getDouble();
					float Y = child.node("yaw").getFloat();
					float P = child.node("pitch").getFloat();
					if (world == null || server == null || Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || Float.isNaN(Y) || Float.isNaN(P)) {
						ShareData.getLogger().warning(String.format("[WARPS] 错误的warp数据: %s %s [%s, %s, %s] [%s,%s]", server, world, x, y, z, Y, P));
					} else {
						WARPS.put(name, new ShareLocation(x, y, z, Y, P, world, server));
					}
				}
			}

			@Override
			protected void save0() throws IOException {
				ConfigurationNode warps = YamlConfigurationLoader.builder()
						.path(getFile().toPath())
						.build()
						.createNode();
				for (Map.Entry<String, ShareLocation> e : WARPS.entrySet()) {
					String name = e.getKey();
					ShareLocation warp = e.getValue();
					warps.node(name, "world").set(warp.getWorld());
					warps.node(name, "server").set(warp.getServer());
					warps.node(name, "x").set(warp.getX());
					warps.node(name, "y").set(warp.getY());
					warps.node(name, "z").set(warp.getZ());
					warps.node(name, ".yaw").set(warp.getYaw());
					warps.node(name, ".pitch").set(warp.getPitch());
				}
				YamlUtil.save(warps, getFile().toPath());
			}

		};

		/** 保存延时 */
		private static final   EnumMap<ConfFile, Long> SAVE_DELAY = new EnumMap<>(ConfFile.class);
		/** 文件名 */
		protected final        String                  fname;

		/** @return file */
		public File getFile() {
			val folder = Main.getMain().getDataFolder();
			return folder.resolve(fname).toFile();
		}

		/** 加载 */
		protected void load() {
			try {
				load0();
			} catch (FileNotFoundException e) {
				// ignore
			} catch (IOException e) {
				ShareData.getLogger().warning("[Conf] " + fname + ":");
				e.printStackTrace();
			}
		}

		/**
		 * 实际加载
		 *
		 * @throws IOException IOE
		 */
		protected abstract void load0() throws IOException;

		/** 保存 */
		protected void save() {
			try {
				save0();
			} catch (IOException e) {
				ShareData.getLogger().warning("[Conf] " + fname + ":");
				e.printStackTrace();
			}
		}

		/**
		 * 实际保存
		 *
		 * @throws IOException IOE
		 */
		protected abstract void save0() throws IOException;
	}

	/**
	 * 玩家配置文件<br>
	 * load: 被动式加载, 由LRU调用, 通过 {@link #load(Object, ThrowableFunction) 框架函数}
	 * 调用实际加载函数, 加载数据<br>
	 * save: 被动式保存, 由LRU调用, 通过 {@link #save(UUID, ThrowableRunnable) 框架函数} 调用实际保存函数,
	 * 保存数据<br>
	 * save: 主动式保存, 通过 {@link #save(UUID)} 触发, 由具体配置指定LRU, 调用其保存函数
	 *
	 * @author yuanlu
	 */
	@Getter
	@AllArgsConstructor
	public enum PlayerConfFile {
		/** 传送家 */
		HOME("home.yml") {
			@Override
			protected void save(UUID u) {
				HashMap<String, ShareLocation> map = HOMES.check(u);
				if (map != null) HOMES.clearHandle(u, map);
			}
		};

		/** 文件名 */
		protected final        String                        fname;
		/** 需要保存 */
		protected final        HashSet<UUID>                 needSave   = new HashSet<>();
		/** 保存延时 */
		private final          ConcurrentHashMap<UUID, Long> SAVE_DELAY = new ConcurrentHashMap<>();

		/**
		 * @param u  UUID
		 * @param mk 是否创建文件夹
		 *
		 * @return file
		 */
		public File getFile(UUID u, boolean mk) {
			val folder = Main.getMain().getDataFolder();
			val uuid = u.toString();
			val dirPath = folder.resolve(uuid.substring(0, 2)).resolve(uuid);
			if (mk) {
				dirPath.toFile().mkdirs(); // 创建目录
			}
			// 返回 home.yml 文件，而不是目录
			return dirPath.resolve(fname).toFile();
		}


		/**
		 * 加载
		 *
		 * @param <T> T
		 * @param <R> R
		 * @param t   输入数据
		 * @param r   运行体
		 *
		 * @return result
		 */
		protected <T, R> R load(T t, ThrowableFunction<IOException, T, R> r) {
			try {
				return r.apply(t);
			} catch (FileNotFoundException e) {
				// ignore
			} catch (IOException e) {
				ShareData.getLogger().warning("[Conf] " + fname + ":");
				e.printStackTrace();
			}
			return null;
		}

		/**
		 * 强制保存<br>
		 * 由配置文件实现
		 *
		 * @param u UUID
		 */
		protected abstract void save(UUID u);

		/**
		 * 保存
		 *
		 * @param u UUID
		 * @param r 运行体
		 */
		protected void save(UUID u, ThrowableRunnable<IOException> r) {
			if (!needSave.remove(u)) return;
			try {
				r.run();
			} catch (FileNotFoundException e) {
				// ignore
			} catch (IOException e) {
				ShareData.getLogger().warning("[Conf] " + fname + ":");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Home
	 *
	 * @author yuanlu
	 */
	public static final class HomesLRU extends LRUCache<UUID, HashMap<String, ShareLocation>> {

		/** 配置文件 */
		private static final PlayerConfFile HOME = PlayerConfFile.HOME;

		/** @param size size */
		private HomesLRU(int size) {
			super(size);
		}

		@Override
		protected void clearHandle(UUID k, HashMap<String, ShareLocation> v) {
			HOME.save(k, () -> save0(k, v));
		}

		@Override
		protected HashMap<String, ShareLocation> create(UUID k) {
			HashMap<String, ShareLocation> data = HOME.load(k, this::load0);
			return data == null ? new HashMap<>() : data;
		}

		/**
		 * load
		 *
		 * @param uid UUID
		 *
		 * @return data
		 *
		 * @throws IOException IOE
		 */
		private HashMap<String, ShareLocation> load0(@NonNull UUID uid) throws IOException {
			HashMap<String, ShareLocation> m = new HashMap<>();
			val f = HOME.getFile(uid, false);

			val warps = YamlUtil.load(f.toPath());

			for (val entry : warps.childrenMap().entrySet()) {
				val name = entry.getKey().toString();
				val warp = entry.getValue();

				val world = warp.node("world").getString();
				val server = warp.node("server").getString();
				val x = warp.node("x").getDouble(Double.NaN);
				val y = warp.node("y").getDouble(Double.NaN);
				val z = warp.node("z").getDouble(Double.NaN);
				val Y = (float) warp.node("yaw").getDouble(Double.NaN);
				val P = (float) warp.node("pitch").getDouble(Double.NaN);

				if (world == null || server == null ||
						Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) ||
						Float.isNaN(Y) || Float.isNaN(P)) {

					ShareData.getLogger().warning(
							String.format("[HOMES] 错误的home数据: %s: %s %s [%s, %s, %s] [%s,%s]",
									f.getName(), server, world, x, y, z, Y, P)
					);
				} else {
					m.put(name, new ShareLocation(x, y, z, Y, P, world, server));
				}
			}

			return m;
		}

		/**
		 * save
		 *
		 * @param uid uuid
		 * @param map data
		 *
		 * @throws IOException IOE
		 */
		private void save0(@NonNull UUID uid, HashMap<String, ShareLocation> map) throws IOException {

			val file = PlayerConfFile.HOME.getFile(uid, true).toPath();
			val warps = YamlUtil.createEmptyNode(); // 我下面给你实现

			for (val e : map.entrySet()) {
				val name = e.getKey();
				val warp = e.getValue();

				warps.node(name, "world").set(warp.getWorld());
				warps.node(name, "server").set(warp.getServer());
				warps.node(name, "x").set(warp.getX());
				warps.node(name, "y").set(warp.getY());
				warps.node(name, "z").set(warp.getZ());
				warps.node(name, "yaw").set(warp.getYaw());
				warps.node(name, "pitch").set(warp.getPitch());
			}

			YamlUtil.save(warps, file);
		}
	}
}
