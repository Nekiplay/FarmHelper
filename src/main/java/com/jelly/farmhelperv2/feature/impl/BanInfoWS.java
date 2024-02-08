package com.jelly.farmhelperv2.feature.impl;

import cc.polyfrost.oneconfig.utils.Multithreading;
import cc.polyfrost.oneconfig.utils.Notifications;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jelly.farmhelperv2.FarmHelper;
import com.jelly.farmhelperv2.config.FarmHelperConfig;
import com.jelly.farmhelperv2.event.ReceivePacketEvent;
import com.jelly.farmhelperv2.failsafe.FailsafeManager;
import com.jelly.farmhelperv2.feature.IFeature;
import com.jelly.farmhelperv2.handler.GameStateHandler;
import com.jelly.farmhelperv2.handler.MacroHandler;
import com.jelly.farmhelperv2.util.LogUtils;
import com.jelly.farmhelperv2.util.helper.Clock;
import com.mojang.authlib.exceptions.AuthenticationException;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.StringUtils;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.http.message.BasicHeader;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.spongepowered.asm.mixin.Unique;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

public class BanInfoWS implements IFeature {
    private static BanInfoWS instance;
    private final Clock reconnectDelay = new Clock();
    @Getter
    private long lastReceivedPacket = System.currentTimeMillis();
    @Unique
    private final List<String> times = Arrays.asList(
            "23h 59m 59s",
            "23h 59m 58s",
            "23h 59m 57s",
            "23h 59m 56s"
    );
    @Unique
    private final List<String> days = Arrays.asList(
            "29d",
            "89d",
            "359d"
    );
    private WebSocketClient client;

    @Getter
    @Setter
    private int staffBans = 0;

    @Getter
    @Setter
    private int minutes = 0;

    @Getter
    @Setter
    private int bansByMod = 0;

    @Getter
    private boolean receivedBanwaveInfo = false;

    public BanInfoWS() {

    }

    public static BanInfoWS getInstance() {
        if (instance == null) {
            instance = new BanInfoWS();
        }
        return instance;
    }

    private static String compress(File file) throws IOException {
        ByteArrayOutputStream rstBao = new ByteArrayOutputStream();
        GZIPOutputStream zos = new GZIPOutputStream(rstBao);
        FileInputStream fis = new FileInputStream(file);
        try {
            byte[] buffer = new byte[10240];
            for (int length = 0; (length = fis.read(buffer)) != -1; ) {
                zos.write(buffer, 0, length);
            }
        } finally {
            try {
                fis.close();
            } catch (IOException ignore) {
            }
            try {
                zos.close();
            } catch (IOException ignore) {
            }
        }
        IOUtils.closeQuietly(zos);
        return Base64.getEncoder().encodeToString(rstBao.toByteArray());
    }

    private static String compress(String json) throws IOException {
        ByteArrayOutputStream rstBao = new ByteArrayOutputStream();
        GZIPOutputStream zos = new GZIPOutputStream(rstBao);
        zos.write(json.getBytes());
        IOUtils.closeQuietly(zos);
        return Base64.getEncoder().encodeToString(rstBao.toByteArray());
    }

    private List<BasicHeader> getHttpClientHeaders() {
        List<BasicHeader> headers = new ArrayList<>();
        headers.add(new BasicHeader("User-Agent", "Farm Helper"));
        headers.add(new BasicHeader("Content-Type", "application/json"));
        headers.add(new BasicHeader("Accept", "application/json"));
        return headers;
    }

    @Override
    public String getName() {
        return "Banwave Checker";
    }

    @Override
    public boolean isRunning() {
        return isToggled();
    }

    @Override
    public boolean shouldPauseMacroExecution() {
        return false;
    }

