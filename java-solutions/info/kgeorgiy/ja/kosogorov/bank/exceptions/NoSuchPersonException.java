package info.kgeorgiy.ja.kosogorov.bank.exceptions;

public class NoSuchPersonException extends RuntimeException {
    public NoSuchPersonException(String message) {
        super(message);
    }
}
