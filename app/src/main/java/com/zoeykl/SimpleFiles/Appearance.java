package com.zoeykl.SimpleFiles;

// Developed by ZoeyKL

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Shared color roles keep settings, browser panels and drawn icons consistent. Page, panel and text
 * colors have independent light/dark preference keys.
 */
final class Appearance {
  final SharedPreferences prefs;
  final boolean dark;

  Appearance(Context context) {
    prefs = context.getSharedPreferences("prefs", 0);
    dark = prefs.getBoolean("dark", false);
  }

  int color(String role) {
    return prefs.getInt(key(role), fallback(role));
  }

  String key(String role) {
    return (role.equals("background") || role.equals("surface") || role.equals("text"))
        ? role + (dark ? "_dark" : "_light")
        : role;
  }

  int fallback(String role) {
    switch (role) {
      case "accent":
        return 0xff4169e1;
      case "folderColor":
        return 0xffd5a12b;
      case "fileColor":
        return 0xff5c83ad;
      case "toolbarColor":
        return dark ? 0xffa9c5ff : 0xff4169e1;
      case "background":
        return dark ? 0xff171717 : 0xfff4f5f7;
      case "surface":
        return dark ? 0xff232323 : 0xffffffff;
      default:
        return dark ? 0xfff2f3f5 : 0xff20232a;
    }
  }

  int muted() {
    return blend(color("text"), color("surface"), 0.42f);
  }

  static int blend(int a, int b, float amount) {
    return Color.rgb(
        (int) (Color.red(a) * (1 - amount) + Color.red(b) * amount),
        (int) (Color.green(a) * (1 - amount) + Color.green(b) * amount),
        (int) (Color.blue(a) * (1 - amount) + Color.blue(b) * amount));
  }
}
