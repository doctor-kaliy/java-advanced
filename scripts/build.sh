#!/bin/bash
artifacts=../../java-advanced-2021/artifacts
lib=../../java-advanced-2021/lib
path=$artifacts/info.kgeorgiy.java.advanced.base.jar:$artifacts/info.kgeorgiy.java.advanced.implementor.jar:$lib/junit-4.11.jar
cd ../java-solutions || exit
javac --module-path=$path info/kgeorgiy/ja/kosogorov/implementor/Implementor.java module-info.java -d java.solutions
cd java.solutions || exit
echo "Manifest-Version: 1.0" > MANIFEST.MF
echo "Main-Class: info.kgeorgiy.ja.kosogorov.implementor.Implementor" >> MANIFEST.MF
echo "Class-Path: ../java-advanced-2021/artifacts/info.kgeorgiy.java.advanced.implementor.jar" >> MANIFEST.MF
jar -c --manifest=MANIFEST.MF --file=../../implementor.jar info/kgeorgiy/ja/kosogorov/implementor/* module-info.class
cd .. || exit
rm -rf java.solutions