package com.maxwai.nclientv3.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.async.database.Queries;

/** Local affinity is independent of tag search filters and gallery favorites. */
public final class GalleryTagPreferences {
    private GalleryTagPreferences() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("GalleryTagPreferences", Context.MODE_PRIVATE);
    }

    private static String key(Tag tag) {
        return "liked." + tag.getId();
    }

    public static boolean isLiked(Context context, Tag tag) {
        return prefs(context).getBoolean(key(tag), false);
    }

    public static void setLiked(Context context, Tag tag, boolean liked) {
        SharedPreferences.Editor edit = prefs(context).edit();
        if (liked) edit.putBoolean(key(tag), true);
        else edit.remove(key(tag));
        edit.apply();
    }

    public static boolean isHidden(Tag tag) {
        Tag stored = Queries.TagTable.getTagById(tag.getId());
        return stored != null && stored.getStatus() == TagStatus.AVOIDED;
    }

    public static void setHidden(Tag tag, boolean hidden) {
        Queries.TagTable.insert(tag);
        Queries.TagTable.updateStatus(tag.getId(), hidden ? TagStatus.AVOIDED : TagStatus.DEFAULT);
    }

    public static String hiddenTagQuery() {
        return TagV2.getAvoidedTags();
    }
}
