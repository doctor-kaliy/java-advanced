package info.kgeorgiy.ja.kosogorov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

import static info.kgeorgiy.ja.kosogorov.hello.Utils.*;

public class HelloUDPNonblockingClient implements HelloClient {

    private static class Attachment {
        private final int threadNumber;
        private int request;
        private final StringBuilder builder;
        private Checker checker = null;

        private Attachment(int threadNumber, String prefix) {
            this.threadNumber = threadNumber;
            this.request = 0;
            this.builder = new StringBuilder()
                .append(prefix)
                .append(threadNumber)
                .append("_");
        }

        private String getRequest() {
            return builder.toString() + request;
        }
    }

    private void keyConsumer(final SelectionKey key, final ByteBuffer buffer,
                             final SocketAddress address, final byte[] bytes, int requests) {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Attachment attachment = (Attachment) key.attachment();
        if (key.isWritable()) {
            buffer.clear();
            final String request = attachment.getRequest();
            buffer.put(request.getBytes(StandardCharsets.UTF_8));
            buffer.flip();
            io(() -> {
                channel.send(buffer, address);
                System.out.println(request);
                attachment.checker = new EvilChecker(attachment.threadNumber, attachment.request);
                key.interestOps(SelectionKey.OP_READ);
            }, "Cannot sent request " + request);
        }
        if (key.isReadable()) {
            buffer.clear();
            try {
                channel.receive(buffer);
                buffer.flip();
                buffer.get(bytes, 0, buffer.limit());
                final String response =
                        new String(bytes, 0, buffer.limit(), StandardCharsets.UTF_8);
                if (attachment.checker.check(response)) {
                    attachment.request++;
                }
                if (attachment.request == requests) {
                    key.cancel();
                } else {
                    key.interestOps(SelectionKey.OP_WRITE);
                }
            } catch (IOException e) {
                System.err.println("Cannot receive response, trying send request again: " + e.getMessage());
                key.interestOps(SelectionKey.OP_WRITE);
                attachment.request--;
            }
        }
    }

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        final SocketAddress address = new InetSocketAddress(host, port);
        DatagramChannel[] channels = new DatagramChannel[threads];

        try (final Selector selector = Selector.open()) {
            try {
                for (int i = 0; i < threads; i++) {
                    channels[i] = DatagramChannel.open();
                    channels[i].configureBlocking(false);
                    channels[i].bind(null);
                    channels[i].register(selector, SelectionKey.OP_WRITE, new Attachment(i, prefix));
                }

                ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                byte[] bytes = new byte[BUFFER_SIZE];

                while (!selector.keys().isEmpty()) {
                    try {
                        if (selector.select(k -> keyConsumer(k, buffer, address, bytes, requests), 200L) == 0) {
                            selector.keys().forEach(key -> key.interestOps(SelectionKey.OP_WRITE));
                        }
                    } catch (IOException e) {
                        System.err.println("Error occurred during selection operation: " + e.getMessage());
                    }
                }
            } finally {
                Arrays.stream(channels).filter(Objects::nonNull)
                    .forEach(channel -> io(channel::close, "Cannot close channel "));
            }
        } catch (IOException e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        runClient(new HelloUDPNonblockingClient(), args);
    }
}

