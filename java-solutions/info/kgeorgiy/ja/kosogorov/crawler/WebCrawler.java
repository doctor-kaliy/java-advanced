package info.kgeorgiy.ja.kosogorov.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;

public class WebCrawler implements AdvancedCrawler {
    private final Downloader downloader;
    private final int perHost;
    private final ExecutorService downloadService;
    private final ExecutorService extractService;
    private final Map<String, Semaphore> hostsSemaphores;
    private static final long TIME = 60000;

    public WebCrawler(final Downloader downloader, final int downloaders, final int extractors, final int perHost) {
        this.downloader = downloader;
        this.perHost = perHost;
        this.downloadService = Executors.newFixedThreadPool(downloaders);
        this.extractService = Executors.newFixedThreadPool(extractors);
        this.hostsSemaphores = new ConcurrentHashMap<>();
    }

    private static void addTask(final ExecutorService service, final Phaser phaser, final Runnable task) {
        phaser.register();
        service.submit(task);
    }

    private static String getHost(final String url) {
        String host = null;
        try {
            host = URLUtils.getHost(url);
        } catch (final MalformedURLException ignored) {
        }
        return host;
    }

    private static boolean isValidHost(final String host, final Set<String> hosts) {
        return host != null && (hosts == null || hosts.contains(host));
    }

    private Result downloadImpl(final String start, final int depth, final List<String> hosts) {
        final Map<String, IOException> errors = new ConcurrentHashMap<>();
        final Set<String> visited = ConcurrentHashMap.newKeySet();
        final Set<String> hostsSet = (hosts == null? null : new HashSet<>(hosts));
        final Queue<String> layer = new ArrayDeque<>();
        if (isValidHost(getHost(start), hostsSet)) {
            layer.add(start);
            visited.add(start);
        }
        final Phaser phaser = new Phaser(1);
        for (int i = 0; i < depth; i++) {
            final Queue<String> nextLayer = new ArrayDeque<>();
            final int remainingDepth = depth - i - 1;
            while (!layer.isEmpty()) {
                final String url = layer.poll();
                final String host = getHost(url);
                addTask(downloadService, phaser, () -> {
                    final Semaphore semaphore = hostsSemaphores.computeIfAbsent(host, h -> new Semaphore(perHost));
                    try {
                        semaphore.acquireUninterruptibly();
                        final Document document = downloader.download(url);
                        if (remainingDepth > 0) {
                            addTask(extractService, phaser, () -> {
                                try {
                                    final List<String> links = document.extractLinks();
                                    for (final String link : links) {
                                        if (isValidHost(getHost(link), hostsSet) && visited.add(link)) {
                                            synchronized (nextLayer) {
                                                nextLayer.add(link);
                                            }
                                        }
                                    }
                                } catch (final IOException exception) {
                                    errors.put(url, exception);
                                } finally {
                                    phaser.arriveAndDeregister();
                                }
                            });
                        }
                    } catch (final IOException exception) {
                        errors.put(url, exception);
                    } finally {
                        phaser.arriveAndDeregister();
                        semaphore.release();
                    }
                });
            }
            phaser.arriveAndAwaitAdvance();
            layer.addAll(nextLayer);
        }
        visited.removeAll(errors.keySet());
        return new Result(new ArrayList<>(visited), errors);
    }

    @Override
    public Result download(final String url, final int depth, final List<String> hosts) {
        return downloadImpl(url, depth, hosts);
    }

    @Override
    public Result download(final String url, final int depth) {
        return downloadImpl(url, depth, null);
    }

    @Override
    public void close() {
        shutdown(downloadService);
        shutdown(extractService);
    }

    static void shutdown(final ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(TIME, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
            }
        } catch (final InterruptedException error) {
            pool.shutdownNow();
        }
    }

    private static int getArg(final String[] args, final int id, final int defaultValue) {
        return id < args.length? Integer.parseInt(args[id]) : defaultValue;
    }

    public static void main(final String[] args) {
        if (args == null || args.length == 0 || args.length > 5 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Incorrect arguments. Expected: url [depth [downloads [extractors [perHost]]]]");
            return;
        }
        final String url = args[0];

        if (Arrays.stream(Arrays.copyOfRange(args, 1, args.length)).anyMatch(arg -> {
            try {
                Integer.parseInt(arg);
                return true;
            } catch (final NumberFormatException e) {
                return false;
            }
        })) {
            System.err.println("Arguments expected to be integer numbers.");
            return;
        }

        final int depth = getArg(args, 1, 2);
        final int downloads = getArg(args, 2, 5);
        final int extractors = getArg(args, 3, 5);
        final int perHost = getArg(args, 4, 5);

        final Downloader downloader;
        try {
            downloader = new CachingDownloader();
        } catch (final IOException exception) {
            System.err.println("Cannot create downloader: " + exception.getMessage());
            return;
        }

        final Crawler crawler = new WebCrawler(downloader, downloads, extractors, perHost);
        final Result result = crawler.download(url, depth);
        crawler.close();
        System.out.println(result.getDownloaded().size() + " pages downloaded successfully:");
        for (final String string : result.getDownloaded()) {
            System.out.println(string);
        }
        System.out.println(result.getErrors().size() + " pages downloaded with errors:");
        for (final Map.Entry<String, IOException> error : result.getErrors().entrySet()) {
            System.out.println("url: " + error.getKey() + "\ncause: " + error.getValue().getMessage());
        }
    }
}

