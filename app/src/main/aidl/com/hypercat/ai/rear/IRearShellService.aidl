package com.hypercat.ai.rear;

interface IRearShellService {
    void destroy() = 16777114;
    String exec(String command) = 1;
}
