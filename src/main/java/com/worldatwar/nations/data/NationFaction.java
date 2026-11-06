package com.worldatwar.nations.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistent state and autonomous economic decision model for one civilization. */
public final class NationFaction {
    private static final double MAX_STOCKPILE = 100_000.0;
    private final String id;
    private final String name;
    private final String dimension;
    private final FactionKind kind;
    private final int centerX;
    private final int centerZ;
    private final Set<UUID> citizens = new HashSet<>();
    private final List<String> recentDecisions = new ArrayList<>();
    private double food = 45, wood = 18, stone = 12, iron = 2, knowledge;
    private double prosperity = 50, stability = 75, militaryReadiness = 5;
    private int farms, lumberCamps, quarries, workshops, barracks;
    private int technology = 1, settlementTier = 1;
    private long simulationCount;

    public NationFaction(String id, String name, String dimension, FactionKind kind, int centerX, int centerZ) {
        this.id = id; this.name = name; this.dimension = dimension; this.kind = kind;
        this.centerX = centerX; this.centerZ = centerZ;
        note("Established as an autonomous settlement");
    }
    public String id() { return id; }
    public String name() { return name; }
    public String dimension() { return dimension; }
    public FactionKind kind() { return kind; }
    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public int population() { return citizens.size(); }
    public double food() { return food; }
    public double wood() { return wood; }
    public double stone() { return stone; }
    public double iron() { return iron; }
    public double knowledge() { return knowledge; }
    public double prosperity() { return prosperity; }
    public double stability() { return stability; }
    public double militaryReadiness() { return militaryReadiness; }
    public int technology() { return technology; }
    public int settlementTier() { return settlementTier; }
    public int farms() { return farms; }
    public int lumberCamps() { return lumberCamps; }
    public int quarries() { return quarries; }
    public int workshops() { return workshops; }
    public int barracks() { return barracks; }
    public long simulationCount() { return simulationCount; }
    public Set<UUID> citizens() { return citizens; }
    public List<String> recentDecisions() { return recentDecisions; }
    public boolean addCitizen(UUID uuid) { return citizens.add(uuid); }
    public boolean removeCitizen(UUID uuid) { return citizens.remove(uuid); }
    public boolean hasCitizen(UUID uuid) { return citizens.contains(uuid); }

    /** One abstract national planning/economy step. No world blocks are changed in this alpha. */
    public void simulate() {
        int people = population();
        if (people <= 0) return;
        simulationCount++;
        int workers = Math.max(1, people - Math.max(1, people / 4));
        double efficiency = kind == FactionKind.VILLAGER ? 1.0 : 0.82;
        double foodProduction = people * 1.10 + farms * (kind == FactionKind.VILLAGER ? 3.2 : 2.0);
        double foodConsumption = people * 0.76;
        food = clamp(food + foodProduction - foodConsumption, 0, MAX_STOCKPILE);
        wood = clamp(wood + workers * 0.34 * efficiency + lumberCamps * 1.9, 0, MAX_STOCKPILE);
        stone = clamp(stone + workers * 0.23 * efficiency + quarries * 1.5, 0, MAX_STOCKPILE);
        iron = clamp(iron + workers * 0.055 * efficiency + workshops * 0.55, 0, MAX_STOCKPILE);
        knowledge = clamp(knowledge + people * 0.03 + workshops * 0.30, 0, MAX_STOCKPILE);
        updateWelfare(people);
        makeIndependentDecision(people);
        updateSettlementTier();
    }

    private void updateWelfare(int people) {
        double foodPerPerson = food / Math.max(1, people);
        if (foodPerPerson < 2.0) {
            stability = clamp(stability - 3, 0, 100);
            prosperity = clamp(prosperity - 2, 0, 100);
        } else if (foodPerPerson > 8.0) {
            stability = clamp(stability + 0.8, 0, 100);
            prosperity = clamp(prosperity + 1, 0, 100);
        } else stability = clamp(stability + 0.2, 0, 100);
        if (barracks > 0) militaryReadiness = clamp(militaryReadiness + 0.35, 0, 100);
        else militaryReadiness = clamp(militaryReadiness - 0.1, 0, 100);
    }

