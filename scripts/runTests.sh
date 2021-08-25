#!/bin/bash
lib=../lib/*
dir=../compiled
javac --class-path=$lib ../java-solutions/info/kgeorgiy/ja/kosogorov/bank/*.java \
../java-solutions/info/kgeorgiy/ja/kosogorov/bank/exceptions/*.java -d $dir
java -cp $lib:$dir info.kgeorgiy.ja.kosogorov.bank.BankTests
rm -rf $dir