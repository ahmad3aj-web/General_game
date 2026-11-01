package com.generalgame;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Small LAN transport for GENERAL. Messages are UTF-8 JSON objects, one per line.
 * The game layer remains authoritative and decides which messages are valid.
 */
public final class LanConnection {
    public interface Listener {
        void onConnected();
        void onMessage(JSONObject message);
        void onDisconnected(String reason);
    }

    public static final int PORT = 47821;
    private final Listener listener;
    private volatile boolean closed;
    private volatile Socket socket;
    private volatile BufferedWriter writer;
    private Thread readerThread;
    private volatile ServerSocket server;

    public LanConnection(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("listener");
        this.listener = listener;
    }

    /** Blocks on a worker thread until one peer joins or the timeout expires. */
    public void host() throws IOException {
        server = new ServerSocket(PORT, 1, InetAddress.getByName("0.0.0.0"));
        try {
            socket = server.accept();
        } finally {
            try { server.close(); } catch (IOException ignored) { }
        }
        startReader();
    }

    /** Connects to the host's IPv4 address on the standard GENERAL port. */
    public void join(String hostAddress) throws IOException {
        if (hostAddress == null || hostAddress.trim().isEmpty())
            throw new IllegalArgumentException("عنوان الجهاز المضيف مطلوب");
        Socket candidate = new Socket();
        candidate.connect(new java.net.InetSocketAddress(hostAddress.trim(), PORT), 8000);
        candidate.setTcpNoDelay(true);
        socket = candidate;
        startReader();
    }

    public synchronized void send(JSONObject message) throws IOException {
        if (closed || writer == null) throw new IOException("الاتصال غير جاهز");
        writer.write(message.toString());
        writer.newLine();
        writer.flush();
    }

    private void startReader() throws IOException {
        socket.setTcpNoDelay(true);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        final BufferedReader reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), "UTF-8"));
        listener.onConnected();
        readerThread = new Thread(() -> {
            String line;
            try {
                while (!closed && (line = reader.readLine()) != null) {
                    try {
                        listener.onMessage(new JSONObject(line));
                    } catch (Exception malformed) {
                        listener.onDisconnected("وصلت رسالة غير صالحة");
                        break;
                    }
                }
                if (!closed) listener.onDisconnected("انقطع اتصال الجهاز الآخر");
            } catch (IOException error) {
                if (!closed) listener.onDisconnected("انقطع الاتصال: " + error.getMessage());
            } finally {
                close();
            }
        }, "GENERAL-LAN-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /** Returns a usable private IPv4 address for displaying to the other player. */
    public static String getLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String fallback = "";
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                String interfaceName = network.getName().toLowerCase(java.util.Locale.ROOT);
                boolean wifi = interfaceName.contains("wlan") || interfaceName.contains("wifi");
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        String value = address.getHostAddress();
                        boolean privateAddress = value.startsWith("192.168.") || value.startsWith("10.")
                                || value.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
                        if (privateAddress && wifi) return value;
                        if (privateAddress && fallback.isEmpty()) fallback = value;
                    }
                }
            }
            return fallback;
        } catch (SocketException ignored) { }
        return "";
    }

    public synchronized void close() {
        closed = true;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        writer = null;
    }
}
