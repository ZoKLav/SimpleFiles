package com.zoeykl.SimpleFiles;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Monochrome vector shapes so Android emoji rendering cannot override tint. */
final class FileIcon extends Drawable {
    private final boolean folder;
    private final String extension;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int tint;
    private int opacity=255;
    FileIcon(boolean folder,String extension,int tint) { this.folder=folder;this.extension=extension;this.tint=tint; }
    @Override public void draw(Canvas canvas) {
        Rect b=getBounds(); int save=canvas.save();
        canvas.translate(b.left,b.top); canvas.scale(b.width()/48f,b.height()/48f);
        paint.setColor(tint);paint.setAlpha(opacity);paint.setStyle(Paint.Style.FILL);
        Path path=new Path();
        if(folder) {
            path.moveTo(4,12);path.lineTo(19,12);path.lineTo(23,17);path.lineTo(44,17);path.lineTo(44,38);path.lineTo(4,38);path.close();
            canvas.drawPath(path,paint);
            paint.setColor(Appearance.blend(tint,Color.WHITE,0.2f));paint.setAlpha(opacity);
            canvas.drawRoundRect(4,20,44,39,3,3,paint);
        } else {
            path.moveTo(10,5);path.lineTo(29,5);path.lineTo(39,15);path.lineTo(39,43);path.lineTo(10,43);path.close();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f);paint.setStrokeJoin(Paint.Join.ROUND);canvas.drawPath(path,paint);
            path.reset();path.moveTo(29,5);path.lineTo(29,15);path.lineTo(39,15);canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            paint.setTextSize(8);String label=extension.isEmpty()?"FILE":extension.toUpperCase(java.util.Locale.ROOT);
            canvas.drawText(label.substring(0,Math.min(label.length(),4)),24,32,paint);
        }
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { opacity=alpha;invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter);invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
