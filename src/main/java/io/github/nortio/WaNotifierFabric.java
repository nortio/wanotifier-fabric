package io.github.nortio;

import com.google.gson.Gson;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Properties;

public class WaNotifierFabric implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("wanotifier-fabric");
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Properties props = new Properties();
    public static final String KO_WHATSAPP_BRIDGE_ERROR = "KO: Whatsapp bridge error";
    public static final String WA_BRIDGE_TIMEOUT = "WA Bridge timeout";
    public static final String WEBHOOK_TIMEOUT = "Webhook timeout";

    private static String token = "1234";
    private static String url = "http://localhost:8000/";
    private static String webhook = "";

    private void broadcast(String message, MinecraftServer server) {
        server.getPlayerManager().broadcast(Text.literal(message), false);
    }

    private void doRequest(WaNotifierEvent event, MinecraftServer server) {
        URI uri = URI.create(url + event.getType() + "?token=" + token + event.buildParams());

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
                .exceptionally((s) -> {
                    if (s instanceof HttpTimeoutException) {
                        LOGGER.error(WA_BRIDGE_TIMEOUT);
                        broadcast(WA_BRIDGE_TIMEOUT, server);
                    } else {
                        LOGGER.error("WA Bridge unknown error: {}", s.getMessage());
                        broadcast("WA Bridge unknown error: " + s.getMessage(), server);
                    }
                    return null;
                });
    }

    private void doWebhook(WaNotifierEvent event) {
        URI uri = URI.create(webhook);

        Gson gson = new Gson();
        LinkedHashMap<String, String> payload = new LinkedHashMap<>();
        payload.put("content", event.getTextMessage());

        String json = gson.toJson(payload);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(5))
                .build();
        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .exceptionally((s)->{
                    if(s instanceof HttpTimeoutException) {
                        LOGGER.error(WEBHOOK_TIMEOUT);
                    } else  {
                        LOGGER.error("Webhook unknown error: {}", s.getMessage());
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

            String webhk = props.getProperty("webhook");
            if(webhk != null && !webhk.isEmpty()) {
                webhook = webhk;
            } else {
                LOGGER.warn("No url for webhooks, not going to use them");
            }

        } catch (IOException e) {
            props.setProperty("token", token);
            props.setProperty("url", url);
            props.setProperty("webhook", webhook);
            try {
                props.store(new FileWriter(configPath), "URL is where wanotifier service is running (don't forget trailing slash!!), token is self-explanatory");
            } catch (IOException ex) {
                LOGGER.error("Could not create properties file. Exception: {}", ex.toString());
            }
        }

        ServerPlayConnectionEvents.JOIN.register((ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) -> {
            ServerPlayerEntity player = handler.player;
            Events.Join join = new Events.Join(player.getName().getLiteralString());
            doRequest(join, server);
            if(!webhook.isEmpty()) {
                doWebhook(join);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((ServerPlayNetworkHandler handler, MinecraftServer server) -> {
            ServerPlayerEntity player = handler.player;
            Events.Disconnect disconnect = new Events.Disconnect(player.getName().getLiteralString());
            doRequest(disconnect, server);
            if(!webhook.isEmpty()) {
                doWebhook(disconnect);
            }
        });

        ServerMessageEvents.CHAT_MESSAGE.register((SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params)->{
            Events.Chat chat = new Events.Chat(sender.getName().getLiteralString(), Objects.requireNonNull(message.getContent().getLiteralString()));
            doRequest(chat, sender.server);
            if(!webhook.isEmpty()) {
                doWebhook(chat);
            }
        });
    }
}