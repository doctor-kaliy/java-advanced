package info.kgeorgiy.ja.kosogorov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.AdvancedIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IterativeParallelism implements AdvancedIP {
    private final ParallelMapper parallelMapper;

    public IterativeParallelism() {
        this.parallelMapper = null;
    }

    public IterativeParallelism(ParallelMapper parallelMapper) {
        this.parallelMapper = parallelMapper;
    }

    private <T, R, P> T operation(
        int threads,
        List<? extends P> values,
        Function<? super Stream<? extends P>, R> function,
        Function<? super Stream<R>, T> finisher
    ) throws InterruptedException {
        threads = Math.min(threads, values.size());
        List<Stream<? extends P>> streams = new ArrayList<>();
        int step = values.size() / threads;
        int rest = values.size() % threads + 1;
        for (int i = 0; i < values.size(); i += (step + (rest == 0? 0 : 1))) {
            if (rest > 0) {
                rest--;
            }
            int j = Math.min(i + (step + (rest == 0? 0 : 1)), values.size());
            streams.add(values.subList(i, j).stream());
        }
        List<R> results;
        if (parallelMapper == null) {
            List<Thread> threadList = new ArrayList<>();
            results = new ArrayList<>();
            for (Stream<? extends P> stream : streams) {
                int j = results.size();
                results.add(null);
                Thread thread = new Thread(() -> results.set(j, function.apply(stream)));
                threadList.add(thread);
            }
            threadList.forEach(Thread::start);
            InterruptedException error = null;
            for (int i = 0; i < threadList.size(); i++) {
                try {
                    threadList.get(i).join();
                } catch (InterruptedException e) {
                    --i;
                    if (error == null) {
                        error = e;
                    } else {
                        error.addSuppressed(e);
                    }
                }
            }
            if (error != null) {
                throw error;
            }
        } else {
            results = parallelMapper.map(function, streams);
        }
        return finisher.apply(results.stream());
    }

    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        return operation(threads, values,
            stream -> stream.map(Object::toString).collect(Collectors.joining("")),
            stream -> String.join("", stream.collect(Collectors.toList())));
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        // :NOTE: intermediate list
        return operation(threads, values,
            stream -> stream.filter(predicate),
            stream -> stream.flatMap(Function.identity()).collect(Collectors.toList()));
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends U> f) throws InterruptedException {
        return operation(threads, values,
            stream -> stream.map(f).collect(Collectors.toList()),
            stream -> stream.flatMap(Collection::stream).collect(Collectors.toList()));
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException {
        // :NOTE: breaks function invariant/documentation
        Function<Stream<? extends T>, T> max = stream -> stream.reduce((a, b) ->
            comparator.compare(a, b) < 0? b : a).orElseThrow();
        return operation(threads, values, max, max);
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException {
        return maximum(threads, values, comparator.reversed());
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return operation(threads, values,
            stream -> stream.allMatch(predicate),
            stream -> stream.reduce(true, (a, b) -> a && b));
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return !all(threads, values, predicate.negate());
    }

    @Override
    public <T> T reduce(int threads, List<T> values, Monoid<T> monoid) throws InterruptedException {
        return mapReduce(threads, values, Function.identity(), monoid);
    }

    @Override
    public <T, R> R mapReduce(int threads, List<T> values, Function<T, R> lift, Monoid<R> monoid) throws InterruptedException {
        return operation(threads, values,
            stream -> stream.map(lift).reduce(monoid.getIdentity(), monoid.getOperator()),
            stream -> stream.reduce(monoid.getIdentity(), monoid.getOperator()));
    }
}

