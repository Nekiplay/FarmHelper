package com.jelly.farmhelperv2.failsafe.impl;

import cc.polyfrost.oneconfig.utils.Multithreading;
import com.jelly.farmhelperv2.config.FarmHelperConfig;
import com.jelly.farmhelperv2.config.page.CustomFailsafeMessagesPage;
import com.jelly.farmhelperv2.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv2.event.ReceivePacketEvent;
import com.jelly.farmhelperv2.failsafe.Failsafe;
import com.jelly.farmhelperv2.failsafe.FailsafeManager;
import com.jelly.farmhelperv2.feature.impl.MovRecPlayer;
import com.jelly.farmhelperv2.handler.GameStateHandler;
import com.jelly.farmhelperv2.handler.MacroHandler;
import com.jelly.farmhelperv2.handler.RotationHandler;
import com.jelly.farmhelperv2.util.AngleUtils;
import com.jelly.farmhelperv2.util.BlockUtils;
import com.jelly.farmhelperv2.util.LogUtils;
import com.jelly.farmhelperv2.util.PlayerUtils;
import com.jelly.farmhelperv2.util.helper.Rotation;
import com.jelly.farmhelperv2.util.helper.RotationConfiguration;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;

import java.util.concurrent.TimeUnit;

public class BedrockCageFailsafe extends Failsafe {
    private static BedrockCageFailsafe instance;

    public static BedrockCageFailsafe getInstance() {
        if (instance == null) {
            instance = new BedrockCageFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.BEDROCK_CAGE_CHECK;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnBedrockCageFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnBedrockCageFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnBedrockCageFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnBedrockCageFailsafe;
    }

    @Override
    public void onReceivedPacketDetection(ReceivePacketEvent event) {
        if (MacroHandler.getInstance().isTeleporting()) return;
        if (!(event.packet instanceof S08PacketPlayerPosLook)) {
            return;
        }
        if (mc.thePlayer.getPosition().getY() < 66) return;
        S08PacketPlayerPosLook packet = (S08PacketPlayerPosLook) event.packet;

        if (packet.getY() > 80) {
            rotationBeforeTeleporting = new Rotation(mc.thePlayer.prevRotationYaw, mc.thePlayer.prevRotationPitch);
            positionBeforeTeleporting = mc.thePlayer.getPosition();
            Multithreading.schedule(() -> {
                for (int i = 0; i < 5; i++) {
                    if (GameStateHandler.getInstance().getLocation() != GameStateHandler.Location.GARDEN) {
                        return;
                    }
                    if (BlockUtils.bedrockCount() > 3) {
                        FailsafeManager.getInstance().possibleDetection(this);
                        return;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                }
            }, 100, TimeUnit.MILLISECONDS);
            return;
        }
//        final double lastReceivedPacketDistance = currentPlayerPos.distanceTo(LagDetector.getInstance().getLastPacketPosition());
//        final double playerMovementSpeed = mc.thePlayer.getAttributeMap().getAttributeInstanceByName("generic.movementSpeed").getAttributeValue();
//        final int ticksSinceLastPacket = (int) Math.ceil(LagDetector.getInstance().getTimeSinceLastTick() / 50D);
//        final double estimatedMovement = playerMovementSpeed * ticksSinceLastPacket;
//        if (lastReceivedPacketDistance > 7.5D && Math.abs(lastReceivedPacketDistance - estimatedMovement) < FarmHelperConfig.teleportCheckLagSensitivity)
//            return;
        if (bedrockCageCheckState != BedrockCageCheckState.NONE
                && bedrockCageCheckState != BedrockCageCheckState.WAIT_BEFORE_START
                && bedrockCageCheckState != BedrockCageCheckState.END) {
            if (MovRecPlayer.getInstance().isRunning())
                MovRecPlayer.getInstance().stop();
            if (rotation.isRotating())
                rotation.reset();
            if (passedFailsafe)
                return;
            LogUtils.sendFailsafeMessage("[Failsafe] You've just passed the failsafe check for bedrock cage!", FailsafeNotificationsPage.tagEveryoneOnBedrockCageFailsafe);
            FailsafeManager.getInstance().scheduleRandomDelay(3000, 1000);
            if (mc.thePlayer.getPosition().distanceSq(positionBeforeTeleporting) < 7) {
                bedrockCageCheckState = BedrockCageCheckState.ROTATE_TO_POS_BEFORE;
                LogUtils.sendDebug("[Failsafe] Continuing soon. Distance difference: " + mc.thePlayer.getPosition().distanceSq(positionBeforeTeleporting));
            } else {
                bedrockCageCheckState = BedrockCageCheckState.WARP_GARDEN;
                LogUtils.sendDebug("[Failsafe] Too far away from the position before teleporting, warping to garden");
            }
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
        FailsafeManager.getInstance().restartMacroAfterDelay();
    }

    @Override
    public void resetStates() {
        bedrockCageCheckState = BedrockCageCheckState.NONE;
        rotationBeforeTeleporting = null;
        positionBeforeTeleporting = null;
        randomMessage = null;
        randomContinueMessage = null;
        bedrockOnLeft = false;
        passedFailsafe = false;
        rotation.reset();
    }

    private BedrockCageCheckState bedrockCageCheckState = BedrockCageCheckState.NONE;
    private final RotationHandler rotation = RotationHandler.getInstance();
    private Rotation rotationBeforeTeleporting = null;
    private BlockPos positionBeforeTeleporting = null;
    private boolean bedrockOnLeft = false;
    private boolean passedFailsafe = false;
    String randomMessage;
    String randomContinueMessage;

    enum BedrockCageCheckState {
        NONE,
        WAIT_BEFORE_START,
        LOOK_AROUND,
        WAIT_BEFORE_SENDING_MESSAGE_1,
        SEND_MESSAGE_1,
        LOOK_AROUND_2,
        WAIT_BEFORE_SENDING_MESSAGE_2,
        SEND_MESSAGE_2,
        WAIT_UNTIL_TP_BACK,
        LOOK_AROUND_3,
        ROTATE_TO_POS_BEFORE,
        WARP_GARDEN,
        END
    }
}
