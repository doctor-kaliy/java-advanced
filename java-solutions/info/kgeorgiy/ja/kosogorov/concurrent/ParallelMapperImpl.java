package info.kgeorgiy.ja.kosogorov.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;

public class ParallelMapperImpl implements ParallelMapper {
    private final Queue<Task> tasks;
    private final List<Thread> threads;
    private boolean isClosed;

    public ParallelMapperImpl(int threads) {
        this.threads = new ArrayList<>();
        tasks = new ArrayDeque<>();
        isClosed = false;
        while (threads-- > 0) {
            // :NOTE: create 1 runnable, not n of them
            Thread thread = new Thread(() -> {
                try {
                    while (!Thread.interrupted()) {
                        Runnable task;
                        synchronized (tasks) {
                            while (tasks.isEmpty()) {
                                tasks.wait();
                            }
                            task = tasks.poll().runnable;
                        }
                        task.run();
                    }
                } catch (InterruptedException ignored) {}
            });
            this.threads.add(thread);
        }
        this.threads.forEach(Thread::start);
    }

    private static class Task {
        private final Counter counter;
        private final Runnable runnable;

        private Task(Counter counter, Runnable runnable) {
            this.counter = counter;
            this.runnable = runnable;
        }
    }

    private static class Counter {
        private int value;
        private final int tasks;
        private boolean isClosed;

        private Counter(int initialValue, int tasks) {
            this.value = initialValue;
            this.tasks = tasks;
            this.isClosed = false;
        }

        private synchronized void increment() {
            value++;
            if (value == tasks) {
                notify();
            }
        }

        private synchronized void close() {
            if (!isClosed) {
                value = tasks;
                isClosed = true;
                notify();
            }
        }
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> args) throws InterruptedException {
        // :NOTE: needs a check for isClosed
        List<R> result = new ArrayList<>(Collections.nCopies(args.size(), null));
        Counter counter = new Counter(0, args.size());
        RuntimeException exception = new RuntimeException("Error occurred while mapping");
        for (int i = 0; i < args.size(); i++) {
            final int j = i;
            final T arg = args.get(j);
            synchronized (tasks) {
                tasks.add(new Task(counter, () -> {
                    try {
                        R res = f.apply(arg);
                        synchronized (result) {
                            result.set(j, res);
                        }
                    } catch (RuntimeException e) {
                        exception.addSuppressed(e);
                    } finally {
                        counter.increment();
                    }
                }));
                tasks.notify();
            }
        }
        if (exception.getSuppressed().length > 0) {
            throw exception;
        }
        synchronized (counter) {
            while (counter.value < args.size()) {
                counter.wait();
            }
        }
        return result;
    }

    @Override
    public void close() {
        if (isClosed) {
            return;
        }
        // :NOTE: before it would make maps hang forever if they started after ending existing tasks
        isClosed = true;
        threads.forEach(Thread::interrupt);
        for (int i = 0; i < threads.size(); i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException ignored) {
                --i;
            }
        }
        synchronized (tasks) {
            tasks.forEach(task -> task.counter.close());
        }
    }
}

