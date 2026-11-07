package com.worldatwar.nations.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.Map;
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
    private final Map<UUID, NationJob> citizenJobs = new java.util.HashMap<>();
    private final List<String> recentDecisions = new ArrayList<>();
    private double food = 45, wood = 18, stone = 12, iron = 2, knowledge;
    private double prosperity = 50, stability = 75, militaryReadiness = 5;
    private int farms, lumberCamps, quarries, workshops, barracks;
    private int technology = 1, settlementTier = 1;
    // Strategic reserve troops are not spawned entities. Housing comes from completed physical shelters.
    private int reserveTroops;
    private int completedShelters;
    private long simulationCount;
    private NationPolicy policy = NationPolicy.BALANCED;

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
    /** Estimated strategic influence radius in blocks; this is not a physical border. */
    public int territoryRadius() {
        int populationContribution = Math.min(160, population() * 4);
        return Math.min(512, 64 + settlementTier * 32 + populationContribution);
    }
    public int farms() { return farms; }
    public int lumberCamps() { return lumberCamps; }
    public int quarries() { return quarries; }
    public int workshops() { return workshops; }
    public int barracks() { return barracks; }
    public int reserveTroops() { return reserveTroops; }
    public int completedShelters() { return completedShelters; }
    public int housingCapacity() { return 2 + completedShelters * 2; }
    public int housingShortfall() { return Math.max(0, population() - housingCapacity()); }
    public void updateCompletedShelters(int count) { completedShelters = Math.max(0, count); }
    public long simulationCount() { return simulationCount; }
    public NationPolicy policy() { return policy; }
    public Set<UUID> citizens() { return citizens; }
    public List<String> recentDecisions() { return recentDecisions; }
    public boolean addCitizen(UUID uuid) {
        boolean added = citizens.add(uuid);
        if (added) citizenJobs.put(uuid, NationJob.GENERAL_WORKER);
        return added;
    }
    public boolean removeCitizen(UUID uuid) {
        citizenJobs.remove(uuid);
        return citizens.remove(uuid);
    }
    public NationJob jobOf(UUID uuid) { return citizenJobs.getOrDefault(uuid, NationJob.GENERAL_WORKER); }
    public int workersIn(NationJob job) {
        int count = 0;
        for (NationJob assigned : citizenJobs.values()) if (assigned == job) count++;
        return count;
    }

    /** Reassigns occupations from national needs; this is a strategic assignment, not yet a physical NPC work AI. */
    private void assignJobs(int people) {
        citizenJobs.keySet().retainAll(citizens);
        List<UUID> ordered = new ArrayList<>(citizens);
        ordered.sort(UUID::compareTo);
        int index = 0;
        int farmersNeeded = Math.max(1, (int) Math.ceil(people * (policy == NationPolicy.FOOD_SECURITY ? 0.40 : 0.25)));
        int guardsNeeded = barracks > 0 ? Math.max(1, Math.min(people / 3, barracks * 2)) : 0;
        int lumberNeeded = lumberCamps > 0 ? Math.max(1, people / 5) : 0;
        int minersNeeded = quarries > 0 ? Math.max(1, people / 6) : 0;
        int craftersNeeded = workshops > 0 ? Math.max(1, people / 6) : 0;
        int scholarsNeeded = policy == NationPolicy.RESEARCH ? Math.max(1, people / 8) : 0;
        for (UUID citizen : ordered) {
            NationJob job;
            if (index < farmersNeeded) job = NationJob.FARMER;
            else if (index < farmersNeeded + guardsNeeded) job = NationJob.GUARD;
            else if (index < farmersNeeded + guardsNeeded + lumberNeeded) job = NationJob.LUMBERJACK;
            else if (index < farmersNeeded + guardsNeeded + lumberNeeded + minersNeeded) job = NationJob.MINER;
            else if (index < farmersNeeded + guardsNeeded + lumberNeeded + minersNeeded + craftersNeeded) job = NationJob.CRAFTSMAN;
            else if (index < farmersNeeded + guardsNeeded + lumberNeeded + minersNeeded + craftersNeeded + scholarsNeeded) job = NationJob.SCHOLAR;
            else job = NationJob.GENERAL_WORKER;
            citizenJobs.put(citizen, job);
            index++;
        }
    }
    /** Transfers a small amount of surplus stock to a partner; returns the quantity moved. */
    double exportSurplusFood() {
        double amount = Math.min(3.0, Math.max(0.0, food - Math.max(30.0, population() * 7.0)));
        food -= amount;
        return amount;
    }
    void receiveFood(double amount) { food = clamp(food + amount, 0, MAX_STOCKPILE); }
    double exportSurplusWood() {
        double amount = Math.min(2.0, Math.max(0.0, wood - 20.0));
        wood -= amount;
        return amount;
    }
    void receiveWood(double amount) { wood = clamp(wood + amount, 0, MAX_STOCKPILE); }
    double exportSurplusStone() {
        double amount = Math.min(2.0, Math.max(0.0, stone - 18.0));
        stone -= amount;
        return amount;
    }
    void receiveStone(double amount) { stone = clamp(stone + amount, 0, MAX_STOCKPILE); }
    double exportSurplusIron() {
        double amount = Math.min(1.0, Math.max(0.0, iron - 5.0));
        iron -= amount;
        return amount;
    }
    void receiveIron(double amount) { iron = clamp(iron + amount, 0, MAX_STOCKPILE); }

    public boolean hasCitizen(UUID uuid) { return citizens.contains(uuid); }

    /** Adds iron credited by a physical miner working a designated ore site. */
    public void recordPhysicalIronMining(double amount) {
        iron = clamp(iron + Math.max(0.0, amount), 0, MAX_STOCKPILE);
        prosperity = clamp(prosperity + 0.04, 0, 100);
    }

    /** Adds stone credited by a physical miner working a designated quarry site. */
    /** Pays for one physical construction project from the national timber stockpile. */
    public boolean spendWoodForConstruction(double amount) {
        if (amount < 0 || wood < amount) return false;
        wood -= amount;
        prosperity = clamp(prosperity + 0.1, 0, 100);
        note("Funded a physical settlement construction project");
        return true;
    }

    public void recordPhysicalStoneMining(double amount) {
        stone = clamp(stone + Math.max(0.0, amount), 0, MAX_STOCKPILE);
        prosperity = clamp(prosperity + 0.03, 0, 100);
    }

    /** One abstract national planning/economy step. No world blocks are changed in this alpha. */
    /** Adds knowledge produced by a scholar working at a physical workshop. */
    public void recordPhysicalResearch(double amount) {
        knowledge = clamp(knowledge + Math.max(0.0, amount), 0, MAX_STOCKPILE);
        prosperity = clamp(prosperity + 0.02, 0, 100);
    }

    /** Adds food produced by a physical worker action in the loaded world. */
    public void recordPhysicalFoodHarvest(double amount) {
        food = clamp(food + Math.max(0.0, amount), 0, MAX_STOCKPILE);
        prosperity = clamp(prosperity + 0.05, 0, 100);
    }

    /** Adds timber gathered by a physical lumberjack action in the loaded world. */
    public void recordPhysicalWoodHarvest(double amount) {
        wood = clamp(wood + Math.max(0.0, amount), 0, MAX_STOCKPILE);
        prosperity = clamp(prosperity + 0.03, 0, 100);
    }

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
        trainReserveTroops(people);
        updatePolicy(people);
        assignJobs(people);
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
        if (housingShortfall() > 0) {
            stability = clamp(stability - Math.min(2.0, housingShortfall() * 0.25), 0, 100);
        }
        if (barracks > 0) militaryReadiness = clamp(militaryReadiness + 0.35, 0, 100);
        else militaryReadiness = clamp(militaryReadiness - 0.1, 0, 100);
    }

    /** Trains an abstract reserve only when supplied and housed; no mobs are spawned here. */
    private void trainReserveTroops(int people) {
        int limit = Math.min(people / 3, barracks * 3);
        if (reserveTroops > limit) reserveTroops = limit;
        if (reserveTroops < limit && food >= Math.max(30.0, people * 4.0)
                && iron >= 2.0 && stability >= 35 && housingShortfall() == 0) {
            food -= 2.0;
            iron -= 1.0;
            reserveTroops++;
            militaryReadiness = clamp(militaryReadiness + 0.5, 0, 100);
            note("Trained a reserve defender (strategic roster only)");
        }
    }

    private void updatePolicy(int people) {
        NationPolicy next;
        double foodPerPerson = food / Math.max(1, people);
        if (foodPerPerson < 5.0 || food < Math.max(45, people * 6.0)) {
            next = NationPolicy.FOOD_SECURITY;
        } else if (kind == FactionKind.ILLAGER && people >= 3 && barracks < 2 && militaryReadiness < 65) {
            next = NationPolicy.MILITARY_EXPANSION;
        } else if (kind == FactionKind.VILLAGER && people >= 5 && barracks == 0 && militaryReadiness < 30) {
            next = NationPolicy.DEFENSE;
        } else if (iron >= 7 && knowledge >= 5 && workshops < 2) {
            next = NationPolicy.INDUSTRIALIZATION;
        } else if (knowledge >= 18.0 * technology) {
            next = NationPolicy.RESEARCH;
        } else {
            next = NationPolicy.BALANCED;
        }
        if (next != policy) {
            policy = next;
            note("National leadership shifted its strategic policy to " + policy.name().toLowerCase().replace('_', ' '));
        }
    }

    private void makeIndependentDecision(int people) {
        double foodTarget = Math.max(35, people * 8.0);
        boolean illager = kind == FactionKind.ILLAGER;

        if (policy == NationPolicy.FOOD_SECURITY && food < foodTarget && wood >= 8 && stone >= 3) {
            wood -= 8; stone -= 3; farms++;
            note("Food security policy funded a new farm");
        } else if (policy == NationPolicy.MILITARY_EXPANSION && people >= 3
                && food >= 45 && wood >= 16 && stone >= 10 && barracks < 2) {
            food -= 8; wood -= 14; stone -= 8; barracks++;
            note("War council approved new military infrastructure");
        } else if (policy == NationPolicy.DEFENSE && people >= 5
                && food >= 60 && wood >= 18 && stone >= 12 && barracks == 0) {
            food -= 10; wood -= 14; stone -= 9; barracks++;
            note("Defense council established a protective barracks");
        } else if (policy == NationPolicy.INDUSTRIALIZATION && iron >= 9 && knowledge >= 7 && wood >= 10) {
            iron -= 5; knowledge -= 5; wood -= 10; workshops++;
            note("Industrial policy funded a new workshop");
        } else if (policy == NationPolicy.RESEARCH && knowledge >= 18.0 * technology) {
            knowledge -= 18.0 * technology; technology++;
            note("Research council advanced technology to level " + technology);
        } else if (food < foodTarget && wood >= 8 && stone >= 3) {
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
        tag.putInt("ReserveTroops", reserveTroops);
        tag.putInt("CompletedShelters", completedShelters);
        tag.putInt("Technology", technology); tag.putInt("SettlementTier", settlementTier);
        tag.putLong("SimulationCount", simulationCount);
        tag.putString("Policy", policy.name());
        ListTag citizensTag = new ListTag();
        for (UUID citizen : citizens) {
            CompoundTag citizenTag = new CompoundTag();
            citizenTag.putUUID("UUID", citizen); citizenTag.putString("Job", jobOf(citizen).name()); citizensTag.add(citizenTag);
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
        faction.reserveTroops = Math.max(0, tag.getInt("ReserveTroops"));
        faction.completedShelters = Math.max(0, tag.getInt("CompletedShelters"));
        faction.technology = Math.max(1, tag.getInt("Technology"));
        faction.settlementTier = Math.max(1, tag.getInt("SettlementTier"));
        faction.simulationCount = tag.getLong("SimulationCount");
        if (tag.contains("Policy", Tag.TAG_STRING)) {
            try { faction.policy = NationPolicy.valueOf(tag.getString("Policy")); }
            catch (IllegalArgumentException ignored) { faction.policy = NationPolicy.BALANCED; }
        }
        faction.citizens.clear();
        ListTag citizensTag = tag.getList("Citizens", Tag.TAG_COMPOUND);
        for (int i = 0; i < citizensTag.size(); i++) {
            CompoundTag citizenTag = citizensTag.getCompound(i);
            if (citizenTag.hasUUID("UUID")) {
                UUID uuid = citizenTag.getUUID("UUID");
                faction.citizens.add(uuid);
                if (citizenTag.contains("Job", Tag.TAG_STRING)) {
                    try { faction.citizenJobs.put(uuid, NationJob.valueOf(citizenTag.getString("Job"))); }
                    catch (IllegalArgumentException ignored) { faction.citizenJobs.put(uuid, NationJob.GENERAL_WORKER); }
                } else faction.citizenJobs.put(uuid, NationJob.GENERAL_WORKER);
            }
        }
        faction.recentDecisions.clear();
        ListTag decisionsTag = tag.getList("RecentDecisions", Tag.TAG_STRING);
        for (int i = 0; i < decisionsTag.size(); i++) faction.recentDecisions.add(decisionsTag.getString(i));
        return faction;
    }
}
