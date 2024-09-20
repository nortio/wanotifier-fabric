package io.github.nortio;

public interface WaNotifierEvent {
    String getType();
    String buildParams();
    String getTextMessage();
}