    private void makeIndependentDecision(int people) {
        double foodTarget = Math.max(35, people * 8.0);
        boolean illager = kind == FactionKind.ILLAGER;
        if (food < foodTarget && wood >= 8 && stone >= 3) {
            wood -= 8; stone -= 3; farms++;
            note("National council prioritized food security and approved a farm project");
        } else if (wood < 28 && stone >= 2) {
            stone -= 2; lumberCamps++;
            note("Expanded timber production with a lumber camp");
        } else if (stone < 24 && wood >= 5) {
            wood -= 5; quarries++;
            note("Expanded stone production with a quarry");
        } else if (illager && people >= 3 && food >= 45 && wood >= 16 && stone >= 10 && barracks < 2) {
            food -= 8; wood -= 14; stone -= 8; barracks++;
            note("War council invested in military infrastructure");
        } else if (!illager && people >= 5 && food >= 60 && wood >= 18 && stone >= 12 && barracks == 0) {
            food -= 10; wood -= 14; stone -= 9; barracks++;
            note("Established a defensive barracks to protect the settlement");
        } else if (iron >= 9 && knowledge >= 7 && wood >= 10) {
            iron -= 5; knowledge -= 5; wood -= 10; workshops++;
            note("Invested in a workshop to improve industry and research");
        } else if (knowledge >= 18.0 * technology) {
            knowledge -= 18.0 * technology; technology++;
            note("Advanced to technology level " + technology);
        } else if (food > 100 && wood > 35 && stone > 25 && farms < 4) {
            wood -= 10; stone -= 5; farms++;
            note("Expanded food production to support future growth");
        } else if (prosperity > 65 && stability < 90 && food > 30) {
            stability = clamp(stability + 2, 0, 100);
            note("Prioritized public order and settlement stability");
        } else if (simulationCount % 5 == 0) note("Maintained reserves and deferred major construction");
    }

    private void updateSettlementTier() {
        int infrastructure = farms + lumberCamps + quarries + workshops + barracks;
        int nextTier = 1 + infrastructure / 4;
        if (nextTier > settlementTier) {
            settlementTier = nextTier;
            note("Settlement developed into tier " + settlementTier);
        }
    }
    private void note(String message) {
        recentDecisions.add(0, message);
        while (recentDecisions.size() > 8) recentDecisions.remove(recentDecisions.size() - 1);
    }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id); tag.putString("Name", name); tag.putString("Dimension", dimension);
        tag.putString("Kind", kind.name()); tag.putInt("CenterX", centerX); tag.putInt("CenterZ", centerZ);
        tag.putDouble("Food", food); tag.putDouble("Wood", wood); tag.putDouble("Stone", stone);
        tag.putDouble("Iron", iron); tag.putDouble("Knowledge", knowledge);
        tag.putDouble("Prosperity", prosperity); tag.putDouble("Stability", stability);
        tag.putDouble("MilitaryReadiness", militaryReadiness);
        tag.putInt("Farms", farms); tag.putInt("LumberCamps", lumberCamps); tag.putInt("Quarries", quarries);
        tag.putInt("Workshops", workshops); tag.putInt("Barracks", barracks);
        tag.putInt("Technology", technology); tag.putInt("SettlementTier", settlementTier);
        tag.putLong("SimulationCount", simulationCount);
        ListTag citizensTag = new ListTag();
        for (UUID citizen : citizens) {
            CompoundTag citizenTag = new CompoundTag();
            citizenTag.putUUID("UUID", citizen); citizensTag.add(citizenTag);
        }
        tag.put("Citizens", citizensTag);
        ListTag decisionsTag = new ListTag();
        for (String decision : recentDecisions) decisionsTag.add(net.minecraft.nbt.StringTag.valueOf(decision));
        tag.put("RecentDecisions", decisionsTag);
        return tag;
    }

    public static NationFaction load(CompoundTag tag) {
        FactionKind kind = FactionKind.valueOf(tag.getString("Kind"));
        NationFaction faction = new NationFaction(tag.getString("Id"), tag.getString("Name"),
                tag.getString("Dimension"), kind, tag.getInt("CenterX"), tag.getInt("CenterZ"));
        faction.food = tag.getDouble("Food"); faction.wood = tag.getDouble("Wood");
        faction.stone = tag.getDouble("Stone"); faction.iron = tag.getDouble("Iron");
        faction.knowledge = tag.getDouble("Knowledge");
        if (tag.contains("Prosperity", Tag.TAG_DOUBLE)) faction.prosperity = tag.getDouble("Prosperity");
        if (tag.contains("Stability", Tag.TAG_DOUBLE)) faction.stability = tag.getDouble("Stability");
        if (tag.contains("MilitaryReadiness", Tag.TAG_DOUBLE)) faction.militaryReadiness = tag.getDouble("MilitaryReadiness");
        faction.farms = tag.getInt("Farms"); faction.lumberCamps = tag.getInt("LumberCamps");
        faction.quarries = tag.getInt("Quarries"); faction.workshops = tag.getInt("Workshops");
        faction.barracks = tag.getInt("Barracks");
        faction.technology = Math.max(1, tag.getInt("Technology"));
        faction.settlementTier = Math.max(1, tag.getInt("SettlementTier"));
        faction.simulationCount = tag.getLong("SimulationCount");
        faction.citizens.clear();
        ListTag citizensTag = tag.getList("Citizens", Tag.TAG_COMPOUND);
        for (int i = 0; i < citizensTag.size(); i++) {
            CompoundTag citizenTag = citizensTag.getCompound(i);
            if (citizenTag.hasUUID("UUID")) faction.citizens.add(citizenTag.getUUID("UUID"));
        }
        faction.recentDecisions.clear();
        ListTag decisionsTag = tag.getList("RecentDecisions", Tag.TAG_STRING);
        for (int i = 0; i < decisionsTag.size(); i++) faction.recentDecisions.add(decisionsTag.getString(i));
        return faction;
    }
}
