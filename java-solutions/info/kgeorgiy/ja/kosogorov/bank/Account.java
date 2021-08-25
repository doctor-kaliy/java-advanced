package info.kgeorgiy.ja.kosogorov.bank;

import java.io.Serializable;
import java.util.Objects;

public abstract class Account implements Serializable {
    protected final String id;
    protected int amount;

    public Account(final String id) {
        this(id, 0);
    }

    public Account(final String id, int amount) {
        this.id = id;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public synchronized int getAmount() {
        return amount;
    }

    public synchronized void setAmount(final int amount) {
        this.amount = amount;
    }
}
