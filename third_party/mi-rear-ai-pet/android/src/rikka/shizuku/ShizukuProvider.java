package rikka.shizuku;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import com.mirearpet.ShizukuBridge;

import moe.shizuku.api.BinderContainer;

/**
 * Official Shizuku client ContentProvider endpoint (${applicationId}.shizuku).
 * Receives "sendBinder" calls pushed by Shizuku server and forwards the Binder to ShizukuBridge.
 */
public class ShizukuProvider extends ContentProvider {
    public static final String METHOD_SEND_BINDER = "sendBinder";
    public static final String METHOD_GET_BINDER = "getBinder";
    public static final String EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER";
    public static final String MANAGER_AUTHORITY = "moe.shizuku.privileged.api.shizuku";

    @Override
    public boolean onCreate() {
        Context ctx = getContext();
        if (ctx != null) {
            ShizukuBridge.initContext(ctx.getApplicationContext());
        }
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_SEND_BINDER.equals(method) && extras != null) {
            extras.setClassLoader(BinderContainer.class.getClassLoader());
            BinderContainer container = extras.getParcelable(EXTRA_BINDER);
            if (container != null && container.binder != null) {
                Context ctx = getContext();
                if (ctx != null) {
                    ShizukuBridge.onBinderReceived(ctx.getApplicationContext(), container.binder);
                }
            }
            return new Bundle();
        }
        return super.call(method, arg, extras);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
