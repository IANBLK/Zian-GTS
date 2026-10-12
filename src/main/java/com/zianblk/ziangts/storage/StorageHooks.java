package com.zianblk.ziangts.storage;

import com.zianblk.ziangts.storage.StorageFiles;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid="ziangts")
public final class StorageHooks {
  @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { track(event); }
  @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { track(event); }
  private static void track(PlayerEvent event) {
    if(event.getEntity() instanceof ServerPlayer player)try{
      StorageFiles.touchOwner(player.server.getWorldPath(LevelResource.ROOT),player.getUUID());
    }catch(Exception error){org.slf4j.LoggerFactory.getLogger("ZianStorage").error("SQLite activity tracking unavailable; cleanup will remain conservative",error);}
  }
  @SubscribeEvent(priority=EventPriority.LOWEST) public static void stop(ServerStoppedEvent event) {
    try{StorageFiles.closeAll();}catch(Exception error){org.slf4j.LoggerFactory.getLogger("ZianStorage").error("SQLite close failed",error);}
  }
  @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
    var command=Commands.literal(StorageFiles.MOD).requires(source->com.zianblk.ziangts.v2.runtime.GtsPermissions.INSTANCE.allows(source,"admin.storage",true));
    command.then(Commands.literal("status").executes(c->{try{String status=StorageFiles.status(c.getSource().getServer().getWorldPath(LevelResource.ROOT));c.getSource().sendSuccess(()->Component.literal(status),false);return 1;}catch(Exception error){c.getSource().sendFailure(Component.literal(error.getMessage()==null?"Storage unavailable":error.getMessage()));return 0;}}));
    command.then(Commands.literal("retention").then(Commands.argument("historyDays",IntegerArgumentType.integer(7,3650)).then(Commands.argument("inactiveDays",IntegerArgumentType.integer(1,3650)).executes(c->{try{
      Path directory=c.getSource().getServer().getWorldPath(LevelResource.ROOT).resolve("zian-storage");
      Path file=directory.resolve("storage.properties");Properties properties=new Properties();
      if(!Files.isRegularFile(file))throw new java.io.IOException("SQLite is not configured");
      try(var reader=Files.newBufferedReader(file)){properties.load(reader);}
      properties.setProperty(StorageFiles.MOD+".history-days",Integer.toString(IntegerArgumentType.getInteger(c,"historyDays")));
      properties.setProperty(StorageFiles.MOD+".inactive-days",Integer.toString(IntegerArgumentType.getInteger(c,"inactiveDays")));
      Path temporary=Files.createTempFile(directory,"retention-",".tmp");
      try{try(var writer=Files.newBufferedWriter(temporary)){properties.store(writer,"Zian retention; pending receipts and progression are protected");}try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){channel.force(true);}Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temporary);}
      c.getSource().sendSuccess(()->Component.literal("Retención guardada para "+StorageFiles.MOD+". La limpieza se ejecuta con el servidor detenido y respaldo."),false);return 1;
    }catch(Exception error){c.getSource().sendFailure(Component.literal(error.getMessage()==null?"Storage configuration failed":error.getMessage()));return 0;}}))));
    event.getDispatcher().register(Commands.literal("zianstorage").requires(source->source.hasPermission(3)).then(command));
  }
}
