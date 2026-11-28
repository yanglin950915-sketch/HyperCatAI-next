package moe.shizuku.server;

import android.os.Bundle;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuApplication;

interface IShizukuService {
    int getVersion() = 2;
    int getUid() = 3;
    int checkPermission(String permission) = 4;
    IRemoteProcess newProcess(in String[] cmd, in String[] env, in String dir) = 7;
    String getSELinuxContext() = 8;
    String getSystemProperty(in String name, in String defaultValue) = 9;
    void setSystemProperty(in String name, in String value) = 10;
    void attachApplicationV11(in IShizukuApplication application, String packageName) = 13;
    void requestPermission(int requestCode) = 14;
    boolean checkSelfPermission() = 15;
    boolean shouldShowRequestPermissionRationale() = 16;
    void attachApplication(in IShizukuApplication application, in Bundle args) = 17;
}
