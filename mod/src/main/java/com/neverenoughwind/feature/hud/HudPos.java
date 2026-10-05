package com.neverenoughwind.feature.hud;

// where a widget sits: a point on the screen (anchor 0 = left/top, 0.5 = middle, 1 = right/bottom, plus a pixel offset)
// and the part of the widget that is held on that point (pivot 0 = its left/top edge, 0.5 = its middle, 1 = its right/bottom edge).
// the pivot is what stays put when the content gets wider or narrower
public record HudPos(float anchorX, float anchorY, int offsetX, int offsetY, float pivotX, float pivotY) {
    public int x(int screenWidth, int widgetWidth) {
        return Math.round(screenWidth * anchorX) + offsetX - Math.round(widgetWidth * pivotX);
    }

    public int y(int screenHeight, int widgetHeight) {
        return Math.round(screenHeight * anchorY) + offsetY - Math.round(widgetHeight * pivotY);
    }
}
