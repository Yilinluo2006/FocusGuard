package com.luoyilin.focusguard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.Gravity;
import androidx.appcompat.widget.AppCompatButton;

/** Raised primary action with a physical press state, shared by XML and Java screens. */
public final class RaisedButton extends AppCompatButton {
    private boolean raised = true;
    private final int travel;

    public RaisedButton(Context context) { this(context, null); }
    public RaisedButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        travel = Math.round(6 * getResources().getDisplayMetrics().density);
        setBackgroundTintList(null);
        setBackgroundResource(R.drawable.raised_button);
        setStateListAnimator(null);
        setElevation(0);
        setAllCaps(false);
        setGravity(Gravity.CENTER);
        setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{getContext().getColor(R.color.text_secondary), getContext().getColor(R.color.on_primary)}));
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom() + travel);
        setMinHeight(Math.round(52 * getResources().getDisplayMetrics().density));
    }

    void setRaised(boolean value) {
        if (raised == value) return;
        raised = value;
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
                getPaddingBottom() + (value ? travel : -travel));
        setBackgroundResource(value ? R.drawable.raised_button : R.drawable.surface_rounded);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (raised && isPressed()) {
            int save = canvas.save();
            canvas.translate(0, travel);
            super.onDraw(canvas);
            canvas.restoreToCount(save);
        } else super.onDraw(canvas);
    }
}
