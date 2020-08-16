package org.richardcarter.chunkpurge;

import net.minecraft.init.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.Logger;
import org.richardcarter.chunkpurge.proxy.IProxy;
import org.richardcarter.chunkpurge.proxy.ServerProxy;

@Mod(modid = ChunkPurgeMod.MODID, name = ChunkPurgeMod.NAME, version = ChunkPurgeMod.VERSION, acceptableRemoteVersions = "*")
public class ChunkPurgeMod
{
    public static final String MODID = "chunkpurge";
    public static final String NAME = "ChunkPurge";
    public static final String VERSION = "2.0";

    public static Logger log;

    @Instance(MODID)
    public static ChunkPurgeMod instance;

    @SidedProxy(clientSide = "org.richardcarter.chunkpurge.proxy.ClientProxy", serverSide = "org.richardcarter.chunkpurge.proxy.ServerProxy")
    public static IProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        // TODO do I need to: MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
