package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.*;

/** All focus reads and writes go through the existing configuration-owner process. */
final class FocusPreferences implements SharedPreferences {
    private final Context context;
    FocusPreferences(Context context) { this.context = context.getApplicationContext(); }
    static Map<String, ?> read(Context context, String name) {
        Bundle result = context.getContentResolver().call(LimitConfigProvider.URI, "read", name, null);
        if (result == null) throw new IllegalStateException("Configuration provider unavailable");
        return (Map<String, ?>) result.getSerializable("values");
    }
    @Override public Map<String, ?> getAll() { return read(context, "focus_mode"); }
    @Override public String getString(String k, String d) { Object v = getAll().get(k); return v == null ? d : (String)v; }
    @Override public Set<String> getStringSet(String k, Set<String> d) {
        Object v = getAll().get(k); return v == null ? d : new HashSet<>((Set<String>)v);
    }
    @Override public int getInt(String k, int d) { Object v = getAll().get(k); return v == null ? d : (Integer)v; }
    @Override public long getLong(String k, long d) { Object v = getAll().get(k); return v == null ? d : (Long)v; }
    @Override public float getFloat(String k, float d) { Object v = getAll().get(k); return v == null ? d : (Float)v; }
    @Override public boolean getBoolean(String k, boolean d) { Object v = getAll().get(k); return v == null ? d : (Boolean)v; }
    @Override public boolean contains(String k) { return getAll().containsKey(k); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        throw new UnsupportedOperationException("Use explicit focus-state refresh");
    }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    @Override public Editor edit() { return new Changes(); }
    private final class Changes implements Editor {
        final Bundle values = new Bundle();
        boolean clear;
        @Override public Editor putString(String k, String v) { values.putString(k,v); return this; }
        @Override public Editor putStringSet(String k, Set<String> v) {
            values.putStringArrayList(k, v == null ? null : new ArrayList<>(v)); return this;
        }
        @Override public Editor putInt(String k, int v) { values.putInt(k,v); return this; }
        @Override public Editor putLong(String k, long v) { values.putLong(k,v); return this; }
        @Override public Editor putFloat(String k, float v) { values.putFloat(k,v); return this; }
        @Override public Editor putBoolean(String k, boolean v) { values.putBoolean(k,v); return this; }
        @Override public Editor remove(String k) { values.putString(k,null); return this; }
        @Override public Editor clear() { clear = true; return this; }
        @Override public boolean commit() {
            Bundle extras = new Bundle();
            extras.putBundle("changes", values); extras.putBoolean("clear", clear);
            try {
                Bundle result = context.getContentResolver().call(LimitConfigProvider.URI, "write", "focus_mode", extras);
                return result != null && result.getBoolean("saved");
            } catch (RuntimeException e) {
                android.util.Log.e("FocusPreferences", "Cannot save focus state", e);
                return false;
            }
        }
        @Override public void apply() { commit(); }
    }
}
