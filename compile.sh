mkdir -p build
javac RunGame.java -d build
cp -r sprites build/
cp manifest.txt build/
cd build
jar cfm Snek.jar manifest.txt *.class sprites
java -jar Snek.jar
