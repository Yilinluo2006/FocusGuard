package com.luoyilin.focusguard;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps units readable while giving numeric values visual priority. */
final class MetricText {
    private static final Pattern NUMBERS = Pattern.compile("[0-9]+");

    static CharSequence emphasize(String value, float scale) {
        SpannableString text = new SpannableString(value);
        Matcher matcher = NUMBERS.matcher(value);
        while (matcher.find()) {
            text.setSpan(new RelativeSizeSpan(scale), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }
}
