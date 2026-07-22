package com.codex.sonyedge;

public final class DmsContainerItem {
    public final String id;
    public final String title;
    public final int childCount;
    public final String parentId;
    public final String date;
    public final String contentClass;
    public final boolean restricted;
    public final boolean searchable;

    public DmsContainerItem(String id, String title, int childCount) {
        this(id, title, childCount, "", "", "", false, false);
    }

    public DmsContainerItem(
            String id,
            String title,
            int childCount,
            String parentId,
            String date,
            String contentClass,
            boolean restricted,
            boolean searchable
    ) {
        this.id = id;
        this.title = title;
        this.childCount = childCount;
        this.parentId = parentId;
        this.date = date;
        this.contentClass = contentClass;
        this.restricted = restricted;
        this.searchable = searchable;
    }
}
