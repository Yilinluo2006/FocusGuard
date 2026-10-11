package com.luoyilin.focusguard;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/** Keeps reading and editing pages comfortable inside a large or resized window. */
final class AdaptivePageFrame extends FrameLayout {
    private final int maxWidth;

    AdaptivePageFrame(Context context, View page, int maxWidthDp) {
        super(context);
        maxWidth = PageUi.dp(context, maxWidthDp);
        addView(page, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
            LayoutParams params = (LayoutParams) getChildAt(0).getLayoutParams();
            params.width = Math.min(maxWidth, Math.max(0,
                    MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight()));
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
