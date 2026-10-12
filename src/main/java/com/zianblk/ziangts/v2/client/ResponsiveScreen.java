package com.zianblk.ziangts.v2.client;


import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.21.1 adaptation: one render transform, inverse input, native unscaled tooltips. */
public abstract class ResponsiveScreen extends Screen {
  private ResponsiveLayout layout;
  private List<Component> tooltip = List.of();
  private net.minecraft.world.item.ItemStack itemTooltip;

  protected ResponsiveScreen(Component title) {
    super(title);
  }

  protected final void fitViewport(int minimumWidth, int minimumHeight) {
    // Rebuilding widgets must read the real logical viewport, never rescale the previous virtual
    // one.
    layout =
        ResponsiveLayout.fit(
            minecraft.getWindow().getGuiScaledWidth(),
            minecraft.getWindow().getGuiScaledHeight(),
            minimumWidth,
            minimumHeight);
    width = layout.width();
    height = layout.height();
  }

  protected void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY, float partial) { renderWidgets(graphics,mouseX,mouseY,partial); }

  protected final void renderWidgets(GuiGraphics g, int x, int y, float partial) {
    super.render(g, x, y, partial);
  }

  protected final void queueTooltip(List<Component> lines) {
    tooltip = List.copyOf(lines);
  }

  @Override
  public final void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    tooltip = List.of();
    itemTooltip = null;
    g.pose().pushPose();
    try {
      g.pose().translate(layout.x(), layout.y(), 0);
      g.pose().scale((float) layout.scale(), (float) layout.scale(), 1);
      renderCanvas(
          g,
          (int) Math.floor(layout.mouseX(mouseX)),
          (int) Math.floor(layout.mouseY(mouseY)),
          partial);
    } finally {
      g.pose().popPose();
    }
    // Tooltip bounds are calculated by Minecraft in its actual logical viewport.
    if(itemTooltip!=null)g.renderTooltip(font,itemTooltip,mouseX,mouseY);
    else if (!tooltip.isEmpty()) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
  }

  @Override
  public final boolean mouseClicked(double x, double y, int button) {
    return canvasMouseClicked(layout.mouseX(x), layout.mouseY(y), button);
  }

  @Override
  public final boolean mouseReleased(double x, double y, int button) {
    return canvasMouseReleased(layout.mouseX(x), layout.mouseY(y), button);
  }

  @Override
  public final void mouseMoved(double x, double y) {
    canvasMouseMoved(layout.mouseX(x), layout.mouseY(y));
  }

  @Override
  public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    return canvasMouseDragged(
        layout.mouseX(x), layout.mouseY(y), button, dx / layout.scale(), dy / layout.scale());
  }

  @Override
  public final boolean mouseScrolled(double x, double y, double dx, double dy) {
    return canvasMouseScrolled(layout.mouseX(x), layout.mouseY(y), dx, dy);
  }

  protected boolean canvasMouseClicked(double x,double y,int button){return super.mouseClicked(x,y,button);}
  protected boolean canvasMouseReleased(double x,double y,int button){return super.mouseReleased(x,y,button);}
  protected boolean canvasMouseDragged(double x,double y,int button,double dx,double dy){return super.mouseDragged(x,y,button,dx,dy);}
  protected boolean canvasMouseScrolled(double x,double y,double dx,double dy){return super.mouseScrolled(x,y,dx,dy);}
  protected void canvasMouseMoved(double x,double y){super.mouseMoved(x,y);}
  protected final void canvasScissor(GuiGraphics g,int left,int top,int right,int bottom){
    g.enableScissor((int)Math.floor(layout.x()+left*layout.scale()),(int)Math.floor(layout.y()+top*layout.scale()),(int)Math.ceil(layout.x()+right*layout.scale()),(int)Math.ceil(layout.y()+bottom*layout.scale()));
  }
  @Override public void setTooltipForNextRenderPass(java.util.List<net.minecraft.util.FormattedCharSequence> lines,net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner,boolean replace){
    super.setTooltipForNextRenderPass(lines,net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,replace);
  }
  protected final void queueItemTooltip(net.minecraft.world.item.ItemStack stack){itemTooltip=stack;}
}
