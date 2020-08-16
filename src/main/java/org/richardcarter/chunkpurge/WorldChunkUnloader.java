package org.richardcarter.chunkpurge;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Value;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.Level;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * A class to handle the unloading of excess chunks from a WorldServer.
 * Excess loaded chunks are those that are currently not within a player's view distance,
 * forced by a chunk loader, or loaded by the world's spawn area.
 */
public class WorldChunkUnloader
{

    private final WorldServer world;

    public WorldChunkUnloader (WorldServer world) {
        this.world = world;
    }


    /*
     * A flood fill algorithm to find the shape of the loaded chunks surrounding a player-occupied chunk, or seed.
     * Will not return chunks that are further than radiusLimit from the seed. Set radiusLimit to 0 in order to
     * ignore any limit.
     *
     * 1. Set Q to the empty queue.
     * 2. If the color of node is not equal to target-color, return.
     * 3. Add node to Q.
     * 4. For each element N of Q:
     * 5.     If the color of N is equal to target-color:
     * 6.         Set w and e equal to N.
     * 7.         Move w to the west until the color of the node to the west of w no longer matches target-color.
     * 8.         Move e to the east until the color of the node to the east of e no longer matches target-color.
     * 9.         For each node n between w and e:
     * 10.             Set the color of n to replacement-color.
     * 11.             If the color of the node to the north of n is target-color, add that node to Q.
     * 12.             If the color of the node to the south of n is target-color, add that node to Q.
     * 13. Continue looping until Q is exhausted.
     * 14. Return
     */
    @VisibleForTesting
    static Set<ChunkPos> groupedChunksFinder(Set<ChunkPos> loadedChunks, ChunkPos seed, int radiusLimit)
    {

        LinkedList<ChunkPos> queue = new LinkedList<>();
        Set<ChunkPos> groupedChunks = new HashSet<>();

        if (!loadedChunks.contains(seed)) return groupedChunks;
        queue.add(seed);

        while (!queue.isEmpty())
        {

            ChunkPos chunk = queue.remove();

            if (!groupedChunks.contains(chunk))
            {
                int west, east;

                for (west = chunk.x;
                     loadedChunks.contains(new ChunkPos(west-1, chunk.z))
                             && (radiusLimit <= 0 || Math.abs(west-1 - seed.x) <= radiusLimit);
                     --west);

                for (east = chunk.x;
                     loadedChunks.contains(new ChunkPos(east+1, chunk.z))
                             && (radiusLimit <= 0 || Math.abs(east+1 - seed.x) <= radiusLimit);
                     ++east);

                for (int x = west; x <= east; ++x)
                {

                    groupedChunks.add(new ChunkPos(x, chunk.z));

                    if (loadedChunks.contains(new ChunkPos(x, chunk.z+1))
                            && (radiusLimit <= 0 || Math.abs(chunk.z+1 - seed.z) <= radiusLimit))
                    {

                        queue.add(new ChunkPos (x, chunk.z+1));

                    }

                    if (loadedChunks.contains(new ChunkPos(x, chunk.z-1))
                            && (radiusLimit <= 0 || Math.abs(chunk.z-1 - seed.z) <= radiusLimit))
                    {

                        queue.add(new ChunkPos (x, chunk.z-1));

                    }

                }

            }

        }

        return groupedChunks;
    }

    @Value
    private static class ChunksToUnload {
        static final ChunksToUnload EMPTY = new ChunksToUnload(
                ImmutableMap.of(),
                ImmutableSet.of(),
                ImmutableSet.of(),
                ImmutableSet.of());

        Map<ChunkPos, Chunk> unloadThese;

        Set<ChunkPos> playerChunks;
        Set<ChunkPos> ticketChunks;
        Set<ChunkPos> spawnChunks;
    }

