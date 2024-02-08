package com.jelly.farmhelperv2.failsafe.impl;

import com.jelly.farmhelperv2.config.FarmHelperConfig;
import com.jelly.farmhelperv2.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv2.failsafe.Failsafe;
import com.jelly.farmhelperv2.failsafe.FailsafeManager;
import com.jelly.farmhelperv2.feature.impl.ProfitCalculator;
import com.jelly.farmhelperv2.handler.GameStateHandler;
import com.jelly.farmhelperv2.handler.MacroHandler;
import com.jelly.farmhelperv2.handler.RotationHandler;
import com.jelly.farmhelperv2.util.KeyBindUtils;
import com.jelly.farmhelperv2.util.helper.FifoQueue;
import com.jelly.farmhelperv2.util.helper.Rotation;
import com.jelly.farmhelperv2.util.helper.RotationConfiguration;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class LowerAvgBpsFailsafe extends Failsafe {
    private static LowerAvgBpsFailsafe instance;

    public static LowerAvgBpsFailsafe getInstance() {
        if (instance == null) {
            instance = new LowerAvgBpsFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 9;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.LOWER_AVERAGE_BPS;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnLowerAverageBPS;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnLowerAverageBPS;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnLowerAverageBPS;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnLowerAverageBPS;
    }

    @Override
    public void onTickDetection(TickEvent.ClientTickEvent event) {
        if (MacroHandler.getInstance().isCurrentMacroPaused()) {
            bpsQueue.clear();
            lastTimeCheckedBPS = System.currentTimeMillis();
            return;
        }
        if (!FarmHelperConfig.averageBPSDropCheck) return;

        if (System.currentTimeMillis() - lastTimeCheckedBPS < 1_000) return;
        lastTimeCheckedBPS = System.currentTimeMillis();
        float bps = ProfitCalculator.getInstance().getBPSFloat();
        bpsQueue.add(bps);
        if (!bpsQueue.isAtFullCapacity()) return;
        float averageBPS = getAverageBPS();
        if (averageBPS > bps) {
            float percentage = (averageBPS - bps) / averageBPS * 100;
            if (percentage > FarmHelperConfig.averageBPSDrop) {
                FailsafeManager.getInstance().possibleDetection(this);
            }
        }
    }

    @Override
    public void duringFailsafeTrigger() {

    }

    @Override
    public void endOfFailsafeTrigger() {
    }

    @Override
    public void resetStates() {
        lowerBPSState = LowerBPSState.NONE;
        bpsQueue.clear();
    }

    public float getAverageBPS() {
        float averageBPS = 0;
        for (float bpsValue : bpsQueue) {
            averageBPS += bpsValue;
        }
        averageBPS /= bpsQueue.size();
        return averageBPS;
    }

    enum LowerBPSState {
        NONE,
        WAIT_BEFORE_START,
        WARP_BACK,
        END
    }

    private LowerBPSState lowerBPSState = LowerBPSState.NONE;
    private final RotationHandler rotation = RotationHandler.getInstance();
    private final FifoQueue<Float> bpsQueue = new FifoQueue<>(20);
    private long lastTimeCheckedBPS = System.currentTimeMillis();
}
