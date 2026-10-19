package com.zianblk.ziangts.v2.client
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
class MarketChoiceScreen(private val parent:Screen,heading:String,private val choices:List<Pair<String,String>>,private val accept:(String)->Unit):ResponsiveScreen(Component.literal(heading)) {
 private var query="";private var page=0
 override fun init(){fitViewport(500,350);val x=(width-420)/2;val y=(height-300)/2
  val search=addRenderableWidget(EditBox(font,x+12,y+32,396,20,Component.translatableWithFallback("ui.ziangts.c518cc7b7f45", "Buscar por nombre o ID")));search.setHint(Component.translatableWithFallback("ui.ziangts.60194a11d9c0", "Buscar por nombre o ID…"));search.value=query;search.setResponder { value->query=value;page=0;rebuildWidgets() };setInitialFocus(search)
  val rows=choices.filter { (it.first+" "+it.second).contains(query,true) };page=page.coerceIn(0,((rows.size-1)/8).coerceAtLeast(0))
  rows.drop(page*8).take(8).forEachIndexed { i,row->val b=addRenderableWidget(GtsButton.create(Component.literal(row.second)){accept(row.first);minecraft?.setScreen(parent)}.bounds(x+12,y+60+i*24,396,20).build());b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(row.second+" · "+row.first))) }
  addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.d97f1097b8f3", "Anterior")){page--;rebuildWidgets()}.bounds(x+12,y+270,100,20).build()).active=page>0
  addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.84a715a785a9", "Volver")){minecraft?.setScreen(parent)}.bounds(x+160,y+270,100,20).build())
  addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.c4d83a3b6d12", "Siguiente")){page++;rebuildWidgets()}.bounds(x+308,y+270,100,20).build()).active=(page+1)*8<rows.size
 }
 override fun renderCanvas(g:GuiGraphics,mx:Int,my:Int,partial:Float){ZianGtsTheme.background(g,width,height);g.drawCenteredString(font,title,width/2,(height-300)/2+10,ZianGtsTheme.GOLD);renderWidgets(g,mx,my,partial)}
 override fun onClose(){minecraft?.setScreen(parent)}
}
