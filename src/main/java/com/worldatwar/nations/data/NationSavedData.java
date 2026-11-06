package com.worldatwar.nations.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NationSavedData extends SavedData {
    private static final String DATA_NAME = "worldatwar_nations";
    private static final int SETTLEMENT_CELL_SIZE = 192;
    private static final double MERGE_RADIUS_SQUARED = 112.0 * 112.0;
    private final Map<String, NationFaction> factions = new LinkedHashMap<>();

    public static NationSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(NationSavedData::load, NationSavedData::new, DATA_NAME);
    }

    public Collection<NationFaction> factions() { return factions.values(); }
    public Optional<NationFaction> find(String id) { return Optional.ofNullable(factions.get(id)); }
    public Optional<NationFaction> findCitizen(UUID uuid) {
        return factions.values().stream().filter(faction -> faction.hasCitizen(uuid)).findFirst();
    }

    public NationFaction registerCitizen(ServerLevel level, FactionKind kind, UUID uuid, int blockX, int blockZ) {
        Optional<NationFaction> existing = findCitizen(uuid);
        if (existing.isPresent()) return existing.get();
        String dimension = level.dimension().location().toString();
        NationFaction nearest = factions.values().stream()
                .filter(f -> f.kind() == kind && f.dimension().equals(dimension))
                .min(Comparator.comparingDouble(f -> distanceSquared(blockX, blockZ, f.centerX(), f.centerZ())))
                .filter(f -> distanceSquared(blockX, blockZ, f.centerX(), f.centerZ()) <= MERGE_RADIUS_SQUARED)
                .orElse(null);
        NationFaction faction = nearest;
        if (faction == null) {
            int cellX = Math.floorDiv(blockX, SETTLEMENT_CELL_SIZE);
            int cellZ = Math.floorDiv(blockZ, SETTLEMENT_CELL_SIZE);
            int centerX = cellX * SETTLEMENT_CELL_SIZE + SETTLEMENT_CELL_SIZE / 2;
            int centerZ = cellZ * SETTLEMENT_CELL_SIZE + SETTLEMENT_CELL_SIZE / 2;
            String id = kind.name().toLowerCase() + "_" + dimension.replace(':', '_') + "_" + cellX + "_" + cellZ;
            faction = factions.get(id);
            if (faction == null) {
                faction = new NationFaction(id, createName(kind, cellX, cellZ, dimension), dimension, kind, centerX, centerZ);
                factions.put(id, faction);
            }
        }
        if (faction.addCitizen(uuid)) setDirty();
        return faction;
    }

    public void removeCitizen(UUID uuid) {
        boolean changed = false;
        for (NationFaction faction : factions.values()) changed |= faction.removeCitizen(uuid);
        if (changed) setDirty();
    }

    public void simulateAll() {
        boolean changed = false;
        for (NationFaction faction : factions.values()) {
            int before = faction.population();
            faction.simulate();
            changed |= before > 0;
        }
        if (changed) setDirty();
    }

    private static double distanceSquared(int x1, int z1, int x2, int z2) {
        double dx = x1 - x2, dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private static String createName(FactionKind kind, int cellX, int cellZ, String dimension) {
        String[] villagers = {"Oakvale", "Stonebrook", "Meadowhold", "Riverstead", "Greenwatch", "Ironfield", "Pinecrest"};
        String[] illagers = {"Black Banner", "Ashen Dominion", "Ravager Clans", "Obsidian Host", "Dread March", "Iron Covenant"};
        String[] names = kind == FactionKind.VILLAGER ? villagers : illagers;
        int hash = (cellX * 73471) ^ (cellZ * 9127) ^ dimension.hashCode();
        int safe = Math.floorMod(hash, 1_000_000);
        return names[safe % names.length] + " " + (1 + (safe / names.length) % 99);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (NationFaction faction : factions.values()) list.add(faction.save());
        tag.put("Factions", list);
        return tag;
    }

    public static NationSavedData load(CompoundTag tag) {
        NationSavedData data = new NationSavedData();
        ListTag list = tag.getList("Factions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            NationFaction faction = NationFaction.load(list.getCompound(i));
            data.factions.put(faction.id(), faction);
        }
        return data;
    }
}
