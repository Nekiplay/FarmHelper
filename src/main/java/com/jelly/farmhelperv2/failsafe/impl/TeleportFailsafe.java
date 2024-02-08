package com.jelly.farmhelperv2.failsafe.impl;

import com.google.common.collect.EvictingQueue;
import com.jelly.farmhelperv2.config.FarmHelperConfig;
import com.jelly.farmhelperv2.config.page.CustomFailsafeMessagesPage;
import com.jelly.farmhelperv2.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv2.event.ReceivePacketEvent;
import com.jelly.farmhelperv2.failsafe.Failsafe;
import com.jelly.farmhelperv2.failsafe.FailsafeManager;
import com.jelly.farmhelperv2.feature.impl.AntiStuck;
import com.jelly.farmhelperv2.feature.impl.LagDetector;
import com.jelly.farmhelperv2.feature.impl.MovRecPlayer;
import com.jelly.farmhelperv2.handler.BaritoneHandler;
import com.jelly.farmhelperv2.handler.GameStateHandler;
import com.jelly.farmhelperv2.handler.MacroHandler;
import com.jelly.farmhelperv2.handler.RotationHandler;
import com.jelly.farmhelperv2.macro.AbstractMacro;
import com.jelly.farmhelperv2.util.*;
import com.jelly.farmhelperv2.util.helper.Rotation;
import com.jelly.farmhelperv2.util.helper.RotationConfiguration;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.*;
import java.util.Optional;

public class TeleportFailsafe extends Failsafe {
    private static TeleportFailsafe instance;

    public static TeleportFailsafe getInstance() {
        if (instance == null) {
            instance = new TeleportFailsafe();
        }
        return instance;
    }

