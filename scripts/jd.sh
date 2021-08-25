#!/bin/bash
lib=../../java-advanced-2021/lib
javadoc -private -link https://docs.oracle.com/en/java/javase/11/docs/api/ \
-d ../jdoc --class-path=$lib/hamcrest-core-1.3.jar:$lib/junit-4.11.jar:$lib/jsoup-1.8.1.jar:$lib/quickcheck-0.6.jar \
../java-solutions/info/kgeorgiy/ja/kosogorov/implementor/Implementor.java \
../../java-advanced-2021/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/Impler.java \
../../java-advanced-2021/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/JarImpler.java \
../../java-advanced-2021/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/ImplerException.java