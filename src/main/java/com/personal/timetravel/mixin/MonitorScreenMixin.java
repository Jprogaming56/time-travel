package com.personal.timetravel.mixin;

import com.personal.timetravel.client.TimeRow;
import dev.amble.ait.client.screens.MonitorScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Adds a "land at" time row under AIT's Monitor screen. */
@Mixin(value = MonitorScreen.class, remap = false)
public abstract class MonitorScreenMixin extends Screen {
    @Shadow int backgroundHeight;

    protected MonitorScreenMixin(Component title) { super(title); }

    // m_7856_ is Screen#init in Forge's runtime (SRG) names, which is what AIT's jar uses.
    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void timetravel$addTimeRow(CallbackInfo ci) {
        UUID id = ((MonitorScreen) (Object) this).tardis().getUuid();
        int cx = this.width / 2;
        int y = (this.height - this.backgroundHeight) / 2 + this.backgroundHeight + 6;

        Button label = Button.builder(TimeRow.labelText(id), b -> { }).bounds(cx - 50, y, 100, 20).build();
        label.active = false;

        Button minus = Button.builder(Component.literal("-"), b -> {
            TimeRow.step(id, -1);
            label.setMessage(TimeRow.labelText(id));
        }).bounds(cx - 76, y, 22, 20).build();

        Button plus = Button.builder(Component.literal("+"), b -> {
            TimeRow.step(id, +1);
            label.setMessage(TimeRow.labelText(id));
        }).bounds(cx + 54, y, 22, 20).build();

        Button off = Button.builder(Component.literal("Off"), b -> {
            TimeRow.off(id);
            label.setMessage(TimeRow.labelText(id));
        }).bounds(cx + 80, y, 30, 20).build();

        this.addRenderableWidget(label);
        this.addRenderableWidget(minus);
        this.addRenderableWidget(plus);
        this.addRenderableWidget(off);
    }
}
