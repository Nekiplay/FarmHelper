package com.jelly.farmhelperv2.feature;

import com.jelly.farmhelperv2.failsafe.FailsafeManager;
import com.jelly.farmhelperv2.feature.impl.*;
import com.jelly.farmhelperv2.util.LogUtils;
import com.jelly.farmhelperv2.util.TickRate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FeatureManager {
    private static FeatureManager instance;
    private final ArrayList<IFeature> features = new ArrayList<>();

    public static FeatureManager getInstance() {
        if (instance == null) {
            instance = new FeatureManager();
        }
        return instance;
    }

    public List<IFeature> fillFeatures() {
        List<IFeature> featuresList = Arrays.asList(
                AutoBazaar.getInstance(),
                AntiStuck.getInstance(),
                AutoCookie.getInstance(),
                AutoGodPot.getInstance(),
                AutoReconnect.getInstance(),
                AutoPestHunter.getInstance(),
                AutoRepellent.getInstance(),
                AutoSell.getInstance(),
                DesyncChecker.getInstance(),
                Freelook.getInstance(),
                LagDetector.getInstance(),
                LeaveTimer.getInstance(),
                PerformanceMode.getInstance(),
                PestsDestroyer.getInstance(),
                AutoSprayonator.getInstance(),
                PetSwapper.getInstance(),
                PlotCleaningHelper.getInstance(),
                ProfitCalculator.getInstance(),
                Scheduler.getInstance(),
                UngrabMouse.getInstance(),
                VisitorsMacro.getInstance()
        );
        features.addAll(featuresList);
        return features;
    }

    public boolean shouldPauseMacroExecution() {
        return features.stream().anyMatch(feature -> {
            if (feature.isRunning()) {
                return feature.shouldPauseMacroExecution();
            }
            return false;
        });
    }

    public void disableAll() {
        features.forEach(feature -> {
            if (feature.isToggled() && feature.isRunning()) {
                feature.stop();
                LogUtils.sendDebug("Disabled feature: " + feature.getName());
            }
        });
    }

    public void disableAllExcept(IFeature... sender) {
        features.forEach(feature -> {
            if (feature.isToggled() && feature.isRunning() && !Arrays.asList(sender).contains(feature)) {
                feature.stop();
                LogUtils.sendDebug("Disabled feature: " + feature.getName());
            }
        });
    }

    public void resetAllStates() {
        features.forEach(IFeature::resetStatesAfterMacroDisabled);
    }

    public boolean isAnyOtherFeatureEnabled(IFeature... sender) {
        return features.stream().anyMatch(feature -> feature.shouldPauseMacroExecution() && feature.isRunning() && !Arrays.asList(sender).contains(feature));
    }

    public boolean shouldIgnoreFalseCheck() {
        if (AutoCookie.getInstance().isRunning() && !AutoCookie.getInstance().shouldCheckForFailsafes()) {
            return true;
        }
        if (AutoGodPot.getInstance().isRunning() && !AutoGodPot.getInstance().shouldCheckForFailsafes()) {
            return true;
        }
        if (AutoReconnect.getInstance().isRunning() && !AutoReconnect.getInstance().shouldCheckForFailsafes()) {
            return true;
        }
        if (PestsDestroyer.getInstance().isRunning()) {
            if (!PestsDestroyer.getInstance().shouldCheckForFailsafes()) return true;
            AtomicBoolean result = new AtomicBoolean(false);
            FailsafeManager.getInstance().triggeredFailsafe.ifPresent(failsafe -> {
                if (failsafe.getType().equals(FailsafeManager.EmergencyType.KNOCKBACK_CHECK)) {
                    result.set(true);
                    return;
                }
                if (failsafe.getType().equals(FailsafeManager.EmergencyType.TELEPORT_CHECK)) {
                    result.set(true);
                    return;
                }
                if (failsafe.getType().equals(FailsafeManager.EmergencyType.ROTATION_CHECK)) {
                    result.set(true);
                }
            });
            return result.get();
        }
        if (PlotCleaningHelper.getInstance().isRunning() && !PlotCleaningHelper.getInstance().shouldCheckForFailsafes()) {
            return true;
        }
        if (VisitorsMacro.getInstance().isRunning() && !VisitorsMacro.getInstance().shouldCheckForFailsafes()) {
            return true;
        }
        if (AutoPestHunter.getInstance().isRunning() && !AutoPestHunter.getInstance().shouldCheckForFailsafes()) {
            return true;
        }
        return false;
    }

    public void enableAll() {
        features.forEach(feature -> {
            if (feature.shouldStartAtMacroStart() && feature.isToggled()) {
                feature.start();
                LogUtils.sendDebug("Enabled feature: " + feature.getName());
            }
        });
    }

    public void disableCurrentlyRunning(IFeature sender) {
        features.forEach(feature -> {
            if (feature.isRunning() && feature != sender) {
                feature.stop();
                LogUtils.sendDebug("Disabled feature: " + feature.getName());
            }
        });
    }

    public List<IFeature> getCurrentRunningFeatures() {
        List<IFeature> runningFeatures = new ArrayList<>();
        features.forEach(feature -> {
            if (feature.isRunning() && !feature.shouldStartAtMacroStart()) {
                runningFeatures.add(feature);
            }
        });
        return runningFeatures;
    }
}
