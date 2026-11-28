package app.morphe.extension.music.patches.lyrics.ui;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;
import android.content.res.ColorStateList;
import android.app.Dialog;
import android.graphics.Rect;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import app.morphe.extension.music.patches.lyrics.LyricsManager;
import app.morphe.extension.music.patches.lyrics.model.Lyrics;
import app.morphe.extension.music.patches.lyrics.model.TrackInfo;
import app.morphe.extension.shared.ui.Dim;

/** Compact live adjustment above the toolbar; lyrics stay visible while listening. */
final class LyricsOffsetDialog {
    private static final int LIMIT_MS = 30_000;
    private static final int STEP_MS = 100;

    private LyricsOffsetDialog() {}

    static void show(View anchor, BiConsumer<TextView, String> styleButton) {
        Context context = anchor.getContext();
        LyricsManager manager = LyricsManager.getInstance();
        TrackInfo track = manager.getCurrentTrack();
        if (track == null) return;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Dim.dp8, Dim.dp4, Dim.dp8, Dim.dp4);
        int width = Math.min(Dim.dp(320),
                context.getResources().getDisplayMetrics().widthPixels - Dim.dp32);
        Dialog popup = new Dialog(context);
        popup.requestWindowFeature(Window.FEATURE_NO_TITLE);
        popup.setContentView(content);
        popup.setCanceledOnTouchOutside(true);
        Window window = popup.getWindow();
        if (window == null) return;
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xB84B4B57, 0xA022222D});
        glass.setCornerRadius(Dim.dp(24));
        glass.setStroke(Math.max(1, Dim.dp(0.5f)), 0x55FFFFFF);
        window.setBackgroundDrawable(glass);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.getDecorView().setElevation(Dim.dp(12));
        window.getDecorView().setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(Dim.dp(24));
        }

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(row, new LinearLayout.LayoutParams(-1, Dim.dp(40)));
        TextView minus = control(context, "−",
                str("morphe_music_lyrics_track_offset_earlier"), styleButton);
        row.addView(minus, new LinearLayout.LayoutParams(Dim.dp(40), Dim.dp(40)));
        SeekBar slider = new SeekBar(context);
        slider.setMax(2 * LIMIT_MS / STEP_MS);
        slider.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        slider.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        styleSlider(slider);
        slider.setContentDescription(str("morphe_music_lyrics_track_offset_title"));
        row.addView(slider, new LinearLayout.LayoutParams(0, Dim.dp(40), 1f));
        TextView plus = control(context, "+",
                str("morphe_music_lyrics_track_offset_later"), styleButton);
        row.addView(plus, new LinearLayout.LayoutParams(Dim.dp(40), Dim.dp(40)));

        IntConsumer render = offset -> slider.setProgress((offset + LIMIT_MS) / STEP_MS);
        IntConsumer apply = requested -> {
            if (!Objects.equals(track, manager.getCurrentTrack())) {
                popup.dismiss();
                return;
            }
            int offset = Math.max(-LIMIT_MS, Math.min(LIMIT_MS, requested));
            manager.setTemporaryOffsetMs(offset);
            manager.persistTemporaryOffsetMs();
            render.accept(offset);
        };
        render.accept(manager.getTemporaryOffsetMs());
        minus.setOnClickListener(v -> apply.accept(manager.getTemporaryOffsetMs() - STEP_MS));
        plus.setOnClickListener(v -> apply.accept(manager.getTemporaryOffsetMs() + STEP_MS));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) apply.accept(progress * STEP_MS - LIMIT_MS);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        LyricsManager.Listener listener = new LyricsManager.Listener() {
            @Override public void onLyricsChanged(LyricsManager.State state, Lyrics lyrics) {
                if (!Objects.equals(track, manager.getCurrentTrack())) popup.dismiss();
            }
            @Override public void onTemporaryOffsetChanged(int offsetMs) {
                if (Objects.equals(track, manager.getCurrentTrack())) render.accept(offsetMs);
            }
        };
        popup.setOnDismissListener(d -> manager.removeListener(listener));
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        Rect visibleFrame = new Rect();
        anchor.getWindowVisibleDisplayFrame(visibleFrame);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        window.setGravity(Gravity.TOP | Gravity.LEFT);
        WindowManager.LayoutParams params = window.getAttributes();
        params.x = (screenWidth - width) / 2;
        // Dialog TOP coordinates start below the status bar; the anchor uses screen
        // coordinates. Leave a full 16dp gap above the toolbar after converting them.
        params.y = Math.max(0, location[1] - visibleFrame.top
                - content.getMeasuredHeight() - Dim.dp16);
        params.width = width;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        popup.show();
        manager.addListener(listener);
    }

    private static TextView control(Context context, String text, String description,
                                    BiConsumer<TextView, String> styleButton) {
        TextView button = new TextView(context);
        styleButton.accept(button, null);
        button.setPadding(0, 0, 0, 0);
        button.setText(text);
        button.setTextSize(22);
        button.setTextColor(0xFFFFFFFF);
        button.setContentDescription(description);
        GradientDrawable surface = new GradientDrawable();
        surface.setShape(GradientDrawable.OVAL);
        surface.setColor(0x15FFFFFF);
        surface.setStroke(Math.max(1, Dim.dp(0.5f)), 0x22FFFFFF);
        button.setBackground(surface);
        return button;
    }

    private static void styleSlider(SeekBar slider) {
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x36FFFFFF);
        track.setCornerRadius(Dim.dp(2));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xDDFFFFFF);
        fill.setCornerRadius(Dim.dp(2));
        LayerDrawable progress = new LayerDrawable(new android.graphics.drawable.Drawable[]{
                track, new ClipDrawable(fill, Gravity.LEFT, ClipDrawable.HORIZONTAL)});
        progress.setId(0, android.R.id.background);
        progress.setId(1, android.R.id.progress);
        for (int i = 0; i < 2; i++) {
            progress.setLayerHeight(i, Dim.dp(2));
            progress.setLayerGravity(i, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
        }
        slider.setProgressDrawable(progress);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(0xFFF6F6FA);
        thumb.setSize(Dim.dp(12), Dim.dp(12));
        slider.setThumb(thumb);
        slider.setThumbOffset(Dim.dp6);
    }
}
