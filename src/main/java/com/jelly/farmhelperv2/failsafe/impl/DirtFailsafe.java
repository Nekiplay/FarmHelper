package com.jelly.farmhelperv2.failsafe.impl;

import baritone.api.pathing.goals.GoalBlock;
import com.jelly.farmhelperv2.config.FarmHelperConfig;
import com.jelly.farmhelperv2.config.page.CustomFailsafeMessagesPage;
import com.jelly.farmhelperv2.config.page.FailsafeNotificationsPage;
import com.jelly.farmhelperv2.event.BlockChangeEvent;
import com.jelly.farmhelperv2.failsafe.Failsafe;
import com.jelly.farmhelperv2.failsafe.FailsafeManager;
import com.jelly.farmhelperv2.feature.impl.MovRecPlayer;
import com.jelly.farmhelperv2.handler.BaritoneHandler;
import com.jelly.farmhelperv2.handler.GameStateHandler;
import com.jelly.farmhelperv2.handler.MacroHandler;
import com.jelly.farmhelperv2.util.*;
import com.jelly.farmhelperv2.util.helper.FlyPathfinder;
import com.jelly.farmhelperv2.util.helper.Rotation;
import com.jelly.farmhelperv2.util.helper.RotationConfiguration;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.util.Vec3;

import java.util.ArrayList;

public class DirtFailsafe extends Failsafe {
    private static DirtFailsafe instance;

    public static DirtFailsafe getInstance() {
        if (instance == null) {
            instance = new DirtFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 3;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.DIRT_CHECK;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnDirtFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnDirtFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnDirtFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnDirtFailsafe;
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (FailsafeManager.getInstance().firstCheckReturn()) return;
        if (event.update.getBlock().equals(Blocks.air) && !CropUtils.isCrop(event.old.getBlock())) {
            LogUtils.sendDebug("[Failsafe] Block destroyed: " + event.pos);
            blocksDestroyedByPlayer.add(new Tuple<>(event.pos, System.currentTimeMillis()));
        }
        blocksDestroyedByPlayer.removeIf(tuple -> System.currentTimeMillis() - tuple.getSecond() > 2000);
        if ((event.old.getBlock().equals(Blocks.air) || CropUtils.isCrop(event.old.getBlock()) || event.old.getBlock().equals(Blocks.water) || event.old.getBlock().equals(Blocks.flowing_water)) &&
                event.update.getBlock() != null && !event.update.getBlock().equals(Blocks.air) &&
                !CropUtils.isCrop(event.update.getBlock()) && event.update.getBlock().isCollidable() &&
                !event.update.getBlock().equals(Blocks.trapdoor) && !event.update.getBlock().equals(Blocks.ladder) &&
                !event.update.getBlock().equals(Blocks.water) && !event.update.getBlock().equals(Blocks.flowing_water) &&
                event.update.getBlock().isFullCube()) { // If old block was air or crop and new block is not air, crop, trapdoor, water or flowing water
            if (blocksDestroyedByPlayer.stream().anyMatch(tuple -> tuple.getFirst().equals(event.pos))) {
                LogUtils.sendDebug("[Failsafe] Block destroyed by player and resynced by hypixel: " + event.pos);
                return;
            }
            LogUtils.sendWarning("[Failsafe] Someone put a block on your garden! Block: " + event.update.getBlock() + " Pos: " + event.pos);
            dirtBlocks.add(new Tuple<>(event.pos, System.currentTimeMillis()));
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
        blocksDestroyedByPlayer.clear();
        dirtBlocks.clear();
        dirtCheckState = DirtCheckState.NONE;
        positionBeforeReacting = null;
        rotationBeforeReacting = null;
        randomMessage = null;
        dirtOnLeft = false;
        maxReactions = 3;
    }

    public boolean isTouchingDirtBlock() {
        for (Tuple<BlockPos, Long> tuple : dirtBlocks) {
            BlockPos dirtBlock = tuple.getFirst();
            double distance = Math.sqrt(mc.thePlayer.getPositionEyes(1).distanceTo(new Vec3(dirtBlock.getX() + 0.5, dirtBlock.getY() + 0.5, dirtBlock.getZ() + 0.5)));
            LogUtils.sendDebug(distance + " " + dirtBlock);
            if (distance <= 1.5) {
                return true;
            }
        }
        return false;
    }

    public boolean hasDirtBlocks() {
        return !dirtBlocks.isEmpty();
    }

    private final ArrayList<Tuple<BlockPos, Long>> dirtBlocks = new ArrayList<>();
    private final ArrayList<Tuple<BlockPos, Long>> blocksDestroyedByPlayer = new ArrayList<>();

    private BlockPos positionBeforeReacting = null;
    private Rotation rotationBeforeReacting = null;
    private boolean dirtOnLeft = false;
    private int maxReactions = 3;
    private DirtCheckState dirtCheckState = DirtCheckState.NONE;
    String randomMessage;

    enum DirtCheckState {
        NONE,
        WAIT_BEFORE_START,
        PLAY_RECORDING,
        WAIT_BEFORE_SENDING_MESSAGE,
        SEND_MESSAGE,
        KEEP_PLAYING,
        GO_BACK_END,
        GO_BACK_START,
        ROTATE_TO_POS_BEFORE,
        END_DIRT_CHECK
    }
}
