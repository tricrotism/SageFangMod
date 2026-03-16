package com.tricrotism.modules.clientdetect.labymod;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.packets.*;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.CipherDecoder;
import net.minecraft.network.CipherEncoder;
import net.minecraft.util.Crypt;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LabyConnectSession {
    enum State {HELLO, LOGIN, PLAY, DISCONNECTED}

    private final LabyConnectClient client;
    @Getter private State state = State.HELLO;
    private final Set<UUID> labyModUsers = ConcurrentHashMap.newKeySet();

    LabyConnectSession(LabyConnectClient client) {
        this.client = client;
    }

    public void handlePacket(Packet packet) {
        LabyConnectClient.LOGGER.debug("[LabyConnect] Received packet: {} (state={})", packet.getClass().getSimpleName(), state);
        try {
            switch (packet) {
                case HelloPong pong -> handleHelloPong(pong);
                case EncryptionRequest req -> handleEncryptionRequest(req);
                case LoginComplete complete -> handleLoginComplete(complete);
                case Ping ping -> handlePing(ping);
                case UserBadge badge -> handleUserBadge(badge);
                case Disconnect disc -> handleDisconnect(disc);
                case Kick kick -> handleKick(kick);
                case AddonMessage msg ->
                    LabyConnectClient.LOGGER.debug("[LabyConnect] AddonMessage key='{}'", msg.getKey());
                default ->
                    LabyConnectClient.LOGGER.debug("[LabyConnect] Unhandled packet: {}", packet.getClass().getSimpleName());
            }
        } catch (Exception e) {
            LabyConnectClient.LOGGER.error("[LabyConnect] Error handling packet {}", packet.getClass().getSimpleName(), e);
        }
    }

    private void handleHelloPong(HelloPong pong) {
        LabyConnectClient.LOGGER.info("[LabyConnect] HelloPong received, sending login packets");

        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();

        client.sendPacket(new LoginVersion("1.21.10", 770));
        client.sendPacket(new LoginData(user.getProfileId(), user.getName()));
        client.sendPacket(new LoginOptions());

        state = State.LOGIN;
        LabyConnectClient.LOGGER.info("[LabyConnect] State -> LOGIN");
    }

    private void handleEncryptionRequest(EncryptionRequest req) {
        // Run auth off the event loop to avoid blocking
        Thread.ofVirtual().name("labyconnect-auth").start(() -> {
            try {
                SecretKey secretKey = Crypt.generateSecretKey();
                PublicKey publicKey = Crypt.byteToPublicKey(req.getPublicKey());

                authenticate(req.getServerId(), publicKey, secretKey);

                byte[] encryptedSecret = Crypt.encryptUsingKey(publicKey, secretKey.getEncoded());

                // Build 10-byte verify token: first 4 from server, rest zeroed
                byte[] serverToken = req.getVerifyToken();
                byte[] verifyToken10 = new byte[10];
                System.arraycopy(serverToken, 0, verifyToken10, 0, Math.min(serverToken.length, 4));
                byte[] encryptedVerify = Crypt.encryptUsingKey(publicKey, verifyToken10);

                byte[] emptyPin = new byte[0]; // no PIN — send raw empty, NOT encrypted

                client.sendPacket(new EncryptionResponse(
                    encryptedSecret,
                    encryptedVerify,
                    emptyPin
                ), ch -> {
                    try {
                        Cipher encrypt = Crypt.getCipher(Cipher.ENCRYPT_MODE, secretKey);
                        Cipher decrypt = Crypt.getCipher(Cipher.DECRYPT_MODE, secretKey);
                        ch.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(decrypt));
                        ch.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(encrypt));
                        LabyConnectClient.LOGGER.info("[LabyConnect] Encryption enabled");
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to enable encryption", e);
                    }
                });
            } catch (Exception e) {
                LabyConnectClient.LOGGER.error("Encryption handshake failed", e);
                client.disconnect();
            }
        });
    }

    private void authenticate(String serverId, PublicKey publicKey, SecretKey secretKey) throws Exception {
        byte[] hash = Crypt.digestData(serverId, publicKey, secretKey);
        String hashStr = new BigInteger(hash).toString(16);

        User user = Minecraft.getInstance().getUser();
        String uuid = user.getProfileId().toString().replace("-", "");

        String body = "{\"accessToken\":\"" + user.getAccessToken() + "\","
            + "\"selectedProfile\":\"" + uuid + "\","
            + "\"serverId\":\"" + hashStr + "\"}";

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/join"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        // Retry with backoff on 429 (Mojang rate limit)
        int maxRetries = 3;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || response.statusCode() == 200) {
                break;
            }
            if (response.statusCode() == 429 && attempt < maxRetries) {
                long delay = (long) (2000 * Math.pow(2, attempt)); // 2s, 4s, 8s
                LabyConnectClient.LOGGER.info("[LabyConnect] Mojang rate limited, retrying in {}ms", delay);
                Thread.sleep(delay);
                continue;
            }
            throw new RuntimeException("Mojang auth failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        LabyConnectClient.LOGGER.info("[LabyConnect] Mojang joinServer auth succeeded");
    }

    private void handleLoginComplete(LoginComplete complete) {
        state = State.PLAY;
        LabyConnectClient.LOGGER.info("LabyConnect login complete");
        publishServerStatus();
    }

    /**
     * Sends the current server address to LabyConnect so friends see us as
     * playing on a server instead of "away".
     */
    private void publishServerStatus() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            String ip = mc.getCurrentServer().ip;
            int port = 25565;
            // Parse port from ip if present (e.g. "play.example.com:25566")
            int colonIdx = ip.lastIndexOf(':');
            if (colonIdx > 0) {
                try {
                    port = Integer.parseInt(ip.substring(colonIdx + 1));
                    ip = ip.substring(0, colonIdx);
                } catch (NumberFormatException ignored) {
                }
            }
            client.sendPacket(new ServerStatusUpdate(ip, port));
            LabyConnectClient.LOGGER.info("[LabyConnect] Published server status: {}:{}", ip, port);
        }
    }

    private void handlePing(Ping ping) {
        client.sendPacket(new Pong());
    }

    private void handleUserBadge(UserBadge badge) {
        UUID[] uuids = badge.getUuids();
        Collections.addAll(labyModUsers, uuids);
        LabyConnectClient.LOGGER.debug("[LabyConnect] UserBadge: {} users, total tracked: {}", uuids.length, labyModUsers.size());
    }

    private void handleDisconnect(Disconnect disc) {
        LabyConnectClient.LOGGER.warn("LabyConnect server disconnected: {}", disc.getReason());
        state = State.DISCONNECTED;
        client.disconnect();
    }

    private void handleKick(Kick kick) {
        LabyConnectClient.LOGGER.warn("[LabyConnect] Kicked: {}", kick.getReason());
        state = State.DISCONNECTED;
        client.disconnect();
    }

    void onDisconnected() {
        state = State.DISCONNECTED;
    }

    public void sendUserTracker(byte channel, byte action, List<UUID> uuids) {
        if (state != State.PLAY) {
            return;
        }
        client.sendPacket(new UserTracker(channel, action, uuids.toArray(new UUID[0])));
    }

    public Set<UUID> getLabyModUsers() {
        return Collections.unmodifiableSet(labyModUsers);
    }

    public boolean isLabyModUser(UUID uuid) {
        return labyModUsers.contains(uuid);
    }

}
