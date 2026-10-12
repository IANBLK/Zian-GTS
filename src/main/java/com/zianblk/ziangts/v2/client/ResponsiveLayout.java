package com.zianblk.ziangts.v2.client;

/** Fits a logical canvas uniformly. Mouse coordinates remain doubles until hover rendering. */
public record ResponsiveLayout(double scale, int width, int height, double x, double y) {
  public static ResponsiveLayout fit(int width, int height, int minimumWidth, int minimumHeight) {
    if (width <= 0 || height <= 0 || minimumWidth <= 0 || minimumHeight <= 0)
      throw new IllegalArgumentException("Positive logical dimensions required");
    double scale =
        Math.min(1, Math.min(width / (double) minimumWidth, height / (double) minimumHeight));
    int virtualWidth = Math.max(minimumWidth, (int) Math.floor(width / scale));
    int virtualHeight = Math.max(minimumHeight, (int) Math.floor(height / scale));
    return new ResponsiveLayout(
        scale,
        virtualWidth,
        virtualHeight,
        (width - virtualWidth * scale) / 2,
        (height - virtualHeight * scale) / 2);
  }

  public double mouseX(double value) {
    return (value - x) / scale;
  }

  public double mouseY(double value) {
    return (value - y) / scale;
  }
}
