package com.personal.timetravel;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TimeTravelMod.MOD_ID)
public class TimeTravelMod {
    public static final String MOD_ID = "timetravel";

    public TimeTravelMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(TimeTravelController.class);
        Settings.load();
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(TimeTravelNet::register);
    }
}
