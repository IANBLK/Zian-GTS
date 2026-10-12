package com.zianblk.ziangts;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
/** Consistent player-facing notices; component actions and existing explicit colors survive. */
public final class Messages {
 private Messages(){}
 public static Component format(Component original){
  String text=original.getString();if(text.isBlank()||text.startsWith("✦"))return original;
  var body=original.copy();if(body.getStyle().getColor()==null)body.withStyle(ChatFormatting.GRAY);
  return Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
   .append(Component.literal("Rassvet").withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD))
   .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY)).append(body);
 }
}
