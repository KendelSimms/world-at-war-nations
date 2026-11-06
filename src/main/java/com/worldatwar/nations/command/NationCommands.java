package com.worldatwar.nations.command;

import com.worldatwar.nations.data.NationFaction;
import com.worldatwar.nations.data.NationSavedData;
import com.worldatwar.nations.data.NationRelation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.Locale;

public final class NationCommands {
    private NationCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wwn")
                .then(Commands.literal("factions").executes(context -> listFactions(context.getSource())))
                .then(Commands.literal("diplomacy").executes(context -> listDiplomacy(context.getSource())))
                .then(Commands.literal("territory").executes(context -> listTerritory(context.getSource())))
                .then(Commands.literal("worksite")
                        .then(Commands.literal("lumber")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> addLumberWorksite(context.getSource(), StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("farm")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> addFarmWorksite(context.getSource(), StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("mine")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> addMiningWorksite(context.getSource(), StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("remove-mine")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> removeMiningWorksite(context.getSource(), StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("remove-farm")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> removeFarmWorksite(context.getSource(), StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> removeLumberWorksite(context.getSource(), StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> listWorksites(context.getSource(), StringArgumentType.getString(context, "id"))))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> inspectFaction(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("help").executes(context -> help(context.getSource()))));
    }

    private static int addLumberWorksite(CommandSourceStack source, String id) {
        NationSavedData data = NationSavedData.get(source.getServer());
        NationFaction faction = data.find(id).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Unknown faction ID. Use /wwn factions."));
            return 0;
        }
        String dimension = source.getLevel().dimension().location().toString();
        if (!faction.dimension().equals(dimension)) {
            source.sendFailure(Component.literal("You must place this worksite in the faction's dimension."));
            return 0;
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(source.getPosition());
        double dx = pos.getX() - faction.centerX();
        double dz = pos.getZ() - faction.centerZ();
        if (dx * dx + dz * dz > (double) faction.territoryRadius() * faction.territoryRadius()) {
            source.sendFailure(Component.literal("That position is outside the faction's estimated influence radius."));
            return 0;
        }
        if (!data.addLumberWorksite(faction, dimension, pos)) {
            source.sendFailure(Component.literal("A lumber worksite already exists at that position."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Registered lumber worksite for " + faction.name()
                + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + ". Lumberjacks may harvest only within 8 blocks of this marker."), true);
        return 1;
    }

    private static int addFarmWorksite(CommandSourceStack source, String id) {
        NationSavedData data = NationSavedData.get(source.getServer());
        NationFaction faction = data.find(id).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Unknown faction ID. Use /wwn factions."));
            return 0;
        }
        String dimension = source.getLevel().dimension().location().toString();
        if (!faction.dimension().equals(dimension)) {
            source.sendFailure(Component.literal("You must place this worksite in the faction's dimension."));
            return 0;
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(source.getPosition());
        double dx = pos.getX() - faction.centerX();
        double dz = pos.getZ() - faction.centerZ();
        if (dx * dx + dz * dz > (double) faction.territoryRadius() * faction.territoryRadius()) {
            source.sendFailure(Component.literal("That position is outside the faction's estimated influence radius."));
            return 0;
        }
        if (!data.addFarmWorksite(faction, dimension, pos)) {
            source.sendFailure(Component.literal("A farm worksite already exists at that position."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Registered farm worksite for " + faction.name()
                + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + ". Farmers may tend crops only within 8 blocks of this marker."), true);
        return 1;
    }

    private static int addMiningWorksite(CommandSourceStack source, String id) {
        NationSavedData data = NationSavedData.get(source.getServer());
        NationFaction faction = data.find(id).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Unknown faction ID. Use /wwn factions."));
            return 0;
        }
        String dimension = source.getLevel().dimension().location().toString();
        if (!faction.dimension().equals(dimension)) {
            source.sendFailure(Component.literal("You must place this worksite in the faction's dimension."));
            return 0;
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(source.getPosition());
        double dx = pos.getX() - faction.centerX();
        double dz = pos.getZ() - faction.centerZ();
        if (dx * dx + dz * dz > (double) faction.territoryRadius() * faction.territoryRadius()) {
            source.sendFailure(Component.literal("That position is outside the faction's estimated influence radius."));
            return 0;
        }
        if (!data.addMiningWorksite(faction, dimension, pos)) {
            source.sendFailure(Component.literal("A mining worksite already exists at that position."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Registered mining worksite for " + faction.name()
                + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + ". This marks the permitted mining area; miners extract exposed iron ore, stone, and deepslate within it."), true);
        return 1;
    }

    private static int removeMiningWorksite(CommandSourceStack source, String id) {
        NationSavedData data = NationSavedData.get(source.getServer());
        NationFaction faction = data.find(id).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Unknown faction ID. Use /wwn factions."));
            return 0;
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(source.getPosition());
        String dimension = source.getLevel().dimension().location().toString();
        if (!data.removeMiningWorksite(id, dimension, pos)) {
            source.sendFailure(Component.literal("No mining worksite for this faction is within 8 blocks of your position."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Removed the nearest mining worksite for " + faction.name() + "."), true);
        return 1;
    }

    private static int removeFarmWorksite(CommandSourceStack source, String id) {
        NationSavedData data = NationSavedData.get(source.getServer());
        NationFaction faction = data.find(id).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Unknown faction ID. Use /wwn factions."));
            return 0;
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(source.getPosition());
        String dimension = source.getLevel().dimension().location().toString();
        if (!data.removeFarmWorksite(id, dimension, pos)) {
            source.sendFailure(Component.literal("No farm worksite for this faction is within 8 blocks of your position."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Removed the nearest farm worksite for " + faction.name() + "."), true);
        return 1;
    }

    private static int removeLumberWorksite(CommandSourceStack source, String id) {
        NationSavedData data = NationSavedData.get(source.getServer());
        NationFaction faction = data.find(id).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Unknown faction ID. Use /wwn factions."));
            return 0;
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(source.getPosition());
        String dimension = source.getLevel().dimension().location().toString();
        if (!data.removeLumberWorksite(id, dimension, pos)) {
            source.sendFailure(Component.literal("No lumber worksite for this faction is within 8 blocks of your position."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Removed the nearest lumber worksite for " + faction.name() + "."), true);
        return 1;
    }

    private static int listWorksites(CommandSourceStack source, String id) {
        NationSavedData data = NationSavedData.get(source.getServer());
        NationFaction faction = data.find(id).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Unknown faction ID. Use /wwn factions."));
            return 0;
        }
        var sites = data.worksites().stream().filter(site -> site.factionId().equals(id)).toList();
        source.sendSuccess(() -> Component.literal("Worksites for " + faction.name() + ": " + sites.size()), false);
        for (var site : sites) {
            source.sendSuccess(() -> Component.literal("- " + site.type().name().toLowerCase(Locale.ROOT)
                    + " | " + site.dimension() + " | " + site.x() + ", " + site.y() + ", " + site.z()), false);
        }
        return sites.size();
    }

    private static int listFactions(CommandSourceStack source) {
        var factions = NationSavedData.get(source.getServer()).factions().stream()
                .sorted(Comparator.comparing(NationFaction::name)).toList();
        source.sendSuccess(() -> Component.literal("[World At War: Nations] Registered civilizations: " + factions.size()), false);
        if (factions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No civilizations discovered yet. Explore naturally generated villages and illager settlements; their NPCs are registered when their chunks load."), false);
            return 0;
        }
        for (NationFaction faction : factions) {
            String line = String.format(Locale.ROOT, "- %s [%s] | citizens: %d | tech: %d | tier: %d | ID: %s",
                    faction.name(), faction.kind().name().toLowerCase(Locale.ROOT), faction.population(),
                    faction.technology(), faction.settlementTier(), faction.id());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return factions.size();
    }


    private static int listTerritory(CommandSourceStack source) {
        var factions = NationSavedData.get(source.getServer()).factions().stream()
                .sorted(Comparator.comparing(NationFaction::name)).toList();
        source.sendSuccess(() -> Component.literal("[World At War: Nations] Estimated settlement influence: " + factions.size()), false);
        for (NationFaction faction : factions) {
            String line = String.format(Locale.ROOT, "- %s | %s | center %d, %d | influence radius %d blocks",
                    faction.name(), faction.dimension(), faction.centerX(), faction.centerZ(), faction.territoryRadius());
            source.sendSuccess(() -> Component.literal(line), false);
            for (NationFaction other : factions) {
                if (faction.id().compareTo(other.id()) >= 0 || !faction.dimension().equals(other.dimension())) continue;
                double dx = faction.centerX() - other.centerX();
                double dz = faction.centerZ() - other.centerZ();
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance <= faction.territoryRadius() + other.territoryRadius()) {
                    String overlap = String.format(Locale.ROOT,
                            "  * Potentially overlapping claim with %s (centers %.0f blocks apart)", other.name(), distance);
                    source.sendSuccess(() -> Component.literal(overlap), false);
                }
            }
        }
        source.sendSuccess(() -> Component.literal("Influence is a strategic estimate, not a physical border or enforced land claim."), false);
        return factions.size();
    }

    private static int listDiplomacy(CommandSourceStack source) {
        var relations = NationSavedData.get(source.getServer()).relations().stream()
                .sorted(Comparator.comparing(NationRelation::firstFactionId)
                        .thenComparing(NationRelation::secondFactionId)).toList();
        source.sendSuccess(() -> Component.literal("[World At War: Nations] Diplomatic relations: " + relations.size()), false);
        if (relations.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No relations recorded yet. Load at least two civilizations of the same dimension."), false);
            return 0;
        }
        for (NationRelation relation : relations) {
            String firstName = NationSavedData.get(source.getServer()).find(relation.firstFactionId()).map(NationFaction::name).orElse(relation.firstFactionId());
            String secondName = NationSavedData.get(source.getServer()).find(relation.secondFactionId()).map(NationFaction::name).orElse(relation.secondFactionId());
            String line = firstName + " <-> " + secondName + " | " + relation.status().name() + " | opinion " + String.format(Locale.ROOT, "%.1f", relation.score());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return relations.size();
    }

    private static int inspectFaction(CommandSourceStack source, String id) {
        var optional = NationSavedData.get(source.getServer()).find(id);
        if (optional.isEmpty()) {
            source.sendFailure(Component.literal("No civilization found with ID: " + id + ". Use /wwn factions to list known civilizations."));
            return 0;
        }
        NationFaction f = optional.get();
        source.sendSuccess(() -> Component.literal("=== " + f.name() + " ==="), false);
        source.sendSuccess(() -> Component.literal("Type: " + f.kind() + " | Dimension: " + f.dimension()), false);
        source.sendSuccess(() -> Component.literal("Center: " + f.centerX() + ", " + f.centerZ() + " | Citizens: " + f.population() + " | Estimated influence radius: " + f.territoryRadius() + " blocks"), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Stockpiles — Food %.1f | Wood %.1f | Stone %.1f | Iron %.1f | Knowledge %.1f",
                f.food(), f.wood(), f.stone(), f.iron(), f.knowledge())), false);
        source.sendSuccess(() -> Component.literal("Government — Strategic policy " + f.policy().name().toLowerCase(Locale.ROOT).replace('_', ' ')), false);
        source.sendSuccess(() -> Component.literal("Development — Technology " + f.technology() + " | Settlement tier " + f.settlementTier()
                + " | Prosperity " + String.format(Locale.ROOT, "%.1f", f.prosperity())
                + " | Stability " + String.format(Locale.ROOT, "%.1f", f.stability())
                + " | Military readiness " + String.format(Locale.ROOT, "%.1f", f.militaryReadiness())), false);
        source.sendSuccess(() -> Component.literal("Workforce — Farmers " + f.workersIn(com.worldatwar.nations.data.NationJob.FARMER)
                + " | Guards " + f.workersIn(com.worldatwar.nations.data.NationJob.GUARD)
                + " | Lumberjacks " + f.workersIn(com.worldatwar.nations.data.NationJob.LUMBERJACK)
                + " | Miners " + f.workersIn(com.worldatwar.nations.data.NationJob.MINER)
                + " | Craftsmen " + f.workersIn(com.worldatwar.nations.data.NationJob.CRAFTSMAN)
                + " | Scholars " + f.workersIn(com.worldatwar.nations.data.NationJob.SCHOLAR)
                + " | General " + f.workersIn(com.worldatwar.nations.data.NationJob.GENERAL_WORKER)), false);
        source.sendSuccess(() -> Component.literal("Projects — Farms " + f.farms() + " | Lumber camps " + f.lumberCamps()
                + " | Quarries " + f.quarries() + " | Workshops " + f.workshops() + " | Barracks " + f.barracks()), false);
        source.sendSuccess(() -> Component.literal("Recent autonomous decisions:"), false);
        for (String decision : f.recentDecisions()) source.sendSuccess(() -> Component.literal(" • " + decision), false);
        return 1;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("World At War: Nations commands:"), false);
        source.sendSuccess(() -> Component.literal("/wwn factions — list discovered civilizations"), false);
        source.sendSuccess(() -> Component.literal("/wwn inspect <id> — view a civilization's economy and decisions"), false);
        source.sendSuccess(() -> Component.literal("/wwn diplomacy — view relations between civilizations"), false);
        source.sendSuccess(() -> Component.literal("/wwn territory — view estimated influence radii and potential overlaps"), false);
        source.sendSuccess(() -> Component.literal("/wwn worksite lumber <factionId> — designate a lumber worksite at your position"), false);
        source.sendSuccess(() -> Component.literal("/wwn worksite farm <factionId> — designate a farm worksite at your position"), false);
        source.sendSuccess(() -> Component.literal("/wwn worksite mine <factionId> — designate a mining area at your position"), false);
        source.sendSuccess(() -> Component.literal("/wwn worksite remove-mine <factionId> — remove the nearest mining worksite"), false);
        source.sendSuccess(() -> Component.literal("/wwn worksite remove-farm <factionId> — remove the nearest farm worksite"), false);
        source.sendSuccess(() -> Component.literal("/wwn worksite list <factionId> — list a faction's worksites"), false);
        source.sendSuccess(() -> Component.literal("/wwn worksite remove <factionId> — remove the nearest worksite within 8 blocks"), false);
        source.sendSuccess(() -> Component.literal("Civilizations simulate autonomously every 60 in-game seconds. No player orders are required."), false);
        return 1;
    }
}
