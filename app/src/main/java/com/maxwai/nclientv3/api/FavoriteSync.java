package com.maxwai.nclientv3.api;

import android.content.Context;

import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.settings.AuthStore;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.Utility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Merges missing favorites into each library without propagating deletions. */
public final class FavoriteSync {
    private static final HttpUrl API = HttpUrl.get("https://" + Utility.ORIGINAL_URL + "/api/v2/");
    private final Context context;
    private final String authorization;
    private volatile boolean canceled;
    private volatile Call activeCall;
    private int downloaded;
    private int uploaded;
    private int unavailable;

    public FavoriteSync(Context context) {
        this.context = context.getApplicationContext();
        authorization = AuthStore.hasValidApiKey(context) ? AuthStore.getAuthorizationHeader(context) : null;
    }

    public void run() throws IOException, JSONException {
        requireAccount();
        List<Integer> localIds = Queries.FavoriteTable.listIds();
        Set<Integer> ids = new LinkedHashSet<>();
        int pageCount = -1;
        for (int page = 1; pageCount < 0 || page <= pageCount; page++) {
            JSONObject response = get(API.newBuilder().addPathSegment("favorites")
                .addQueryParameter("page", String.valueOf(page)).build());
            JSONArray results = response.getJSONArray("result");
            int reportedPages = response.getInt("num_pages");
            if (reportedPages < 0 || (pageCount >= 0 && reportedPages != pageCount)
                || (results.length() == 0 && (page != 1 || reportedPages > 1))
                || (reportedPages == 0 && results.length() != 0)) {
                throw new IOException("Favorites changed or pagination is invalid");
            }
            pageCount = reportedPages;
            for (int index = 0; index < results.length(); index++) {
                int id = results.getJSONObject(index).getInt("id");
                if (id <= 0 || !ids.add(id)) throw new IOException("Invalid or repeated favorite ID");
            }
        }

        List<Gallery> missing = new ArrayList<>();
        for (int id : ids) {
            requireAccount();
            if (Queries.FavoriteTable.isFavorite(id)) continue;
            JSONObject detail = get(API.newBuilder().addPathSegment("galleries")
                .addPathSegment(String.valueOf(id)).build());
            Gallery gallery = new Gallery(context, detail.toString(), null, true);
            if (!gallery.isValid() || gallery.getId() != id) throw new IOException("Invalid gallery details");
            missing.add(gallery);
        }
        requireAccount();
        downloaded = Queries.FavoriteTable.importMissing(missing);

        for (int id : localIds) {
            requireAccount();
            if (ids.contains(id) || !Queries.FavoriteTable.isFavorite(id)) continue;
            HttpUrl url = API.newBuilder().addPathSegment("galleries")
                .addPathSegment(String.valueOf(id)).addPathSegment("favorite").build();
            try {
                execute(newRequest(url).post(RequestBody.EMPTY).build(), false);
                uploaded++;
            } catch (HttpFailure e) {
                if (e.status != 404 && e.status != 410) throw e;
                unavailable++;
            }
        }
    }

    public int getDownloaded() {
        return downloaded;
    }

    public int getUploaded() {
        return uploaded;
    }

    public int getUnavailable() {
        return unavailable;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void cancel() {
        canceled = true;
        Call call = activeCall;
        if (call != null) call.cancel();
    }

    private void requireAccount() throws IOException {
        if (canceled) throw new InterruptedIOException("Favorites sync canceled");
        if (authorization == null || !authorization.equals(AuthStore.getAuthorizationHeader(context))) {
            throw new IOException("No matching account is connected");
        }
    }

    private JSONObject get(HttpUrl url) throws IOException, JSONException {
        requireAccount();
        return new JSONObject(execute(newRequest(url).build(), true));
    }

    private Request.Builder newRequest(HttpUrl url) {
        return new Request.Builder().url(url).header("Authorization", authorization)
            .header("User-Agent", "NVbook (https://github.com/YuNaiAFK/NV)");
    }

    private String execute(Request request, boolean readBody) throws IOException {
        requireAccount();
        Call call = Global.getClient(context).newCall(request);
        activeCall = call;
        try {
            requireAccount();
            try (Response response = call.execute()) {
                if (!response.isSuccessful()) throw new HttpFailure(response.code());
                if (!readBody) return "";
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Favorites API response had no body");
                return body.string();
            }
        } finally {
            activeCall = null;
        }
    }

    private static final class HttpFailure extends IOException {
        final int status;

        HttpFailure(int status) {
            super("Favorites API returned HTTP " + status);
            this.status = status;
        }
    }
}
