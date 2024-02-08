package com.jelly.farmhelperv2.failsafe.impl;

import com.jelly.farmhelperv2.config.FarmHelperConfig;
import com.jelly.farmhelperv2.config.page.CustomFailsafeMessagesPage;
import com.jelly.farmhelperv2.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv2.event.ReceivePacketEvent;
import com.jelly.farmhelperv2.failsafe.Failsafe;
import com.jelly.farmhelperv2.failsafe.FailsafeManager;
import com.jelly.farmhelperv2.feature.impl.AntiStuck;
import com.jelly.farmhelperv2.feature.impl.MovRecPlayer;
import com.jelly.farmhelperv2.handler.BaritoneHandler;
import com.jelly.farmhelperv2.handler.GameStateHandler;
import com.jelly.farmhelperv2.handler.MacroHandler;
import com.jelly.farmhelperv2.handler.RotationHandler;
import com.jelly.farmhelperv2.util.AngleUtils;
import com.jelly.farmhelperv2.util.LogUtils;
import com.jelly.farmhelperv2.util.TickRate;
import com.jelly.farmhelperv2.util.helper.Rotation;
import com.jelly.farmhelperv2.util.helper.RotationConfiguration;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;

public class RotationFailsafe extends Failsafe {
    private static RotationFailsafe instance;

    public static RotationFailsafe getInstance() {
        if (instance == null) {
            instance = new RotationFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 4;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.ROTATION_CHECK;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnRotationFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnRotationFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnRotationFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnRotationFailsafe;
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
            LogUtils.sendDebug("[Failsafe] Rotation packet received while AntiStuck is running. Ignoring");
            return;
        }

        S08PacketPlayerPosLook packet = (S08PacketPlayerPosLook) event.packet;
        double packetYaw = packet.getYaw();
        double packetPitch = packet.getPitch();
        double playerYaw = mc.thePlayer.rotationYaw;
        double playerPitch = mc.thePlayer.rotationPitch;
        double yawDiff = Math.abs(packetYaw - playerYaw);
        double pitchDiff = Math.abs(packetPitch - playerPitch);
        double threshold = FarmHelperConfig.rotationCheckSensitivity;
        if (yawDiff == 360 && pitchDiff == 0) // prevents false checks
            return;
        if (yawDiff >= threshold || pitchDiff >= threshold) {
            rotationBeforeReacting = new Rotation((float) playerYaw, (float) playerPitch);
            LogUtils.sendDebug("[Failsafe] Rotation detected! Yaw diff: " + yawDiff + ", Pitch diff: " + pitchDiff);
            FailsafeManager.getInstance().possibleDetection(this);
        }
    }

    @Override
    public void duringFailsafeTrigger() {

    }

    @Override
    public void endOfFailsafeTrigger() {
        FailsafeManager.getInstance().stopFailsafes();
        if (mc.thePlayer.getPosition().getY() < 100 && GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.GARDEN)
            FailsafeManager.getInstance().restartMacroAfterDelay();
    }

    @Override
    public void resetStates() {
        rotationCheckState = RotationCheckState.NONE;
        rotationBeforeReacting = null;
        positionBeforeReacting = null;
        randomMessage = null;
        randomContinueMessage = null;
        rotation.reset();
    }

    private RotationCheckState rotationCheckState = RotationCheckState.NONE;
    private final RotationHandler rotation = RotationHandler.getInstance();
    private BlockPos positionBeforeReacting = null;
    private Rotation rotationBeforeReacting = null;
    String randomMessage;
    String randomContinueMessage;

    enum RotationCheckState {
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
    }
}