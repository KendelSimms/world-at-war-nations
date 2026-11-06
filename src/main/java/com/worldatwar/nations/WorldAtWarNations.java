package com.worldatwar.nations;

import com.worldatwar.nations.command.NationCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(WorldAtWarNations.MOD_ID)
public final class WorldAtWarNations {
    public static final String MOD_ID = "worldatwarnation";

    public WorldAtWarNations() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        NationCommands.register(event.getDispatcher());
    }
}
