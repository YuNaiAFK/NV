package com.maxwai.nclientv3.adapters;

import android.content.Intent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.CopyToClipboardActivity;
import com.maxwai.nclientv3.GalleryActivity;
import com.maxwai.nclientv3.MainActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.ZoomActivity;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GalleryData;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.components.TagList;
import com.maxwai.nclientv3.api.enums.SpecialTagIds;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.api.local.LocalGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.widgets.CustomGridLayoutManager;
import com.maxwai.nclientv3.files.GalleryFolder;
import com.maxwai.nclientv3.github.chrisbanes.photoview.PhotoView;
import com.maxwai.nclientv3.settings.GalleryTagPreferences;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.TagTranslations;
import com.maxwai.nclientv3.utility.ImageDownloadUtility;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;
import com.maxwai.nclientv3.api.enums.TitleType;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {
    private static final Pattern TITLE_CREDITS = Pattern.compile("^\\[[^]]+\\]\\s*(?=\\S)|\\s+\\[[^]]+\\]$");
    private static final int[] TAG_NAMES = {
        R.string.unknown,
        R.string.tag_parody_gallery,
        R.string.tag_character_gallery,
        R.string.tag_tag_gallery,
        R.string.tag_artist_gallery,
        R.string.tag_group_gallery,
        R.string.tag_language_gallery,
        R.string.tag_category_gallery,
    };
    private final SparseIntArray angles = new SparseIntArray();
    private final GalleryActivity context;
    private final GenericGallery gallery;
    private GalleryFolder directory = null;
    private Policy policy;
    private int colCount;

    public GalleryAdapter(GalleryActivity cont, GenericGallery gallery, int colCount) {
        this.context = cont;
        this.gallery = gallery;
        setColCount(colCount);
        try {
            if (gallery instanceof LocalGallery) {
                directory = gallery.getGalleryFolder();
            } else if (Global.hasStoragePermission(cont)) {
                if (gallery.getId() != -1) {
                    File f = Global.findGalleryFolder(context, gallery.getId());
                    if (f != null) directory = new GalleryFolder(f);
                } else {
                    directory = new GalleryFolder(gallery.getTitle());
                }
            }
        } catch (IllegalArgumentException ignore) {
            directory = null;
        }
        LogUtility.d("Max maxSize: " + gallery.getMaxSize() + ", min maxSize: " + gallery.getMinSize());
    }

    public Type positionToType(int pos) {
        if (pos == 0) return Type.TAG;
        if (pos > gallery.getPageCount()) return Type.RELATED;
        return Type.PAGE;
    }

    public void setColCount(int colCount) {
        this.colCount = colCount;
        applyProportionPolicy();
    }

    private void applyProportionPolicy() {
        if (colCount == 1) policy = Policy.FULL;
        else policy = Policy.PROPORTION;
        LogUtility.d("NEW POLICY: " + policy);
    }

    public GalleryFolder getDirectory() {
        return directory;
    }

    @NonNull
    @Override
    public GalleryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int id = 0;
        switch (Type.values()[viewType]) {
            case TAG:
                id = R.layout.tags_layout;
                break;
            case PAGE:
                switch (policy) {
                    case FULL:
                        id = R.layout.image_void_full;
                        break;
                    case PROPORTION:
                        id = R.layout.image_void_static;
                        break;
                }
                break;
            case RELATED:
                id = R.layout.related_recycler;
                break;
        }
        return new GalleryAdapter.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(id, parent, false), Type.values()[viewType]);
    }

    @Override
    public void onBindViewHolder(@NonNull final GalleryAdapter.ViewHolder holder, int position) {

        switch (positionToType(holder.getBindingAdapterPosition())) {
            case TAG:
                loadTagLayout(holder);
                break;
            case PAGE:
                loadPageLayout(holder);
                break;
            case RELATED:
                loadRelatedLayout(holder);
                break;
        }
    }

    private void loadRelatedLayout(ViewHolder holder) {
        LogUtility.d("Called RElated");
        final RecyclerView recyclerView = holder.master.findViewById(R.id.recycler);
        if (gallery.isLocal()) {
            holder.master.setVisibility(View.GONE);
            return;
        }
        final Gallery gallery = (Gallery) this.gallery;
        if (!gallery.isRelatedLoaded() || gallery.getRelated().isEmpty()) {
            holder.master.setVisibility(View.GONE);
            return;
        } else holder.master.setVisibility(View.VISIBLE);
        recyclerView.setLayoutManager(new CustomGridLayoutManager(context, 1, RecyclerView.HORIZONTAL, false));
        if (gallery.isRelatedLoaded()) {
            ListAdapter adapter = new ListAdapter(context);
            adapter.addGalleries(new ArrayList<>(gallery.getRelated()));
            recyclerView.setAdapter(adapter);
        }
    }

    private void loadTagLayout(ViewHolder holder) {
        bindGalleryTitles(holder);
        initializeIdContainer(holder.master.findViewById(R.id.id_num));

        final ViewGroup groups = holder.master.findViewById(R.id.tag_master);
        groups.setVisibility(gallery.hasGalleryData() ? View.VISIBLE : View.GONE);
        if (gallery.hasGalleryData()) {
            TagList tagList = gallery.getGalleryData().getTags();
            LayoutInflater inflater = context.getLayoutInflater();
            for (TagType type : TagType.values) {
                ChipGroup group = (ChipGroup) groups.getChildAt(type.getId());
                int count = tagList.getCount(type);
                group.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
                ((TextView) group.findViewById(R.id.title)).setText(context.getString(TAG_NAMES[type.getId()]) + ":");
                if (group.getChildCount() > 1) group.removeViews(1, group.getChildCount() - 1);
                for (int index = 0; index < count; index++) {
                    Tag tag = tagList.getTag(type, index);
                    View badge = inflater.inflate(R.layout.gallery_tag_badge, group, false);
                    decorateTag(badge, tag);
                    badge.setOnClickListener(v -> showTagActions(badge, tag));
                    badge.setOnLongClickListener(v -> {
                        CopyToClipboardActivity.copyTextToClipboard(context, tag.getName());
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                        return true;
                    });
                    group.addView(badge);
                }
            }
        }
        addInfoLayout(holder);
    }

    private void bindGalleryTitles(ViewHolder holder) {
        TextView title = holder.master.findViewById(R.id.gallery_primary_title);
        TextView subtitle = holder.master.findViewById(R.id.gallery_secondary_title);
        String first = gallery.getTitle();
        String second = "";
        if (gallery.hasGalleryData()) {
            GalleryData data = gallery.getGalleryData();
            boolean japaneseFirst = Global.getTitleType() == TitleType.JAPANESE;
            String preferred = data.getTitle(japaneseFirst ? TitleType.JAPANESE : TitleType.ENGLISH);
            if (preferred != null && !preferred.trim().isEmpty()) first = preferred;
            second = data.getTitle(japaneseFirst ? TitleType.ENGLISH : TitleType.JAPANESE);
        }
        SpannableString styled = new SpannableString(first);
        int muted = MaterialColors.getColor(title, com.google.android.material.R.attr.colorOnSurfaceVariant);
        Matcher credits = TITLE_CREDITS.matcher(first);
        while (credits.find()) {
            styled.setSpan(new ForegroundColorSpan(muted), credits.start(), credits.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        title.setText(styled);
        boolean showSubtitle = second != null && !second.trim().isEmpty() && !second.equals(first);
        subtitle.setText(showSubtitle ? second : "");
        subtitle.setVisibility(showSubtitle ? View.VISIBLE : View.GONE);
        enableTitleCopy(title);
        enableTitleCopy(subtitle);
    }

    private void enableTitleCopy(TextView title) {
        title.setOnLongClickListener(v -> {
            CopyToClipboardActivity.copyTextToClipboard(context, title.getText().toString());
            Toast.makeText(context, R.string.title_copied_to_clipboard, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private static String compactCount(int count) {
        if (count >= 1_000_000) return String.format(Locale.getDefault(), "%.1fM", count / 1_000_000.0);
        if (count >= 1000) return String.format(Locale.getDefault(), "%.1fk", count / 1000.0);
        return String.format(Locale.getDefault(), "%d", count);
    }

    private void decorateTag(View badge, Tag tag) {
        TextView name = badge.findViewById(R.id.tag_badge_name);
        TextView count = badge.findViewById(R.id.tag_badge_count);
        String label = TagTranslations.label(context, tag.getType().getSingle(), tag.getName());
        boolean hidden = GalleryTagPreferences.isHidden(tag);
        boolean liked = GalleryTagPreferences.isLiked(context, tag);
        name.setText(label);
        name.setCompoundDrawablesRelativeWithIntrinsicBounds(hidden ? R.drawable.ic_close
            : liked ? R.drawable.ic_favorite : 0, 0, 0, 0);
        name.setCompoundDrawablePadding(4);
        name.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(name.getCurrentTextColor()));
        count.setText(compactCount(tag.getCount()));
        count.setVisibility(tag.getCount() > 0 ? View.VISIBLE : View.GONE);
        String description = hidden ? context.getString(R.string.gallery_tag_hidden_label, label)
            : liked ? context.getString(R.string.gallery_tag_liked_label, label) : label;
        badge.setContentDescription(tag.getCount() > 0
            ? description + ", " + String.format(Locale.getDefault(), "%,d", tag.getCount()) : description);
    }

    private void showTagActions(View chip, Tag tag) {
        String label = TagTranslations.label(context, tag.getType().getSingle(), tag.getName());
        boolean liked = GalleryTagPreferences.isLiked(context, tag);
        boolean hidden = GalleryTagPreferences.isHidden(tag);
        String title = label.equals(tag.getName()) ? label : label + " · " + tag.getName();
        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(new CharSequence[]{
                context.getString(R.string.gallery_tag_find),
                context.getString(liked ? R.string.gallery_tag_unlike : R.string.gallery_tag_like),
                context.getString(hidden ? R.string.gallery_tag_unhide : R.string.gallery_tag_hide),
            }, (dialog, which) -> {
                if (which == 0) {
                    Intent intent = new Intent(context, MainActivity.class);
                    intent.putExtra(context.getPackageName() + ".TAG", tag);
                    intent.putExtra(context.getPackageName() + ".ISBYTAG", true);
                    context.startActivity(intent);
                } else if (which == 1) {
                    GalleryTagPreferences.setLiked(context, tag, !liked);
                    decorateTag(chip, tag);
                } else {
                    GalleryTagPreferences.setHidden(tag, !hidden);
                    decorateTag(chip, tag);
                    Toast.makeText(context, hidden ? R.string.gallery_tag_unhidden
                        : R.string.gallery_tag_hidden, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void initializeIdContainer(TextView idContainer) {
        if (gallery.getId() <= 0) {
            idContainer.setVisibility(View.GONE);
            return;
        }
        String id = Integer.toString(gallery.getId());
        idContainer.setText("#" + id);
        idContainer.setVisibility(gallery.getId() != SpecialTagIds.INVALID_ID ? View.VISIBLE : View.GONE);
        idContainer.setOnClickListener(v -> {
            CopyToClipboardActivity.copyTextToClipboard(context, id);
            context.runOnUiThread(() ->
                Toast.makeText(context, R.string.id_copied_to_clipboard, Toast.LENGTH_SHORT).show()
            );
        });
    }

    private void addInfoLayout(ViewHolder holder) {
        TextView pages = holder.master.findViewById(R.id.page_count);
        pages.setText(String.format(Locale.getDefault(), "%d", gallery.getPageCount()));
        TextView date = holder.master.findViewById(R.id.upload_date);
        TextView favorites = holder.master.findViewById(R.id.favorite_count);
        date.setVisibility(gallery.hasGalleryData() ? View.VISIBLE : View.GONE);
        favorites.setVisibility(gallery.hasGalleryData() ? View.VISIBLE : View.GONE);
        if (!gallery.hasGalleryData()) return;

        GalleryData data = gallery.getGalleryData();
        date.setText(context.getString(R.string.upload_date_format,
            android.text.format.DateFormat.getDateFormat(context).format(data.getUploadDate()),
            android.text.format.DateFormat.getTimeFormat(context).format(data.getUploadDate())));
        favorites.setText(context.getString(R.string.favorite_count_format, data.getFavoriteCount()));
    }

    private void loadPageLayout(ViewHolder holder) {
        final int pos = holder.getBindingAdapterPosition();
        final ImageView imgView = holder.master.findViewById(R.id.image);

        imgView.setOnClickListener(v -> startGallery(holder.getBindingAdapterPosition()));
        imgView.setOnLongClickListener(null);
        holder.master.setOnClickListener(v -> startGallery(holder.getBindingAdapterPosition()));
        holder.master.setOnLongClickListener(null);

        holder.pageNumber.setText(String.format(Locale.US, "%d", pos));

        if (policy == Policy.FULL) {
            PhotoView photoView = (PhotoView) imgView;
            photoView.setZoomable(Global.isZoomOneColumn());
            photoView.setOnMatrixChangeListener(rect -> photoView.setAllowParentInterceptOnEdge(photoView.getScale() <= 1f));
            photoView.setOnClickListener(v -> {
                if (photoView.getScale() <= 1f)
                    startGallery(holder.getBindingAdapterPosition());
            });
            View.OnLongClickListener listener = v -> {
                optionDialog(imgView, pos);
                return true;
            };
            imgView.setOnLongClickListener(listener);
            holder.master.setOnLongClickListener(listener);
        }

        loadImageOnPolicy(imgView, pos);


    }

    private void optionDialog(ImageView imgView, final int pos) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.select_dialog_item);
        adapter.add(context.getString(R.string.share));
        adapter.add(context.getString(R.string.rotate_image));
        adapter.add(context.getString(R.string.bookmark_here));
        if (Global.hasStoragePermission(context))
            adapter.add(context.getString(R.string.save_page));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(R.string.settings).setIcon(R.drawable.ic_share);
        builder.setAdapter(adapter, (dialog, which) -> {
            switch (which) {
                case 0:
                    openSendImageDialog(imgView, pos);
                    break;
                case 1:
                    rotate(pos);
                    break;
                case 2:
                    Queries.ResumeTable.insert(gallery.getId(), pos);
                    break;
                case 3:
                    String name = String.format(Locale.US, "%d-%d.jpg", gallery.getId(), pos);
                    Utility.saveImage(imgView.getDrawable(), new File(Global.SCREENFOLDER, name));
                    break;
            }
        }).show();
    }

    private void openSendImageDialog(ImageView img, int pos) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setPositiveButton(R.string.yes, (dialog, which) -> sendImage(img, pos, true))
            .setNegativeButton(R.string.no, (dialog, which) -> sendImage(img, pos, false))
            .setCancelable(true).setTitle(R.string.send_with_title)
            .setMessage(R.string.caption_send_with_title)
            .show();
    }

    private void sendImage(ImageView img, int pos, boolean text) {
        Utility.sendImage(context, img.getDrawable(), text ? gallery.sharePageUrl(pos - 1) : null);
    }

    private void rotate(int pos) {
        angles.append(pos, (angles.get(pos) + 270) % 360);
        context.runOnUiThread(() -> notifyItemChanged(pos));
    }

    private void startGallery(int page) {
        if (!gallery.isLocal() && Global.getDownloadPolicy() == Global.DataUsageType.NONE) {
            context.runOnUiThread(() ->
                Toast.makeText(context, R.string.enable_network_to_continue, Toast.LENGTH_SHORT).show()
            );
            return;
        }
        Intent intent = new Intent(context, ZoomActivity.class);
        intent.putExtra(context.getPackageName() + ".GALLERY", gallery);
        intent.putExtra(context.getPackageName() + ".DIRECTORY", directory);
        intent.putExtra(context.getPackageName() + ".PAGE", page);
        context.startActivity(intent);
    }

    private void loadImageOnPolicy(ImageView imgView, int pos) {
        final File file = directory == null ? null : directory.getPage(pos);
        int angle = angles.get(pos);

        if (policy == Policy.FULL) {
            if (file != null && file.exists())
                ImageDownloadUtility.loadImageOp(context, imgView, file, angle);
            else if (!gallery.isLocal()) {
                Gallery ent = (Gallery) gallery;
                ImageDownloadUtility.loadImageOp(context, imgView, ent, pos - 1, angle);
            } else ImageDownloadUtility.loadImage(R.mipmap.ic_launcher, imgView);
        } else {
            if (file != null && file.exists())
                ImageDownloadUtility.loadImage(context, file, imgView);
            else if (!gallery.isLocal()) {
                Gallery ent = (Gallery) gallery;
                ImageDownloadUtility.downloadPage(context, imgView, ent, pos - 1, false);
            } else ImageDownloadUtility.loadImage(R.mipmap.ic_launcher, imgView);
        }
    }



    @Override
    public int getItemViewType(int position) {
        return positionToType(position).ordinal();
    }

    @Override
    public int getItemCount() {
        return gallery.getPageCount() + 2;
    }

    public enum Type {TAG, PAGE, RELATED}

    public enum Policy {PROPORTION, FULL}

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View master;
        final TextView pageNumber;

        ViewHolder(View v, Type type) {
            super(v);
            master = v.findViewById(R.id.master);
            pageNumber = v.findViewById(R.id.page_number);
            if (Global.useRtl()) v.setRotationY(180);
            if (type == Type.RELATED) Global.applyFastScroller(master.findViewById(R.id.recycler));
        }
    }

}
