package info.kgeorgiy.ja.kosogorov.bank;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemotePerson extends Remote {
    String getFirstName() throws RemoteException;

    String getLastName() throws RemoteException;

    String getPassport() throws RemoteException;
}
