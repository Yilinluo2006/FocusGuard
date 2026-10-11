package com.luoyilin.focusguard;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Names remain recognizable when a remote app is not installed locally. */
final class AppLabelIcon extends Drawable {
    private final String label;private final int color;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    AppLabelIcon(String name,int color){label=name.substring(0,Math.min(2,name.length()));this.color=color;}
    @Override public void draw(Canvas canvas){Rect b=getBounds();float size=Math.min(b.width(),b.height());paint.setColor(color);canvas.drawRoundRect(new RectF(b),size*.22f,size*.22f,paint);paint.setColor(Color.rgb(25,25,25));paint.setTextSize(size*.32f);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(label,b.exactCenterX(),b.exactCenterY()-(paint.ascent()+paint.descent())/2,paint);}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return 48;}
    @Override public int getIntrinsicHeight(){return 48;}
}
