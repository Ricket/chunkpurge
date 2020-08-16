package org.richardcarter.chunkpurge.proxy;

import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config.Type;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.richardcarter.chunkpurge.ChunkPurgeMod;
import org.richardcarter.chunkpurge.WorldTickHandler;
import org.richardcarter.chunkpurge.commands.ChunkPurgeCommand;

public class ServerProxy implements IProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ChunkPurgeMod.log = event.getModLog();
        // TODO do we need to: ConfigManager.sync(ChunkPurgeMod.MODID, Type.INSTANCE);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new WorldTickHandler());
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new ChunkPurgeCommand());
    }
}
