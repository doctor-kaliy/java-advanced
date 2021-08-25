package info.kgeorgiy.ja.kosogorov.bank;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Objects;

public final class Client {
    public static void main(final String... args) {
        if (args == null || args.length != 5 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.out.println("Expected 5 non-null arguments");
            return;
        }
        final String firstName = args[0];
        final String lastName = args[1];
        final String passport = args[2];
        final String accountId = args[3];
        final int addAmount;
        try {
            addAmount = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.out.println("4th argument expected to be an integer number.\n" + e.getMessage());
            return;
        }
        try {
            Bank bank = (Bank)Naming.lookup("//localhost/bank");
            RemotePerson person = bank.getRemotePerson(passport);
            if (person == null) {
                System.out.println("No such person. Creating new one.");
                person = bank.createPerson(firstName, lastName, passport);
            } else if (!person.getFirstName().equals(firstName) || !person.getLastName().equals(lastName)) {
                System.out.println("Incorrect person's data. Expected first name: '" + person.getFirstName() +
                    "', last name: '" + person.getLastName() + "'.");
                System.exit(1);
            }
            System.out.println("Person: " + firstName + " " + lastName);
            RemoteAccount account = bank.getAccount(person, accountId);
            if (account == null) {
                System.out.println("No such account. Creating empty one.");
                account = bank.createAccount(passport, accountId);
            }
            System.out.println("Account ID: " + account.getId());
            System.out.println("Amount: " + account.getAmount());
            account.setAmount(account.getAmount() + addAmount);
            System.out.println("Money added successfully. New amount: " + account.getAmount());
        } catch (RemoteException e) {
            System.out.println("RMI error occurred: " + e.getMessage());
            e.printStackTrace();
        } catch (MalformedURLException e) {
            System.out.println("Malformed URL");
        } catch (NotBoundException e) {
            System.out.println("Cannot bound bank: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
