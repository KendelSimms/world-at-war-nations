# World At War: Nations — 0.1.7-alpha

Standalone Forge mod for Minecraft Java 1.20.1 / Forge 47.4.10. This is the autonomous-civilization simulation layer for WORLD AT WAR — Global Conflict.

## Current alpha features
- Discovers vanilla Villagers and AbstractIllager entities when they join or load.
- Registers persistent villager and illager civilization records grouped by faction type, dimension, and approximate settlement region.
- Stores faction records in overworld SavedData.
- Tracks observed living NPC UUIDs and removes citizens on death.
- Runs an autonomous abstract economy every 1,200 overworld ticks (60 in-game seconds).
- Simulates food, wood, stone, iron, knowledge, prosperity, stability, military readiness, investment priorities, and technology.
- Selects and persists an autonomous national strategic policy: food security, industrialization, defense, military expansion, research, or balanced development.
- Shows each civilization's current strategic policy in `/wwn inspect <id>`.
- Assigns each known citizen a saved strategic occupation: farmer, guard, lumberjack, miner, craftsman, scholar, or general worker.
- Rebalances workforce occupations according to food needs, infrastructure, and national policy.
- Displays workforce distribution in `/wwn inspect <id>`.
- Tracks persistent bilateral relations between active civilizations in the same dimension.
- Autonomously shifts diplomatic opinion based on faction type, prosperity, stability, and military readiness.
- Classifies relations as alliance, trade pact, peace, tense, rivalry, or war.
- Adds `/wwn diplomacy` to inspect known relations by settlement name, status, and opinion score.
- Enables autonomous surplus food, wood, stone, and iron transfers between civilizations with trade pacts or alliances.
- Calculates an estimated influence radius for each settlement from its population and development tier.
- Adds `/wwn territory` to list settlement centers, influence radii, and potential overlaps with nearby settlements in the same dimension.
- Shows estimated territory radius directly in `/wwn inspect <id>`.
- Trade exports preserve minimum national reserves and never intentionally export below configured safety stockpiles.
- Adds `/wwn factions`, `/wwn inspect <id>`, and `/wwn help`.

## Important limitations
This is an early simulation alpha, not a finished civilization AI. Territory is an estimated strategic influence radius, not a physical border or enforced land claim. Buildings and projects are abstract counters; the mod does not physically harvest blocks, place structures, assign visible NPC jobs, spawn citizens, run trade caravans, physically enforce diplomacy, spawn physical trade caravans, physically enforce treaties, or conduct combat. Friendly trade currently transfers abstract surplus stockpiles during the strategic simulation. Diplomatic statuses are currently strategic simulation data and do not yet trigger attacks, raids, or treaties with in-world effects. Physical work behavior remains a future milestone: assigned occupations currently represent national workforce planning and do not yet make NPCs perform block harvesting, construction, or job-site tasks. Population counts are observed living NPC UUIDs, not simulated demographic estimates.

A settlement is discovered only when its NPCs load. Known factions continue abstract simulation while chunks are unloaded as long as the server is running. Simulation does not advance while the server is shut down.

## Build
Requires Java 17 and internet access for the first build. Windows: run `build.bat`; Linux/macOS: run `./build.sh`. GitHub Actions builds the project and uploads the JAR artifact.

## Install
After a successful build, put the JAR from `build/libs/` into the `mods` folder of a Minecraft 1.20.1 Forge 47.4.10 profile. Test on a copy of the profile; do not overwrite the known-good WORLD AT WAR v0.1.7 setup.

## First test
1. Start a new Forge 1.20.1 test world.
2. Load a village and an illager outpost/patrol so their entities are observed.
3. Run `/wwn factions`, then `/wwn inspect <id>`.
4. Wait five in-game minutes and inspect again.
5. Save, quit, relaunch, and verify records persist.

See BUILD_STATUS.md and DEVELOPMENT_NOTES.md for build and implementation notes.
