package org.richardcarter.chunkpurge;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

public class WorldTickHandler {
    private final HashMap<WorldServer, WorldTickData> worldTickData = new HashMap<>();
    private final WeakHashMap<WorldServer, Set<Long>> droppedChunksSets = new WeakHashMap<>();

    private static class WorldTickData {
        long lastTick;
        int tickTimer;
        boolean turnSaveOff;
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!(event.world instanceof WorldServer)) {
            return;
        }

        WorldServer world = (WorldServer) event.world;

        if (event.phase == Phase.START) {
            onWorldTickStart(world);
        } else if (event.phase == Phase.END) {
            onWorldTickEnd(world);
        }
    }

    private void onWorldTickStart(WorldServer world) {
        determineIfWorldSaveShouldTurnOff(world);
    }

    private int getPendingDroppedChunks(WorldServer world) {
        ChunkProviderServer chunkProvider = world.getChunkProvider();
        Set<Long> droppedChunksSet = droppedChunksSets.get(world);
        if (droppedChunksSet == null) {
            try {
                Field fieldDroppedChunksSet = ChunkProviderServer.class.getDeclaredField("droppedChunksSet");
                fieldDroppedChunksSet.setAccessible(true);
                droppedChunksSet = (Set<Long>) fieldDroppedChunksSet.get(chunkProvider);
                droppedChunksSets.put(world, droppedChunksSet);
            } catch (Exception e) {
                ChunkPurgeMod.log.warn("Reflection error, not doing auto save behavior", e);
            }
        }

        if (droppedChunksSet == null) {
            return 0;
        }

        return droppedChunksSet.size();
    }

    private void determineIfWorldSaveShouldTurnOff(WorldServer world) {
        if (world.disableLevelSaving || !ChunkPurgeConfig.autoSaveHandlingEnabled) {
            return;
        }

        WorldTickData worldTickData = this.worldTickData.computeIfAbsent(world, w -> new WorldTickData());
        // ChunkProviderServer.tick() will only unload up to 100 chunks in a single tick. If more then 100 are pending,
        // we should wait to turn save off and check again next tick.
        worldTickData.turnSaveOff = getPendingDroppedChunks(world) < 100;

    }

    private void onWorldTickEnd(WorldServer world) {
        doAutoChunkPurge(world);
        doUpdateSaveState(world);
    }

    private void doAutoChunkPurge(WorldServer world) {
        WorldTickData tickData = worldTickData.computeIfAbsent(world, (k) -> new WorldTickData());
        tickData.lastTick = MinecraftServer.getCurrentTimeMillis();

        if (!ChunkPurgeConfig.autoChunkPurgeEnabled) {
            tickData.tickTimer = 0;
            return;
        }

        tickData.tickTimer++;

        if (tickData.tickTimer < ChunkPurgeConfig.autoChunkPurgeInterval) {
            return;
        }

        tickData.tickTimer = 0;

        MinecraftServer server = world.getMinecraftServer();
        if (server != null) {
            PlayerList playerList = server.getPlayerList();
            List<EntityPlayerMP> allPlayers = playerList.getPlayers();
            // I'm not sure if it ever includes a FakePlayer, but just in case, filter it out
            List<EntityPlayerMP> realPlayers = allPlayers.stream()
                    .filter(p -> !(p instanceof FakePlayer))
                    .collect(Collectors.toList());
            if (realPlayers.isEmpty()) {
                // nobody is online, skip unloading
                return;
            }
        }

        WorldChunkUnloader worldChunkUnloader = new WorldChunkUnloader(world);
        worldChunkUnloader.unloadChunks();
    }

    private void doUpdateSaveState(WorldServer world) {
        if (!ChunkPurgeConfig.autoSaveHandlingEnabled) {
            return;
        }

        if (AromaBackupProxy.isBackupRunning()) {
            // don't want to manipulate save logic while backup is running
            return;
        }

        WorldTickData worldTickData = this.worldTickData.computeIfAbsent(world, w -> new WorldTickData());
        if (!world.disableLevelSaving && worldTickData.turnSaveOff) {
            world.disableLevelSaving = true;
            if (ChunkPurgeConfig.debug) {
                ChunkPurgeMod.log.info("Disabled saving for " + world.provider.getDimensionType().getName() + " (" + world.provider.getDimension() + ")");
            }
        } else if (world.disableLevelSaving) {
            int pendingDroppedChunks = getPendingDroppedChunks(world);
            if (pendingDroppedChunks >= ChunkPurgeConfig.minChunksToSave) {
                world.disableLevelSaving = false;
                if (ChunkPurgeConfig.debug) {
                    ChunkPurgeMod.log.info("Enabled saving for " + world.provider.getDimensionType().getName() + " (" + world.provider.getDimension() + ")");
                }
            }
        }
    }
}
