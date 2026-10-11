package com.luoyilin.focusguard;

import android.content.Context;
import android.content.Intent;
import android.util.AtomicFile;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Local installation only. No page features are included in backups or device sync. */
final class RecordedPageStore {
    private static final class ReadFailure extends IOException {
        ReadFailure(Exception cause) { super("已有记录读取失败", cause); }
    }
    static final class ReadResult {
        final List<RecordedPageRule> rules;
        final String error;
        ReadResult(List<RecordedPageRule> rules, String error) { this.rules = rules; this.error = error; }
    }
    private interface Change { void apply(List<RecordedPageRule> rules); }
    static String captureAction(Context c) { return c.getPackageName() + ".RECORD_PAGE"; }
    private static synchronized List<RecordedPageRule> access(Context c, Change change) throws Exception {
        File dir = c.getNoBackupFilesDir();
        try (RandomAccessFile lock = new RandomAccessFile(new File(dir, "recorded-pages.lock"), "rw");
             FileLock held = lock.getChannel().lock()) {
            AtomicFile file = new AtomicFile(new File(dir, "recorded-pages.json"));
            List<RecordedPageRule> rules = new ArrayList<>();
            try (InputStream in = file.openRead()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) { bytes.write(buffer, 0, n); if (bytes.size() > 65536) throw new IOException("规则文件过大"); }
                JSONArray list = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
                if (list.length() > 24) throw new IOException("规则数量过多");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.getJSONObject(i); List<RecordedPageRule.Anchor> anchors = new ArrayList<>();
                    JSONArray a = r.getJSONArray("anchors");
                    if ((!r.optBoolean("classOnly") && a.length() < 1) || a.length() > 3) throw new IOException("规则特征无效");
                    for (int j = 0; j < a.length(); j++) {
                        JSONObject v = a.getJSONObject(j);
                        String text = v.getString("text"); if (text.isEmpty() || text.length() > 80) throw new IOException("特征文字无效");
                        anchors.add(new RecordedPageRule.Anchor(v.getString("viewId"), text, v.getString("widget"), v.optBoolean("description"), v.optBoolean("selected"), 0));
                    }
                    rules.add(new RecordedPageRule(r.getString("id"), r.getString("name"), r.getString("pkg"), r.getString("type"),
                            r.getBoolean("system"), r.getBoolean("enabled"), anchors, r.optBoolean("classOnly"), r.optBoolean("restrictSystem")));
                    RecordedPageRule loaded = rules.get(rules.size() - 1);
                    if (loaded.classOnly && (!loaded.anchors.isEmpty() || !loaded.canUseClassOnly())) throw new IOException("界面标识无效");
                }
            } catch (FileNotFoundException missing) {
                if (file.getBaseFile().exists() || new File(dir, "recorded-pages.json.bak").exists()) throw new ReadFailure(missing);
            } catch (Exception damaged) { throw new ReadFailure(damaged); }
            if (change != null) {
                change.apply(rules); JSONArray list = new JSONArray();
                for (RecordedPageRule r : rules) {
                    JSONArray anchors = new JSONArray();
                    for (RecordedPageRule.Anchor a : r.anchors) anchors.put(new JSONObject().put("viewId", a.viewId).put("text", a.text)
                            .put("widget", a.widget).put("description", a.description).put("selected", a.selected));
                    list.put(new JSONObject().put("id", r.id).put("name", r.name).put("pkg", r.pkg).put("type", r.type)
                            .put("system", r.system).put("enabled", r.enabled).put("anchors", anchors)
                            .put("classOnly", r.classOnly).put("restrictSystem", r.restrictSystem));
                }
                byte[] bytes = list.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 65536) throw new IOException("记录容量已满，请先管理已有页面");
                FileOutputStream out = null;
                try { out = file.startWrite(); out.write(bytes); file.finishWrite(out); }
                catch (Exception error) { if (out != null) file.failWrite(out); throw error; }
            }
            return rules;
        }
    }
    static List<RecordedPageRule> read(Context c) {
        return readResult(c).rules;
    }
    static ReadResult readResult(Context c) {
        try { return new ReadResult(access(c, null), null); }
        catch (Exception error) { return new ReadResult(Collections.emptyList(), "记录读取失败。原记录未删除，请重试。"); }
    }
    private static boolean edit(Context c, Change change) {
        try { access(c, change); c.sendBroadcast(new Intent(PageMonitoringStore.action(c)).setPackage(c.getPackageName())); return true; }
        catch (Exception error) { return false; }
    }
    static boolean add(Context c, RecordedPageRule rule) {
        return save(c, rule, null) == null;
    }
    /** Null means independently read back and verified; otherwise a user-facing error. */
    static String save(Context c, RecordedPageRule rule, String replacingId) {
        if (!PageMonitoringStore.read(c).enabled) return "页面监控已关闭，请返回应用开启后重试";
        if ((rule.classOnly ? !rule.canUseClassOnly() || !rule.anchors.isEmpty() : rule.anchors.isEmpty()) || rule.anchors.size() > 3) return "请选择 1–3 项特征";
        for (RecordedPageRule.Anchor a : rule.anchors)
            if (a.text.isEmpty() || a.text.length() > 80) return "特征文字无效，请重新录入";
        if (rule.name.trim().isEmpty() || rule.name.length() > 40 || rule.pkg.isEmpty() || rule.type.isEmpty()) return "页面信息不完整，请重新录入";
        try {
            access(c, list -> {
                if (!PageMonitoringStore.read(c).enabled) throw new IllegalStateException("页面监控已关闭");
                if (replacingId != null) {
                    int index = -1;
                    for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(replacingId)) index = i;
                    if (index < 0) throw new IllegalStateException("原记录已删除，请重新开始录入");
                    RecordedPageRule original = list.get(index);
                    if (!original.pkg.equals(rule.pkg)) throw new IllegalStateException("请选择原记录所属应用的页面");
                    list.set(index, new RecordedPageRule(original.id, original.name, rule.pkg, rule.type,
                            rule.system, original.enabled, rule.anchors, rule.classOnly, rule.restrictSystem));
                } else {
                    if (list.size() >= 24) throw new IllegalStateException("最多保存 24 条，请先删除不再使用的记录");
                    for (RecordedPageRule r : list) if (r.id.equals(rule.id)) throw new IllegalStateException("记录已存在，请查看已录入页面");
                    list.add(rule);
                }
            });
            String id = replacingId == null ? rule.id : replacingId;
            boolean verified = false;
            for (RecordedPageRule r : access(c, null)) if (r.id.equals(id) && r.pkg.equals(rule.pkg)
                    && r.type.equals(rule.type) && r.classOnly == rule.classOnly && r.restrictSystem == rule.restrictSystem && r.anchors.size() == rule.anchors.size()) {
                verified = true;
                for (int i = 0; i < r.anchors.size(); i++) {
                    RecordedPageRule.Anchor a = rule.anchors.get(i), b = r.anchors.get(i);
                    verified &= a.text.equals(b.text) && a.viewId.equals(b.viewId) && a.widget.equals(b.widget) && a.description == b.description && a.selected == b.selected;
                }
            }
            c.sendBroadcast(new Intent(PageMonitoringStore.action(c)).setPackage(c.getPackageName()));
            return verified ? null : "写入后未能确认记录，请先查看已录入页面，避免重复保存";
        } catch (IllegalStateException error) { return error.getMessage(); }
        catch (ReadFailure error) { return "已有记录读取失败，无法确认保存。请先查看已录入页面，勿重复提交。"; }
        catch (IOException error) { return "记录写入失败，请检查手机剩余空间后重试。"; }
        catch (Exception error) { return "保存失败，请重试或返回已录入页面检查记录"; }
    }
    static boolean rename(Context c, String id, String name) {
        String clean = name.trim(); if (clean.isEmpty() || clean.length() > 40) return false;
        return edit(c, list -> {
            for (int i = 0; i < list.size(); i++) {
                RecordedPageRule r = list.get(i);
                if (r.id.equals(id)) { list.set(i, r.renamed(clean, r.enabled)); return; }
            }
            throw new IllegalStateException("记录已删除");
        });
    }
    static boolean enable(Context c, String id, boolean enabled) {
        return edit(c, list -> {
            for (int i = 0; i < list.size(); i++) { RecordedPageRule r = list.get(i);
                if (r.id.equals(id)) { list.set(i, r.renamed(r.name, enabled)); return; }
            }
            throw new IllegalStateException("记录已删除");
        });
    }
    static boolean delete(Context c, String id) { return edit(c, list -> list.removeIf(r -> r.id.equals(id))); }
}
