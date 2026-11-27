package com.hypercat.ai.rear;
interface IRearShellService {
    void destroy() = 16777114;
    String exec(String command) = 1;
    String scanCarriers() = 2;
    String deployCarrier(String cardId, String packagePath, String backupPath) = 3;
    String restoreCarrier(String cardId, String backupPath) = 4;
}
