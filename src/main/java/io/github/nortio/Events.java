package io.github.nortio;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class Events {
    private Events() {}

    static public class Join implements WaNotifierEvent {
        private final String playerName;

        public Join(String playerName) {
            this.playerName = playerName;
        }

        @Override
        public String getType() {
            return "join";
        }

        @Override
        public String buildParams() {
            return "&joined=" + playerName;
        }

        @Override
        public String getTextMessage() {
            return playerName + " è entrato nel server";
        }
    }

    static public class Disconnect implements WaNotifierEvent {
        private final String playerName;

        public Disconnect(String playerName) {
            this.playerName = playerName;
        }

        @Override
        public String getType() {
            return "quit";
        }

        @Override
        public String buildParams() {
            return "&duration=sconosciuto" + "&joined=" + playerName;
        }

        @Override
        public String getTextMessage() {
            return playerName + " è uscito dal server";
        }
    }

    static  public class Chat implements WaNotifierEvent {
        private final String playerName;
        private final String chatMessage;

        public Chat(String playerName, String chatMessage) {
            this.playerName = playerName;
            this.chatMessage = chatMessage;
        }

        @Override
        public String getType() {
            return "chat";
        }

        @Override
        public String buildParams() {
            String encoded_message = URLEncoder.encode(chatMessage, StandardCharsets.UTF_8);
            return "&message="+ encoded_message + "&author=" + playerName;
        }

        @Override
        public String getTextMessage() {
            return "**[MC-CHAT]** *" + playerName + "*: " + chatMessage;
        }
    }

}
