package com.luoyilin.focusguard;

/** Capability decision independent of whether the user has configured an active plan. */
final class ProtectionCapabilities {
    final boolean daily,sleep,focus,wallpaper;
    ProtectionCapabilities(boolean usage,boolean accessibilityEnabled,boolean accessibilityRunning,
            boolean overlay,boolean wallpaperSelected,boolean wallpaperRunning) {
        boolean accessibility=accessibilityEnabled&&accessibilityRunning;
        wallpaper=usage&&overlay&&wallpaperSelected&&wallpaperRunning;
        daily=usage&&(accessibility||wallpaper);
        sleep=accessibility;focus=accessibility;
    }
    String summary() {return daily&&sleep&&focus?"保护服务就绪":daily?"仅应用额度保护可用":sleep&&focus?"应用额度保护需修复":"保护需修复";}
}
