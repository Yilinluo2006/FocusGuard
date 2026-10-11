package com.luoyilin.focusguard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.nio.file.Files;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Candidate selection never writes the active wallpaper until explicitly applied. */
public class GuardWallpaperCatalogTest {
    private Context c;
    @Before public void setup(){c=InstrumentationRegistry.getInstrumentation().getTargetContext();assumeTrue(c.getPackageName().endsWith(".setupprobe"));WallpaperGuard.image(c).delete();WallpaperGuard.draft(c).delete();}
    @After public void cleanup(){if(c!=null&&c.getPackageName().endsWith(".setupprobe")){WallpaperGuard.image(c).delete();WallpaperGuard.draft(c).delete();}}
    @Test public void allFocusWallpapersCanBeSelectedWithoutChangingActiveWallpaper()throws Exception {
        int original=AppWallpaper.selectedIndex(c);
        for(int i=0;i<AppWallpaper.count();i++){
            GuardWallpaperImage.builtIn(c,i);Bitmap bitmap=BitmapFactory.decodeFile(WallpaperGuard.draft(c).getAbsolutePath());
            assertNotNull(AppWallpaper.name(i),bitmap);assertTrue(bitmap.getWidth()>1);assertTrue(Math.max(bitmap.getWidth(),bitmap.getHeight())<=2048);bitmap.recycle();
            assertFalse(WallpaperGuard.image(c).exists());assertEquals(original,AppWallpaper.selectedIndex(c));
        }
    }
    @Test public void pureColorsStayFlatAndOnlyChangeDraft()throws Exception {
        assertEquals(GuardWallpaperCatalog.COLOR_NAMES.length,GuardWallpaperCatalog.COLORS.length);
        for(int color:GuardWallpaperCatalog.COLORS){GuardWallpaperImage.solid(c,color);Bitmap bitmap=BitmapFactory.decodeFile(WallpaperGuard.draft(c).getAbsolutePath());
            assertEquals(1,bitmap.getWidth());assertEquals(1,bitmap.getHeight());int pixel=bitmap.getPixel(0,0);
            assertTrue(Math.abs(Color.red(pixel)-Color.red(color))<=3);assertTrue(Math.abs(Color.green(pixel)-Color.green(color))<=3);assertTrue(Math.abs(Color.blue(pixel)-Color.blue(color))<=3);bitmap.recycle();
            assertFalse(WallpaperGuard.image(c).exists());}
    }
    @Test public void changingPreviewPreservesOldActivePhotoUntilApplied()throws Exception {
        GuardWallpaperImage.solid(c,GuardWallpaperCatalog.COLORS[0]);GuardWallpaperImage.applyDraft(c);
        byte[] original=Files.readAllBytes(WallpaperGuard.image(c).toPath());
        GuardWallpaperImage.solid(c,GuardWallpaperCatalog.COLORS[5]);assertArrayEquals(original,Files.readAllBytes(WallpaperGuard.image(c).toPath()));
        GuardWallpaperImage.applyDraft(c);assertArrayEquals(Files.readAllBytes(WallpaperGuard.draft(c).toPath()),Files.readAllBytes(WallpaperGuard.image(c).toPath()));
    }
}
