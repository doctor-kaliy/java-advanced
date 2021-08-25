package info.kgeorgiy.ja.kosogorov.bank;

import java.util.List;
import java.util.Map;

public class LocalPerson extends Person {
    private final Map<String, LocalAccount> accounts;

    public LocalPerson(String firstName, String lastName, String passport, Map<String, LocalAccount> accounts) {
        super(firstName, lastName, passport);
        this.accounts = accounts;
    }

    public LocalAccount getAccount(String subId) {
        return accounts.get(subId);
    }

    public List<LocalAccount> getAccounts() {
        return List.copyOf(accounts.values());
    }
}
