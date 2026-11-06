package dev.dev7.lib.v2ray.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.dev7.lib.v2ray.core.SmartDelayRequestStore;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

/** Handle returned to the host app so a running smart-server scan can be cancelled. */
public final class RealDelayTestHandle {
    private final Context appContext;
    private final String requestId;
    private final BroadcastReceiver receiver;
    private volatile boolean released;

    public RealDelayTestHandle(Context context, String requestId, BroadcastReceiver receiver) {
        this.appContext = context.getApplicationContext();
        this.requestId = requestId;
        this.receiver = receiver;
    }

    public String getRequestId() {
        return requestId;
    }

    public void cancel() {
        if (released) return;

        // The worker reads this in-process flag, so cancellation still works even if Android
        // delays delivery of the command broadcast.
        SmartDelayRequestStore.cancel(requestId);

        Intent intent = new Intent(V2rayConstants.V2RAY_SERVICE_COMMAND_INTENT)
                .setPackage(appContext.getPackageName())
                .putExtra(
                        V2rayConstants.V2RAY_SERVICE_COMMAND_EXTRA,
                        V2rayConstants.SERVICE_COMMANDS.CANCEL_SMART_DELAY_TEST
                )
                .putExtra(V2rayConstants.SMART_DELAY_REQUEST_ID_EXTRA, requestId);
        appContext.sendBroadcast(intent);
    }

    public void release() {
        if (released) return;
        released = true;
        try {
            appContext.unregisterReceiver(receiver);
        } catch (Throwable ignored) {
        }
    }
}
