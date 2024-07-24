package ec.edu.ups.proyectofinal;

import okhttp3.*;
import okio.ByteString;
import java.util.concurrent.TimeUnit;
public class WebSocketClient {
    private final OkHttpClient client;
    private final Request request;
    private WebSocket webSocket;

    public WebSocketClient(String serverUrl) {
        client = new OkHttpClient.Builder()
                .readTimeout(3, TimeUnit.SECONDS)
                .build();
        request = new Request.Builder()
                .url(serverUrl)
                .build();
    }
    public void connect() {
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("Connected to server");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                System.out.println("Received message: " + text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                System.out.println("Received bytes: " + bytes.hex());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
                System.out.println("Closing: " + code + " / " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                System.out.println("Error: " + t.getMessage());
            }
        });
    }
    public void send(byte[] data) {
        webSocket.send(ByteString.of(data));
    }

    public void close() {
        webSocket.close(1000, "Client closed connection");
    }
}
