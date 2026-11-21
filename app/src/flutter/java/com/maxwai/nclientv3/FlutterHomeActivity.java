package com.maxwai.nclientv3;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.os.LocaleListCompat;

import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.api.SimpleGallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.settings.AuthStore;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.TagV2;
import com.maxwai.nclientv3.utility.Utility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public final class FlutterHomeActivity extends FlutterActivity {
    private static final int STORAGE_PERMISSION = 104;
    private final ExecutorService catalog = Executors.newSingleThreadExecutor();
    private MethodChannel channel;

    @Override
    protected void onCreate(Bundle state) {
        if (Global.hideMultitask()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(state);
    }

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine engine) {
        super.configureFlutterEngine(engine);
        channel = new MethodChannel(engine.getDartExecutor().getBinaryMessenger(), "nvbook/home");
        channel.setMethodCallHandler((call, result) -> {
            switch (call.method) {
                case "settings":
                    result.success(settings());
                    break;
                case "catalog":
                    loadCatalog(call, result);
                    break;
                case "open":
                    open(call, result);
                    break;
                default:
                    result.notImplemented();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (channel != null) channel.setMethodCallHandler(null);
        catalog.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Global.hideMultitask()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private Context localizedContext() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty()) return this;
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()));
        return createConfigurationContext(configuration);
    }

    private Map<String, Object> settings() {
        Context localized = localizedContext();
        Map<String, Object> settings = new HashMap<>();
        settings.put("browse", localized.getString(R.string.flutter_browse));
        settings.put("library", localized.getString(R.string.flutter_library));
        settings.put("classic", localized.getString(R.string.flutter_classic));
        settings.put("empty", localized.getString(R.string.flutter_empty));
        settings.put("more", localized.getString(R.string.flutter_more));
        settings.put("search", localized.getString(R.string.search));
        settings.put("advanced", localized.getString(R.string.search_settings));
        settings.put("favorites", localized.getString(R.string.favorite_manga));
        settings.put("online", localized.getString(R.string.online_favorite_manga));
        settings.put("downloads", localized.getString(R.string.downloaded_manga));
        settings.put("bookmarks", localized.getString(R.string.manage_bookmarks));
        settings.put("history", localized.getString(R.string.view_history));
        settings.put("settings", localized.getString(R.string.settings));
        settings.put("tags", localized.getString(R.string.manage_tags));
        settings.put("status", localized.getString(R.string.status_viewer));
        settings.put("account", localized.getString(R.string.title_activity_api_key));
        settings.put("random", localized.getString(R.string.random_manga));
        settings.put("retry", localized.getString(R.string.retry));
        settings.put("error", localized.getString(R.string.unable_to_connect_to_the_site));
        settings.put("theme", getSharedPreferences("Settings", MODE_PRIVATE)
            .getString(getString(R.string.preference_key_theme_select), "light"));
        settings.put("showTitles", Global.showTitles());
        settings.put("signedIn", AuthStore.hasValidApiKey(this));
        return settings;
    }

    private void loadCatalog(MethodCall call, MethodChannel.Result result) {
        Integer page = call.argument("page");
        String query = call.argument("query");
        if (page == null || page < 1 || query == null) {
            result.error("invalid_catalog", "Invalid catalog request", null);
            return;
        }

        catalog.execute(() -> {
            try {
                InspectorV3 inspector = InspectorV3.searchInspector(this, query, null, page,
                    Global.getSortType(), null, new InspectorV3.DefaultInspectorResponse() {});
                if (!inspector.createDocument()) throw new IOException("Catalog request failed");
                inspector.parseDocument();
                List<Map<String, Object>> rows = new ArrayList<>();
                String avoided = TagV2.getAvoidedTags();
                for (GenericGallery item : inspector.getGalleries()) {
                    if (!(item instanceof SimpleGallery) || !item.isValid()) continue;
                    SimpleGallery gallery = (SimpleGallery) item;
                    boolean hidden = avoided != null && gallery.hasIgnoredTags(avoided);
                    if (hidden && Global.removeAvoidedGalleries()) continue;
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", gallery.getId());
                    row.put("title", gallery.getTitle());
                    row.put("thumbnail", hidden ? "" : gallery.getThumbnail().toString());
                    row.put("flag", Global.getLanguageFlag(gallery.getLanguage()));
                    row.put("hidden", hidden);
                    rows.add(row);
                }
                Map<String, Object> payload = new HashMap<>();
                payload.put("rows", rows);
                payload.put("pages", inspector.getPageCount());
                runOnUiThread(() -> { if (!isDestroyed()) result.success(payload); });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isDestroyed()) result.error("catalog", "Unable to load catalog", null);
                });
            }
        });
    }

    private void open(MethodCall call, MethodChannel.Result result) {
        String route = call.argument("route");
        if (route == null) {
            result.error("route", "Missing route", null);
            return;
        }
        Intent intent;
        switch (route) {
            case "gallery":
                Integer id = call.argument("id");
                if (id == null || id < 1) {
                    result.error("route", "Invalid gallery", null);
                    return;
                }
                intent = new Intent(this, GalleryActivity.class)
                    .setData(Uri.parse(Utility.getBaseUrl() + "g/" + id + "/"));
                break;
            case "advanced": intent = new Intent(this, SearchActivity.class); break;
            case "favorites": intent = new Intent(this, FavoriteActivity.class); break;
            case "downloads":
                if (!Global.hasStoragePermission(this) && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION);
                    result.success(null);
                    return;
                }
                intent = new Intent(this, LocalActivity.class);
                break;
            case "bookmarks": intent = new Intent(this, BookmarkActivity.class); break;
            case "history": intent = new Intent(this, HistoryActivity.class); break;
            case "settings": intent = new Intent(this, SettingsActivity.class); break;
            case "account": intent = new Intent(this, ApiKeyActivity.class); break;
            case "tags": intent = new Intent(this, TagFilterActivity.class); break;
            case "status": intent = new Intent(this, StatusViewerActivity.class); break;
            case "random": intent = new Intent(this, RandomActivity.class); break;
            case "classic": intent = new Intent(this, MainActivity.class); break;
            case "online":
                if (!AuthStore.hasValidApiKey(this)) {
                    intent = new Intent(this, ApiKeyActivity.class);
                } else {
                    intent = new Intent(this, MainActivity.class)
                        .putExtra(getPackageName() + ".FAVORITE", true);
                }
                break;
            default:
                result.error("route", "Unknown route", null);
                return;
        }
        startActivity(intent);
        result.success(null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
        @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION && Global.hasStoragePermission(this)) {
            startActivity(new Intent(this, LocalActivity.class));
        }
    }
}
