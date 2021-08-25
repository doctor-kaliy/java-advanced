package info.kgeorgiy.ja.kosogorov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class HelloUDPServer implements HelloServer {
    private ExecutorService pool;
    private  DatagramSocket socket;
    private int size;

    public HelloUDPServer() {
        this.pool = null;
        this.socket = null;
        this.size = 0;
    }

    @Override
    public void start(int port, int threads) {
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(200);
            size = socket.getReceiveBufferSize();
        } catch (SocketException e) {
            System.err.println("Cannot create socket on port " + port);
            return;
        }
        pool = Executors.newFixedThreadPool(threads);
        IntStream.range(0, threads).forEach(i ->
            pool.submit(() -> {
                final byte[] buffer = new byte[size];
                final DatagramPacket packet = Utils.emptyPacket();
                while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                    try {
                        Utils.receiveData(socket, packet, buffer);
                        Utils.sendData(socket, packet,"Hello, " + Utils.getDatagramMessage(packet));
                    } catch (SocketException ignored) {
                    } catch (IOException e) {
                        System.err.println("Error occurred while processing request: " + e.getMessage());
                    }
                }
            })
        );
    }

    @Override
    public void close() {
        socket.close();
        Utils.shutDownPool(pool, 30000);
    }

    public static void main(String[] args) {
        Utils.runServer(new HelloUDPServer(), args);
    }
}
