package com.luoyilin.focusguard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.AtomicFile;
import java.io.*;

final class GuardWallpaperImage {
    static void save(Context c, Uri uri) throws IOException {
        save(c,uri,WallpaperGuard.image(c));
    }
    static void save(Context c, Uri uri, File destination) throws IOException {
        Bitmap bitmap = null;
        AtomicFile file = new AtomicFile(destination); FileOutputStream out = null;
        try {
            if (Build.VERSION.SDK_INT >= 28) bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(), uri), (decoder, info, source) -> {
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                float scale = Math.min(1f, 2048f / Math.max(w, h));
                decoder.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            else {
                BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inJustDecodeBounds = true;
                try (InputStream in = c.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, opts); }
                opts.inJustDecodeBounds = false; opts.inSampleSize = 1;
                while (Math.max(opts.outWidth, opts.outHeight) / opts.inSampleSize > 2048) opts.inSampleSize *= 2;
                try (InputStream in = c.getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in, null, opts); }
            }
            if (bitmap == null) throw new IOException("Invalid image");
            out = file.startWrite(); if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new IOException("Cannot encode image");
            file.finishWrite(out); out = null;
        } finally { if (out != null) file.failWrite(out); if (bitmap != null) bitmap.recycle(); }
    }
    static void builtIn(Context c,int index)throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeResource(c.getResources(),AppWallpaper.resource(index),options);
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048)options.inSampleSize*=2;
        options.inScaled=false;
        Bitmap bitmap=BitmapFactory.decodeResource(c.getResources(),AppWallpaper.resource(index),options);
        if(bitmap==null)throw new IOException("Image unavailable");
        try{write(bitmap,WallpaperGuard.draft(c));}finally{bitmap.recycle();}
    }
    static void solid(Context c,int color)throws IOException {
        Bitmap bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);
        try{write(bitmap,WallpaperGuard.draft(c));}finally{bitmap.recycle();}
    }
    private static void write(Bitmap bitmap,File target)throws IOException {
        AtomicFile file=new AtomicFile(target);FileOutputStream out=null;
        try{out=file.startWrite();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,92,out))throw new IOException("Cannot encode image");file.finishWrite(out);out=null;}
        finally{if(out!=null)file.failWrite(out);}
    }
    static void applyDraft(Context c)throws IOException {
        if(!WallpaperGuard.draft(c).exists())return;
        AtomicFile file=new AtomicFile(WallpaperGuard.image(c));FileOutputStream out=null;
        try(InputStream in=new FileInputStream(WallpaperGuard.draft(c))){
            out=file.startWrite();byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            file.finishWrite(out);out=null;
        }finally{if(out!=null)file.failWrite(out);}
    }
}
