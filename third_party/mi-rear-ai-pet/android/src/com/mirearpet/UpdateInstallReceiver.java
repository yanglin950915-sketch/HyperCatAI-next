package com.mirearpet;

import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.view.Display;
import android.widget.Toast;

/**
 * Receives Android PackageInstaller session callbacks for 1-tap in-app OTA updates.
 */
public class UpdateInstallReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_RESULT = "com.mirearpet.INSTALL_UPDATE_RESULT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    // Always launch system update dialog on main Display 0 (even when pet is running on Display 1)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ActivityOptions opts = ActivityOptions.makeBasic();
                        opts.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
                        context.startActivity(confirm, opts.toBundle());
                    } else {
                        context.startActivity(confirm);
                    }
                } catch (Throwable t) {
                    DiagLog.log("Update", "打开系统安装确认界面失败: " + t.getMessage());
                    Toast.makeText(context, "无法打开安装弹窗: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            DiagLog.log("Update", "应用更新安装成功");
            Toast.makeText(context, "更新安装成功！", Toast.LENGTH_SHORT).show();
        } else {
            String detail = "更新安装未完成 (status=" + status + (msg != null ? ", " + msg : "") + ")";
            DiagLog.log("Update", detail);
            Toast.makeText(context, detail, Toast.LENGTH_LONG).show();
        }
    }
}