    @Override
    public boolean shouldStartAtMacroStart() {
        return false; // it's running all the time
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void resetStatesAfterMacroDisabled() {

    }

    @Override
    public boolean isToggled() {
        return FarmHelperConfig.banwaveCheckerEnabled;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public boolean isBanwave() {
        return getAllBans() >= FarmHelperConfig.banwaveThreshold;
    }

    public int getAllBans() {
        switch (FarmHelperConfig.banwaveThresholdType) {
            case 0: {
                return staffBans;
            }
            case 1: {
                return bansByMod;
            }
            case 2: {
                return bansByMod + staffBans;
            }
        }
        return 0;
    }

    @SubscribeEvent
    public void onCheckIfDisconnected(TickEvent.ClientTickEvent event) {
        if (client != null && client.isOpen() && System.currentTimeMillis() - lastReceivedPacket > 120_000L) {
            LogUtils.sendDebug("Disconnected from analytics server (no packets received in 2 minutes)");
            client.close();
            reconnectDelay.schedule(1_000);
        }
    }

    @SubscribeEvent
    public void onTickReconnect(TickEvent.ClientTickEvent event) {
        
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent event) {
        Packet<?> packet = event.packet;
        if (packet instanceof S40PacketDisconnect) {
            String reason = ((S40PacketDisconnect) packet).getReason().getFormattedText();
            processBanScreen(reason);
        }
    }

    // SKYSKIPPED BAN STATS

    private void processBanScreen(String wholeReason) {

    }

    public void playerBanned(int days, String reason, String banId, String fullReason) {

    }

    private void collectMods(JsonObject obj) {

    }

    public void sendAnalyticsData() {
        sendAnalyticsData(AnalyticsState.INFO);
    }

    public void sendAnalyticsData(AnalyticsState state) {

    }

    public void sendFailsafeInfo(FailsafeManager.EmergencyType type) {

    }

    private JsonObject getHeaders() {
        JsonObject handshake = new JsonObject();
        handshake.addProperty("reason", "WebSocketConnector");
        handshake.addProperty("id", Minecraft.getMinecraft().getSession().getPlayerID()); // that's public uuid bozos, not a token to login
        handshake.addProperty("username", Minecraft.getMinecraft().getSession().getUsername());
        handshake.addProperty("modVersion", FarmHelper.VERSION);
        handshake.addProperty("mod", "farmHelper");
        try {
            String serverId = mojangAuthentication();
            handshake.addProperty("serverId", serverId);
        } catch (AuthenticationException e) {
            e.printStackTrace();
            reconnectDelay.schedule(6_000L);
            return null;
        }
        return handshake;
    }

    private String mojangAuthentication() throws AuthenticationException {
        Random r1 = new Random();
        Random r2 = new Random(System.identityHashCode(new Object()));
        BigInteger random1Bi = new BigInteger(128, r1);
        BigInteger random2Bi = new BigInteger(128, r2);
        BigInteger serverBi = random1Bi.xor(random2Bi);
        String serverId = serverBi.toString(16);
        String commentForDecompilers =
                "This sends a request to Mojang's auth server, used for verification. This is how we verify you are the real user without your session details. This is the exact same system as Skytils and Optifine use.";
        try {
            Minecraft.getMinecraft().getSessionService().joinServer(Minecraft.getMinecraft().getSession().getProfile(), Minecraft.getMinecraft().getSession().getToken(), serverId);
        } catch (AuthenticationException e) {
            throw new AuthenticationException("Failed to authenticate with Mojang's servers. " + e.getMessage());
        }
        return serverId;
    }

    private WebSocketClient createNewWebSocketClient() throws URISyntaxException {
        return new WebSocketClient(new URI("ws://ws.may2bee.pl")) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
            }

            @Override
            public void onMessage(String message) {
                JsonObject jsonObject = FarmHelper.gson.fromJson(message, JsonObject.class);
                String msg = jsonObject.get("message").getAsString();
                switch (msg) {
                    case "banwaveInfo": {
                        int bans = jsonObject.get("bansInLast15Minutes").getAsInt();
                        int minutes = jsonObject.get("bansInLast15MinutesTime").getAsInt();
                        int bansByMod = jsonObject.get("bansInLast15MinutesMod").getAsInt();
                        BanInfoWS.getInstance().setStaffBans(bans);
                        BanInfoWS.getInstance().setMinutes(minutes);
                        BanInfoWS.getInstance().setBansByMod(bansByMod);
                        lastReceivedPacket = System.currentTimeMillis();
                        if (!receivedBanwaveInfo) {
                            if (client.isOpen() && client.getReadyState() != ReadyState.NOT_YET_CONNECTED) {
                                LogUtils.sendDebug("Connected to analytics websocket server");
                                Notifications.INSTANCE.send("FarmHelper INFO", "Connected to analytics websocket server");
                            }
                        }
                        receivedBanwaveInfo = true;
                        System.out.println("Banwave info received: " + bans + " global staff bans in the last " + minutes + " minutes, " + bansByMod + " bans by this mod");
                        break;
                    }
                    case "playerGotBanned": {
                        String username = jsonObject.get("username").getAsString();
                        String days = jsonObject.get("days").getAsString();
                        String reason = jsonObject.get("reason").getAsString();
                        LogUtils.sendWarning("Detected ban screen in " + username + "'s client for " + days + " days (reason: " + reason + ")");
                        Notifications.INSTANCE.send("FarmHelper INFO", "Detected ban screen in " + username + "'s client for " + days + " days");
                        break;
                    }
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                LogUtils.sendDebug("Disconnected from analytics server");
                LogUtils.sendDebug("Code: " + code + ", reason: " + reason + ", remote: " + remote);
                if (!reconnectDelay.isScheduled())
                    reconnectDelay.schedule(5_000L);
            }

            @Override
            public void onError(Exception ex) {
                LogUtils.sendDebug("Error while connecting to analytics server. " + ex.getMessage());
                ex.printStackTrace();
            }
        };
    }

    public enum AnalyticsState {
        START_SESSION,
        INFO,
        END_SESSION,
    }
}
