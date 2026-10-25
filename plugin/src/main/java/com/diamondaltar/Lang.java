package com.diamondaltar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Textes français (copie du fichier de langue du mod), utilisés si le pack de ressources manque. */
public final class Lang {
	private static final Map<String, String> TEXT = new HashMap<>();

	static {
		try (InputStream in = Lang.class.getResourceAsStream("/lang/fr_fr.json")) {
			if (in != null) {
				JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				obj.entrySet().forEach(e -> TEXT.put(e.getKey(), e.getValue().getAsString()));
			}
		} catch (Exception ignored) {
		}
	}

	private Lang() {
	}

	public static String get(String key) {
		return TEXT.getOrDefault(key, key);
	}
}
