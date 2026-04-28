package com.alexander.autoreplybot;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogEntry {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);

    private String contactName;
    private String message;
    private long timestamp;

    public LogEntry() {}

    public LogEntry(String contactName, String message, long timestamp) {
        this.contactName = contactName;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getFormattedTime() {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("contactName", contactName);
        obj.put("message", message);
        obj.put("timestamp", timestamp);
        return obj;
    }

    public static LogEntry fromJson(JSONObject obj) throws JSONException {
        LogEntry entry = new LogEntry();
        entry.contactName = obj.getString("contactName");
        entry.message = obj.getString("message");
        entry.timestamp = obj.getLong("timestamp");
        return entry;
    }
}
