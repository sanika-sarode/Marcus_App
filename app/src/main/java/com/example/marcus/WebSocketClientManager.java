package com.example.marcus;

import android.util.Log;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.net.URISyntaxException;

public class WebSocketClientManager {

    private static final String TAG = "WebSocketClientManager";
    private static WebSocketClient webSocketClient;
    private static WebSocketClientManager instance;
    private WebSocketConnectionListener listener;

    public interface WebSocketConnectionListener {
        void onConnected();
        void onCommandReceived(String command);
    }

    private WebSocketClientManager() {
        // Private constructor to enforce singleton pattern
    }

    public static WebSocketClientManager getInstance() {
        if (instance == null) {
            instance = new WebSocketClientManager();
        }
        return instance;
    }

    public void setWebSocketConnectionListener(WebSocketConnectionListener listener) {
        this.listener = listener;
    }

    public void connect() {
        URI uri;
        try {
            uri = new URI(Config.WEBSOCKET_SERVER_URI);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Log.d(TAG, "WebSocket Connected");
                if (listener != null) {
                    listener.onConnected();
                }
                webSocketClient.send("Hello from Android!");
            }

            @Override
            public void onMessage(String message) {
                Log.d(TAG, "Received: " + message);
                if (listener != null) {
                    listener.onCommandReceived(message);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.d(TAG, "WebSocket Closed: " + reason);
            }

            @Override
            public void onError(Exception ex) {
                Log.e(TAG, "WebSocket Error: " + ex.getMessage(), ex);
            }
        };

        webSocketClient.connect();
    }

    public void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }

    public WebSocketClient getWebSocketClient() {
        return webSocketClient;
    }
}