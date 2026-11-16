package com.zoeykl.SimpleFiles;

import android.graphics.*;
import android.graphics.drawable.Drawable;

// Developed by ZoeyKL
/** Small vector command icons stay crisp and respect the toolbar color on every device. */
final class ActionIcon extends Drawable {
  private final String action;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int tint;
  private int alpha = 255;

  ActionIcon(String action, int tint) {
    this.action = action;
    this.tint = tint;
  }

  private void line(Canvas c, float a, float b, float x, float y) {
    c.drawLine(a, b, x, y, paint);
  }

  @Override
  public void draw(Canvas c) {
    Rect b = getBounds();
    int save = c.save();
    c.translate(b.left, b.top);
    c.scale(b.width() / 24f, b.height() / 24f);
    paint.setColor(tint);
    paint.setAlpha(alpha);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(1.7f);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    switch (action) {
      case "cut":
        c.drawCircle(6, 17, 3, paint);
        c.drawCircle(18, 17, 3, paint);
        line(c, 7, 14, 18, 3);
        line(c, 17, 14, 6, 3);
        break;
      case "copy":
        c.drawRoundRect(8, 7, 21, 21, 1, 1, paint);
        line(c, 4, 17, 3, 17);
        line(c, 3, 17, 3, 3);
        line(c, 3, 3, 16, 3);
        break;
      case "paste":
        c.drawRoundRect(5, 5, 20, 22, 1, 1, paint);
        c.drawRoundRect(9, 2, 16, 7, 1, 1, paint);
        line(c, 9, 12, 16, 12);
        line(c, 9, 16, 16, 16);
        break;
      case "rename":
        line(c, 4, 20, 8, 19);
        line(c, 8, 19, 21, 6);
        line(c, 21, 6, 17, 2);
        line(c, 17, 2, 4, 15);
        line(c, 4, 15, 4, 20);
        line(c, 3, 23, 21, 23);
        break;
      case "delete":
        line(c, 3, 6, 21, 6);
        line(c, 9, 3, 15, 3);
        line(c, 6, 6, 7, 22);
        line(c, 7, 22, 17, 22);
        line(c, 17, 22, 18, 6);
        line(c, 10, 10, 10, 18);
        line(c, 14, 10, 14, 18);
        break;
      case "new":
        Path p = new Path();
        p.moveTo(2, 6);
        p.lineTo(9, 6);
        p.lineTo(12, 9);
        p.lineTo(22, 9);
        p.lineTo(22, 21);
        p.lineTo(2, 21);
        p.close();
        c.drawPath(p, paint);
        line(c, 12, 12, 12, 18);
        line(c, 9, 15, 15, 15);
        break;
      case "select":
        c.drawRect(3, 3, 21, 21, paint);
        line(c, 7, 12, 11, 16);
        line(c, 11, 16, 18, 8);
        break;
      case "share":
        c.drawCircle(5, 12, 3, paint);
        c.drawCircle(19, 4, 3, paint);
        c.drawCircle(19, 20, 3, paint);
        line(c, 8, 10, 16, 6);
        line(c, 8, 14, 16, 18);
        break;
      case "info":
        c.drawCircle(12, 12, 10, paint);
        line(c, 12, 11, 12, 18);
        c.drawPoint(12, 7, paint);
        break;
      case "pin":
        line(c, 8, 3, 18, 3);
        line(c, 10, 3, 10, 10);
        line(c, 16, 3, 16, 10);
        line(c, 10, 10, 6, 15);
        line(c, 6, 15, 20, 15);
        line(c, 20, 15, 16, 10);
        line(c, 13, 15, 13, 23);
        break;
    }
    c.restoreToCount(save);
  }

  @Override
  public void setAlpha(int value) {
    alpha = value;
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
