package com.zoeykl.SimpleFiles;

// Developed by ZoeyKL

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

/** Apply the same page, panel and text palette to native dialogs, including scrolling list rows. */
final class DialogTheme {
  static final class Builder extends AlertDialog.Builder {
    private final Context context;

    Builder(Context context) {
      super(context);
      this.context = context;
    }

    @Override
    public AlertDialog create() {
      AlertDialog dialog = super.create();
      Appearance palette = new Appearance(context);
      dialog.getWindow().setBackgroundDrawable(new ColorDrawable(palette.color("surface")));
      View decor = dialog.getWindow().getDecorView();
      // Layout observation also catches list-choice rows as Android creates/recycles them.
      // It leaves caller-owned OnShow listeners free to validate names and color input.
      decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> style(decor, palette));
      return dialog;
    }
  }

  private static void style(View view, Appearance palette) {
    if (view instanceof TextView) {
      TextView text = (TextView) view;
      if (text.getCurrentTextColor() != palette.color("text"))
        text.setTextColor(palette.color("text"));
      if (text instanceof EditText) ((EditText) text).setHintTextColor(palette.muted());
      if (view instanceof android.widget.CheckedTextView)
        ((android.widget.CheckedTextView) view)
            .setCheckMarkTintList(ColorStateList.valueOf(palette.color("accent")));
    }
    int id = view.getId();
    if (id != View.NO_ID) {
      try {
        String name = view.getResources().getResourceEntryName(id);
        if (name.equals("parentPanel")
            || name.equals("topPanel")
            || name.equals("contentPanel")
            || name.equals("buttonPanel")
            || name.equals("customPanel")) view.setBackgroundColor(palette.color("surface"));
      } catch (android.content.res.Resources.NotFoundException ignored) {
        /* App-generated IDs have no resource name. */
      }
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) style(group.getChildAt(i), palette);
    }
  }
}
