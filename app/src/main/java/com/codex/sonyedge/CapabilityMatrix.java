package com.codex.sonyedge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CapabilityMatrix {
    private final Map<String, Boolean> values = new LinkedHashMap<>();
    private final Map<String, String> notes = new LinkedHashMap<>();

    public void set(String key, boolean value, String note) {
        values.put(key, value);
        notes.put(key, note == null ? "" : note);
    }

    public String toDisplayString() {
        StringBuilder builder = new StringBuilder();
        for (String key : values.keySet()) {
            builder
                    .append(values.get(key) ? "[OK] " : "[--] ")
                    .append(key);
            String note = notes.get(key);
            if (note != null && !note.isEmpty()) {
                builder.append(" - ").append(note);
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        JSONArray rows = new JSONArray();
        for (String key : values.keySet()) {
            JSONObject row = new JSONObject();
            row.put("capability", key);
            row.put("supported", values.get(key));
            row.put("note", notes.get(key));
            rows.put(row);
        }
        obj.put("rows", rows);
        return obj;
    }
}
