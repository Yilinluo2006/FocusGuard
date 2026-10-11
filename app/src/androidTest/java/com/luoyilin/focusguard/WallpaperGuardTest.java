package com.luoyilin.focusguard;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.io.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Uses only the independent probe's files; never changes the system wallpaper. */
public class WallpaperGuardTest {
    private Context c;
    @Before public void setup() { c=InstrumentationRegistry.getInstrumentation().getTargetContext(); assumeTrue(c.getPackageName().endsWith(".setupprobe")); }
    @After public void cleanup() { if(c!=null && c.getPackageName().endsWith(".setupprobe")) { WallpaperGuard.image(c).delete(); new File(c.getCacheDir(),"wallpaper-input.png").delete(); } }
    @Test public void importedPhotoIsLocalAndBounded() throws Exception {
        File input=new File(c.getCacheDir(),"wallpaper-input.png"); Bitmap image=Bitmap.createBitmap(3000,1500,Bitmap.Config.ARGB_8888); image.eraseColor(Color.BLUE);
        try(FileOutputStream out=new FileOutputStream(input)){image.compress(Bitmap.CompressFormat.PNG,100,out);} image.recycle();
        GuardWallpaperImage.save(c,Uri.fromFile(input)); Bitmap saved=BitmapFactory.decodeFile(WallpaperGuard.image(c).getAbsolutePath());
        assertNotNull(saved); assertTrue(Math.max(saved.getWidth(),saved.getHeight())<=2048); assertEquals(2f,saved.getWidth()/(float)saved.getHeight(),0.01f); saved.recycle();
    }
    @Test public void failedImportPreservesThePreviousPhoto() throws Exception {
        File input=new File(c.getCacheDir(),"wallpaper-input.png"); Bitmap image=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888); image.eraseColor(Color.GREEN);
        try(FileOutputStream out=new FileOutputStream(input)){image.compress(Bitmap.CompressFormat.PNG,100,out);} image.recycle(); GuardWallpaperImage.save(c,Uri.fromFile(input));
        long length=WallpaperGuard.image(c).length();
        try { GuardWallpaperImage.save(c,Uri.fromFile(new File(c.getCacheDir(),"nonexistent.jpg"))); fail(); } catch(IOException expected) { }
        assertEquals(length,WallpaperGuard.image(c).length()); assertNotNull(BitmapFactory.decodeFile(WallpaperGuard.image(c).getAbsolutePath()));
    }
    @Test public void openingAndReturningNeverAppliesAWallpaper() {
        boolean before=WallpaperGuard.selected(c);
        try(ActivityScenario<android.app.Activity> scenario=ActivityScenario.launch(new Intent().setClassName(c.getPackageName(),"com.luoyilin.focusguard.WallpaperGuardActivity"))) {
            scenario.onActivity(activity -> { assertFalse(activity.isFinishing()); activity.finish(); });
        }
        assertEquals(before,WallpaperGuard.selected(c));
    }
}
