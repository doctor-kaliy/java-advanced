package info.kgeorgiy.ja.kosogorov.bank;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteServer extends Remote {
    void bindBank() throws RemoteException;
    Bank createBank() throws RemoteException;
}
