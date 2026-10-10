package com.personal.timetravel;

import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Turns one of the console's buttons (config: consoleButton, default Protocol 813) into the time travel button.
 *   right-click: next time of day      left-click: previous time of day      sneak + right-click: go
 *
 * Works by catching clicks on AiT's console control entity before AiT sees them, so nothing inside AiT is changed.
 * If anything goes wrong the click is passed on and the button does its normal job.
 */
@Mod.EventBusSubscriber(modid = TimeTravelMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConsoleButton {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ConsoleButton() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (handle(player, event.getTarget(), false)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClick(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (handle(player, event.getTarget(), true)) {
            event.setCanceled(true);
        }
    }

    /** Returns true if this click was on the time travel button and we handled it (so AiT must not). */
    private static boolean handle(ServerPlayer player, Entity target, boolean leftClick) {
        try {
            String wanted = TimeConfig.consoleButton();
            if (wanted.isEmpty()) return false;
            if (!target.getClass().getName().startsWith("dev.amble.ait")) return false;
            if (!(target.level() instanceof ServerLevel level)) return false;
            if (!AitBridge.isControlEntity(target, wanted)) return false;

            UUID id = AitBridge.tardisIdOf(level);
            if (id == null) return false;
            if (aitNeedsThisClick(player)) return false;

            MinecraftServer server = level.getServer();
            TimeData data = TimeData.get(server);
            int count = TimeUtil.PRESET_NAMES.length;
            int current = data.get(id);

            if (!leftClick && player.isShiftKeyDown()) {
                String problem = TimeTravelManager.start(level, player, TimeUtil.presetTicks(current));
                if (problem != null) {
                    player.displayClientMessage(Component.literal(problem), true);
                } else {
                    level.playSound(null, target.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0f, 1.4f);
                }
                return true;
            }

            int next = Math.floorMod(current + (leftClick ? -1 : 1), count);
            data.set(id, next);
            level.playSound(null, target.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0f, 0.8f + next * 0.06f);
            player.displayClientMessage(Component.literal(
                    "Time travel: " + TimeUtil.describe(TimeUtil.presetTicks(next)) + "  (sneak + right-click to go)"), true);
            return true;
        } catch (Throwable t) {
            LOGGER.error("The time travel button failed (the button's normal action will run instead)", t);
            return false;
        }
    }

    /** Clicks AiT uses for its own tools and editing; those must never be swallowed. */
    private static boolean aitNeedsThisClick(ServerPlayer player) {
        if ("minecraft:command_block".equals(itemKey(player.getOffhandItem()))) return true; // control editor
        String main = itemKey(player.getMainHandItem());
        return main.equals("ait:repair_tool") || main.equals("ait:redstone_control_block");
    }

    private static String itemKey(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }
}
