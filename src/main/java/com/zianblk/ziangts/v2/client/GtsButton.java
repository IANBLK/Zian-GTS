package com.zianblk.ziangts.v2.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Draw exactly once inside the screen canvas; hit testing uses the same coordinates. */
public final class GtsButton extends Button {
 private GtsButton(int x,int y,int width,int height,Component text,OnPress action){super(x,y,width,height,text,action,DEFAULT_NARRATION);}
 public static Builder create(Component text,OnPress action){return new Builder(text,action);}
 public static final class Builder {
  private final Component text;private final OnPress action;private int x,y,width=100,height=20;
  Builder(Component text,OnPress action){this.text=text;this.action=action;}
  public Builder bounds(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;return this;}
  public GtsButton build(){return new GtsButton(x,y,width,height,text,action);}
 }
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
  int x=getX(),y=getY();var font=Minecraft.getInstance().font;
  g.fill(x,y,x+width,y+height,active?0xFF20262D:0xFF161A20);
  g.renderOutline(x,y,width,height,!active?0xFF3B4148:isHoveredOrFocused()?0xFFF0C75E:0xFF526575);
  String text=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,width-8));
  g.drawCenteredString(font,text,x+width/2,y+(height-font.lineHeight)/2,active?0xFFF4F7FA:0xFF7C838B);
 }
}
