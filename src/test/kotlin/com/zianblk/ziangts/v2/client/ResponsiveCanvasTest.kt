package com.zianblk.ziangts.v2.client
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class ResponsiveCanvasTest {
 @Test fun `market rows and pagination remain separate at scale four and small windows`() {
  for ((w,h) in listOf(960 to 540,640 to 360,480 to 270,320 to 240)) {
   val canvas=ResponsiveLayout.fit(w,h,640,440)
   val grid=MarketLayout.calculate(canvas.width(),canvas.height())
   for(i in 0 until 6){assertTrue(grid.cardX(i)>=0);assertTrue(grid.cardX(i)+grid.cardWidth<=canvas.width());assertTrue(grid.cardY(i)+grid.cardHeight<=grid.footerY-12)}
   assertTrue(canvas.y()+(grid.footerY+20)*canvas.scale()<=h)
   val x=grid.cardX(5)+grid.cardWidth-20.0;val y=grid.cardY(5)+grid.cardHeight-10.0
   assertEquals(x,canvas.mouseX(canvas.x()+x*canvas.scale()),1e-8)
   assertEquals(y,canvas.mouseY(canvas.y()+y*canvas.scale()),1e-8)
  }
 }
}
