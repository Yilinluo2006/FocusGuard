package com.luoyilin.focusguard;

import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;

final class AvatarCatalog {
    static final String[] KEYS = {"default", "sad", "strong", "thinking", "thumbs_up"};
    static final String[] NAMES = {"小奶娃", "委屈奶娃", "健壮奶娃", "思考奶娃", "点赞奶娃"};
    private static final int[] IMAGES = {R.drawable.avatar_default, R.drawable.avatar_sad, R.drawable.avatar_strong,
            R.drawable.avatar_thinking, R.drawable.avatar_thumbs_up};
    static int index(String key) { for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i; return 0; }
    static void show(ImageView view, String key, int sizeDp) {
        String tag = KEYS[index(key)] + ":" + sizeDp;
        if (tag.equals(view.getTag())) return;
        int resource = IMAGES[index(key)], target = Math.round(sizeDp * view.getResources().getDisplayMetrics().density);
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(view.getResources(), resource, options); options.inJustDecodeBounds = false; options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > target * 2) options.inSampleSize *= 2;
        view.setImageBitmap(BitmapFactory.decodeResource(view.getResources(), resource, options));
        GradientDrawable background = new GradientDrawable(); background.setShape(GradientDrawable.OVAL);
        background.setColor(view.getContext().getColor(R.color.secondary_surface)); view.setBackground(background);
        view.setClipToOutline(true); view.setScaleType(ImageView.ScaleType.CENTER_CROP); view.setTag(tag);
        view.setContentDescription(NAMES[index(key)] + "头像");
    }
}
