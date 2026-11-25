package com.hypercat.ai.shizuku;

interface IRemoteShell {
    String exec(String command);
    void destroy();
}
