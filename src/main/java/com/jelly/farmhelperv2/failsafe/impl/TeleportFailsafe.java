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
            // just in case something in the hand keeps opening the screen
            if (FailsafeManager.getInstance().swapItemDuringRecording && mc.thePlayer.inventory.currentItem > 1)
                FailsafeManager.getInstance().selectNextItemSlot();
            return;
        }
        switch (teleportCheckState) {
            case NONE:
                teleportCheckState = TeleportCheckState.WAIT_BEFORE_START;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 500);
                break;
            case WAIT_BEFORE_START:
                MacroHandler.getInstance().pauseMacro();
                if (rotationBeforeReacting == null)
                    rotationBeforeReacting = new Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
                if (positionBeforeReacting == null)
                    positionBeforeReacting = mc.thePlayer.getPosition();
                MovRecPlayer.setYawDifference(AngleUtils.getClosest(rotationBeforeReacting.getYaw()));
                teleportCheckState = TeleportCheckState.LOOK_AROUND;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 500);
                break;
            case LOOK_AROUND:
                MovRecPlayer.getInstance().playRandomRecording("TELEPORT_CHECK_Start_");
                teleportCheckState = TeleportCheckState.WAIT_BEFORE_SENDING_MESSAGE_1;
                FailsafeManager.getInstance().scheduleRandomDelay(2000, 3000);
                break;
            case WAIT_BEFORE_SENDING_MESSAGE_1:
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                if (!FarmHelperConfig.sendFailsafeMessage) {
                    teleportCheckState = TeleportCheckState.ROTATE_TO_POS_BEFORE;
                    FailsafeManager.getInstance().scheduleRandomDelay(300, 600);
                    break;
                }
                if (!CustomFailsafeMessagesPage.customJacobMessages.isEmpty()
                        && GameStateHandler.getInstance().inJacobContest()
                        && Math.random() > CustomFailsafeMessagesPage.customJacobChance / 100.0) {
                    String[] customJacobMessages = CustomFailsafeMessagesPage.customJacobMessages.split("\\|");
                    randomMessage = FailsafeManager.getRandomMessage(customJacobMessages);
                } else if (CustomFailsafeMessagesPage.customTeleportationMessages.isEmpty()) {
                    randomMessage = FailsafeManager.getRandomMessage();
                } else {
                    String[] customMessages = CustomFailsafeMessagesPage.customTeleportationMessages.split("\\|");
                    randomMessage = FailsafeManager.getRandomMessage(customMessages);
                }
                teleportCheckState = TeleportCheckState.SEND_MESSAGE;
                FailsafeManager.getInstance().scheduleRandomDelay(2000, 3000);
                break;
            case SEND_MESSAGE:
                LogUtils.sendDebug("[Failsafe] Chosen message: " + randomMessage);
                mc.thePlayer.sendChatMessage("/ac " + randomMessage);
                teleportCheckState = TeleportCheckState.ROTATE_TO_POS_BEFORE;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case ROTATE_TO_POS_BEFORE:
                FailsafeManager.getInstance().rotation.easeTo(new RotationConfiguration(new Rotation((float) (rotationBeforeReacting.getYaw() + (Math.random() * 30 - 15)), (float) (Math.random() * 30 + 30)),
                        500, null));
                teleportCheckState = TeleportCheckState.LOOK_AROUND_2;
                FailsafeManager.getInstance().swapItemDuringRecording = Math.random() < 0.2;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case LOOK_AROUND_2:
                if (rotation.isRotating())
                    break;
                if (Math.random() < 0.2) {
                    if (Math.random() > 0.4)
                        teleportCheckState = TeleportCheckState.WAIT_BEFORE_SENDING_MESSAGE_2;
                    else
                        teleportCheckState = TeleportCheckState.GO_BACK_START;
                    FailsafeManager.getInstance().scheduleRandomDelay(2000, 3000);
                    break;
                } else if (mc.thePlayer.getActivePotionEffects() != null
                        && mc.thePlayer.getActivePotionEffects().stream().anyMatch(potionEffect -> potionEffect.getPotionID() == 8)
                        && Math.random() < 0.2) {
                    MovRecPlayer.getInstance().playRandomRecording("TELEPORT_CHECK_JumpBoost_");
                } else if (mc.thePlayer.capabilities.allowFlying && BlockUtils.isAboveHeadClear() && Math.random() < 0.3) {
                    MovRecPlayer.getInstance().playRandomRecording("TELEPORT_CHECK_Fly_");
                } else {
                    MovRecPlayer.getInstance().playRandomRecording("TELEPORT_CHECK_OnGround_");
                }
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case WAIT_BEFORE_SENDING_MESSAGE_2:
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                if (!FarmHelperConfig.sendFailsafeMessage) {
                    teleportCheckState = TeleportCheckState.GO_BACK_START;
                    FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                    break;
                }
                if (CustomFailsafeMessagesPage.customContinueMessages.isEmpty()) {
                    randomContinueMessage = FailsafeManager.getRandomContinueMessage();
                } else {
                    String[] customContinueMessages = CustomFailsafeMessagesPage.customContinueMessages.split("\\|");
                    randomContinueMessage = FailsafeManager.getRandomMessage(customContinueMessages);
                }
                teleportCheckState = TeleportCheckState.SEND_MESSAGE_2;
                FailsafeManager.getInstance().scheduleRandomDelay(3500, 2500);
                break;
            case SEND_MESSAGE_2:
                LogUtils.sendDebug("[Failsafe] Chosen message: " + randomContinueMessage);
                mc.thePlayer.sendChatMessage("/ac " + randomContinueMessage);
                teleportCheckState = TeleportCheckState.GO_BACK_START;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case GO_BACK_START:
                if (MovRecPlayer.getInstance().isRunning())
                    break;
                if (FailsafeManager.getInstance().swapItemDuringRecording)
                    FailsafeManager.getInstance().swapItemDuringRecording = false;
                if (mc.thePlayer.getPosition().distanceSq(positionBeforeReacting) < 2) {
                    teleportCheckState = TeleportCheckState.ROTATE_TO_POS_BEFORE_2;
                    break;
                }
                if (mc.thePlayer.getPosition().distanceSq(positionBeforeReacting) < 10) {
                    BaritoneHandler.walkToBlockPos(positionBeforeReacting);
                    teleportCheckState = TeleportCheckState.GO_BACK_END;
                } else {
                    teleportCheckState = TeleportCheckState.WARP_GARDEN;
                }
                break;
            case GO_BACK_END:
                if (BaritoneHandler.hasFailed() || !BaritoneHandler.isWalkingToGoalBlock()) {
                    teleportCheckState = TeleportCheckState.ROTATE_TO_POS_BEFORE_2;
                    FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                    break;
                }
                LogUtils.sendDebug("Distance difference: " + mc.thePlayer.getPosition().distanceSq(positionBeforeReacting));
                FailsafeManager.getInstance().scheduleDelay(200);
                break;
            case ROTATE_TO_POS_BEFORE_2:
                if (rotation.isRotating()) break;
                FailsafeManager.getInstance().rotation.easeTo(new RotationConfiguration(new Rotation((float) (rotationBeforeReacting.getYaw() + (Math.random() * 30 - 15)), (float) (Math.random() * 30 + 30)),
                        500, null));
                teleportCheckState = TeleportCheckState.END;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case WARP_GARDEN:
                if (mc.thePlayer.getPosition().distanceSq(new BlockPos(PlayerUtils.getSpawnLocation())) < 3) {
                    teleportCheckState = TeleportCheckState.ROTATE_TO_POS_BEFORE_2;
                    break;
                }
                MacroHandler.getInstance().triggerWarpGarden(true, true);
                FailsafeManager.getInstance().scheduleRandomDelay(3000, 1000);
                break;
            case END:
                this.endOfFailsafeTrigger();
                break;
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
