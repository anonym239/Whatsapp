package com.alexander.autoreplybot;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppPreferences {

    private static final String PREF_NAME = "AutoReplyBotPrefs";
    private static final String KEY_BOT_ENABLED = "bot_enabled";
    private static final String KEY_DELAY_MINUTES = "delay_minutes";
    private static final String KEY_REPLY_MESSAGE = "reply_message";
    private static final String KEY_BLACKLIST = "blacklist";
    private static final String KEY_LOG_ENTRIES = "log_entries";

    public static final String DEFAULT_MESSAGE =
            "Hi [Name], Alexander ist gerade nicht erreichbar. " +
            "Er wird dir aber so schnell wie möglich zurückschreiben. " +
            "LG Alexander's Bot \uD83E\uDD16";

    private final SharedPreferences prefs;

    public AppPreferences(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ── Bot enabled ──────────────────────────────────────────────────────────
    public boolean isBotEnabled() {
        return prefs.getBoolean(KEY_BOT_ENABLED, false);
    }

    public void setBotEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BOT_ENABLED, enabled).apply();
    }

    // ── Delay ────────────────────────────────────────────────────────────────
    public int getDelayMinutes() {
        return prefs.getInt(KEY_DELAY_MINUTES, 10);
    }

    public void setDelayMinutes(int minutes) {
        prefs.edit().putInt(KEY_DELAY_MINUTES, minutes).apply();
    }

    // ── Reply message ─────────────────────────────────────────────────────────
    public String getReplyMessage() {
        return prefs.getString(KEY_REPLY_MESSAGE, DEFAULT_MESSAGE);
    }

    public void setReplyMessage(String message) {
        prefs.edit().putString(KEY_REPLY_MESSAGE, message).apply();
    }

    // ── Blacklist ─────────────────────────────────────────────────────────────
    public Set<String> getBlacklist() {
        return prefs.getStringSet(KEY_BLACKLIST, new HashSet<>());
    }

    public void setBlacklist(Set<String> blacklist) {
        prefs.edit().putStringSet(KEY_BLACKLIST, blacklist).apply();
    }

    public void addToBlacklist(String contactName) {
        Set<String> list = new HashSet<>(getBlacklist());
        list.add(contactName);
        setBlacklist(list);
    }

    public void removeFromBlacklist(String contactName) {
        Set<String> list = new HashSet<>(getBlacklist());
        list.remove(contactName);
        setBlacklist(list);
    }

    public boolean isBlacklisted(String contactName) {
        return getBlacklist().contains(contactName);
    }

    // ── Log entries ───────────────────────────────────────────────────────────
    public List<LogEntry> getLogEntries() {
        String json = prefs.getString(KEY_LOG_ENTRIES, null);
        List<LogEntry> entries = new ArrayList<>();
        if (json == null) return entries;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                entries.add(LogEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return entries;
    }

    public void addLogEntry(LogEntry entry) {
        List<LogEntry> entries = getLogEntries();
        entries.add(0, entry); // newest first
        if (entries.size() > 100) {
            entries = entries.subList(0, 100);
        }
        try {
            JSONArray arr = new JSONArray();
            for (LogEntry e : entries) {
                arr.put(e.toJson());
            }
            prefs.edit().putString(KEY_LOG_ENTRIES, arr.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void clearLog() {
        prefs.edit().remove(KEY_LOG_ENTRIES).apply();
    }
}
