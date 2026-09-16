package com.luoyilin.focusguard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Collections;

/** Reads configuration in its writer process; guard must not cache SharedPreferences. */
public class LimitConfigProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://com.luoyilin.focusguard.limits/config");

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown configuration URI");
        SharedPreferences prefs = getContext().getSharedPreferences("focus_guard_prefs", 0);
        MatrixCursor cursor = new MatrixCursor(new String[]{"package_name", "limit_minutes"});
        for (String pkg : prefs.getStringSet("selected_packages", Collections.emptySet())) {
            int minutes = prefs.getInt("limit_minutes_" + pkg, 30);
            if (minutes > 0) cursor.addRow(new Object[]{pkg, minutes});
        }
        return cursor;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd.focusguard.limit"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