    /*
     * Populate chunksToUnload with chunks that are isolated from all players, chunk loaders, and the spawn.
     *
     * Use a flood-fill algorithm to find the set of all loaded chunks in the world which link back
     * to a chunk watcher through other loaded chunks. The idea is to find the isolated chunks
     * which do NOT link back to a valid chunk watcher, and unload those.
     *
     * This is a better alternative to simply unloading all chunks outside of a player's view radius.
     * Unloading chunks while not unloading their neighbours would result in tps-spikes due to the breaking
     * of energy nets and the like. This approach should reduce the severity of those tps-spikes.
     *
     * ChunkProviderServer.loadedChunks is a private field, so an access transformer is required to access it.
     */
    private ChunksToUnload findChunksToUnload()
    {
        ImmutableMap<ChunkPos, Chunk> loadedChunks = world.getChunkProvider().getLoadedChunks().stream()
                .collect(ImmutableMap.toImmutableMap(Chunk::getPos, Function.identity()));
        if (loadedChunks.isEmpty()) {
            return ChunksToUnload.EMPTY;
        }

        Set<ChunkPos> playerChunks = findPlayerChunks(loadedChunks);
        Set<ChunkPos> ticketChunks = findTicketChunks(loadedChunks);
        Set<ChunkPos> spawnChunks = findSpawnChunks(loadedChunks);

        Map<ChunkPos, Chunk> unloadThese = Maps.filterKeys(loadedChunks,
                k -> !playerChunks.contains(k) && !ticketChunks.contains(k) && !spawnChunks.contains(k));

        return new ChunksToUnload(unloadThese, playerChunks, ticketChunks, spawnChunks);
    }

    private Set<ChunkPos> findPlayerChunks(Map<ChunkPos, Chunk> loadedChunks) {
        Builder<ChunkPos> playerChunks = ImmutableSet.builder();

        final int PLAYER_RADIUS = ChunkPurgeConfig.ignoreRadiusPlayer +
                FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getViewDistance();
        for (EntityPlayer player : world.playerEntities)
        {
            if (!(player instanceof FakePlayer))
            {
                ChunkPos playerChunkCoords = new ChunkPos(player.chunkCoordX, player.chunkCoordZ);
                playerChunks.addAll(groupedChunksFinder(loadedChunks.keySet(), playerChunkCoords, PLAYER_RADIUS));
            }
        }
        return playerChunks.build();
    }

    private Set<ChunkPos> findTicketChunks(Map<ChunkPos, Chunk> loadedChunks) {
        Builder<ChunkPos> ticketChunks = ImmutableSet.builder();
        for (ChunkPos coord : world.getPersistentChunks().keySet())
        {
            ticketChunks.addAll(groupedChunksFinder(loadedChunks.keySet(), coord, ChunkPurgeConfig.ignoreRadiusTicket));
        }
        return ticketChunks.build();
    }

    private Set<ChunkPos> findSpawnChunks(Map<ChunkPos, Chunk> loadedChunks) {
        Builder<ChunkPos> spawnChunks = ImmutableSet.builder();
        if (world.provider.canRespawnHere() && world.provider.getDimensionType().shouldLoadSpawn())
        {
            ChunkPos spawnChunkCoords = new ChunkPos(
                    world.getSpawnPoint().getX() / 16,
                    world.getSpawnPoint().getZ() / 16);

            spawnChunks.addAll(groupedChunksFinder(loadedChunks.keySet(), spawnChunkCoords, ChunkPurgeConfig.ignoreRadiusSpawn));
        }
        return spawnChunks.build();
    }

    /*
     * Analyse the chunks that are currently loaded in this world. Select loaded chunks that are isolated from any chunk watchers,
     * and queue these isolated chunks for unloading.
     */
    public int unloadChunks()
    {

        long initialTime = MinecraftServer.getCurrentTimeMillis();

        world.profiler.startSection("ChunkPurge");

        ChunksToUnload chunksToUnload = findChunksToUnload();

        if (!chunksToUnload.unloadThese.isEmpty()) {
            ChunkProviderServer chunkProvider = this.world.getChunkProvider();
            for (Entry<ChunkPos, Chunk> chunkToUnload : chunksToUnload.unloadThese.entrySet()) {
                chunkProvider.queueUnload(chunkToUnload.getValue());
            }
        }

        world.profiler.endSection();

        if (ChunkPurgeConfig.debug && !chunksToUnload.unloadThese.isEmpty()) {
            String logMessage = "Queued " + chunksToUnload.unloadThese.size()
                    + " chunks out of " + world.getChunkProvider().getLoadedChunks().size()
                    + " for unload in dim " + this.world.provider.getDimensionType().getName()
                    + " (" + this.world.provider.getDimension()
                    + ") in " + (MinecraftServer.getCurrentTimeMillis() - initialTime)
                    + " ms. ("
                    + chunksToUnload.playerChunks.size() + " p, "
                    + chunksToUnload.ticketChunks.size() + " t, "
                    + chunksToUnload.spawnChunks.size() + " s)";
            ChunkPurgeMod.log.info(logMessage);

        }

        return chunksToUnload.unloadThese.size();
    }

}