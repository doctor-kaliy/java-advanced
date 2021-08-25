package info.kgeorgiy.ja.kosogorov.bank;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface Bank extends Remote {
    /**
     * Creates a new account with specified identifier if it is not already exists.
     * @param id account id
     * @return created or existing account.
     */
    RemoteAccount createAccount(String passport, String subId) throws RemoteException;

    /**
     * Returns account by identifier.
     * @param id account id
     * @return account with specified identifier or {@code null} if such account does not exists.
     */
    RemoteAccount getAccount(String id) throws RemoteException;

    RemotePerson getRemotePerson(String passport) throws RemoteException;

    LocalPerson getLocalPerson(String passport) throws RemoteException;

    RemotePerson createPerson(String firstName, String lastName, String passport) throws RemoteException;

    RemoteAccount getAccount(RemotePerson person, String subId) throws RemoteException;

    List<RemoteAccount> getAccounts(RemotePerson person) throws RemoteException;
}
