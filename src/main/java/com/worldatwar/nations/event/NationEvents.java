package com.worldatwar.nations.event;

import com.worldatwar.nations.WorldAtWarNations;
import com.worldatwar.nations.data.FactionKind;
import com.worldatwar.nations.data.NationSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldAtWarNations.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NationEvents {
    private static final long SIMULATION_INTERVAL_TICKS = 1200L;

    private NationEvents() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        FactionKind kind;
        if (entity instanceof Villager) {
            kind = FactionKind.VILLAGER;
        } else if (entity instanceof AbstractIllager) {
            kind = FactionKind.ILLAGER;
        } else {
            return;
        }
        NationSavedData.get(level.getServer()).registerCitizen(
                level, kind, entity.getUUID(), entity.blockPosition().getX(), entity.blockPosition().getZ());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!(entity instanceof Villager) && !(entity instanceof AbstractIllager)) return;
        NationSavedData.get(level.getServer()).removeCitizen(entity.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime > 0 && gameTime % SIMULATION_INTERVAL_TICKS == 0) {
            NationSavedData.get(event.getServer()).simulateAll();
        }
    }
}
