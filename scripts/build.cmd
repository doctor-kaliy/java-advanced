cd ..\java-solutions
javac --module-path=..\..\java-advanced-2021\artifacts\info.kgeorgiy.java.advanced.base.jar;..\..\java-advanced-2021\artifacts\info.kgeorgiy.java.advanced.implementor.jar;..\..\java-advanced-2021\lib\junit-4.11.jar info\kgeorgiy\ja\kosogorov\implementor\Implementor.java module-info.java -d java.solutions
cd java.solutions
echo Manifest-Version: 1.0 > MANIFEST.MF
echo Main-Class: info.kgeorgiy.ja.kosogorov.implementor.Implementor >> MANIFEST.MF
echo Class-Path: ../java-advanced-2021/artifacts/info.kgeorgiy.java.advanced.implementor.jar >> MANIFEST.MF
jar -c --manifest=MANIFEST.MF --file=..\..\implementor.jar info\kgeorgiy\ja\kosogorov\implementor\* module-info.class
cd ..
rmdir /q /s java.solutions