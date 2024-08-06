package xyz.skyfalls.shared.api;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.mojang.authlib.exceptions.AuthenticationException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.literal.LiteralText;
import net.minecraft.text.style.Style;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.BlockPos;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.skyfalls.MapIndexerClient;
import xyz.skyfalls.shared.VarInt;
import xyz.skyfalls.shared.exceptions.ApiException;
import xyz.skyfalls.shared.utils.CryptoUtils;
import xyz.skyfalls.Version;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiService {
    private static final Logger logger = LogManager.getLogger(MapIndexerClient.MODID + "/API");
    private static final String AUTH_PREFIX = "MapIndexerMod@IsSkyfalls_";
    
    private static final Text MSG_AUTH_SUCCESS = new LiteralText("Successfully authenticated with MapIndexer.")
        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x00FF00)));
    
    private static final Text MSG_AUTH_STILL_VALID = new LiteralText("Welcome back.")
        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x00FF00)));
    
    private static final Text MSG_AUTH_FAIL = new LiteralText("Failed to authenticate with MapIndexer")
        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF0000)));
    
    private static final String MSG_AUTH_FAIL_REASON = "§cFailed to authenticate with MapIndexer: §4%s";
    
    private final String apiBase;
    private final Gson gson = new Gson();
    private String authToken;
    private String username;
    private volatile ListenableFuture<?> authFuture;
    private final HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .build();
    
    private ApiService(String apiBase) {
        this.apiBase = apiBase;
    }
    
    public synchronized ListenableFuture<?> authenticateIfNeeded() {
        if (authFuture != null) {
            return authFuture;
        }
        if (authToken != null) {
            MapIndexerClient.sendChatMessage(MSG_AUTH_STILL_VALID);
            return authFuture;
        }
        if (!MapIndexerClient.isOnS26()) {
            return Futures.immediateCancelledFuture();
        }
        
        var client = MinecraftClient.getInstance();
        var session = client.getSession();
        var username = session.getUsername();
        this.username = username;
        var uuid = session.getUuidOrNull();
        String nonce = Long.toString(System.currentTimeMillis() / 1000 / 60);
        var key = AUTH_PREFIX + username + uuid + nonce;
        var hash = CryptoUtils.sha1(key);
        
        authFuture = NetworkUtils.EXECUTOR.submit(() -> {
            try {
                client.getSessionService().joinServer(uuid, session.getAccessToken(), hash);
                AuthBlob auth = new AuthBlob(username, uuid, nonce, Version.VERSION);
                doAuthenticate(auth);
                MapIndexerClient.sendChatMessage(MSG_AUTH_SUCCESS);
                return true;
            } catch (ApiException e) {
                logger.error("Failed to authenticate", e);
                MapIndexerClient.sendChatMessage(Text.literal(MSG_AUTH_FAIL_REASON.formatted(e.getMessage())));
            } catch (InterruptedException | IOException | AuthenticationException e) {
                logger.error("Failed to authenticate", e);
                MapIndexerClient.sendChatMessage(MSG_AUTH_FAIL);
            }
            throw new RuntimeException(""); // Consider a more informative exception
        });
        
        return authFuture;
    }
    
    private void doAuthenticate(AuthBlob blob) throws IOException, InterruptedException, ApiException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase + "/auth/minecraft"))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(blob)))
            .build();
        
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), response.body());
        }
        this.authToken = response.body();
    }
    
    public void resetFailedAuthentication() {
        if (authToken == null) {
            authFuture = null;
        }
    }
    
    public void submit(Submission submission) throws IOException, InterruptedException, ApiException {
        if (authToken == null) {
            throw new ApiException(100403, "Not authenticated (no request performed)");
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase + "/shop/submit"))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
            .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(submission)))
            .build();
        
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), response.body());
        }
    }
    
    public Map<BlockPos, Long> downloadIndex(String dimension) throws IOException, InterruptedException, ApiException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase + "/shop/indexes/" + dimension))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_OCTET_STREAM.getMimeType())
            .GET().build();
        
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), "");
        }
        
        var buffer = ByteBuffer.wrap(response.body());
        Map<BlockPos, Long> indexes = new HashMap<>();
        while (buffer.hasRemaining()) {
            var x = VarInt.readSignedVarLong(buffer);
            var y = VarInt.readSignedVarLong(buffer);
            var z = VarInt.readSignedVarLong(buffer);
            var age = VarInt.readSignedVarLong(buffer);
            indexes.put(new BlockPos((int) x, (int) y, (int) z), age);
        }
        return indexes;
    }
    
    public void makeReport(List<BlockStateReport> reports) throws ApiException, IOException, InterruptedException {
        if (authToken == null) {
            throw new ApiException(100403, "Not authenticated (no request performed)");
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase + "/blockstate/report"))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(reports)))
            .build();
        
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new ApiException(response.statusCode(), response.body());
        }
    }
    
    public void delete(Deletion deletion) throws ApiException, IOException, InterruptedException {
        if (authToken == null) {
            throw new ApiException(100403, "Not authenticated (no request performed)");
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase + "/shop/remove"))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(deletion)))
            .build();
        
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new ApiException(response.statusCode(), response.body());
        }
    }
    
    public static synchronized ApiService getInstance() {
        if (instance == null) {
            instance = new ApiService(MapIndexerClient.getApiBase());
        }
        return instance;
    }
    
    public String getDebugInformation() {
        var sb = new StringBuilder();
        sb.append("§dApi:§r ");
        if (this.authToken != null) {
            sb.append("§2Logged in as ").append(this.username).append("§r");
        } else {
            sb.append("§cNot Authenticated§r");
        }
        sb.append("\n    IsOnServer26: ");
        if (MapIndexerClient.isOnS26()) {
            sb.append("§2Yes§r");
        } else {
            sb.append("§7No§r");
        }
        return sb.toString();
    }
}
