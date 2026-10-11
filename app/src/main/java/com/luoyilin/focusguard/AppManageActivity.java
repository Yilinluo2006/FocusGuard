package com.luoyilin.focusguard;

public class AppManageActivity extends androidx.appcompat.app.AppCompatActivity {
    private AppManagePage page;
    @Override protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        page = new AppManagePage(this);
        android.view.View root = page.create();
        page.restoreState(state);
        setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            int margin = Math.round(20 * getResources().getDisplayMetrics().density);
            v.setPadding(bars.left + margin, bars.top + margin,
                    bars.right + margin, bars.bottom + margin);
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
    @Override protected void onDestroy() {
        if (page != null) page.dispose();
        super.onDestroy();
    }
}
