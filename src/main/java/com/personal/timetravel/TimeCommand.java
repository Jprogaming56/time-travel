package com.personal.timetravel;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /timetravel 18:30     (also 6pm, noon, sunset, midnight, t12000 ...)
 * Works from inside your TARDIS, for an exact time without the Time Dial.
 */
@Mod.EventBusSubscriber(modid = TimeTravelMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TimeCommand {
    private TimeCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("timetravel")
                .then(Commands.argument("time", StringArgumentType.greedyString())
                        .executes(TimeCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Only a player inside a TARDIS can time travel."));
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "time");
        int target = TimeUtil.parse(text);
        if (target < 0) {
            ctx.getSource().sendFailure(Component.literal(
                    "Couldn't read \"" + text + "\" as a time. Try 18:30, 6pm, noon, sunset, midnight or t12000."));
            return 0;
        }
        try {
            String problem = TimeTravelManager.start(player.serverLevel(), player, target);
            if (problem != null) {
                ctx.getSource().sendFailure(Component.literal(problem));
                return 0;
            }
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Time travel failed (see the log)."));
            return 0;
        }
        return 1;
    }
}
