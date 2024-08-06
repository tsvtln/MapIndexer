package xyz.skyfalls;

import com.google.gson.Gson;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.NetworkUtils;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.apache.http.*;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xyz.skyfalls.shared.abstraction.FreeBlockPos;
import xyz.skyfalls.shared.api.ClientInfo;
import xyz.skyfalls.shared.utils.RegistryUtils;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RpcServer {
	private final static Logger logger = LogManager.getLogger(MapIndexerClient.MODID + "/RPC");

	private final static String CTX_PREFLIGHT = "Preflight";
	private final static String CTX_ORIGIN = "Origin";
	private final static List<String> ALLOWED_ORIGIN = List.of("http://localhost:1234", "https://map26.skyfalls.xyz");
	@Getter
	private static volatile boolean isRunning = false;
	private static HttpServer server;
	private final static Gson gson = new Gson();

	public static void serve() {
		NetworkUtils.EXECUTOR.execute(() -> {
			doServe();
		});
	}

	private static void doServe() {
		var bootstrap = ServerBootstrap.bootstrap()
				.setLocalAddress(InetAddress.getLoopbackAddress())
				.setExceptionLogger(e -> logger.info("Request failed", e))
				.addInterceptorFirst((HttpRequestInterceptor) (request, context) -> {
					context.setAttribute(CTX_ORIGIN, request.getFirstHeader("origin").getValue());
					if (request.getRequestLine().getMethod().equals("OPTIONS")) {
						context.setAttribute(CTX_PREFLIGHT, true);
					}
					var auth = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
					if (auth == null || !auth.getValue().equals("Bearer " + Config.getRpcSecret())) {
						// this will just kill the connection
						throw new HttpException();
					}
				})
				.addInterceptorFirst((HttpResponseInterceptor) (response, context) -> {
					var origin = (String) context.getAttribute(CTX_ORIGIN);
					if (ALLOWED_ORIGIN.contains(origin)) {
						response.addHeader("Access-Control-Allow-Origin", origin);
						response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
						response.addHeader("Access-Control-Allow-Headers", HttpHeaders.AUTHORIZATION);
					}

					if (context.getAttribute(CTX_PREFLIGHT) != null) {
						response.setStatusCode(HttpStatus.SC_NO_CONTENT);
					}
				})
				.registerHandler("/track", (request, response, context) -> {
					if (!request.getRequestLine().getMethod().equals("POST")) {
						// preflight should go here
						response.setStatusCode(HttpStatus.SC_NOT_FOUND);
						return;
					}
					var uri = URI.create(request.getRequestLine().getUri());
					var params = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8)
							.stream().collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
					var x = Integer.parseInt(params.get("x"));
					var y = Integer.parseInt(params.get("y"));
					var z = Integer.parseInt(params.get("z"));
					var dim = params.get("dim");
					TrackerOverlay.track(dim, new BlockPos(x, y, z));
					GLFW.glfwRequestWindowAttention(MinecraftClient.getInstance().getWindow().getHandle());
					response.setStatusCode(HttpStatus.SC_NO_CONTENT);
				})
				.registerHandler("/info", (request, response, context) -> {
					if (!request.getRequestLine().getMethod().equals("GET")) {
						// preflight should go here
						response.setStatusCode(HttpStatus.SC_NOT_FOUND);
						return;
					}
					var mc = MinecraftClient.getInstance();
					var session = mc.getSession();
					var player = Optional.ofNullable(mc.player);
					FreeBlockPos pos = player.map(e -> FreeBlockPos.of(e.getBlockPos())).orElse(null);
					String dim = player.map(e -> RegistryUtils.toString(e.clientWorld.getRegistryKey())).orElse(null);
					var info = new ClientInfo(Version.VERSION, MapIndexerClient.isOnS26(),
							session.getUsername(), session.getUuidOrNull(),
							pos, dim);
					response.setStatusCode(HttpStatus.SC_OK);
					response.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
					response.setEntity(new StringEntity(gson.toJson(info)));
				});
		for (int i = 0; i < 5; i++) {
			int port = Config.getRpcPort() + i;
			try {
				server = bootstrap.setListenerPort(port).create();
				logger.info("RPC server listening on {}, try {}/5", port, i + 1);
				server.start();
				isRunning = true;
				break;
			} catch (BindException e) {
				logger.warn(
						"RPC server failed to bind due to port conflict. MapIndexer will incrementally try up to 5 ports to maximize the chance of it working. If you find this annoyting please manually change the port setting.");
			} catch (IOException e) {
				logger.error("Failed to start RPC server:", e);
			}
		}
	}

	public static void warnIfNotRunning() {
		if (!isRunning) {
			MapIndexerClient.sendChatMessage(Text.literal(
					"§aMapIndexer: §eBrowser features will not work because RPC server failed to start. Check logs for more detail."));
		}
	}

	public static String getLinkUrl() {
		return "https://map26.skyfalls.xyz/link.html#port.%d!secret.%s"
				.formatted(Config.getRpcPort(), Config.getRpcSecret());
	}
}
