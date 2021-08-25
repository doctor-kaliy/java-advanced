package info.kgeorgiy.ja.kosogorov.bank;

import java.io.Serializable;

public abstract class Person implements Serializable {
    private final String firstName;
    private final String lastName;
    private final String passport;

    public Person(String firstName, String lastName, String passport) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.passport = passport;
    }

    public String getFirstName() {
        return firstName;
    }


    public String getLastName() {
        return lastName;
    }


    public String getPassport() {
        return passport;
    }
}
