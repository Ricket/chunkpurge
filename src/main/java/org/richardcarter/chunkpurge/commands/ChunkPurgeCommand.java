package org.richardcarter.chunkpurge.commands;

import com.google.common.base.Enums;
import com.google.common.base.Joiner;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.richardcarter.chunkpurge.ChunkPurgeConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ChunkPurgeCommand implements ICommand {
    @Override
    @Nonnull
    public String getName() {
        return ChunkPurgeConfig.commandChunkPurge;
    }

    @Override
    @Nonnull
    public String getUsage(ICommandSender sender) {
        List<String> examples = Arrays.stream(ConfigSubcommand.values())
                .map(v -> "/" + getName() + " " + v.name() + " " + v.usageValueExample)
                .collect(Collectors.toList());
        return Joiner.on("\n").join(examples);
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("Usage:\n" + getUsage(sender)));
            return;
        }
        Optional<ConfigSubcommand> subcommand = Arrays.stream(ConfigSubcommand.values())
                .filter(v -> v.name().equals(args[0]))
                .findFirst();
        if (subcommand.isPresent()) {
            subcommand.get().handler.update(sender, args[1]);
        } else {
            sender.sendMessage(new TextComponentString("Usage:\n" + getUsage(sender)));
        }
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        // op or server console only

        if (sender instanceof EntityPlayer) {
            return FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().canSendCommands(((EntityPlayer) sender).getGameProfile());
        }

        return sender instanceof MinecraftServer;
    }

    @Override
    @Nonnull
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 0 || (args.length == 1 && args[0].equals(""))) {
            return Arrays.stream(ConfigSubcommand.values())
                    .map(Enum::name)
                    .collect(Collectors.toList());
        } else if (args.length == 1) {
            // complete subcommand names that start with the args[0]
            return Arrays.stream(ConfigSubcommand.values())
                    .map(Enum::name)
                    .filter(n -> n.startsWith(args[0]))
                    .collect(Collectors.toList());
        }

        ConfigSubcommand subcommand = Enums.getIfPresent(ConfigSubcommand.class, args[0]).orNull();
        if (subcommand == null) {
            return Collections.emptyList();
        }

        return subcommand.completions.getTabCompletions(server, sender, args, targetPos);
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(ICommand o) {
        return getName().compareTo(o.getName());
    }
}
