package info.kgeorgiy.ja.kosogorov.bank;

import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;
import java.net.*;

public final class Server implements RemoteServer {
    private final static int DEFAULT_PORT = 8888;
    private final int port;

    public Server(int port) throws RemoteException {
        this.port = port;
        LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
    }

    public static void main(final String... args) {
        if (args == null || args.length > 2) {
            System.out.println("Wrong number of arguments. Expected from 0 to 2.");
            return;
        }
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[args.length - 1]);
            } catch (NumberFormatException exception) {
                System.out.println("Using default port: " + DEFAULT_PORT);
            }
        }
        try {
            final RemoteServer server = new Server(port);
            try {
                UnicastRemoteObject.exportObject(server, port);
                Naming.rebind("//localhost/server", server);
                System.out.println("Server started");
                if (args.length >= 1 && "--bind-bank".equals(args[0])) {
                    server.bindBank();
                }
            } catch (final RemoteException e) {
                System.out.println("Cannot export server: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            } catch (final MalformedURLException e) {
                System.out.println("Malformed URL");
            }
        } catch (RemoteException e) {
            System.out.println("Cannot create RMI registry: " + e.getMessage());
        }
    }

    @Override
    public void bindBank() throws RemoteException {
        try {
            Naming.rebind("//localhost/bank", createBank());
        } catch (MalformedURLException e) {
            System.out.println("Malformed URL");
        }
    }

    @Override
    public Bank createBank() throws RemoteException {
        final Bank bank = new RemoteBank(port);
        UnicastRemoteObject.exportObject(bank, port);
        return bank;
    }
}
