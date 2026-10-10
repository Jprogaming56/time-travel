package com.personal.timetravel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

public final class TimeTravelNet {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TimeTravelMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private TimeTravelNet() { }

    public static void register() {
        CHANNEL.messageBuilder(SetTarget.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetTarget::encode).decoder(SetTarget::decode)
                .consumerMainThread(SetTarget::handle).add();
    }

    /** Client (Monitor screen) -> server: land this TARDIS at this day-tick, or -1 to switch it off. */
    public static final class SetTarget {
        public final UUID tardis;
        public final int tick;

        public SetTarget(UUID tardis, int tick) { this.tardis = tardis; this.tick = tick; }

        public void encode(FriendlyByteBuf buf) { buf.writeUUID(tardis); buf.writeVarInt(tick + 1); }

        public static SetTarget decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            return new SetTarget(id, buf.readVarInt() - 1);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            if (c.getSender() != null) TimeTravelController.setTarget(c.getSender(), tardis, tick);
            c.setPacketHandled(true);
        }
    }
}