    private final EvictingQueue<Tuple<BlockPos, AbstractMacro.State>> lastWalkedPositions = EvictingQueue.create(400);

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.TELEPORT_CHECK;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnTeleportationFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnTeleportationFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnTeleportationFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnTeleportationFailsafe;
    }

    @Override
    public void onTickDetection(TickEvent.ClientTickEvent event) {
        if (MacroHandler.getInstance().isTeleporting()) {
            if (!lastWalkedPositions.isEmpty())
                lastWalkedPositions.clear();
            return;
        }
        BlockPos playerPos = mc.thePlayer.getPosition();
        if (lastWalkedPositions.isEmpty()) {
            lastWalkedPositions.add(new Tuple<>(playerPos, MacroHandler.getInstance().getCurrentMacro().map(AbstractMacro::getCurrentState).orElse(null)));
            return;
        }
        BlockPos last = ((Tuple<BlockPos, AbstractMacro.State>) lastWalkedPositions.toArray()[lastWalkedPositions.size() - 1]).getFirst();
        if (last.distanceSq(playerPos) < 1) {
            return;
        }
        lastWalkedPositions.add(new Tuple<>(playerPos, MacroHandler.getInstance().getCurrentMacro().map(AbstractMacro::getCurrentState).orElse(null)));
    }

    @Override
    public void onReceivedPacketDetection(ReceivePacketEvent event) {
        if (MacroHandler.getInstance().isTeleporting())
            return;
        if (!(event.packet instanceof S08PacketPlayerPosLook)) {
            return;
        }

//        if (LagDetector.getInstance().isLagging() || LagDetector.getInstance().wasJustLagging()) {
//            LogUtils.sendWarning("[Failsafe] Got rotation packet while lagging! Ignoring that one.");
//            return;
//        }

        if (AntiStuck.getInstance().isRunning()) {
            LogUtils.sendDebug("[Failsafe] Teleportation packet received while AntiStuck is running. Ignoring");
            return;
        }

        S08PacketPlayerPosLook packet = (S08PacketPlayerPosLook) event.packet;
        Vec3 currentPlayerPos = mc.thePlayer.getPositionVector();
        Vec3 packetPlayerPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        BlockPos packetPlayerBlockPos = new BlockPos(packetPlayerPos);
        Optional<Tuple<BlockPos, AbstractMacro.State>> lastWalkedPosition = lastWalkedPositions.stream().filter(pos -> pos.getFirst().equals(packetPlayerBlockPos)).findFirst();
        if (lastWalkedPosition.isPresent()) {
            if (packetPlayerPos.distanceTo(currentPlayerPos) < 1) {
                LogUtils.sendDebug("[Failsafe] AntiStuck should trigger there. Ignoring");
                return;
            }
            AbstractMacro.State currentState = MacroHandler.getInstance().getCurrentMacro().map(AbstractMacro::getCurrentState).orElse(null);
            LowerAvgBpsFailsafe.getInstance().resetStates();
            if (currentState == null || currentState != lastWalkedPosition.get().getSecond()) {
                LogUtils.sendFailsafeMessage("[Failsafe] You got lag backed into previous row! Fixing state", FailsafeNotificationsPage.tagEveryoneOnLagBackFailsafe);
                if (FailsafeNotificationsPage.notifyOnLagBackFailsafe)
                    FailsafeUtils.getInstance().sendNotification("You got lag backed into previous row! Fixing state", TrayIcon.MessageType.WARNING);
                MacroHandler.getInstance().getCurrentMacro().ifPresent(macro -> {
                    macro.setCurrentState(lastWalkedPosition.get().getSecond());
                    long delay = (long) (1_500 + Math.random() * 1_000);
                    long delayBefore = Math.max(500, delay - 500);
                    macro.setBreakTime(delay, delayBefore);
                });
                return;
            }
            LogUtils.sendFailsafeMessage("[Failsafe] You got lag backed! Not reacting", FailsafeNotificationsPage.tagEveryoneOnLagBackFailsafe);
            if (FailsafeNotificationsPage.notifyOnLagBackFailsafe)
                FailsafeUtils.getInstance().sendNotification("You got lag backed! Not reacting", TrayIcon.MessageType.WARNING);
            return;
        }

        if (packet.getY() >= 90 || BlockUtils.bedrockCount() > 2) {
            LogUtils.sendDebug("[Failsafe] Most likely a bedrock check! Will check in a moment to be sure.");
            return;
        }

//        if (BlockUtils.cropAroundAmount(packetPlayerBlockPos) > 5) {
//            LogUtils.sendDebug("[Failsafe] Still in the farm! Not reacting");
//            LogUtils.sendFailsafeMessage("[Failsafe] You probably got tp check forward into farm! Not reacting", shouldTagEveryone());
//            return;
//        }

        rotationBeforeReacting = new Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        double distance = currentPlayerPos.distanceTo(packetPlayerPos);
        LogUtils.sendDebug("[Failsafe] Teleport 2 detected! Distance: " + distance);
        if (distance >= FarmHelperConfig.teleportCheckSensitivity || (MacroHandler.getInstance().getCurrentMacro().isPresent() && Math.abs(packet.getY()) - Math.abs(MacroHandler.getInstance().getCurrentMacro().get().getLayerY()) > 0.8)) {
            LogUtils.sendDebug("[Failsafe] Teleport detected! Distance: " + distance);
            final double lastReceivedPacketDistance = currentPlayerPos.distanceTo(LagDetector.getInstance().getLastPacketPosition());
            // blocks per tick
            final double playerMovementSpeed = mc.thePlayer.getAttributeMap().getAttributeInstanceByName("generic.movementSpeed").getAttributeValue();
            final int ticksSinceLastPacket = (int) Math.ceil(LagDetector.getInstance().getTimeSinceLastTick() / 50D);
            final double estimatedMovement = playerMovementSpeed * ticksSinceLastPacket;
            if (lastReceivedPacketDistance > 7.5D && Math.abs(lastReceivedPacketDistance - estimatedMovement) < FarmHelperConfig.teleportCheckLagSensitivity)
                return;
            FailsafeManager.getInstance().possibleDetection(this);
        }
    }

    @Override
    public void duringFailsafeTrigger() {
        if (mc.currentScreen != null) {
            PlayerUtils.closeScreen();
            return;
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        FailsafeManager.getInstance().stopFailsafes();
        if (mc.thePlayer.getPosition().getY() < 100 && GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.GARDEN)
            FailsafeManager.getInstance().restartMacroAfterDelay();
    }

    @Override
    public void resetStates() {
        teleportCheckState = TeleportCheckState.NONE;
        rotationBeforeReacting = null;
        positionBeforeReacting = null;
        randomMessage = null;
        randomContinueMessage = null;
        rotation.reset();
        lastWalkedPositions.clear();
    }

    private TeleportCheckState teleportCheckState = TeleportCheckState.NONE;
    private final RotationHandler rotation = RotationHandler.getInstance();
    private BlockPos positionBeforeReacting = null;
    private Rotation rotationBeforeReacting = null;
    String randomMessage;
    String randomContinueMessage;

    enum TeleportCheckState {
        NONE,
        WAIT_BEFORE_START,
        LOOK_AROUND,
        WAIT_BEFORE_SENDING_MESSAGE_1,
        SEND_MESSAGE,
        ROTATE_TO_POS_BEFORE,
        LOOK_AROUND_2,
        WAIT_BEFORE_SENDING_MESSAGE_2,
        SEND_MESSAGE_2,
        ROTATE_TO_POS_BEFORE_2,
        GO_BACK_START,
        GO_BACK_END,
        WARP_GARDEN,
        END
    }
}
