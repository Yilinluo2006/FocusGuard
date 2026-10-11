package com.luoyilin.focusguard;
final class AccountInsets {
    static void apply(android.view.View view) {
        android.content.Context context = view.getContext();
        while (context instanceof android.content.ContextWrapper && !(context instanceof android.app.Activity))
            context = ((android.content.ContextWrapper) context).getBaseContext();
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            androidx.core.view.WindowInsetsControllerCompat controller =
                    androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(), view);
            boolean light = (view.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    != android.content.res.Configuration.UI_MODE_NIGHT_YES;
            controller.setAppearanceLightStatusBars(light);
            controller.setAppearanceLightNavigationBars(light);
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                    | androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(view);
    }
}
