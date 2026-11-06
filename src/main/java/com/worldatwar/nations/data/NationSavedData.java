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
    private final Map<String, NationRelation> relations = new LinkedHashMap<>();

    public static NationSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(NationSavedData::load, NationSavedData::new, DATA_NAME);
    }

    public Collection<NationFaction> factions() { return factions.values(); }
    public Collection<NationRelation> relations() { return relations.values(); }
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
        updateDiplomacy();
        if (changed || !relations.isEmpty()) setDirty();
    }

    private void updateDiplomacy() {
        java.util.List<NationFaction> active = factions.values().stream()
                .filter(faction -> faction.population() > 0).toList();
        for (int i = 0; i < active.size(); i++) {
            NationFaction first = active.get(i);
            for (int j = i + 1; j < active.size(); j++) {
                NationFaction second = active.get(j);
                if (!first.dimension().equals(second.dimension())) continue;
                String key = relationKey(first.id(), second.id());
                NationRelation relation = relations.get(key);
                if (relation == null) {
                    double initial = first.kind() == second.kind() ? 12.0 : -38.0;
                    relation = new NationRelation(first.id(), second.id(), initial);
                    relations.put(key, relation);
                }
                double change;
                if (first.kind() != second.kind()) {
                    double combinedReadiness = first.militaryReadiness() + second.militaryReadiness();
                    change = combinedReadiness >= 60 ? -2.0 : -0.35;
                    if (first.prosperity() > 75 && second.prosperity() > 75) change += 0.15;
                } else {
                    change = first.prosperity() > 60 && second.prosperity() > 60 ? 0.8 : 0.2;
                    if (first.stability() < 25 || second.stability() < 25) change -= 0.5;
                }
                relation.adjust(change);

                // Friendly civilizations exchange only genuine surplus; no stockpile can go negative.
                if (relation.status() == NationRelationStatus.TRADE_PACT
                        || relation.status() == NationRelationStatus.ALLIANCE) {
                    tradeSurplus(first, second);
                    tradeSurplus(second, first);
                }
            }
        }
    }

    private static void tradeSurplus(NationFaction exporter, NationFaction importer) {
        double food = exporter.exportSurplusFood();
        double wood = exporter.exportSurplusWood();
        double stone = exporter.exportSurplusStone();
        double iron = exporter.exportSurplusIron();
        importer.receiveFood(food);
        importer.receiveWood(wood);
        importer.receiveStone(stone);
        importer.receiveIron(iron);
    }

    private static String relationKey(String first, String second) {
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
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
        ListTag relationList = new ListTag();
        for (NationRelation relation : relations.values()) relationList.add(relation.save());
        tag.put("Relations", relationList);
        return tag;
    }

    public static NationSavedData load(CompoundTag tag) {
        NationSavedData data = new NationSavedData();
        ListTag list = tag.getList("Factions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            NationFaction faction = NationFaction.load(list.getCompound(i));
            data.factions.put(faction.id(), faction);
        }
        ListTag relationList = tag.getList("Relations", Tag.TAG_COMPOUND);
        for (int i = 0; i < relationList.size(); i++) {
            NationRelation relation = NationRelation.load(relationList.getCompound(i));
            data.relations.put(relationKey(relation.firstFactionId(), relation.secondFactionId()), relation);
        }
        return data;
    }
}
