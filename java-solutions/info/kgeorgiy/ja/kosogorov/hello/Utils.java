package info.kgeorgiy.ja.kosogorov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class Utils {
    public static final int BUFFER_SIZE = 1024;

    public interface Task {
        void run() throws IOException;
    }

    public static void io(final Task task, String message) {
        try {
            task.run();
        } catch (final IOException e) {
            System.err.println(message + ": " + e.getMessage());
        }
    }

    public static void shutDownPool(ExecutorService pool, long time) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(time, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            pool.shutdownNow();
        }
    }

    public static DatagramPacket emptyPacket(SocketAddress address) {
        return new DatagramPacket(new byte[0], 0, address);
    }

    public static DatagramPacket emptyPacket() {
        return new DatagramPacket(new byte[0], 0);
    }

    public static String getDatagramMessage(DatagramPacket packet) {
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
    }

    public static void sendData(DatagramSocket socket, DatagramPacket packet, String message) throws IOException {
        packet.setData(message.getBytes(StandardCharsets.UTF_8));
        socket.send(packet);
    }

    public static void receiveData(DatagramSocket socket, DatagramPacket packet, byte[] buffer) throws IOException {
        packet.setData(buffer);
        socket.receive(packet);
    }

    public static void runServer(HelloServer server, String... args) {
        if (args == null || args.length != 2) {
            System.err.println("Expected 2 arguments");
            return;
        }
        if (Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Arguments should be correct integer numbers: Found null.");
            return;
        }
        try {
            server.start(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        } catch (NumberFormatException e) {
            System.err.println("Arguments should be correct integer numbers: " + e.getMessage());
        }
    }

    public static void runClient(HelloClient client, String... args) {
        if (args == null || args.length != 5) {
            System.err.println("Expected 5 arguments");
            return;
        }
        if (Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Arguments should be correct integer numbers: Found null.");
            return;
        }
        try {
            client.run(args[0], Integer.parseInt(args[1]), args[2],
                    Integer.parseInt(args[3]), Integer.parseInt(args[4]));
        } catch (NumberFormatException e) {
            System.err.println("Arguments should be correct integer numbers: " + e.getMessage());
        }
    }
}
