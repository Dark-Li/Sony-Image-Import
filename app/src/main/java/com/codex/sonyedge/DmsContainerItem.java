package com.codex.sonyedge;

public final class DmsContainerItem {
    public final String id;
    public final String title;
    public final int childCount;

    public DmsContainerItem(String id, String title, int childCount) {
        this.id = id;
        this.title = title;
        this.childCount = childCount;
    }
}
