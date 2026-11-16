package com.zoeykl.SimpleFiles;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import java.io.*;

// Developed by ZoeyKL
/** Root checks run away from the main thread so a superuser prompt cannot freeze settings. */
final class RootAccess {
  interface Result {
    void complete(boolean granted);
  }

  static void request(Activity activity, Result result) {
    new Thread(
            () -> {
              boolean granted = false;
              Process process = null;
              try {
                process = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
                // A denied or abandoned prompt must not leave an authorization worker hanging
                // forever.
                final Process running = process;
                Handler handler = new Handler(Looper.getMainLooper());
                Runnable timeout = () -> running.destroy();
                handler.postDelayed(timeout, 30000);
                try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                  String line;
                  while ((line = reader.readLine()) != null)
                    if (line.contains("uid=0")) granted = true;
                }
                granted = process.waitFor() == 0 && granted;
                handler.removeCallbacks(timeout);
              } catch (Exception ignored) {
                granted = false;
              } finally {
                if (process != null) process.destroy();
              }
              final boolean allowed = granted;
              activity.runOnUiThread(
                  () -> {
                    if (!activity.isFinishing() && !activity.isDestroyed())
                      result.complete(allowed);
                  });
            },
            "SimpleFiles-root-check")
        .start();
  }
}
