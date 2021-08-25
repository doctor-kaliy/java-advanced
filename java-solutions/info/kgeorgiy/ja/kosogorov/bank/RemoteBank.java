package info.kgeorgiy.ja.kosogorov.bank;

import info.kgeorgiy.ja.kosogorov.bank.exceptions.InvalidIdException;
import info.kgeorgiy.ja.kosogorov.bank.exceptions.NoSuchPersonException;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RemoteBank implements Bank {
    private final int port;
    private final Map<String, RemotePersonImpl> persons;
    private final Map<String, Map<String, RemoteAccountImpl>> accounts;

    public RemoteBank(final int port) {
        this.port = port;
        this.persons = new ConcurrentHashMap<>();
        this.accounts = new ConcurrentHashMap<>();
    }

    private static class SuppressedRemoteException {
        RemoteException exception;

        private SuppressedRemoteException() {
            exception = null;
        }

        void set(RemoteException exception) {
            if (this.exception == null) {
                this.exception = exception;
            }
        }

        void throwIfExists() throws RemoteException {
            if (exception != null) {
                throw exception;
            }
        }
    }

    private interface RemoteFunction {
        void get() throws RemoteException;
    }

    private <K, V extends Remote> V createRemote(K key, V obj, Map<K, V> objs,
                                                 RemoteFunction fun) throws RemoteException {
        SuppressedRemoteException exception = new SuppressedRemoteException();
        V result = objs.computeIfAbsent(key, k -> {
            try {
                fun.get();
                return obj;
            } catch (RemoteException e) {
                exception.set(e);
                return null;
            }
        });
        exception.throwIfExists();
        return result;
//        if (objs.putIfAbsent(key, obj) == null) {
//            fun.get();
//            return obj;
//        } else {
//            return objs.get(key);
//        }
    }

    @Override
    public RemoteAccount createAccount(String passport, String subId) throws RemoteException {
        Map<String, RemoteAccountImpl> personAccounts = getRemoteAccountsImpl(passport);
        if (personAccounts == null) {
            throw new NoSuchPersonException("Person with passport " + passport + " doesn't exist.");
        }
        final RemoteAccountImpl account = new RemoteAccountImpl(passport + ":" + subId);
        return createRemote(subId, account, personAccounts, () ->
            UnicastRemoteObject.exportObject(account, port));
    }

    private static String validateString(String string, String message) {
        if (string.contains(":")) {
            throw new InvalidIdException("Illegal character ':' in " + message);
        }
        return string;
    }

    @Override
    public RemoteAccount getAccount(RemotePerson person, String subId) throws RemoteException {
        return person == null? null : getAccount(person.getPassport() + ":" + subId);
    }

    @Override
    public List<RemoteAccount> getAccounts(RemotePerson person) throws RemoteException {
        return List.copyOf(getRemoteAccountsImpl(person.getPassport()).values());
    }

    @Override
    public RemoteAccount getAccount(String id) throws RemoteException {
        int i = id.indexOf(':');
        if (i == -1) {
            throw new InvalidIdException("Invalid account id format. Expected 'passport:subId'");
        }
        final String subId = validateString(id.substring(i + 1), "subId");
        Map<String, RemoteAccountImpl> account = getRemoteAccountsImpl(id.substring(0, i));
        return Optional.ofNullable(account)
            .map(acc -> acc.get(subId)).orElse(null);
    }

    @Override
    public RemotePerson createPerson(String firstName, String lastName, String passport) throws RemoteException {
        final String validPassport = passport = validateString(passport, "passport");
        final RemotePersonImpl person = new RemotePersonImpl(firstName, lastName, passport);
        return createRemote(passport, person, persons, () -> {
            UnicastRemoteObject.exportObject(person, port);
            accounts.putIfAbsent(validPassport, new ConcurrentHashMap<>());
        });
    }

    private Map<String, RemoteAccountImpl> getRemoteAccountsImpl(String passport) {
        return passport == null? null : accounts.get(validateString(passport, "passport"));
    }

    private RemotePersonImpl getRemotePersonImpl(String passport) {
        return passport == null? null : persons.get(validateString(passport, "passport"));
    }

    @Override
    public RemotePerson getRemotePerson(String passport) throws RemoteException {
        return getRemotePersonImpl(passport);
    }

    @Override
    public LocalPerson getLocalPerson(String passport) throws RemoteException {
        return Optional.ofNullable(getRemotePersonImpl(passport)).map(person ->
            new LocalPerson(
                person.getFirstName(),
                person.getLastName(),
                person.getPassport(),
                accounts.get(passport).values().stream()
                    .map(account -> new LocalAccount(account.getId(), account.getAmount()))
                    .collect(Collectors.toUnmodifiableMap(acc -> {
                        final String id = acc.getId();
                        return id.substring(id.indexOf(':') + 1);
                    }, Function.identity()))
            )
        ).orElse(null);
    }
}
