package net.classicube.api;

import java.awt.Color;

public class BlockSelection {

    private Location point1;
    private Location point2;
    private Color outlineColor;
    private String label;
    private byte selectionId;
    public BlockSelection(Location point1, Location point2, Color outlineColor, String label) {
        this.point1 = point1;
        this.point2 = point2;
        this.outlineColor = outlineColor;
        this.label = label;
    }

    // Getters and setters
    public Location getPoint1() {
        return point1;
    }

    public void setPoint1(Location point1) {
        this.point1 = point1;
    }

    public Location getPoint2() {
        return point2;
    }

    public void setPoint2(Location point2) {
        this.point2 = point2;
    }

    public Color getOutlineColor() {
        return outlineColor;
    }
    public void setOutlineColor(Color outlineColor) {
        this.outlineColor = outlineColor;
    }

    @Override
    public String toString() {
        return "Selection{" +
                "point1=" + point1 +
                ", point2=" + point2 +
                ", outlineColor=" + outlineColor +
                '}';
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public byte getSelectionId() {
        return selectionId;
    }

    public void setSelectionId(byte selectionId) {
        this.selectionId = selectionId;
    }
}
