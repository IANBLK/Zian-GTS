package com.zianblk.ziangts;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.nio.file.*;
/** Shared visual vocabulary; configurable server name, without changing component actions. */
public final class Messages {
 private Messages(){}
 private static String prefix="Rassvet";private static long nextRead;private static long modified=Long.MIN_VALUE;
 private static synchronized String prefix(){
  long now=System.nanoTime();if(now<nextRead)return prefix;nextRead=now+5_000_000_000L;
  Path file=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ziangts/messages.json");
  try{
   if(!Files.exists(file)){Files.createDirectories(file.getParent());Files.writeString(file,"{\n  \"prefix\": \"Rassvet\"\n}\n",java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
   if(Files.isSymbolicLink(file)||Files.size(file)>4096)throw new IllegalArgumentException("Archivo de mensajes inválido");
   long stamp=Files.getLastModifiedTime(file).toMillis();if(stamp==modified)return prefix;
   var json=com.google.gson.JsonParser.parseString(Files.readString(file,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
   String next=json.get("prefix").getAsString();if(next.isBlank()||next.length()>32||next.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Prefijo inválido");prefix=next;modified=stamp;
  }catch(Exception error){org.slf4j.LoggerFactory.getLogger("ziangts").warn("No se pudo leer messages.json; se conserva el último prefijo válido: {}",error.toString());}
  return prefix;
 }
 public static Component format(Component original){
  String text=original.getString();if(text.isBlank()||text.startsWith("✦"))return original;
  var body=original.copy();if(body.getStyle().getColor()==null)body.withStyle(ChatFormatting.GRAY);
  return Component.literal("✦ ").withStyle(ChatFormatting.GOLD).append(Component.literal(prefix()).withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD)).append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY)).append(body);
 }
}
