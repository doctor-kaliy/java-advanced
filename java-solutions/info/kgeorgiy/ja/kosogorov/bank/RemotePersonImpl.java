package info.kgeorgiy.ja.kosogorov.bank;

public class RemotePersonImpl extends Person implements RemotePerson {
    public RemotePersonImpl(String firstName, String lastName, String passport) {
        super(firstName, lastName, passport);
    }
}
