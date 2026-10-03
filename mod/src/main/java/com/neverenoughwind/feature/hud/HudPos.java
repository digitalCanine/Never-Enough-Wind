package com.neverenoughwind.feature.hud;

// where a widget sits: a screen corner or edge (0 = left/top, 0.5 = middle, 1 = right/bottom) plus a pixel offset.
// every widget gets one of these so the settings menu can move them later
public record HudPos(float anchorX, float anchorY, int offsetX, int offsetY) {
    public int x(int screenWidth, int widgetWidth) {
        return Math.round((screenWidth - widgetWidth) * anchorX) + offsetX;
    }

    public int y(int screenHeight, int widgetHeight) {
        return Math.round((screenHeight - widgetHeight) * anchorY) + offsetY;
    }
}
