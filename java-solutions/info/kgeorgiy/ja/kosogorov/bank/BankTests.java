package info.kgeorgiy.ja.kosogorov.bank;

import info.kgeorgiy.ja.kosogorov.bank.exceptions.InvalidIdException;
import info.kgeorgiy.ja.kosogorov.bank.exceptions.NoSuchPersonException;
import org.junit.*;
import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class BankTests {

    private interface RemoteTester {
        void test() throws RemoteException;
    }

    private static RemoteServer server;
    private static Process serverProcess;
    private static String PACKAGE = "info.kgeorgiy.ja.kosogorov.bank.";
    private static String CLASS_PATH;
    private static final int DEFAULT_PORT = 8888;

    private Bank bank;

    private static String getClassPath(Class<?> token) {
        try {
            return Path.of(token.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (final URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    public static void main(String[] args) {
        JUnitCore core = new JUnitCore();
        core.addListener(new TextListener(System.out));
        Result result = core.run(BankTests.class);
        System.exit(result.wasSuccessful()? 0 : 1);
    }

    @BeforeClass
    public static void before() throws IOException, NotBoundException {
        CLASS_PATH = getClassPath(BankTests.class);
        PACKAGE = BankTests.class.getPackageName() + ".";
        serverProcess =
            new ProcessBuilder("java", "-cp", CLASS_PATH, PACKAGE + "Server",
                String.valueOf(DEFAULT_PORT)).start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {}
        server = (RemoteServer)Naming.lookup("//localhost/server");
    }

    @Before
    public void beforeTest() throws RemoteException {
        bank = server.createBank();
    }

    private static RemotePerson createBot(Bank bank, int number) throws RemoteException {
        return createBotWithPassport(bank, number, "p" + number);
    }

    private static RemotePerson createBotWithPassport(Bank bank, int number, String passport) throws RemoteException {
        return bank.createPerson("name" + number, "surname" + number, passport);
    }

    private static Integer runProcessWithIO(ProcessBuilder builder) throws IOException {
        final Process process = builder.start();
        Integer code = null;
        while (code == null) {
            try {
                code = process.waitFor();
            } catch (InterruptedException ignore) {}
        }
        return code;
    }

    private static Integer runProcess(ProcessBuilder builder, Consumer<IOException> errorConsumer) {
        try {
            return runProcessWithIO(builder);
        } catch (IOException e) {
            errorConsumer.accept(e);
        }
        return null;
    }

    private static void parallelTest(int threads, List<RemoteTester> testers) throws RemoteException {
        AtomicReference<RemoteException> remoteException = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (RemoteTester tester: testers) {
            pool.submit(() -> {
                try {
                    tester.test();
                } catch (RemoteException exception) {
                    if (!remoteException.compareAndSet(null, exception)) {
                        remoteException.get().addSuppressed(exception);
                    }
                }
            });
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            pool.shutdownNow();
        }
        RemoteException e = remoteException.get();
        if (e != null) {
            throw e;
        }
    }

    private static void invalidIdFail(RemoteTester tester, String message) throws RemoteException {
        try {
            tester.test();
            Assert.fail(message);
        } catch (InvalidIdException ignore) {}
    }

    @Test
    public void test00_personDoesntExist() throws RemoteException {
        final String passport = "this passport doesn't belong to anyone";
        assertNull(bank.getLocalPerson(passport));
        assertNull(bank.getRemotePerson(passport));
    }

    @Test
    public void test01_add_money() throws RemoteException {
        String passport = createBot(bank, 1).getPassport();
        RemoteAccount account = bank.createAccount(passport, "1");
        RemotePerson person = bank.getRemotePerson(passport);
        account.setAmount(account.getAmount() + 2224);
        assertEquals(account.getAmount(), bank.getAccount(person, "1").getAmount());
    }

    @Test
    public void test02_visibility() throws RemoteException {
        final String passport = createBot(bank, 1).getPassport();
        RemoteAccount account = bank.createAccount(passport, "4");
        RemotePerson remotePerson = bank.getRemotePerson(passport);
        LocalPerson localPerson = bank.getLocalPerson(passport);
        assertEquals(localPerson.getAccount("4").getAmount(), bank.getAccount(remotePerson, "4").getAmount());
        account.setAmount(account.getAmount() + 100);
        assertEquals(account.getAmount(), bank.getAccount(remotePerson, "4").getAmount());
        LocalAccount localAccount = localPerson.getAccount("4");
        assertNotEquals(localAccount.getAmount(), account.getAmount());
        bank.createAccount(passport, "5").setAmount(200);
        localPerson = bank.getLocalPerson(passport);
        localPerson.getAccount("5").setAmount(0);
        assertNotEquals(localPerson.getAccount("5").getAmount(), bank.getAccount(passport + ":5").getAmount());
    }

    @Test
    public void test03_manyPeople() throws RemoteException {
        List<RemotePerson> people = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            people.add(createBot(bank, i));
        }
        for (RemotePerson p : people) {
            LocalPerson local = bank.getLocalPerson(p.getPassport());
            assertEquals(p.getFirstName(), local.getFirstName());
            assertEquals(p.getLastName(), local.getLastName());
            assertEquals(p.getPassport(), local.getPassport());
            bank.createAccount(p.getPassport(), "subId");
            assertFalse(bank.getAccounts(p).isEmpty());
            assertTrue(local.getAccounts().isEmpty());
        }
    }

    @Test
    public void test04_parallelCreation() throws RemoteException {
        final String passport = "test04's passport";
        Queue<RemotePerson> result = new ConcurrentLinkedQueue<>();
        parallelTest(20, IntStream.range(0, 200).mapToObj(i -> (RemoteTester)() ->
            result.add(createBotWithPassport(bank, i, passport))
        ).collect(Collectors.toList()));
        bank.createAccount(passport, "1");
        RemotePerson personToCompare = result.poll();
        assertFalse(personToCompare == null || bank.getAccounts(personToCompare).isEmpty());
        for (RemotePerson person : result) {
            assertFalse(person == null || bank.getAccounts(person).isEmpty());
            assertEquals(personToCompare.getFirstName(), person.getFirstName());
            assertEquals(personToCompare.getLastName(), person.getLastName());
            assertEquals(personToCompare.getPassport(), person.getPassport());
        }
    }

    @Test
    public void test05_parallelSetWithClient() throws IOException, NotBoundException {
        Naming.rebind("//localhost/bank", bank);
        final RemotePerson bot = createBot(bank, 1);
        final RemoteAccount account = bank.createAccount(bot.getPassport(), "1");
        final ProcessBuilder builder = new ProcessBuilder("java", "-cp", CLASS_PATH,
            PACKAGE + "Client", bot.getFirstName(),
                bot.getLastName(), bot.getPassport(), "1", "100");
        AtomicReference<IOException> ioException = new AtomicReference<>();
        parallelTest(10, Collections.nCopies(10, () ->
            runProcess(builder, e -> {
                if (!ioException.compareAndSet(null, e)) {
                    ioException.get().addSuppressed(e);
                }
            })
        ));
        Naming.unbind("//localhost/bank");
        IOException exception = ioException.get();
        if (exception != null) {
            throw exception;
        }
        assertTrue(account.getAmount() >= 100);
    }

    @Test
    public void test06_noneExistingPersonsAccount() throws RemoteException {
        final String passport = "a7572b";
        try {
            bank.createAccount(passport, "326");
            Assert.fail("There is no person with passport '" + passport + "'. NoSuchPersonException expected");
        } catch (NoSuchPersonException ignore) {}
    }

    @Test
    public void test07_invalidIds() throws RemoteException {
        final String invalidSubId = "absolutely:invalid:id";
        final String invalidId = "25";
        invalidIdFail(() -> bank.getAccount(invalidId),
        "'" + invalidId + "' is invalid account id. Exception expected.");
        final String passport = "bad:passport";
        invalidIdFail(() -> bank.getRemotePerson(passport),
                "'" + passport + "' is invalid passport. ExceptionExpected");
        invalidIdFail(() -> bank.getAccount(invalidSubId),
        "'" + invalidSubId + "' is invalid account's subId. Exception expected.");
    }

    @Test
    public void test08_invalidPersonDataClient() throws IOException, NotBoundException {
        Naming.rebind("//localhost/bank", bank);
        String passport = createBotWithPassport(bank, 1, "passport").getPassport();
        final ProcessBuilder builder = new ProcessBuilder("java", "-cp", CLASS_PATH,
            PACKAGE + "Client", "Michael",
            "Oxmaul", passport, "1", "100");
        assertEquals(Integer.valueOf(1), runProcessWithIO(builder));
        Naming.unbind("//localhost/bank");
    }

    @Test
    public void test09_remotes() throws RemoteException {
        RemotePerson remote1 = createBot(bank, 1);
        final String passport = remote1.getPassport();
        bank.createAccount(passport, "1");
        RemotePerson remote2 = bank.getRemotePerson(passport);
        assertEquals(bank.getAccounts(remote1).size(), 1);
        assertEquals(bank.getAccounts(remote2).size(), 1);
    }

    @Test
    public void test10_locals() throws RemoteException {
        final String passport = createBot(bank, 1).getPassport();
        LocalPerson local1 = bank.getLocalPerson(passport);
        bank.createAccount(passport, "1");
        LocalPerson local2 = bank.getLocalPerson(passport);
        assertEquals(local1.getAccounts().size(), 0);
        assertEquals(local2.getAccounts().size(), 1);
    }

    @AfterClass
    public static void after() {
        serverProcess.destroy();
    }
}
