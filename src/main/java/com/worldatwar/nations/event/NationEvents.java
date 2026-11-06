package com.worldatwar.nations.event;

import com.worldatwar.nations.WorldAtWarNations;
import com.worldatwar.nations.data.FactionKind;
import com.worldatwar.nations.data.NationSavedData;
import com.worldatwar.nations.data.NationFaction;
import com.worldatwar.nations.data.NationJob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final long FARM_WORK_INTERVAL_TICKS = 200L;
    private static final int FARM_WORK_RADIUS = 4;
    private static final int LUMBER_WORK_RADIUS = 6;
    private static final int MINING_WORK_RADIUS = 4;

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
        if (gameTime > 0 && gameTime % FARM_WORK_INTERVAL_TICKS == 0) {
            NationSavedData data = NationSavedData.get(event.getServer());
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (Entity loaded : level.getAllEntities()) {
                    if (!(loaded instanceof Villager villager) || !villager.isAlive()) continue;
                    NationFaction faction = data.findCitizen(villager.getUUID()).orElse(null);
                    if (faction == null) continue;
                    NationJob job = faction.jobOf(villager.getUUID());
                    if (job == NationJob.CRAFTSMAN || job == NationJob.GENERAL_WORKER) {
                        constructSmallShelter(level, villager, faction, data);
                    }
                    if (job == NationJob.FARMER && !harvestNearbyCrop(level, villager, faction, data)) plantNearbyWheat(level, villager, faction, data);
                    else if (job == NationJob.LUMBERJACK) harvestNearbyLog(level, villager, faction, data);
                    else if (job == NationJob.MINER && !harvestNearbyIronOre(level, villager, faction, data)) harvestNearbyStone(level, villager, faction, data);
                }
            }
        }
    }

    /**
     * Builds one small timber shelter at a reserved build marker.
     * It only places blocks into a completely clear, supported footprint and never replaces terrain or structures.
     */
    private static void constructSmallShelter(ServerLevel level, Villager villager, NationFaction faction, NationSavedData data) {
        BlockPos base = villager.blockPosition();
        String dimension = level.dimension().location().toString();
        if (!data.isBuildWorksite(faction.id(), dimension, base)) return;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos ground = base.offset(dx, -1, dz);
                BlockPos lower = base.offset(dx, 0, dz);
                BlockPos upper = base.offset(dx, 1, dz);
                BlockPos roof = base.offset(dx, 2, dz);
                if (!level.hasChunkAt(ground) || !level.hasChunkAt(lower)
                        || !level.hasChunkAt(upper) || !level.hasChunkAt(roof)) return;
                if (level.getBlockState(ground).isAir()
                        || !level.getBlockState(lower).isAir()
                        || !level.getBlockState(upper).isAir()
                        || !level.getBlockState(roof).isAir()) return;
            }
        }

        if (!data.spendWoodForConstruction(faction.id(), 24.0)) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean perimeter = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                if (perimeter) {
                    BlockPos lower = base.offset(dx, 0, dz);
                    BlockPos upper = base.offset(dx, 1, dz);
                    // Leave a two-block doorway on the south-facing wall.
                    if (!(dz == 1 && dx == 0)) level.setBlock(lower, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    level.setBlock(upper, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }
                level.setBlock(base.offset(dx, 2, dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
    }

    /** A loaded lumberjack removes one nearby tree log only inside its faction's designated lumber worksite. */
    private static void harvestNearbyLog(ServerLevel level, Villager villager, NationFaction faction, NationSavedData data) {
        BlockPos origin = villager.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 3; dy++) {
            for (int dx = -LUMBER_WORK_RADIUS; dx <= LUMBER_WORK_RADIUS; dx++) {
                for (int dz = -LUMBER_WORK_RADIUS; dz <= LUMBER_WORK_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(BlockTags.LOGS) || !hasNearbyLeaves(level, cursor)
                            || !data.isLumberWorksite(faction.id(), level.dimension().location().toString(), cursor)) continue;
                    level.destroyBlock(cursor, false);
                    data.recordPhysicalWoodHarvest(villager.getUUID(), 1.0);
                    return;
                }
            }
        }
    }

    private static boolean hasNearbyLeaves(ServerLevel level, BlockPos logPos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    cursor.set(logPos.getX() + dx, logPos.getY() + dy, logPos.getZ() + dz);
                    if (level.getBlockState(cursor).is(BlockTags.LEAVES)) return true;
                }
            }
        }
        return false;
    }

    /**
     * A loaded miner removes at most one exposed iron ore block per work interval,
     * and only inside a designated mining worksite. No tunneling or wall removal.
     */
    private static boolean harvestNearbyIronOre(ServerLevel level, Villager villager, NationFaction faction, NationSavedData data) {
        BlockPos origin = villager.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -MINING_WORK_RADIUS; dx <= MINING_WORK_RADIUS; dx++) {
                for (int dz = -MINING_WORK_RADIUS; dz <= MINING_WORK_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(BlockTags.IRON_ORES)
                            || !data.isMiningWorksite(faction.id(), level.dimension().location().toString(), cursor)
                            || !hasAdjacentAir(level, cursor)) continue;
                    if (level.destroyBlock(cursor, true)) {
                        data.recordPhysicalIronMining(villager.getUUID(), 1.0);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** A miner may extract one exposed stone/deepslate block per interval inside a designated mine. */
    private static void harvestNearbyStone(ServerLevel level, Villager villager, NationFaction faction, NationSavedData data) {
        BlockPos origin = villager.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -MINING_WORK_RADIUS; dx <= MINING_WORK_RADIUS; dx++) {
                for (int dz = -MINING_WORK_RADIUS; dz <= MINING_WORK_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if ((state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE))
                            && data.isMiningWorksite(faction.id(), level.dimension().location().toString(), cursor)
                            && hasAdjacentAir(level, cursor) && level.destroyBlock(cursor, true)) {
                        data.recordPhysicalStoneMining(villager.getUUID(), 1.0);
                        return;
                    }
                }
            }
        }
    }

    private static boolean hasAdjacentAir(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.above()).isAir()
                || level.getBlockState(pos.below()).isAir()
                || level.getBlockState(pos.north()).isAir()
                || level.getBlockState(pos.south()).isAir()
                || level.getBlockState(pos.east()).isAir()
                || level.getBlockState(pos.west()).isAir();
    }

    /** A loaded farmer harvests and replants one mature crop near its current position. */
    private static boolean harvestNearbyCrop(ServerLevel level, Villager villager, NationFaction faction, NationSavedData data) {
        BlockPos origin = villager.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -FARM_WORK_RADIUS; dx <= FARM_WORK_RADIUS; dx++) {
                for (int dz = -FARM_WORK_RADIUS; dz <= FARM_WORK_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)
                            || !data.isFarmWorksite(faction.id(), level.dimension().location().toString(), cursor)) continue;
                    level.setBlock(cursor, crop.getStateForAge(0), 3);
                    data.recordPhysicalFoodHarvest(villager.getUUID(), 1.0);
                    return true;
                }
            }
        }
        return false;
    }

    /** Plants one wheat seed on empty farmland inside the registered farm worksite. */
    private static void plantNearbyWheat(ServerLevel level, Villager villager, NationFaction faction, NationSavedData data) {
        if (villager.getInventory().countItem(Items.WHEAT_SEEDS) <= 0) return;
        BlockPos origin = villager.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -FARM_WORK_RADIUS; dx <= FARM_WORK_RADIUS; dx++) {
                for (int dz = -FARM_WORK_RADIUS; dz <= FARM_WORK_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockPos cropPos = cursor.above();
                    if (!level.getBlockState(cursor).is(Blocks.FARMLAND) || !level.getBlockState(cropPos).isAir()
                            || !data.isFarmWorksite(faction.id(), level.dimension().location().toString(), cropPos)) continue;
                    net.minecraft.world.item.ItemStack seed = villager.getInventory().removeItemType(Items.WHEAT_SEEDS, 1);
                    if (seed.isEmpty()) return;
                    if (!level.setBlock(cropPos, Blocks.WHEAT.defaultBlockState(), 3)) {
                        villager.getInventory().addItem(seed);
                    }
                    return;
                }
            }
        }
    }
}
