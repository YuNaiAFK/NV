package com.maxwai.nclientv3;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;

import com.maxwai.nclientv3.adapters.FavoriteAdapter;
import com.maxwai.nclientv3.api.FavoriteSync;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.async.downloader.DownloadGalleryV2;
import com.maxwai.nclientv3.components.activities.BaseActivity;
import com.maxwai.nclientv3.components.views.PageSwitcher;
import com.maxwai.nclientv3.settings.AuthStore;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;


public class FavoriteActivity extends BaseActivity {
    private static final int ENTRY_PER_PAGE = 24;
    private FavoriteAdapter adapter = null;
    private boolean sortByTitle = false;
    private PageSwitcher pageSwitcher;
    private SearchView searchView;
    private FavoriteSync favoriteSync;
    private AlertDialog syncDialog;

    public static int getEntryPerPage() {
        return Global.isInfiniteScrollFavorite() ? Integer.MAX_VALUE : ENTRY_PER_PAGE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Global.initActivity(this);

        setContentView(R.layout.app_bar_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(R.string.favorite_manga);
        pageSwitcher = findViewById(R.id.page_switcher);
        recycler = findViewById(R.id.recycler);
        refresher = findViewById(R.id.refresher);
        refresher.setRefreshing(true);
        adapter = new FavoriteAdapter(this);


        refresher.setOnRefreshListener(adapter::forceReload);
        changeLayout(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        recycler.setAdapter(adapter);
        pageSwitcher.setPages(1, 1);
        pageSwitcher.setChanger(new PageSwitcher.DefaultPageChanger() {
            @Override
            public void pageChanged() {
                if (adapter != null) adapter.changePage();
            }
        });

    }

    public int getActualPage() {
        return pageSwitcher.getActualPage();
    }

    @Override
    protected int getLandscapeColumnCount() {
        return Global.getColLandFavorite();
    }

    @Override
    protected int getPortraitColumnCount() {
        return Global.getColPortFavorite();
    }

    private int calculatePages(@Nullable String text) {
        int perPage = getEntryPerPage();
        int totalEntries = Queries.FavoriteTable.countFavorite(text);
        int div = totalEntries / perPage;
        int mod = totalEntries % perPage;
        return div + (mod == 0 ? 0 : 1);
    }

    @Override
    protected void onResume() {
        refresher.setEnabled(true);
        refresher.setRefreshing(true);
        String query = searchView == null ? null : searchView.getQuery().toString();
        pageSwitcher.setTotalPage(calculatePages(query));
        adapter.forceReload();
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        getMenuInflater().inflate(R.menu.favorite_sync, menu);
        menu.findItem(R.id.download_page).setVisible(true);
        menu.findItem(R.id.sort_by_name).setVisible(true);
        menu.findItem(R.id.by_popular).setVisible(false);
        menu.findItem(R.id.only_language).setVisible(false);
        menu.findItem(R.id.add_bookmark).setVisible(false);

        searchView = (androidx.appcompat.widget.SearchView) menu.findItem(R.id.search).getActionView();
        Objects.requireNonNull(searchView).setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                pageSwitcher.setTotalPage(calculatePages(newText));
                if (adapter != null)
                    adapter.getFilter().filter(newText);
                return true;
            }
        });
        Utility.tintMenu(this, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i;
        if (item.getItemId() == R.id.sync_online_favorites) {
            confirmOnlineFavoriteSync();
            return true;
        } else if (item.getItemId() == R.id.open_browser) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(Utility.getBaseUrl() + "favorites/"));
            startActivity(i);
        } else if (item.getItemId() == R.id.download_page) {
            if (adapter != null) showDialogDownloadAll();
        } else if (item.getItemId() == R.id.sort_by_name) {
            sortByTitle = !sortByTitle;
            adapter.setSortByTitle(sortByTitle);
            item.setTitle(sortByTitle ? R.string.sort_by_latest : R.string.sort_by_title);
        } else if (item.getItemId() == R.id.random_favorite) {
            adapter.randomGallery();
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmOnlineFavoriteSync() {
        if (favoriteSync != null) return;
        if (!AuthStore.hasValidApiKey(this)) {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.favorites_sync_title)
                .setMessage(R.string.favorites_sync_sign_in)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.title_activity_api_key,
                    (dialog, which) -> startActivity(new Intent(this, ApiKeyActivity.class)))
                .show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.favorites_sync_title)
            .setMessage(R.string.favorites_sync_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, (dialog, which) -> syncOnlineFavorites())
            .show();
    }

    private void syncOnlineFavorites() {
        if (favoriteSync != null) return;
        FavoriteSync task = new FavoriteSync(this);
        favoriteSync = task;
        syncDialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.favorites_sync_title)
            .setMessage(R.string.favorites_sync_running)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel, (dialog, which) -> task.cancel())
            .show();
        new Thread(() -> {
            try {
                task.run();
                runOnUiThread(() -> finishSync(task, true));
            } catch (Exception e) {
                if (!task.isCanceled()) LogUtility.e("Online favorites sync did not complete", e);
                runOnUiThread(() -> finishSync(task, false));
            }
        }, "favorite-sync").start();
    }

    private void finishSync(FavoriteSync task, boolean completed) {
        if (favoriteSync != task || isFinishing() || isDestroyed()) return;
        favoriteSync = null;
        syncDialog.dismiss();
        syncDialog = null;
        if (task.getDownloaded() > 0) {
            pageSwitcher.setTotalPage(calculatePages(searchView == null ? null : searchView.getQuery().toString()));
            adapter.forceReload();
        }
        int message = completed ? R.string.favorites_sync_done : R.string.favorites_sync_failed;
        if (completed && task.getUnavailable() > 0) message = R.string.favorites_sync_skipped;
        if (!completed && task.isCanceled()) message = R.string.favorites_sync_canceled;
        Toast.makeText(this, getString(message, task.getDownloaded(), task.getUploaded(),
            task.getUnavailable()), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (favoriteSync != null) favoriteSync.cancel();
        if (syncDialog != null) syncDialog.dismiss();
        super.onDestroy();
    }

    private void showDialogDownloadAll() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder
            .setTitle(R.string.download_all_galleries_in_this_page)
            .setIcon(R.drawable.ic_file)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                for (Gallery g : adapter.getAllGalleries())
                    DownloadGalleryV2.downloadGallery(this, g);
            });
        builder.show();
    }
}
