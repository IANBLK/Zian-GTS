package com.zianblk.ziangts.v2.client
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Confirmation displays the exact immutable offer; server validation still owns the operation. */
class MarketConfirmScreen(private val parent: Screen, private val heading:String, private val summary:List<String>, private val confirm:()->Unit):ResponsiveScreen(Component.literal(heading)) {
 private var sent=false
 override fun init(){fitViewport(500,310);val x=(width-440)/2;val y=(height-240)/2
  addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.c111e0ab9ddb", "Cancelar")){minecraft?.setScreen(parent)}.bounds(x+18,y+198,190,22).build())
  addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.81b4b67ff827", "Confirmar")){if(!sent){sent=true;minecraft?.setScreen(parent);confirm()}}.bounds(x+232,y+198,190,22).build())
 }
 override fun renderCanvas(g:GuiGraphics,mx:Int,my:Int,partial:Float){ZianGtsTheme.background(g,width,height);val x=(width-440)/2;val y=(height-240)/2;g.fill(x,y,x+440,y+240,0xF0181D24.toInt());g.fill(x,y,x+440,y+3,ZianGtsTheme.GOLD);g.drawCenteredString(font,title,width/2,y+16,ZianGtsTheme.GOLD);var lineY=y+45
  summary.forEach { text->font.split(Component.literal(text),398).forEach { line->g.drawString(font,line,x+20,lineY,0xFFE2E8F0.toInt());lineY+=12 };lineY+=6 }
  renderWidgets(g,mx,my,partial)
 }
 override fun onClose(){minecraft?.setScreen(parent)}
 override fun isPauseScreen()=false
}
