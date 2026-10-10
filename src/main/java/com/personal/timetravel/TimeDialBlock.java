package com.personal.timetravel;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

/**
 * Put it inside your TARDIS.
 *   Right-click:          pick the next time of day (the block changes colour to match).
 *   Sneak + right-click:  take off, race the sky to that time, and land again.
 */
public class TimeDialBlock extends Block {
    public static final IntegerProperty TIME = IntegerProperty.create("time", 0, TimeUtil.PRESET_NAMES.length - 1);
    private static final Logger LOGGER = LogUtils.getLogger();

    public TimeDialBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TIME, 2));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIME);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        int setting = state.getValue(TIME);
        int target = TimeUtil.presetTicks(setting);
        try {
            if (player.isShiftKeyDown()) {
                if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
                    String problem = TimeTravelManager.start(server, sp, target);
                    if (problem != null) {
                        sp.displayClientMessage(Component.literal(problem), true);
                    }
                }
            } else {
                int next = (setting + 1) % TimeUtil.PRESET_NAMES.length;
                level.setBlock(pos, state.setValue(TIME, next), 3);
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0f, 0.8f + next * 0.06f);
                player.displayClientMessage(Component.literal(
                        "Time Dial: " + TimeUtil.describe(TimeUtil.presetTicks(next)) + "  (sneak + right-click to go)"), true);
            }
        } catch (Exception e) {
            LOGGER.error("Time Dial failed", e);
            player.displayClientMessage(Component.literal("The Time Dial couldn't reach the TARDIS (see the log)."), true);
        }
        return InteractionResult.CONSUME;
    }
}
