package info.kgeorgiy.ja.kosogorov.walk;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class HashFileVisitor extends SimpleFileVisitor<Path> {
    private final BufferedWriter writer;
    private final static long ZERO_HASH = 0L;
    private final static int BUFFER_SIZE = 1024;

    private long hash(Path file) {
        try (final InputStream input = Files.newInputStream(file)) {
            long result = 0;
            final byte[] buffer = new byte[BUFFER_SIZE];
            int size;
            int singleByte;
            while ((size = input.read(buffer)) >= 0) {
                for (int i = 0; i < size; i++) {
                    singleByte = buffer[i] & 0xff;
                    result = (result << 8) + (singleByte & 0xff);
                    final long high = result & 0xff00_0000_0000_0000L;
                    if (high != 0) {
                        result ^= high >> 48;
                        result &= ~high;
                    }
                }
            }
            return result;
        } catch (IOException ignored) {
            return ZERO_HASH;
        }
    }

    public HashFileVisitor(BufferedWriter writer) {
        this.writer = writer;
    }

    private FileVisitResult writeWithHash(Path path, long hash) throws IOException {
        writer.write(String.format("%016x", hash) + " " + path);
        writer.newLine();
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        return writeWithHash(file, hash(file));
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        return writeWithHash(file, ZERO_HASH);
    }
}

