package com.codex.sonyedge;

import java.util.ArrayList;
import java.util.List;

public final class DmsBrowseResult {
    public final String objectId;
    public final List<DmsContainerItem> containers = new ArrayList<>();
    public final List<CameraContentItem> items = new ArrayList<>();
    public int numberReturned;
    public int totalMatches;
    public long updateId = -1;
    public String sortCriteria = "";

    public DmsBrowseResult(String objectId) {
        this.objectId = objectId;
    }
}
