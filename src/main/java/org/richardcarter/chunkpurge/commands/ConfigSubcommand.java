package org.richardcarter.chunkpurge.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.config.Config.Type;
import net.minecraftforge.common.config.ConfigManager;
import org.richardcarter.chunkpurge.ChunkPurgeConfig;
import org.richardcarter.chunkpurge.ChunkPurgeMod;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum ConfigSubcommand {
    enablepurge("[true|false]",
            boolHandler("enablepurge",
                    () -> ChunkPurgeConfig.autoChunkPurgeEnabled,
                    (e) -> ChunkPurgeConfig.autoChunkPurgeEnabled = e),
            SubcommandCompletions.BOOLEAN),
    purgeinterval("[ticks]",
            intHandler("purgeinterval",
                    () -> ChunkPurgeConfig.autoChunkPurgeInterval,
                    (i) -> ChunkPurgeConfig.autoChunkPurgeInterval = i),
            SubcommandCompletions.NO_COMPLETIONS),
    debug("[true|false]",
            boolHandler("debug",
                    () -> ChunkPurgeConfig.debug,
                    (d) -> ChunkPurgeConfig.debug = d),
            SubcommandCompletions.BOOLEAN),
    pradius("[chunks]",
            intHandler("pradius",
                    () -> ChunkPurgeConfig.ignoreRadiusPlayer,
                    (r) -> ChunkPurgeConfig.ignoreRadiusPlayer = r),
            SubcommandCompletions.NO_COMPLETIONS),
    tradius("[chunks]",
            intHandler("tradius",
                    () -> ChunkPurgeConfig.ignoreRadiusTicket,
                    (r) -> ChunkPurgeConfig.ignoreRadiusTicket = r),
            SubcommandCompletions.NO_COMPLETIONS),
    sradius("[chunks]",
            intHandler("sradius",
                    () -> ChunkPurgeConfig.ignoreRadiusSpawn,
                    (r) -> ChunkPurgeConfig.ignoreRadiusSpawn = r),
            SubcommandCompletions.NO_COMPLETIONS),
    enablesave("[true|false]",
            boolHandler("enablesave",
                    () -> ChunkPurgeConfig.autoSaveHandlingEnabled,
                    (e) -> ChunkPurgeConfig.autoSaveHandlingEnabled = e),
            SubcommandCompletions.BOOLEAN),
    minchunkstosave("[chunks]",
            intHandler("minchunkstosave",
                    () -> ChunkPurgeConfig.minChunksToSave,
                    (r) -> ChunkPurgeConfig.minChunksToSave = r),
            SubcommandCompletions.NO_COMPLETIONS);

    public final String usageValueExample;
    public final SubcommandHandler handler;
    public final SubcommandCompletions completions;

    ConfigSubcommand(String usageValueExample, SubcommandHandler handler, SubcommandCompletions completions) {
        this.usageValueExample = usageValueExample;
        this.handler = handler;
        this.completions = completions;
    }

    public interface SubcommandHandler {
        void update(ICommandSender sender, String arg);
    }

    public interface SubcommandCompletions {
        SubcommandCompletions NO_COMPLETIONS = (server, sender, args, targetPos) -> Collections.emptyList();
        SubcommandCompletions BOOLEAN = fromArray("true", "false");

        List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos);

        static SubcommandCompletions fromArray(String... completions) {
            return (server, sender, args, targetPos) -> {
                if (args.length < 2) {
                    return Arrays.asList(completions);
                }
                return Arrays.stream(completions).filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
            };
        }
    }

    private static SubcommandHandler boolHandler(String field, Supplier<Boolean> configGet, Consumer<Boolean> configSet) {
        return handler(field, configGet, Boolean::parseBoolean, configSet);
    }

    private static SubcommandHandler intHandler(String field, Supplier<Integer> configGet, Consumer<Integer> configSet) {
        return handler(field, configGet, Integer::parseInt, configSet);
    }

    private static <T> SubcommandHandler handler(String field, Supplier<T> configGet, Function<String, T> argParse, Consumer<T> configSet) {
        return (sender, arg) -> {
            T newValue = argParse.apply(arg);
            T oldValue = configGet.get();
            if (!Objects.equals(oldValue, newValue)) {
                configSet.accept(newValue);
                syncAndSendUpdated(sender, field, oldValue, newValue);
            } else {
                sendNoChange(sender, field, oldValue);
            }
        };
    }

    private static void syncAndSendUpdated(ICommandSender sender, String field, Object oldValue, Object newValue) {
        ConfigManager.sync(ChunkPurgeMod.MODID, Type.INSTANCE);
        sender.sendMessage(new TextComponentString("Updated " + field + " from " + oldValue + " to " + newValue));
    }

    private static void sendNoChange(ICommandSender sender, String field, Object value) {
        sender.sendMessage(new TextComponentString("No change; " + field + " is already " + value));
    }

}
