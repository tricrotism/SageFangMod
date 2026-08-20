package com.tricrotism.modules.clientdetect.labymod;

import com.tricrotism.SageFang;
import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.packets.*;
import com.tricrotism.utils.MojangSessionGate;
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

/**
 * The LabyConnect protocol state machine for a single connection. Created by
 * {@link LabyConnectClient} once the socket is up, it drives the handshake
 * (hello &rarr; login &rarr; Mojang-backed encryption &rarr; play), dispatches
 * inbound packets, and forwards friend/chat/status packets to
 * {@link LabySocial}.
 *
 * <p>All packet handling runs on the Netty event-loop thread. {@link #state}
 * and the {@code labyModUsers} set are touched from that thread and read from
 * the game thread (via {@link LabyModService}), hence {@code volatile} / a
 * concurrent set. The Mojang session-server auth runs on a virtual thread to
 * keep blocking HTTP off the event loop.
 */
public class LabyConnectSession {
    /**
     * Handshake progression: HELLO &rarr; LOGIN &rarr; PLAY, or DISCONNECTED on teardown.
     */
    enum State {HELLO, LOGIN, PLAY, DISCONNECTED}

    private final LabyConnectClient client;
    /**
     * Current handshake state; read off-thread by {@link LabyModService}.
     */
    @Getter private volatile State state = State.HELLO;
    private final Set<UUID> labyModUsers = ConcurrentHashMap.newKeySet();
    /**
     * Held from just before the Mojang join until login settles; serialized via {@link MojangSessionGate}.
     */
    private volatile MojangSessionGate.Ticket sessionTicket;

    LabyConnectSession(LabyConnectClient client) {
        this.client = client;
    }

    /**
     * Entry point for every inbound packet (called from
     * {@link LabyConnectClient}'s inbound handler on the event loop). Switches
     * on packet type: handshake/keepalive packets are processed locally, while
     * friend, message, status and typing packets are forwarded to
     * {@link LabySocial}. Exceptions are caught and logged so a single bad
     * packet can't tear down the pipeline.
     */
    public void handlePacket(Packet packet) {
        SageFang.LOGGER.debug("[LabyConnect] Received packet: {} (state={})", packet.getClass().getSimpleName(), state);
        try {
            switch (packet) {
                case HelloPong pong -> handleHelloPong(pong);
                case EncryptionRequest req -> handleEncryptionRequest(req);
                case LoginComplete complete -> handleLoginComplete(complete);
                case Ping ping -> handlePing(ping);
                case UserBadge badge -> handleUserBadge(badge);
                case Disconnect disc -> handleDisconnect(disc);
                case Kick kick -> handleKick(kick);
                case AddonMessage msg -> SageFang.LOGGER.debug("[LabyConnect] AddonMessage key='{}'", msg.getKey());
                case LoginFriend p -> LabySocial.instance.onFriendList(p.getFriends());
                case PlayPlayerOnline p -> LabySocial.instance.onPlayerOnline(p.getPlayer());
                case PlayFriendStatus p -> LabySocial.instance.onFriendStatus(p.getPlayer(), p.getPlayerInfo());
                case PlayFriendPlayingOn p -> LabySocial.instance.onPlayingOn(p.getPlayer(), p.getGameModeName());
                case PlayRequestAddFriendResponse p -> LabySocial.instance.onAddFriendResponse(p);
                case ServerMessage p -> LabySocial.instance.onServerMessage(p.getMessage());
                case Message p -> LabySocial.instance.onMessage(p);
                case PlayTyping p -> LabySocial.instance.onTyping(p);
                default ->
                    SageFang.LOGGER.debug("[LabyConnect] Unhandled packet: {}", packet.getClass().getSimpleName());
            }
        } catch (Exception e) {
            SageFang.LOGGER.error("[LabyConnect] Error handling packet {}", packet.getClass().getSimpleName(), e);
        }
    }

    /**
     * Responds to the server's {@code HelloPong} by sending the login burst of
     * {@link LoginVersion}, {@link LoginData} (local profile id and name) and
     * {@link LoginOptions}, then advances to {@link State#LOGIN}.
     */
    private void handleHelloPong(HelloPong pong) {
        SageFang.LOGGER.info("[LabyConnect] HelloPong received, sending login packets");

        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();

        client.sendPacket(new LoginVersion("1.21.10", 770));
        client.sendPacket(new LoginData(user.getProfileId(), user.getName()));
        client.sendPacket(new LoginOptions());

        state = State.LOGIN;
        SageFang.LOGGER.info("[LabyConnect] State -> LOGIN");
    }

