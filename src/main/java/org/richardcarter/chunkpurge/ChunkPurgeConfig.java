package org.richardcarter.chunkpurge;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.RangeInt;

@Config(modid = ChunkPurgeMod.MODID)
public class ChunkPurgeConfig {
    @Comment("Enable the automatic chunk purge behavior")
    public static boolean autoChunkPurgeEnabled = true;

    @Comment("Interval (in ticks) between chunk purge scans")
    @RangeInt(min = 1)
    public static int autoChunkPurgeInterval = 600;

    @Comment("Enable debug stuff")
    public static boolean debug = false;

    @Comment("Command name to register for in game configuration")
    public static String commandChunkPurge = "chunkpurge";

    @Comment("Additional ignore radius around a player (in addition to the player view distance)")
    public static int ignoreRadiusPlayer = 4;

    @Comment("Ignore radius around a force loaded (ticket) chunk")
    public static int ignoreRadiusTicket = 5;

    @Comment("Ignore radius around spawn chunks")
    public static int ignoreRadiusSpawn = 3;

    @Comment("Enable the automatic save on/off behavior")
    public static boolean autoSaveHandlingEnabled = true;

    @Comment("Number of pending unloading chunks to turn save on")
    public static int minChunksToSave = 100;
}
