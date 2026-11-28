package com.mirearpet;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UDP LAN Auto-Discovery client (broadcasts to port 8766 to find PC running server.py).
 */
public final class LanDiscovery {
    public static final int DISCOVERY_PORT = 8766;
    private static final byte[] PROBE = "MI_REAR_PET_DISCOVER".getBytes(StandardCharsets.UTF_8);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static final class DiscoveredHost {
        public final String host;
        public final String os;
        public final String ip;
        public final int port;
        public final String url;

        public DiscoveredHost(String host, String os, String ip, int port, String url) {
            this.host = host;
            this.os = os;
            this.ip = ip;
            this.port = port;
            this.url = url;
        }
    }

    public interface Callback {
        void onFound(List<DiscoveredHost> hosts);
    }

    private LanDiscovery() {}

    public static void discoverAsync(Callback callback) {
        new Thread(() -> {
            Map<String, DiscoveredHost> found = new LinkedHashMap<>();
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.setSoTimeout(350);

                List<InetAddress> targets = new ArrayList<>();
                try {
                    targets.add(InetAddress.getByName("255.255.255.255"));
                } catch (Throwable ignored) {}

                // Also send to subnet broadcast addresses (e.g. 192.168.2.255) for routers that filter 255.255.255.255
                try {
                    Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
                    while (ifaces != null && ifaces.hasMoreElements()) {
                        NetworkInterface nif = ifaces.nextElement();
                        if (nif.isLoopback() || !nif.isUp()) continue;
                        for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                            InetAddress bcast = ia.getBroadcast();
                            if (bcast != null && !targets.contains(bcast)) {
                                targets.add(bcast);
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                // Send 2 quick bursts
                for (int round = 0; round < 2; round++) {
                    for (InetAddress addr : targets) {
                        try {
                            socket.send(new DatagramPacket(PROBE, PROBE.length, addr, DISCOVERY_PORT));
                        } catch (Throwable ignored) {}
                    }
                    long end = System.currentTimeMillis() + 750;
                    byte[] buf = new byte[1024];
                    while (System.currentTimeMillis() < end) {
                        try {
                            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                            socket.receive(pkt);
                            String raw = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8).trim();
                            JSONObject obj = new JSONObject(raw);
                            if ("mi-rear-pet".equals(obj.optString("service"))) {
                                String ip = obj.optString("ip", pkt.getAddress().getHostAddress());
                                int port = obj.optInt("port", 8765);
                                String url = obj.optString("url", "http://" + ip + ":" + port + "/");
                                String host = obj.optString("host", ip);
                                String os = obj.optString("os", "");
                                found.put(url, new DiscoveredHost(host, os, ip, port, url));
                            }
                        } catch (SocketTimeoutException ignored) {
                            break;
                        } catch (Throwable ignored) {}
                    }
                    if (!found.isEmpty()) break;
                }
            } catch (Throwable ignored) {
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (Throwable ignored) {}
                }
            }
            List<DiscoveredHost> result = new ArrayList<>(found.values());
            MAIN.post(() -> callback.onFound(result));
        }, "lan-discovery").start();
    }
}