    /**
     * Performs the encryption handshake in response to {@link EncryptionRequest}.
     * On a virtual thread (to avoid blocking the event loop) it generates a
     * shared secret, runs the Mojang {@code joinServer} session auth, then sends
     * an {@link EncryptionResponse} carrying the RSA-encrypted secret and verify
     * token plus an empty (unencrypted) PIN. The verify token is normalised to
     * 10 bytes (first 4 from the server, rest zeroed) to match LabyMod. Once the
     * response is on the wire, AES cipher de/encoders are spliced into the
     * pipeline around the framing handlers. Any failure disconnects.
     */
    private void handleEncryptionRequest(EncryptionRequest req) {
        Thread.ofVirtual().name("labyconnect-auth").start(() -> {
            try {
                sessionTicket = MojangSessionGate.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
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

                byte[] emptyPin = new byte[0]; // no PIN, so send raw empty, NOT encrypted

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
                        SageFang.LOGGER.info("[LabyConnect] Encryption enabled");
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to enable encryption", e);
                    }
                });
            } catch (Exception e) {
                releaseTicket();
                SageFang.LOGGER.error("Encryption handshake failed", e);
                client.disconnect();
            }
        });
    }

    /**
     * Proves ownership of the Minecraft account to LabyConnect via Mojang's
     * {@code sessionserver} join endpoint: computes the SHA-1 server-id hash
     * from the shared secret and public key, then POSTs the access token,
     * profile id and hash. Retries up to 3 times with exponential backoff
     * (2s/4s/8s) on HTTP 429 rate-limiting; any other non-2xx status throws.
     * Blocking. Must be called off the event loop.
     */
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
                SageFang.LOGGER.info("[LabyConnect] Mojang rate limited, retrying in {}ms", delay);
                Thread.sleep(delay);
                continue;
            }
            throw new RuntimeException("Mojang auth failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        SageFang.LOGGER.info("[LabyConnect] Mojang joinServer auth succeeded");
    }

    /**
     * Marks the session as fully logged in: advances to {@link State#PLAY},
     * hands the live client to {@link LabySocial} so it can send, and publishes
     * the current server status.
     */
    private void handleLoginComplete(LoginComplete complete) {
        releaseTicket();
        state = State.PLAY;
        SageFang.LOGGER.info("LabyConnect login complete");
        LabySocial.instance.setClient(client);
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
            SageFang.LOGGER.info("[LabyConnect] Published server status: {}:{}", ip, port);
        }
    }

    /**
     * Keepalive: replies to a server {@link Ping} with a {@link Pong}.
     */
    private void handlePing(Ping ping) {
        client.sendPacket(new Pong());
    }

    /**
     * Records the UUIDs carried by a {@link UserBadge} as confirmed LabyMod
     * users. These accumulate (the set is never cleared mid-session) and back
     * {@link #isLabyModUser(UUID)} / {@link #getLabyModUsers()}. The parallel
     * rank bytes are forwarded to {@link LabySocial#onUserBadges} for badge display.
     */
    private void handleUserBadge(UserBadge badge) {
        UUID[] uuids = badge.getUuids();
        Collections.addAll(labyModUsers, uuids);
        LabySocial.instance.onUserBadges(uuids, badge.getRanks());
        SageFang.LOGGER.debug("[LabyConnect] UserBadge: {} users, total tracked: {}", uuids.length, labyModUsers.size());
    }

    /**
     * Server-initiated graceful disconnect: logs the reason and tears down the client.
     */
    private void handleDisconnect(Disconnect disc) {
        SageFang.LOGGER.warn("LabyConnect server disconnected: {}", disc.getReason());
        state = State.DISCONNECTED;
        client.disconnect();
    }

    /**
     * Server-initiated kick: logs the reason and tears down the client.
     */
    private void handleKick(Kick kick) {
        SageFang.LOGGER.warn("[LabyConnect] Kicked: {}", kick.getReason());
        state = State.DISCONNECTED;
        client.disconnect();
    }

    /**
     * Called by the client when the channel goes inactive; flips state to DISCONNECTED.
     */
    void onDisconnected() {
        releaseTicket();
        state = State.DISCONNECTED;
    }

    /**
     * Releases the held {@link MojangSessionGate} ticket if any (idempotent).
     */
    private void releaseTicket() {
        MojangSessionGate.Ticket t = sessionTicket;
        if (t != null) {
            t.release();
            sessionTicket = null;
        }
    }

    /**
     * Sends a {@link UserTracker} (add/remove a batch of UUIDs on a tracking
     * channel) to ask the server which of them are LabyMod users. Ignored
     * unless the session is in {@link State#PLAY}.
     */
    public void sendUserTracker(byte channel, byte action, List<UUID> uuids) {
        if (state != State.PLAY) {
            return;
        }
        client.sendPacket(new UserTracker(channel, action, uuids.toArray(new UUID[0])));
    }

    /**
     * An unmodifiable view of all UUIDs confirmed as LabyMod users this session.
     */
    public Set<UUID> getLabyModUsers() {
        return Collections.unmodifiableSet(labyModUsers);
    }

    /**
     * True if {@code uuid} has been confirmed as a LabyMod user via {@link UserBadge}.
     */
    public boolean isLabyModUser(UUID uuid) {
        return labyModUsers.contains(uuid);
    }

}
