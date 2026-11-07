package com.worldatwar.nations.event;

import com.worldatwar.nations.WorldAtWarNations;
import com.worldatwar.nations.data.FactionKind;
import com.worldatwar.nations.data.NationSavedData;
import com.worldatwar.nations.data.NationFaction;
import com.worldatwar.nations.data.NationJob;
import com.worldatwar.nations.data.NationWorksite;
import com.worldatwar.nations.data.NationRelation;
import com.worldatwar.nations.data.NationRelationStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
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
        if (entity instanceof IronGolem golem && golem.isAlive()) {
            // Reconcile owned defenders as their chunks load, without generating chunks
            // or counting unrelated village/player golems as military recruits.
            NationSavedData data = NationSavedData.get(level.getServer());
            String dimension = level.dimension().location().toString();
            for (NationFaction faction : data.factions()) {
                if (faction.kind() == FactionKind.VILLAGER
                        && dimension.equals(faction.dimension())
                        && golem.getTags().contains("wan_garrison_" + faction.id())) {
                    data.registerGarrisonDefender(faction, golem.getUUID());
                    break;
                }
            }
            return;
        }
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
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof IronGolem golem)) return;
        // Chunk unloading is not a permanent loss. Only explicit discard or
        // dimension transfer should release a defender's persistent roster slot.
        Entity.RemovalReason reason = golem.getRemovalReason();
        if (reason != Entity.RemovalReason.DISCARDED
                && reason != Entity.RemovalReason.CHANGED_DIMENSION) return;
        NationSavedData data = NationSavedData.get(level.getServer());
        for (NationFaction faction : data.factions()) {
            if (faction.hasGarrisonDefender(golem.getUUID())) {
                data.removeGarrisonDefender(faction, golem.getUUID());
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        NationSavedData data = NationSavedData.get(level.getServer());
        if (entity instanceof IronGolem) {
            // Unloading is not death: only a real death frees the persistent garrison slot.
            for (NationFaction faction : data.factions()) {
                if (faction.hasGarrisonDefender(entity.getUUID())) {
                    data.removeGarrisonDefender(faction, entity.getUUID());
                    break;
                }
            }
            return;
        }
        if (entity instanceof Villager || entity instanceof AbstractIllager)
            data.removeCitizen(entity.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime > 0 && gameTime % SIMULATION_INTERVAL_TICKS == 0) {
            NationSavedData data = NationSavedData.get(event.getServer());
            data.simulateAll();
            deployVillageGarrisons(event.getServer(), data);
            planShelterExpansion(event.getServer(), data);
            planEconomicInfrastructure(event.getServer(), data);
            growHousedSettlements(event.getServer(), data, gameTime);
        }
        if (gameTime > 0 && gameTime % FARM_WORK_INTERVAL_TICKS == 0) {
            NationSavedData data = NationSavedData.get(event.getServer());
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (Entity loaded : level.getAllEntities()) {
                    if (loaded instanceof AbstractIllager illager && illager.isAlive()) {
                        NationFaction illagerFaction = data.findCitizen(illager.getUUID()).orElse(null);
                        if (illagerFaction != null) defendIllagerAgainstMonsters(level, illager, illagerFaction, data);
                        continue;
                    }
                    // Children are registered citizens but cannot perform adult jobs or fight.
                    if (!(loaded instanceof Villager villager) || !villager.isAlive() || villager.isBaby()) continue;
                    NationFaction faction = data.findCitizen(villager.getUUID()).orElse(null);
                    if (faction == null) continue;
                    NationJob job = faction.jobOf(villager.getUUID());
                    if (job != NationJob.GUARD && retreatCivilianFromThreat(level, villager, faction, data)) continue;
                    guideWorkerToWorksite(level, villager, faction, data, job);
                    if (job == NationJob.GUARD) {
                        boolean responding = respondToSettlementThreat(level, villager, faction, data);
                        if (!responding) guideGuardToSettlement(level, villager, faction);
                        alertNearbyGolems(level, villager, faction, data);
                    }
                    if (job == NationJob.SCHOLAR) studyAtWorkshop(level, villager, faction, data);
                    if (job == NationJob.CRAFTSMAN || job == NationJob.GENERAL_WORKER) {
                        constructSmallShelter(level, villager, faction, data);
                        constructInfrastructure(level, villager, faction, data, NationWorksite.Type.STORAGE);
                        constructInfrastructure(level, villager, faction, data, NationWorksite.Type.WORKSHOP);
                    }
                    if (job == NationJob.FARMER && !harvestNearbyCrop(level, villager, faction, data)) plantNearbyWheat(level, villager, faction, data);
                    else if (job == NationJob.LUMBERJACK) harvestNearbyLog(level, villager, faction, data);
                    else if (job == NationJob.MINER && !harvestNearbyIronOre(level, villager, faction, data)) harvestNearbyStone(level, villager, faction, data);
                }
            }
        }
    }

    /**
     * Deploys one tagged physical iron golem per funded barracks, using only loaded
     * settlement chunks. This is defensive recruitment, not offensive faction warfare.
     */
    private static void deployVillageGarrisons(net.minecraft.server.MinecraftServer server, NationSavedData data) {
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            for (NationFaction faction : data.factions()) {
                if (faction.kind() != FactionKind.VILLAGER || !dimension.equals(faction.dimension())
                        || faction.barracks() <= 0 || faction.population() < 3) continue;
                String ownershipTag = "wan_garrison_" + faction.id();
                Villager recruiter = null;
                for (Entity entity : level.getAllEntities()) {
                    // Adopt already-loaded tagged defenders from worlds saved before the roster existed.
                    if (entity instanceof IronGolem golem && golem.isAlive()
                            && golem.getTags().contains(ownershipTag))
                        data.registerGarrisonDefender(faction, golem.getUUID());
                    if (entity instanceof Villager villager
                            && villager.isAlive() && !villager.isBaby()
                            && faction.citizens().contains(villager.getUUID())) {
                        long dx = (long) villager.blockPosition().getX() - faction.centerX();
                        long dz = (long) villager.blockPosition().getZ() - faction.centerZ();
                        long distanceSquared = dx * dx + dz * dz;
                        if (distanceSquared <= 24L * 24L) {
                            // Prefer the closest eligible home citizen. Entity iteration
                            // order is not stable across chunk loads or server restarts.
                            long bestDistanceSquared = Long.MAX_VALUE;
                            if (recruiter != null) {
                                long bestDx = (long) recruiter.blockPosition().getX() - faction.centerX();
                                long bestDz = (long) recruiter.blockPosition().getZ() - faction.centerZ();
                                bestDistanceSquared = bestDx * bestDx + bestDz * bestDz;
                            }
                            if (distanceSquared < bestDistanceSquared
                                    || (distanceSquared == bestDistanceSquared && recruiter != null
                                        && villager.getUUID().compareTo(recruiter.getUUID()) < 0))
                                recruiter = villager;
                        }
                    }
                }
                // Only recruit at the home settlement. A citizen working at a distant
                // outpost must not turn that outpost into an unplanned garrison.
                if (recruiter == null || faction.garrisonDefenderCount() >= Math.min(2, faction.barracks())) continue;
                long recruitDx = (long) recruiter.blockPosition().getX() - faction.centerX();
                long recruitDz = (long) recruiter.blockPosition().getZ() - faction.centerZ();
                if (recruitDx * recruitDx + recruitDz * recruitDz > 24L * 24L) continue;
                BlockPos origin = recruiter.blockPosition();
                boolean placed = false;
                for (int dx = -3; dx <= 3 && !placed; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos pos = origin.offset(dx, 0, dz);
                        // Keep the actual deployment position inside the home perimeter,
                        // not just the recruiter who initiated the search.
                        long spawnDx = (long) pos.getX() - faction.centerX();
                        long spawnDz = (long) pos.getZ() - faction.centerZ();
                        if (spawnDx * spawnDx + spawnDz * spawnDz > 24L * 24L) continue;
                        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())
                                || !level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                                || !level.getBlockState(pos).isAir()
                                || !level.getBlockState(pos.above()).isAir()
                                || !level.getBlockState(pos.above(2)).isAir()) continue;
                        AABB area = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                                pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1);
                        if (!level.getEntities((Entity) null, area).isEmpty()) continue;
                        IronGolem defender = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(level);
                        if (defender == null) continue;
                        defender.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                        // A three-block air column is not sufficient: the actual golem
                        // bounding box must fit, and recruits must remain inside the border.
                        if (!level.getWorldBorder().isWithinBounds(pos)
                                || !level.noCollision(defender)) continue;
                        defender.setPlayerCreated(true);
                        defender.addTag(ownershipTag);
                        if (!data.fundGarrisonDefender(faction, server.overworld().getGameTime())) break;
                        if (!level.addFreshEntity(defender)) {
                            // EntityJoinLevelEvent may have registered this UUID before
                            // another listener vetoed the spawn. Undo both effects.
                            data.removeGarrisonDefender(faction, defender.getUUID());
                            data.refundGarrisonDefender(faction, server.overworld().getGameTime());
                            continue;
                        }
                        data.registerGarrisonDefender(faction, defender.getUUID());
                        placed = true;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Plans at most one new shelter per settlement per strategic tick. Only loaded,
     * unobstructed ground is considered; this never generates chunks or overwrites blocks.
     * Construction itself remains the responsibility of an assigned worker.
     */
    private static void planShelterExpansion(net.minecraft.server.MinecraftServer server, NationSavedData data) {
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            for (NationFaction faction : data.factions()) {
                if (faction.kind() != FactionKind.VILLAGER || !faction.dimension().equals(dimension)
                        || faction.population() < 2 || faction.population() < faction.housingCapacity()
                        || faction.wood() < 23.0) continue;
                boolean pending = data.worksites().stream().anyMatch(site ->
                        site.factionId().equals(faction.id()) && site.type() == NationWorksite.Type.BUILD
                        && !data.isBuildSiteCompleted(faction.id(), dimension,
                                new BlockPos(site.x(), site.y(), site.z())));
                if (pending) continue;
                Villager builder = null;
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Villager villager && villager.isAlive() && !villager.isBaby()
                            && faction.citizens().contains(villager.getUUID())
                            && (faction.jobOf(villager.getUUID()) == NationJob.CRAFTSMAN
                                || faction.jobOf(villager.getUUID()) == NationJob.GENERAL_WORKER)) {
                        builder = villager;
                        break;
                    }
                }
                if (builder == null) continue;
                BlockPos origin = builder.blockPosition();
                boolean planned = false;
                for (int radius = 6; radius <= 14 && !planned; radius += 4) {
                    for (int dx = -radius; dx <= radius && !planned; dx += 4) {
                        for (int dz = -radius; dz <= radius && !planned; dz += 4) {
                            BlockPos base = origin.offset(dx, 0, dz);
                            if (!safeShelterSite(level, data, dimension, base)) continue;
                            planned = data.addBuildWorksite(faction, dimension, base);
                        }
                    }
                }
            }
        }
    }

    /**
     * Plans physical service stations when the faction can afford them.
     * This does not double-count the abstract workshop economy: a station is a
     * physical work destination, not an additional abstract production multiplier.
     */
    /**
     * Creates at most one real baby villager per eligible faction per Minecraft day.
     * Only existing, loaded adult citizens may act as a parent; no virtual citizens
     * are added to the roster. EntityJoinLevelEvent registers successful spawns.
     */
    private static void growHousedSettlements(net.minecraft.server.MinecraftServer server,
            NationSavedData data, long gameTime) {
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            for (NationFaction faction : data.factions()) {
                if (faction.kind() != FactionKind.VILLAGER || !faction.dimension().equals(dimension)
                        || !faction.canFundGrowth(gameTime)) continue;
                Villager parent = null;
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Villager villager && villager.isAlive() && !villager.isBaby()
                            && faction.citizens().contains(villager.getUUID())) {
                        parent = villager;
                        break;
                    }
                }
                if (parent == null) continue;
                BlockPos origin = parent.blockPosition();
                for (int dx = -3; dx <= 3; dx++) {
                    boolean spawned = false;
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos pos = origin.offset(dx, 0, dz);
                        BlockPos below = pos.below();
                        if (!level.hasChunkAt(pos) || !level.hasChunkAt(below)
                                || !level.getBlockState(below).isSolidRender(level, below)
                                || !level.getBlockState(pos).isAir()
                                || !level.getBlockState(pos.above()).isAir()) continue;
                        AABB volume = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                                pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
                        if (!level.getEntities((Entity) null, volume).isEmpty()) continue;
                        Villager child = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
                        if (child == null) continue;
                        child.setBaby(true);
                        child.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                                parent.getYRot(), 0.0F);
                        if (!data.fundPopulationGrowth(faction, gameTime)) break;
                        if (!data.registerNewborn(faction, child.getUUID())) {
                            data.refundFailedPopulationGrowth(faction, gameTime);
                            break;
                        }
                        if (!level.addFreshEntity(child)) {
                            data.removeCitizen(child.getUUID());
                            data.refundFailedPopulationGrowth(faction, gameTime);
                            // A rejected spawn must not count as a successful birth.
                            // Try another safe position without charging the faction.
                            continue;
                        }
                        spawned = true;
                        break;
                    }
                    if (spawned) break;
                }
            }
        }
    }

    private static void planEconomicInfrastructure(net.minecraft.server.MinecraftServer server, NationSavedData data) {
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            for (NationFaction faction : data.factions()) {
                if (faction.kind() != FactionKind.VILLAGER || !faction.dimension().equals(dimension)
                        || faction.population() < 3) continue;
                NationWorksite.Type type = null;
                if (faction.wood() >= 20.0 && faction.food() >= 35.0) {
                    boolean hasStorage = data.worksites().stream().anyMatch(site ->
                            site.factionId().equals(faction.id()) && site.type() == NationWorksite.Type.STORAGE);
                    if (!hasStorage) type = NationWorksite.Type.STORAGE;
                }
                if (type == null && faction.wood() >= 30.0 && faction.technology() >= 2) {
                    boolean hasWorkshop = data.worksites().stream().anyMatch(site ->
                            site.factionId().equals(faction.id()) && site.type() == NationWorksite.Type.WORKSHOP);
                    if (!hasWorkshop) type = NationWorksite.Type.WORKSHOP;
                }
                if (type == null) continue;
                Villager builder = null;
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Villager villager && villager.isAlive() && !villager.isBaby()
                            && faction.citizens().contains(villager.getUUID())
                            && (faction.jobOf(villager.getUUID()) == NationJob.CRAFTSMAN
                                || faction.jobOf(villager.getUUID()) == NationJob.GENERAL_WORKER)) {
                        builder = villager;
                        break;
                    }
                }
                if (builder == null) continue;
                BlockPos origin = builder.blockPosition();
                boolean planned = false;
                for (int radius = 6; radius <= 14 && !planned; radius += 4) {
                    for (int dx = -radius; dx <= radius && !planned; dx += 4) {
                        for (int dz = -radius; dz <= radius && !planned; dz += 4) {
                            BlockPos site = origin.offset(dx, 0, dz);
                            if (!safeShelterSite(level, data, dimension, site)) continue;
                            planned = type == NationWorksite.Type.STORAGE
                                    ? data.addStorageWorksite(faction, dimension, site)
                                    : data.addWorkshopWorksite(faction, dimension, site);
                        }
                    }
                }
            }
        }
    }

    private static boolean safeShelterSite(ServerLevel level, NationSavedData data,
            String dimension, BlockPos base) {
        if (base.getY() <= level.getMinBuildHeight() + 1
                || base.getY() + 3 >= level.getMaxBuildHeight()) return false;
        for (NationWorksite site : data.worksites()) {
            if (!site.dimension().equals(dimension)) continue;
            long dx = (long) site.x() - base.getX();
            long dz = (long) site.z() - base.getZ();
            if (dx * dx + dz * dz < 36L) return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos ground = base.offset(dx, -1, dz);
                if (!level.hasChunkAt(ground) || !level.getBlockState(ground).isSolidRender(level, ground))
                    return false;
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) return false;
                }
            }
        }
        return level.getEntities((Entity) null,
                new AABB(base.offset(-1, 0, -1), base.offset(2, 3, 2))).isEmpty();
    }

    /**
     * Sends assigned villagers toward the nearest unfinished worksite for their current job.
     * Navigation is refreshed only when the worker is outside the worksite's eight-block radius.
     */
    private static void guideWorkerToWorksite(ServerLevel level, Villager villager, NationFaction faction,
            NationSavedData data, NationJob job) {
        String dimension = level.dimension().location().toString();
        java.util.Set<NationWorksite.Type> allowed = switch (job) {
            case FARMER -> java.util.Set.of(NationWorksite.Type.FARM);
            case LUMBERJACK -> java.util.Set.of(NationWorksite.Type.LUMBER);
            case MINER -> java.util.Set.of(NationWorksite.Type.MINING);
            case SCHOLAR -> java.util.Set.of(NationWorksite.Type.WORKSHOP);
            case CRAFTSMAN, GENERAL_WORKER -> java.util.Set.of(
                    NationWorksite.Type.BUILD, NationWorksite.Type.STORAGE, NationWorksite.Type.WORKSHOP);
            default -> java.util.Set.of();
        };
        if (allowed.isEmpty()) return;

        BlockPos current = villager.blockPosition();
        NationWorksite target = data.worksites().stream()
                .filter(site -> site.factionId().equals(faction.id())
                        && site.dimension().equals(dimension) && allowed.contains(site.type()))
                .filter(site -> {
                    BlockPos anchor = new BlockPos(site.x(), site.y(), site.z());
                    if (site.type() == NationWorksite.Type.BUILD
                            && data.isBuildSiteCompleted(faction.id(), dimension, anchor)) return false;
                    if (site.type() == NationWorksite.Type.STORAGE
                            && data.isInfrastructureSiteCompleted(faction.id(), dimension, anchor, site.type())) return false;
                    if (site.type() == NationWorksite.Type.WORKSHOP
                            && job != NationJob.SCHOLAR
                            && data.isInfrastructureSiteCompleted(faction.id(), dimension, anchor, site.type())) return false;
                    return true;
                })
                .min(java.util.Comparator.comparingLong(site -> {
                    long dx = (long) site.x() - current.getX();
                    long dy = (long) site.y() - current.getY();
                    long dz = (long) site.z() - current.getZ();
                    return dx * dx + dy * dy + dz * dz;
                })).orElse(null);
        if (target == null) return;
        BlockPos anchor = new BlockPos(target.x(), target.y(), target.z());
        if (!target.contains(current, dimension) && level.hasChunkAt(anchor)) {
            villager.getNavigation().moveTo(target.x() + 0.5, target.y(), target.z() + 0.5, 0.8);
        }
    }

    /**
     * Keeps assigned guards near their settlement and rotates them through four patrol points.
     * Patrols are defensive positioning only; guards do not yet engage hostile factions.
     */
    private static void guideGuardToSettlement(ServerLevel level, Villager guard, NationFaction faction) {
        BlockPos current = guard.blockPosition();
        long centerDx = (long) faction.centerX() - current.getX();
        long centerDz = (long) faction.centerZ() - current.getZ();

        int phase = (int) Math.floorMod(level.getGameTime() / 1200L
                + guard.getUUID().hashCode(), 4L);
        int patrolRadius = 10;
        int targetX = faction.centerX();
        int targetZ = faction.centerZ();
        if (centerDx * centerDx + centerDz * centerDz <= 256L) {
            switch (phase) {
                case 0 -> targetX += patrolRadius;
                case 1 -> targetZ += patrolRadius;
                case 2 -> targetX -= patrolRadius;
                default -> targetZ -= patrolRadius;
            }
        }

        // Guards without an active threat spread into nearby defensive posts around
        // the settlement center. The UUID-derived slot keeps assignments stable.
        // Build the roster from a settlement-centered area, not an area centered on each guard.
        // This gives every guard in the settlement the same ordered roster and stable asset slots.
        NationSavedData data = NationSavedData.get(level.getServer());
        AABB settlementArea = new AABB(
                faction.centerX() - 48.0, level.getMinBuildHeight(), faction.centerZ() - 48.0,
                faction.centerX() + 49.0, level.getMaxBuildHeight(), faction.centerZ() + 49.0);
        java.util.List<Villager> guards = level.getEntitiesOfClass(Villager.class,
                        settlementArea,
                        candidate -> candidate.isAlive() && !candidate.isBaby()
                                && faction.id().equals(data.findCitizen(candidate.getUUID())
                                        .map(NationFaction::id).orElse(""))
                                && faction.jobOf(candidate.getUUID()) == NationJob.GUARD)
                .stream()
                .sorted(java.util.Comparator.comparing(candidate -> candidate.getUUID().toString()))
                .toList();
        int guardSlot = 0;
        for (int i = 0; i < guards.size(); i++) {
            if (guards.get(i).getUUID().equals(guard.getUUID())) {
                guardSlot = i;
                break;
            }
        }
        if (guards.size() > 1 && centerDx * centerDx + centerDz * centerDz <= 256L) {
            double angle = (Math.PI * 2.0 * guardSlot) / guards.size();
            targetX = faction.centerX() + (int) Math.round(Math.cos(angle) * patrolRadius);
            targetZ = faction.centerZ() + (int) Math.round(Math.sin(angle) * patrolRadius);
        }

        // Completed homes and service buildings become fallback posts when no threat is active.
        // Guards are distributed over the same ordered post list to avoid crowding one structure.
        String dimension = level.dimension().location().toString();
        java.util.List<BlockPos> assetPosts = data.worksites().stream()
                .filter(site -> site.factionId().equals(faction.id()) && site.dimension().equals(dimension))
                .filter(site -> switch (site.type()) {
                    case BUILD -> data.isBuildSiteCompleted(faction.id(), dimension,
                            new BlockPos(site.x(), site.y(), site.z()))
                            && level.hasChunkAt(new BlockPos(site.x(), site.y(), site.z()))
                            && level.getBlockState(new BlockPos(site.x(), site.y() + 2, site.z())).is(Blocks.OAK_PLANKS)
                            && level.getBlockState(new BlockPos(site.x() - 1, site.y(), site.z())).is(Blocks.OAK_PLANKS)
                            && level.getBlockState(new BlockPos(site.x() + 1, site.y(), site.z())).is(Blocks.OAK_PLANKS);
                    case STORAGE, WORKSHOP -> data.isInfrastructureSiteCompleted(faction.id(), dimension,
                            new BlockPos(site.x(), site.y(), site.z()), site.type())
                            && level.hasChunkAt(new BlockPos(site.x(), site.y(), site.z()))
                            && level.getBlockState(new BlockPos(site.x(), site.y(), site.z())).is(
                                    site.type() == NationWorksite.Type.STORAGE ? Blocks.CHEST : Blocks.CRAFTING_TABLE);
                    default -> false;
                })
                .map(site -> new BlockPos(site.x(), site.y(), site.z()))
                // Defensive patrols must remain local even if a faction has remote worksites.
                // Restrict posts to the settlement perimeter before assigning guard slots.
                .filter(post -> {
                    long px = (long) post.getX() - faction.centerX();
                    long pz = (long) post.getZ() - faction.centerZ();
                    return px * px + pz * pz <= 32L * 32L;
                })
                .filter(level::hasChunkAt)
                .sorted(java.util.Comparator.comparingLong(post -> {
                    long px = (long) post.getX() - faction.centerX();
                    long pz = (long) post.getZ() - faction.centerZ();
                    return px * px + pz * pz;
                }))
                .toList();
        if (!assetPosts.isEmpty()) {
            int assignedPostIndex = guardSlot % assetPosts.size();
            BlockPos assignedPost = assetPosts.get(assignedPostIndex);
            if (centerDx * centerDx + centerDz * centerDz <= 256L) {
                // Guards assigned to the same building take separate perimeter positions.
                // The shared UUID-sorted roster makes each guard's local slot deterministic.
                java.util.List<Villager> postGuards = java.util.stream.IntStream.range(0, guards.size())
                        .filter(candidateIndex -> candidateIndex % assetPosts.size() == assignedPostIndex)
                        .mapToObj(guards::get)
                        .toList();
                int postSlot = 0;
                for (int i = 0; i < postGuards.size(); i++) {
                    if (postGuards.get(i).getUUID().equals(guard.getUUID())) {
                        postSlot = i;
                        break;
                    }
                }
                double postAngle = (Math.PI * 2.0 * postSlot) / Math.max(1, postGuards.size());
                double postRadius = 4.0;
                targetX = assignedPost.getX() + (int) Math.round(Math.cos(postAngle) * postRadius);
                targetZ = assignedPost.getZ() + (int) Math.round(Math.sin(postAngle) * postRadius);
            }
        }

        long dx = (long) targetX - current.getX();
        long dz = (long) targetZ - current.getZ();
        if (dx * dx + dz * dz <= 16L) return;

        BlockPos patrolPoint = new BlockPos(targetX, current.getY(), targetZ);
        if (level.hasChunkAt(patrolPoint)) {
            guard.getNavigation().moveTo(targetX + 0.5, current.getY(), targetZ + 0.5, 0.75);
        }
    }

    /**
     * Sends a guard to investigate a hostile mob threatening a same-faction citizen or registered worksite.
     * This is defensive positioning only: villagers still do not have a combat target/attack AI.
     */
    private static boolean respondToSettlementThreat(ServerLevel level, Villager guard,
            NationFaction faction, NationSavedData data) {
        AABB patrolArea = guard.getBoundingBox().inflate(24.0);
        Monster threat = level.getEntitiesOfClass(Monster.class, patrolArea,
                        candidate -> candidate.isAlive() && !candidate.isAlliedTo(guard))
                .stream()
                .filter(candidate -> diplomaticThreatPriority(candidate, faction, data) < 3)
                .map(candidate -> new ThreatAssessment(candidate,
                        distanceToFactionAsset(level, candidate, faction, data)))
                .filter(assessment -> Double.isFinite(assessment.assetDistanceSquared()))
                .min(java.util.Comparator
                        .comparingInt((ThreatAssessment assessment) ->
                                diplomaticThreatPriority(assessment.threat(), faction, data))
                        .thenComparingDouble(ThreatAssessment::assetDistanceSquared)
                        .thenComparingDouble(assessment -> guard.distanceToSqr(assessment.threat())))
                .map(ThreatAssessment::threat)
                .orElse(null);
        if (threat == null) return false;

        BlockPos destination = threat.blockPosition();
        if (!level.hasChunkAt(destination)) return true;

        // Guards assigned to the same threat take separate positions around it instead of
        // stacking on one block. UUID ordering makes slot assignments stable between updates.
        AABB coordinationArea = threat.getBoundingBox().inflate(16.0);
        java.util.List<Villager> responders = level.getEntitiesOfClass(Villager.class, coordinationArea,
                        candidate -> candidate.isAlive() && !candidate.isBaby()
                                && faction.id().equals(data.findCitizen(candidate.getUUID())
                                        .map(NationFaction::id).orElse(""))
                                && faction.jobOf(candidate.getUUID()) == NationJob.GUARD)
                .stream()
                .sorted(java.util.Comparator.comparing(candidate -> candidate.getUUID().toString()))
                .toList();
        int slot = 0;
        for (int i = 0; i < responders.size(); i++) {
            if (responders.get(i).getUUID().equals(guard.getUUID())) {
                slot = i;
                break;
            }
        }
        // Include the responding guard in the same UUID-ordered formation even if
        // it is outside the threat-centered coordination radius.
        java.util.List<Villager> formation = new java.util.ArrayList<>(responders);
        if (formation.stream().noneMatch(candidate -> candidate.getUUID().equals(guard.getUUID()))) {
            formation.add(guard);
            formation.sort(java.util.Comparator.comparing(candidate -> candidate.getUUID().toString()));
        }
        slot = formation.indexOf(guard);
        int responderCount = Math.max(1, formation.size());
        double angle = (Math.PI * 2.0 * slot) / responderCount;
        double targetX = threat.getX() + Math.cos(angle) * 3.5;
        double targetZ = threat.getZ() + Math.sin(angle) * 3.5;
        double dx = guard.getX() - targetX;
        double dz = guard.getZ() - targetZ;
        if (dx * dx + dz * dz > 4.0) {
            guard.getNavigation().moveTo(targetX, destination.getY(), targetZ, 0.9);
        }
        return true;
    }

    private record ThreatAssessment(Monster threat, double assetDistanceSquared) {}

    /**
     * Measures distance to the nearest threatened faction asset.
     * Only citizens and worksites within their respective danger radii count as active assets.
     */
    private static double distanceToFactionAsset(ServerLevel level, Monster threat,
            NationFaction faction, NationSavedData data) {
        double nearest = Double.POSITIVE_INFINITY;
        AABB citizenArea = threat.getBoundingBox().inflate(12.0);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, citizenArea,
                candidate -> candidate.isAlive()
                        && data.findCitizen(candidate.getUUID())
                                .map(owner -> owner.id().equals(faction.id()))
                                .orElse(false))) {
            nearest = Math.min(nearest, threat.distanceToSqr(villager));
        }

        String dimension = level.dimension().location().toString();
        BlockPos threatPos = threat.blockPosition();
        for (NationWorksite site : data.worksites()) {
            if (!site.factionId().equals(faction.id()) || !site.dimension().equals(dimension)
                    || !site.contains(threatPos, dimension)) continue;
            // Ignore inactive building markers: only finished structures are defensive assets.
            BlockPos anchor = new BlockPos(site.x(), site.y(), site.z());
            if (site.type() == NationWorksite.Type.BUILD
                    && !data.isBuildSiteCompleted(faction.id(), dimension, anchor)) continue;
            if ((site.type() == NationWorksite.Type.STORAGE || site.type() == NationWorksite.Type.WORKSHOP)
                    && !data.isInfrastructureSiteCompleted(faction.id(), dimension, anchor, site.type())) continue;
            // The physical site must still be present to count as an asset.
            if (site.type() == NationWorksite.Type.BUILD
                    && (!level.hasChunkAt(anchor)
                            || !level.getBlockState(anchor.above(2)).is(Blocks.OAK_PLANKS)
                            || !level.getBlockState(anchor.west()).is(Blocks.OAK_PLANKS)
                            || !level.getBlockState(anchor.east()).is(Blocks.OAK_PLANKS))) continue;
            if (site.type() == NationWorksite.Type.STORAGE
                    && (!level.hasChunkAt(anchor) || !level.getBlockState(anchor).is(Blocks.CHEST))) continue;
            if (site.type() == NationWorksite.Type.WORKSHOP
                    && (!level.hasChunkAt(anchor) || !level.getBlockState(anchor).is(Blocks.CRAFTING_TABLE))) continue;
            double dx = threat.getX() - (site.x() + 0.5);
            double dy = threat.getY() - site.y();
            double dz = threat.getZ() - (site.z() + 0.5);
            nearest = Math.min(nearest, dx * dx + dy * dy + dz * dz);
        }
        return nearest;
    }

    /**
     * Non-guard citizens retreat toward their settlement center when hostile mobs get close.
     * This is a simple evacuation response; it does not guarantee the center is safe.
     */
    private static boolean retreatCivilianFromThreat(ServerLevel level, Villager villager, NationFaction faction, NationSavedData data) {
        AABB dangerArea = villager.getBoundingBox().inflate(12.0);
        Monster threat = level.getEntitiesOfClass(Monster.class, dangerArea,
                        candidate -> candidate.isAlive() && !candidate.isAlliedTo(villager)
                                && diplomaticThreatPriority(candidate, faction, data) < 3)
                .stream()
                .min(java.util.Comparator
                        .comparingInt((Monster candidate) -> diplomaticThreatPriority(candidate, faction, data))
                        .thenComparingDouble(villager::distanceToSqr))
                .orElse(null);
        if (threat == null) return false;

        // Keep a threatened civilian away from its normal work assignment this interval.
        // A destination is chosen relative to the actual hostile mob, not to faction borders.
        // Move away from the nearest threat instead of blindly routing toward the settlement center,
        // which could be on the far side of the hostile mob.
        BlockPos current = villager.blockPosition();
        double awayX = current.getX() - threat.blockPosition().getX();
        double awayZ = current.getZ() - threat.blockPosition().getZ();
        double length = Math.sqrt(awayX * awayX + awayZ * awayZ);
        int targetX;
        int targetZ;
        if (length > 0.001) {
            targetX = current.getX() + (int) Math.round(awayX / length * 16.0);
            targetZ = current.getZ() + (int) Math.round(awayZ / length * 16.0);
        } else {
            targetX = faction.centerX();
            targetZ = faction.centerZ();
        }

        BlockPos refuge = new BlockPos(targetX, current.getY(), targetZ);
        if (!level.hasChunkAt(refuge)) return true;
        long dx = (long) refuge.getX() - current.getX();
        long dz = (long) refuge.getZ() - current.getZ();
        if (dx * dx + dz * dz > 16L) {
            villager.getNavigation().moveTo(refuge.getX() + 0.5, refuge.getY(), refuge.getZ() + 0.5, 1.0);
        }
        return true;
    }

    /**
     * Gives registered illagers a basic local defense response: if they have no living target,
     * they may engage the nearest non-allied hostile monster within 16 blocks.
     * Existing combat targets are never overwritten.
     */
    private static void defendIllagerAgainstMonsters(ServerLevel level, AbstractIllager illager,
            NationFaction faction, NationSavedData data) {
        LivingEntity currentTarget = illager.getTarget();
        if (currentTarget != null && currentTarget.isAlive()) {
            // Re-check registered monster targets so a new peace treaty or alliance takes effect.
            if (!(currentTarget instanceof Monster currentMonster)) return;
            if (!currentMonster.isAlliedTo(illager)
                    && diplomaticThreatPriority(currentMonster, faction, data) < 3) return;
            illager.setTarget(null);
        }

        AABB defenseArea = illager.getBoundingBox().inflate(16.0);
        Monster threat = level.getEntitiesOfClass(Monster.class, defenseArea,
                        candidate -> candidate != illager && candidate.isAlive() && !candidate.isAlliedTo(illager)
                                && diplomaticThreatPriority(candidate, faction, data) < 3)
                .stream()
                .min(java.util.Comparator
                        .comparingInt((Monster candidate) -> diplomaticThreatPriority(candidate, faction, data))
                        .thenComparingDouble(illager::distanceToSqr))
                .orElse(null);
        if (threat != null) illager.setTarget(threat);
    }

    /**
     * WAR targets take priority over RIVALRY targets, regardless of small distance differences.
     * Unregistered monsters remain local threats after registered diplomatic enemies are considered.
     * Same-faction, allied, peaceful, trading, and merely tense factions are ignored.
     *
     * @return 0 for WAR, 1 for RIVALRY, 2 for unregistered threats, or 3 for non-hostile factions.
     */
    private static int diplomaticThreatPriority(Monster candidate, NationFaction defender,
            NationSavedData data) {
        NationFaction attacker = data.findCitizen(candidate.getUUID()).orElse(null);
        if (attacker == null) return 2;
        if (attacker.id().equals(defender.id())) return 3;
        return data.relations().stream()
                .filter(relation -> (relation.firstFactionId().equals(defender.id())
                                && relation.secondFactionId().equals(attacker.id()))
                        || (relation.firstFactionId().equals(attacker.id())
                                && relation.secondFactionId().equals(defender.id())))
                .map(NationRelation::status)
                .mapToInt(status -> status == NationRelationStatus.WAR ? 0
                        : status == NationRelationStatus.RIVALRY ? 1 : 3)
                .min()
                .orElse(3);
    }

    /**
     * Settlement guards alert nearby iron golems when hostile mobs approach.
     * Existing golem targets are preserved; this does not make villagers attack.
     */
    private static void alertNearbyGolems(ServerLevel level, Villager guard, NationFaction faction, NationSavedData data) {
        AABB threatArea = guard.getBoundingBox().inflate(24.0);
        Monster threat = level.getEntitiesOfClass(Monster.class, threatArea,
                        candidate -> candidate.isAlive() && !candidate.isAlliedTo(guard)
                                && withinGarrisonDefensePerimeter(candidate, faction)
                                && diplomaticThreatPriority(candidate, faction, data) < 3)
                .stream()
                .map(candidate -> new ThreatAssessment(candidate,
                        distanceToFactionAsset(level, candidate, faction, data)))
                .filter(assessment -> Double.isFinite(assessment.assetDistanceSquared()))
                .min(java.util.Comparator
                        .comparingInt((ThreatAssessment assessment) ->
                                diplomaticThreatPriority(assessment.threat(), faction, data))
                        .thenComparingDouble(ThreatAssessment::assetDistanceSquared)
                        .thenComparingDouble(assessment -> guard.distanceToSqr(assessment.threat())))
                .map(ThreatAssessment::threat)
                .orElse(null);
        AABB defenseArea = guard.getBoundingBox().inflate(24.0);
        // Only command defenders owned by this faction. Unrelated vanilla, player,
        // or other-faction golems must retain their own combat decisions.
        String ownershipTag = "wan_garrison_" + faction.id();
        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, defenseArea,
                candidate -> candidate.isAlive() && candidate.getTags().contains(ownershipTag)
                        && faction.hasGarrisonDefender(candidate.getUUID()))) {
            // Golems that are already fighting remain assigned unless their target
            // has become a diplomatic friend or an entity ally.
            LivingEntity existingTarget = golem.getTarget();
            // Do not preserve an obsolete diplomatic enemy after peace is established.
            if (existingTarget instanceof Monster existingMonster
                    && (existingMonster.isAlliedTo(guard)
                            || diplomaticThreatPriority(existingMonster, faction, data) >= 3)) {
                golem.setTarget(null);
                existingTarget = null;
            }
            // Guard-issued orders must stay near the home settlement. A target can
            // be close to a guard or golem while already outside the defensive zone.
            // Clear such orders when guards next evaluate this defender; vanilla
            // autonomous targeting is not fully constrained by this check.
            if (existingTarget != null && !withinGarrisonDefensePerimeter(existingTarget, faction)) {
                golem.setTarget(null);
                existingTarget = null;
            }
            if (threat != null && withinGarrisonDefensePerimeter(threat, faction)
                    && golem.distanceToSqr(threat) <= 24.0 * 24.0
                    && (existingTarget == null || !existingTarget.isAlive())) {
                golem.setTarget(threat);
            }
        }
    }

    private static boolean withinGarrisonDefensePerimeter(Entity entity, NationFaction faction) {
        double dx = entity.getX() - (faction.centerX() + 0.5);
        double dz = entity.getZ() - (faction.centerZ() + 0.5);
        return dx * dx + dz * dz <= 32.0 * 32.0;
    }

    /**
     * Scholars generate national knowledge while stationed at a completed, intact workshop.
     */
    private static void studyAtWorkshop(ServerLevel level, Villager villager, NationFaction faction, NationSavedData data) {
        String dimension = level.dimension().location().toString();
        BlockPos current = villager.blockPosition();
        NationWorksite site = data.worksites().stream()
                .filter(candidate -> candidate.factionId().equals(faction.id())
                        && candidate.dimension().equals(dimension)
                        && candidate.type() == NationWorksite.Type.WORKSHOP)
                .filter(candidate -> data.isInfrastructureSiteCompleted(faction.id(), dimension,
                        new BlockPos(candidate.x(), candidate.y(), candidate.z()), NationWorksite.Type.WORKSHOP))
                .filter(candidate -> {
                    BlockPos anchor = new BlockPos(candidate.x(), candidate.y(), candidate.z());
                    return level.hasChunkAt(anchor) && level.getBlockState(anchor).is(Blocks.CRAFTING_TABLE);
                })
                .min(java.util.Comparator.comparingLong(candidate -> {
                    long dx = (long) candidate.x() - current.getX();
                    long dy = (long) candidate.y() - current.getY();
                    long dz = (long) candidate.z() - current.getZ();
                    return dx * dx + dy * dy + dz * dz;
                })).orElse(null);
        if (site == null) return;
        BlockPos anchor = new BlockPos(site.x(), site.y(), site.z());
        if (site.contains(current, dimension)) data.recordPhysicalResearch(villager.getUUID(), 0.15);
    }

    /**
     * Builds one small timber shelter at a reserved build marker.
     * It only places blocks into a completely clear, supported footprint and never replaces terrain or structures.
     */
    private static void constructSmallShelter(ServerLevel level, Villager villager, NationFaction faction, NationSavedData data) {
        String dimension = level.dimension().location().toString();
        BlockPos workerPos = villager.blockPosition();
        BlockPos base = data.findBuildWorksite(faction.id(), dimension, workerPos).orElse(null);
        if (base == null || data.isBuildSiteCompleted(faction.id(), dimension, base)) return;

        // The reserved marker is the permanent anchor, so workers cannot scatter huts around the site.
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

        AABB buildVolume = new AABB(base.offset(-1, 0, -1), base.offset(2, 3, 2));
        // The builder is excluded by getEntities(villager, ...); check its own collision box too.
        if (villager.getBoundingBox().intersects(buildVolume)
                || !level.getEntities(villager, buildVolume).isEmpty()) return;

        // 7 lower wall blocks + 7 upper wall blocks + 9 roof blocks = 23.
        if (!data.spendWoodForConstruction(faction.id(), 23.0)) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean perimeter = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                if (perimeter) {
                    // Leave a two-block-high doorway on the south-facing wall.
                    if (!(dz == 1 && dx == 0)) {
                        level.setBlock(base.offset(dx, 0, dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                        level.setBlock(base.offset(dx, 1, dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    }
                }
                level.setBlock(base.offset(dx, 2, dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        data.markBuildSiteCompleted(faction.id(), dimension, base);
    }

    /**
     * Places a small, functional station cluster at a registered storage or workshop site.
     * The chest is an ordinary Minecraft chest; national stockpiles are not yet linked to its inventory.
     */
    private static void constructInfrastructure(ServerLevel level, Villager villager, NationFaction faction,
            NationSavedData data, NationWorksite.Type type) {
        String dimension = level.dimension().location().toString();
        BlockPos base = data.findInfrastructureWorksite(faction.id(), dimension, villager.blockPosition(), type).orElse(null);
        if (base == null || data.isInfrastructureSiteCompleted(faction.id(), dimension, base, type)) return;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos ground = base.offset(dx, -1, dz);
                BlockPos space = base.offset(dx, 0, dz);
                if (!level.hasChunkAt(ground) || !level.hasChunkAt(space)
                        || level.getBlockState(ground).isAir() || !level.getBlockState(space).isAir()) return;
            }
        }
        AABB volume = new AABB(base.offset(-1, 0, -1), base.offset(2, 1, 2));
        // The builder is excluded from getEntities(villager, ...), so check it explicitly.
        if (villager.getBoundingBox().intersects(volume)
                || !level.getEntities(villager, volume).isEmpty()) return;

        if (type == NationWorksite.Type.STORAGE) {
            if (!data.spendWoodForConstruction(faction.id(), 8.0)) return;
            level.setBlock(base, Blocks.CHEST.defaultBlockState(), 3);
            level.setBlock(base.west(), Blocks.BARREL.defaultBlockState(), 3);
            level.setBlock(base.east(), Blocks.BARREL.defaultBlockState(), 3);
        } else if (type == NationWorksite.Type.WORKSHOP) {
            if (!data.spendWoodForConstruction(faction.id(), 12.0)) return;
            level.setBlock(base, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
            level.setBlock(base.west(), Blocks.FURNACE.defaultBlockState(), 3);
            level.setBlock(base.east(), Blocks.STONECUTTER.defaultBlockState(), 3);
        } else return;
        data.markInfrastructureSiteCompleted(faction.id(), dimension, base, type);
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
