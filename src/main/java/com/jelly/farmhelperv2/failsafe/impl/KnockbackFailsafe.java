package com.jelly.farmhelperv2.failsafe.impl;

import com.jelly.farmhelperv2.config.FarmHelperConfig;
import com.jelly.farmhelperv2.config.page.CustomFailsafeMessagesPage;
import com.jelly.farmhelperv2.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv2.event.ReceivePacketEvent;
import com.jelly.farmhelperv2.failsafe.Failsafe;
import com.jelly.farmhelperv2.failsafe.FailsafeManager;
import com.jelly.farmhelperv2.feature.impl.MovRecPlayer;
import com.jelly.farmhelperv2.handler.BaritoneHandler;
import com.jelly.farmhelperv2.handler.GameStateHandler;
import com.jelly.farmhelperv2.handler.MacroHandler;
import com.jelly.farmhelperv2.handler.RotationHandler;
import com.jelly.farmhelperv2.util.AngleUtils;
import com.jelly.farmhelperv2.util.BlockUtils;
import com.jelly.farmhelperv2.util.LogUtils;
import com.jelly.farmhelperv2.util.PlayerUtils;
import com.jelly.farmhelperv2.util.helper.Rotation;
import com.jelly.farmhelperv2.util.helper.RotationConfiguration;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;

public class KnockbackFailsafe extends Failsafe {
    private static KnockbackFailsafe instance;

    public static KnockbackFailsafe getInstance() {
        if (instance == null) {
            instance = new KnockbackFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 4;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.KNOCKBACK_CHECK;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnKnockbackFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnKnockbackFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnKnockbackFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnKnockbackFailsafe;
    }

    @Override
    public void onReceivedPacketDetection(ReceivePacketEvent event) {
        if (MacroHandler.getInstance().isTeleporting())
            return;
        if (!(event.packet instanceof S12PacketEntityVelocity)) {
            return;
        }
        if (((S12PacketEntityVelocity) event.packet).getEntityID() != mc.thePlayer.getEntityId())
            return;
        if (((S12PacketEntityVelocity) event.packet).getMotionY() < FarmHelperConfig.knockbackCheckVerticalSensitivity)
            return;

        rotationBeforeReacting = new Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        positionBeforeReacting = mc.thePlayer.getPosition();
        LogUtils.sendWarning("[Failsafe] Knockback detected! MotionY: " + ((S12PacketEntityVelocity) event.packet).getMotionY());

        FailsafeManager.getInstance().possibleDetection(this);
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
        knockbackCheckState = KnockbackCheckState.NONE;
        rotationBeforeReacting = null;
        positionBeforeReacting = null;
        randomMessage = null;
        randomContinueMessage = null;
        rotation.reset();
    }

    private KnockbackCheckState knockbackCheckState = KnockbackCheckState.NONE;
    private final RotationHandler rotation = RotationHandler.getInstance();
    private BlockPos positionBeforeReacting = null;
    private Rotation rotationBeforeReacting = null;
    String randomMessage;
    String randomContinueMessage;

    enum KnockbackCheckState {
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