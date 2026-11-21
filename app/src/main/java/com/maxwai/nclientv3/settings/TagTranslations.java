package com.maxwai.nclientv3.settings;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Local display names only; API and database always use original tag names. */
public final class TagTranslations {
    private static Map<String, String[]> translations;

    private TagTranslations() {}

    private static synchronized Map<String, String[]> dictionary(Context context) {
        if (translations != null) return translations;
        Map<String, String[]> result = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            context.getAssets().open("tag_names_zh.tsv"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] columns = line.split("\\t", -1);
                if (columns.length == 3) result.put(columns[0], new String[]{columns[1], columns[2]});
            }
        } catch (IOException e) {
            Log.w("TagTranslations", "Bundled tag dictionary unavailable", e);
        }
        translations = result;
        return result;
    }

    public static String label(Context context, String name) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        if (!"zh".equals(locale.getLanguage())) return name;
        String[] pair = dictionary(context).get(name.toLowerCase(Locale.ROOT));
        if (pair == null) return name;
        String region = locale.getCountry();
        boolean traditional = "Hant".equals(locale.getScript()) || "TW".equals(region)
            || "HK".equals(region) || "MO".equals(region);
        return pair[traditional ? 1 : 0];
    }
}
