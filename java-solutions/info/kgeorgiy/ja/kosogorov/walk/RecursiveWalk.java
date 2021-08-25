package info.kgeorgiy.ja.kosogorov.walk;

public class RecursiveWalk {
    private static final int DEPTH = Integer.MAX_VALUE;
    public static void main(String[] args) {
        DeepWalk.walk(args, DEPTH);
    }
}

