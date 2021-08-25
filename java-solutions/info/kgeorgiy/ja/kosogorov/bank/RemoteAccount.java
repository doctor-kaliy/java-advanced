package info.kgeorgiy.ja.kosogorov.bank;

import java.rmi.*;

public interface RemoteAccount extends Remote {
    /** Returns account identifier. */
    String getId() throws RemoteException;

    /** Returns amount of money at the account. */
    int getAmount() throws RemoteException;

    /** Sets amount of money at the account. */
    void setAmount(int amount) throws RemoteException;
}