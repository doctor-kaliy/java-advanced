package info.kgeorgiy.ja.kosogorov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class HelloUDPClient implements HelloClient {
    private static final long AWAIT_TIME_MILLIS = 10101;

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        final SocketAddress address = new InetSocketAddress(host, port);
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        IntStream.range(0, threads).forEachOrdered(i ->
            pool.submit(() -> {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setSoTimeout(200);
                    final DatagramPacket packet = Utils.emptyPacket(address);
                    final byte[] buffer = new byte[socket.getReceiveBufferSize()];
                    IntStream.range(0, requests).forEachOrdered(j -> {
                        String message = prefix + i + "_" + j;
                        processRequest(socket, message, new EvilChecker(i, j), packet, buffer);
                    });
                } catch (SocketException e) {
                    System.err.println("Cannot create socket " + i + ": " + e.getMessage());
                }
            })
        );
        Utils.shutDownPool(pool, threads * requests * AWAIT_TIME_MILLIS);
    }

    private static void processRequest(DatagramSocket socket, String message, Checker checker,
                                       DatagramPacket packet, byte[] buffer) {
        while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
            try {
                Utils.sendData(socket, packet, message);
                System.out.println(message);
                Utils.receiveData(socket, packet, buffer);
                final String responseText = Utils.getDatagramMessage(packet);
                if (checker.check(responseText)) {
                    System.out.println(responseText);
                    break;
                }
            } catch (IOException exception) {
                System.err.println("Error while processing request: " + exception.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        Utils.runClient(new HelloUDPClient(), args);
    }
}
