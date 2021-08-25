module java.solutions {
    requires transitive info.kgeorgiy.java.advanced.base;
    requires info.kgeorgiy.java.advanced.implementor;
    requires info.kgeorgiy.java.advanced.concurrent;
    requires java.compiler;
    requires info.kgeorgiy.java.advanced.crawler;
    requires info.kgeorgiy.java.advanced.hello;
    requires java.rmi;
    exports info.kgeorgiy.ja.kosogorov.implementor;
    exports info.kgeorgiy.ja.kosogorov.concurrent;
    exports info.kgeorgiy.ja.kosogorov.bank;
    opens info.kgeorgiy.ja.kosogorov.bank to junit;
}
