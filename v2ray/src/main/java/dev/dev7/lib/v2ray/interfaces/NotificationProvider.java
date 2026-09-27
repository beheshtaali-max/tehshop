package dev.dev7.lib.v2ray.interfaces;

import android.app.Notification;

public interface NotificationProvider {
    Notification getOngoingNotification(String remark, int iconResource);
    void updateNotificationSpeed(long downloadSpeed, long uploadSpeed, long totalDownload, long totalUpload);
    void dismiss();
}
