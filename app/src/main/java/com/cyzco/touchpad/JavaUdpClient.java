package com.cyzco.touchpad;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

// This is a Java class that does the EXACT same thing as your
public class JavaUdpClient {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private final int port;
    private boolean initialized = false;

    public JavaUdpClient(String ipAddress, int port) {
        this.port = port;
        try {
            this.serverAddress = InetAddress.getByName(ipAddress);
            this.socket = new DatagramSocket();
            this.initialized = true;
        } catch (Exception e) {
            Log.e("JavaUdpClient", "Failed to initialize: " + e.getMessage());
        }
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    // This is NOT a suspend function, so it must be called from a background thread.
    public void sendCommand(String command) {
        if (!initialized)
        {
            Log.e("JavaUdpClient", "Failed to send: Client is not initialized.");
            return;
        }

        try {
            byte[] data = command.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, port);
            socket.send(packet);
            Log.d("JavaUdpClient", "Sent Command: " + command);
        } catch (Exception e) {
            Log.e("JavaUdpClient", "Failed to send: " + e.getMessage());
        }
    }

    public void close() {
        if (socket != null && !socket.isClosed())
            socket.close();
        initialized = false;
    }
}