package info.kgeorgiy.ja.kosogorov.hello;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EvilChecker implements Checker {
    private static final String REGEX = "(\\D*)(\\d+)(\\D+)(\\d+)(\\D*)";
    private static final Pattern PATTERN = Pattern.compile(REGEX);
    private final String first;
    private final String second;

    public EvilChecker(int first, int second) {
        this.first = Integer.toString(first);
        this.second = Integer.toString(second);
    }

    @Override
    public boolean check(String string) {
        Matcher matcher = PATTERN.matcher(string);
        return matcher.matches() && matcher.group(2).equals(first) && matcher.group(4).equals(second);
    }
}
