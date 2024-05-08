package io.github.nortio;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

public class WaNotifierFabric implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("wanotifier-fabric");
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Properties props = new Properties();
    public static final String KO_WHATSAPP_BRIDGE_ERROR = "KO: Whatsapp bridge error";
    public static final String WA_BRIDGE_TIMEOUT = "WA Bridge timeout";
    private static String token = "1234";
    private static String url = "http://localhost:8000/";

    private static final class EventType {
        public static final String JOIN = "join";
        public static final String QUIT = "quit";
        public static final String CHAT = "chat";
    }

    private void broadcast(String message, MinecraftServer server) {
        server.getPlayerManager().broadcast(Text.literal(message), false);
    }

    private void doRequest(String type, String params, MinecraftServer server) {
        URI uri = URI.create(url + type + "?token=" + token + params);

        HttpRequest req = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept((HttpResponse<String> body) -> {
                    if (body == null) {
                        return;
                    }

                    String res = body.body();
                    if (!Objects.equals(res, "ok")) {
                        LOGGER.error(KO_WHATSAPP_BRIDGE_ERROR);
                        broadcast(KO_WHATSAPP_BRIDGE_ERROR, server);
                    }
                })
                .exceptionally((s)->{
                    if(s instanceof HttpTimeoutException) {
                        LOGGER.error(WA_BRIDGE_TIMEOUT);
                        broadcast(WA_BRIDGE_TIMEOUT, server);
                    } else  {
                        LOGGER.error("WA Bridge unknown error: {}", s.getMessage());
                        broadcast("WA Bridge unknown error: "+ s.getMessage() , server);
                    }
                    return null;
                });
    }

    @Override
    public void onInitialize() {
        String configPath = FabricLoader.getInstance().getConfigDir().resolve("wanotifier.properties").toAbsolutePath().toString();
        try {
            props.load(new FileInputStream(configPath));
            String tok = props.getProperty("token");
            if (tok != null) {
                token = tok;
            } else {
                LOGGER.error("No token provided in wanotifier.properties!!");
            }

            String url_prop = props.getProperty("url");
            if (url_prop != null) {
                url = url_prop;
            } else {
                LOGGER.error("No url for wanotifier service provided in wanotifier.properties!!");
            }

        } catch (IOException e) {
            props.setProperty("token", token);
            props.setProperty("url", url);
            try {
                props.store(new FileWriter(configPath), "URL is where wanotifier service is running (don't forget trailing slash!!), token is self-explanatory");
            } catch (IOException ex) {
                LOGGER.error("Could not create properties file. Exception: {}", ex.toString());
            }
        }

        ServerPlayConnectionEvents.JOIN.register((ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) -> {
            ServerPlayerEntity player = handler.player;
            String params = "&joined=" + player.getName().getLiteralString();
            doRequest(EventType.JOIN, params, server);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((ServerPlayNetworkHandler handler, MinecraftServer server) -> {
            ServerPlayerEntity player = handler.player;
            String params = "&duration=sconosciuto" + "&joined=" + player.getName().getLiteralString();
            doRequest(EventType.QUIT, params, server);
        });

        ServerMessageEvents.CHAT_MESSAGE.register((SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params)->{
            String encoded_message = URLEncoder.encode(Objects.requireNonNull(message.getContent().getLiteralString()), StandardCharsets.UTF_8);
            String param = "&message="+ encoded_message + "&author=" + sender.getName().getLiteralString();
            doRequest(EventType.CHAT, param, sender.server);
        });
    }
}