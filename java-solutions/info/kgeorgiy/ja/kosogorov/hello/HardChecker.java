package info.kgeorgiy.ja.kosogorov.hello;

public class HardChecker implements Checker {
    private final String message;

    public HardChecker(String message) {
        this.message = message;
    }

    @Override
    public boolean check(String string) {
        return string.contains(message);
    }
}
