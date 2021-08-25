package info.kgeorgiy.ja.kosogorov.walk;import java.io.BufferedReader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.EnumSet;

public class DeepWalk {
    public static void walk(String[] args, int depth) {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) {
            System.err.println("Expected 2 arguments <input file name> <output file name>");
            return;
        }
        Path inputFilePath;
        try {
            inputFilePath = Path.of(args[0]);
        } catch (InvalidPathException e) {
            System.err.println("Invalid input file path:" + e.getMessage());
            return;
        }
        Path outputFilePath;
        try {
            outputFilePath = Path.of(args[1]);
        } catch (InvalidPathException e) {
            System.err.println("Invalid output file path: " + e.getMessage());
            return;
        }
        Path parent = outputFilePath.getParent();
        if (parent != null && !Files.exists(outputFilePath)) {
            try {
                Files.createDirectory(parent);
            } catch (IOException e) {
                System.err.println("Can't create output file's directories: " + e.getMessage());
            }
        }
        try (BufferedReader inputLines = Files.newBufferedReader(inputFilePath)) {
            try (BufferedWriter writer = Files.newBufferedWriter(outputFilePath)) {
                final HashFileVisitor fileVisitor = new HashFileVisitor(writer);
                String line;
                while (true) {
                    try {
                        line = inputLines.readLine();
                    } catch (IOException e) {
                        System.err.println("Error occurred while reading from input file: " + e.getMessage());
                        return;
                    }
                    if (line == null) {
                        break;
                    }
                    try {
                        final Path path = Path.of(line);
                        Files.walkFileTree(path, EnumSet.noneOf(FileVisitOption.class), depth, fileVisitor);
                    } catch (InvalidPathException e) {
                        writer.write(String.format("%016x", 0) + " " + line);
                        writer.newLine();
                    }
                }
            } catch (IOException e) {
                System.err.println("Error occurred while writing to output file: " + e.getMessage());
            }
        } catch (IOException e) {
            System.err.println("cum Error occurred while reading from input file: " + e.getMessage());
        }
    }
}

