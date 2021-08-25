package info.kgeorgiy.ja.kosogorov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static info.kgeorgiy.ja.kosogorov.hello.Utils.*;

public class HelloUDPNonblockingServer implements HelloServer {
    private static final byte[] prefix = ("Hello, ").getBytes(StandardCharsets.UTF_8);

    private ExecutorService workers;

    public HelloUDPNonblockingServer() {
        this.workers = null;
    }

    private static class Data {
        private final ByteBuffer buffer;
        private SocketAddress target;

        private Data(ByteBuffer buffer, SocketAddress target) {
            this.buffer = buffer;
            this.target = target;
        }
    }

    private static Data newData() {
        return new Data(ByteBuffer.allocate(BUFFER_SIZE), null);
    }

    private static ByteBuffer addPrefix(ByteBuffer buffer) {
        buffer.flip();
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        return buffer.clear().put(prefix).put(bytes).flip();
    }

    private void task(Data data, BlockingQueue<Data> result, SelectionKey key) {
        try {
            addPrefix(data.buffer);
            result.put(data);
            key.interestOpsOr(SelectionKey.OP_WRITE);
            key.selector().wakeup();
        } catch (InterruptedException ignored) {
        }
    }

    private static Data poll(Queue<Data> queue) {
        Data data = queue.poll();
        return data == null ? newData() : data;
    }

    private static void freeData(Data data, Queue<Data> queue) {
        data.buffer.clear();
        queue.add(data);
    }

    @Override
    public void start(int port, int threads) {
        final Selector selector;
        final DatagramChannel datagramChannel;

        try {
            selector = Selector.open();
        } catch (IOException e) {
            System.err.println("Cannot create selector: " + e.getMessage());
            return;
        }

        try {
            datagramChannel = DatagramChannel.open();
            try {
                datagramChannel.configureBlocking(false);
                datagramChannel.bind(new InetSocketAddress(port));
                datagramChannel.register(selector, SelectionKey.OP_READ);
            } catch (IOException e) {
                io(datagramChannel::close, "Cannot close channel");
                io(selector::close, "Cannot close selector");
                System.err.println("Error while configuring server: " + e.getMessage());
            }
        } catch (IOException e) {
            io(selector::close, "Cannot close selector");
            System.err.println("Cannot create channel: " + e.getMessage());
            return;
        }

        workers = Executors.newFixedThreadPool(threads + 1);
        final BlockingQueue<Data> result = new ArrayBlockingQueue<>(threads);
        final Queue<Data> free = new ArrayDeque<>();
        IntStream.range(0, threads + 1).forEachOrdered(i -> free.add(newData()));

        workers.submit(() -> io(() -> {
            try {
                // :NOTE: can't shutdown workers without shutting down the server
                while (!workers.isShutdown() && !Thread.currentThread().isInterrupted()) {
                    selector.select(key -> {
                        final DatagramChannel channel = (DatagramChannel) key.channel();
                        if (key.isWritable() && key.isValid()) {
                            try {
                                final Data data = result.take();
                                io(() -> channel.send(data.buffer, data.target), "Cannot send response");
                                freeData(data, free);
                                if (result.isEmpty()) {
                                    key.interestOps(SelectionKey.OP_READ);
                                }
                            } catch (InterruptedException ignored) {
                            }
                        }
                        if (key.isReadable()) {
                            final Data data = poll(free);
                            try {
                                data.target = channel.receive(data.buffer);
                                workers.submit(() -> task(data, result, key));
                            } catch (IOException e) {
                                freeData(data, free);
                                System.err.println("Cannot read request: " + e.getMessage());
                            } catch (RejectedExecutionException ignored) {
                            }
                        }
                    });
                }
            } finally {
                selector.close();
                datagramChannel.close();
            }
        }, "Server error occurred"));
    }

    @Override
    public void close() {
        workers.shutdown();
        try {
            if (!workers.awaitTermination(100L, TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
                if (!workers.awaitTermination(30L, TimeUnit.SECONDS)) {
                    System.err.println("Cannot close executors");
                }
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
        }
    }

    public static void main(String[] args) {
        runServer(new HelloUDPNonblockingServer(), args);
    }
}
