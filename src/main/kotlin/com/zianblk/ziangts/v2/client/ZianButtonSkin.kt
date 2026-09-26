package com.zianblk.ziangts.v2.client

/**
 * Legacy marker kept so existing source references/build caches remain harmless.
 *
 * IMPORTANT: button styling must not be painted from ScreenEvent.Render.Post. On NeoForge 1.21.1
 * that stage can run after the menu/background blur pass, which leaves only the repainted buttons
 * sharp and makes the rest of the Zian GTS screen look blurred. The post-render overlay has
 * therefore been intentionally removed.
 *
 * Buttons currently fall back to their normal widget rendering. A dedicated widget renderer can
 * style them safely inside their own render pass without touching the rest of the screen.
 */
object ZianButtonSkin
