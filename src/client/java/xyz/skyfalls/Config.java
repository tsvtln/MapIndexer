package xyz.skyfalls;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Properties;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;


public class Config {
	private static final Path CONFIG_DIR = Paths.get(MinecraftClient.getInstance().runDirectory.getPath(), "config");
	private static final Path CONFIG_FILE = CONFIG_DIR.resolve("mapindxer.properties");
	// configuration entries start
	@Getter
	private static String rpcSecret = RandomStringUtils.randomAlphabetic(32);
	@Getter
	private static int rpcPort = 47382;

	public static void load() throws IOException {
		if (!Files.exists(CONFIG_FILE)) {
			Files.createDirectories(CONFIG_DIR);
			Files.createFile(CONFIG_FILE);
			save();
			return;
		}
		var prop = new Properties();
		prop.load(Files.newInputStream(CONFIG_FILE));
		rpcSecret = prop.getProperty("rpcSecret");
		rpcPort=Integer.parseInt(prop.getProperty("rpcPort"));
	}

	public static void save() throws IOException {
		var prop = new Properties();
		prop.setProperty("rpcSecret", rpcSecret);
		prop.setProperty("rpcPort", Integer.toString(rpcPort));
		prop.store(Files.newOutputStream(CONFIG_FILE), "Configuration for Companion Mod map26.skyfalls.xyz");
	}
}
