package com.luoyilin.focusguard;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared presentation for the main pages; no settings or timer state is changed here. */
final class PageUi {
    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        card.setBackgroundResource(R.drawable.page_card_background);
        return card;
    }

    static TextView heading(Context context, String title, int size) {
        TextView text = new TextView(context);
        text.setText(title);
        text.setTextSize(size);
        text.setTypeface(null, Typeface.BOLD);
        text.setTextColor(context.getColor(R.color.text_primary));
        androidx.core.view.ViewCompat.setAccessibilityHeading(text, true);
        return text;
    }

    static void addCard(LinearLayout parent, LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(parent.getContext(), 16);
        parent.addView(card, params);
    }

    static void secondary(Button button) {
        if (button instanceof RaisedButton) ((RaisedButton) button).setRaised(false);
        button.setBackgroundTintList(null);
        button.setBackgroundResource(R.drawable.page_action_background);
        button.setTextColor(button.getContext().getColor(R.color.text_primary));
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(dp(button.getContext(), 48));
        button.setPadding(dp(button.getContext(), 16), dp(button.getContext(), 10),
                dp(button.getContext(), 16), dp(button.getContext(), 10));
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
