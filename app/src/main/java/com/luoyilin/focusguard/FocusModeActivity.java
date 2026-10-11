package com.luoyilin.focusguard;

public class FocusModeActivity extends androidx.appcompat.app.AppCompatActivity {
    private FocusModePage page;
    @Override protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        androidx.activity.EdgeToEdge.enable(this);
        page = new FocusModePage(this);
        android.view.View root = page.create();
        page.restoreState(state);
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        ((android.widget.ScrollView) root).getChildAt(0).setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setContentView(AppWallpaper.wrapMain(this, root, 0));
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
    @Override protected void onSaveInstanceState(android.os.Bundle out) {
        page.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume() {
        super.onResume();
        if (page != null) page.refresh();
    }
}
