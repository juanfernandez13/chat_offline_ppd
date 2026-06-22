.PHONY: all run run-servidor run-cliente jar clean

SRC = src/main/java/br/edu/ifce/chat/*.java
MAIN = br.edu.ifce.chat.Launcher
CP = build:lib/*

all:
	mkdir -p build
	javac -cp "lib/*" -d build $(SRC)

run: all
	java -cp "$(CP)" $(MAIN)

run-servidor: all
	java -cp "$(CP)" br.edu.ifce.chat.ServidorMensagens

run-cliente: all
	java -cp "$(CP)" br.edu.ifce.chat.ClienteChat $(ARGS)

jar: all
	echo "Main-Class: $(MAIN)" > manifest.txt
	echo "Class-Path: $(addprefix lib/,$(notdir $(wildcard lib/*.jar)))" >> manifest.txt
	jar cfm projeto-final.jar manifest.txt -C build .
	rm manifest.txt
	@echo "Executavel criado: projeto-final.jar  (java -jar projeto-final.jar, precisa da pasta lib/ ao lado)"

clean:
	rm -rf build projeto-final.jar
