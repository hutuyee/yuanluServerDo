package yuan.plugins.serverDo.velocity;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 SnakeYAML 的轻量配置封装，用于替代旧代理端配置 API。
 * 保留原项目常用的 getString/getSection/getKeys/set 等调用风格。
 *
 * @author H_aaa
 */
public final class YamlConfig {
	private final Map<String, Object> root;

	public YamlConfig() {
		this.root = new LinkedHashMap<>();
	}

	private YamlConfig(Map<String, Object> root) {
		this.root = root == null ? new LinkedHashMap<>() : root;
	}

	public static YamlConfig load(File file) throws IOException {
		if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
		try (InputStream in = new FileInputStream(file)) {
			return load(in);
		}
	}

	@SuppressWarnings("unchecked")
	public static YamlConfig load(InputStream in) throws IOException {
		LoaderOptions loaderOptions = new LoaderOptions();
		Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
		try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			Object data = yaml.load(reader);
			if (data == null) return new YamlConfig();
			if (!(data instanceof Map)) throw new IOException("Yaml root is not a map");
			return new YamlConfig(toStringObjectMap((Map<?, ?>) data));
		}
	}

	public static void save(YamlConfig config, File file) throws IOException {
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) parent.mkdirs();
		try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			createYaml().dump(config.root, writer);
		}
	}

	private static Yaml createYaml() {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		options.setIndent(2);
		Representer representer = new Representer(options);
		return new Yaml(representer, options);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> toStringObjectMap(Map<?, ?> input) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : input.entrySet()) {
			String key = String.valueOf(entry.getKey());
			Object value = entry.getValue();
			if (value instanceof Map) value = toStringObjectMap((Map<?, ?>) value);
			else if (value instanceof List) value = convertList((List<?>) value);
			result.put(key, value);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> convertList(List<?> input) {
		List<Object> result = new ArrayList<>();
		for (Object value : input) {
			if (value instanceof Map) result.add(toStringObjectMap((Map<?, ?>) value));
			else if (value instanceof List) result.add(convertList((List<?>) value));
			else result.add(value);
		}
		return result;
	}

	public Set<String> getKeys() {
		return root.keySet();
	}

	public YamlConfig getSection(String path) {
		Object value = get(path);
		if (!(value instanceof Map)) return null;
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) value;
		return new YamlConfig(map);
	}

	public String getString(String path, String def) {
		Object value = get(path);
		return value == null ? def : String.valueOf(value);
	}

	public long getLong(String path, long def) {
		Object value = get(path);
		if (value instanceof Number) return ((Number) value).longValue();
		if (value instanceof String) try {
			return Long.parseLong((String) value);
		} catch (NumberFormatException ignored) {
		}
		return def;
	}

	public boolean getBoolean(String path, boolean def) {
		Object value = get(path);
		if (value instanceof Boolean) return (Boolean) value;
		if (value instanceof String) return Boolean.parseBoolean((String) value);
		return def;
	}

	public double getDouble(String path, double def) {
		Object value = get(path);
		if (value instanceof Number) return ((Number) value).doubleValue();
		if (value instanceof String) try {
			return Double.parseDouble((String) value);
		} catch (NumberFormatException ignored) {
		}
		return def;
	}

	public float getFloat(String path, float def) {
		Object value = get(path);
		if (value instanceof Number) return ((Number) value).floatValue();
		if (value instanceof String) try {
			return Float.parseFloat((String) value);
		} catch (NumberFormatException ignored) {
		}
		return def;
	}

	public List<String> getStringList(String path) {
		Object value = get(path);
		if (!(value instanceof List)) return Collections.emptyList();
		List<String> result = new ArrayList<>();
		for (Object item : (List<?>) value) if (item != null) result.add(String.valueOf(item));
		return result;
	}

	public Object get(String path) {
		String[] parts = path.split("\\.");
		Object current = root;
		for (String part : parts) {
			if (!(current instanceof Map)) return null;
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) current;
			current = map.get(part);
			if (current == null) return null;
		}
		return current;
	}

	public void set(String path, Object value) {
		String[] parts = path.split("\\.");
		Map<String, Object> current = root;
		for (int i = 0; i < parts.length - 1; i++) {
			String part = parts[i];
			Object next = current.get(part);
			if (!(next instanceof Map)) {
				next = new LinkedHashMap<String, Object>();
				current.put(part, next);
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> nextMap = (Map<String, Object>) next;
			current = nextMap;
		}
		current.put(parts[parts.length - 1], value);
	}
}
