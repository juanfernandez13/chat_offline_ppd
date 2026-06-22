#!/usr/bin/env bash
# Abre o menu principal (Launcher) com opcoes para iniciar o servidor e os clientes.
set -e
cd "$(dirname "$0")"
JAR="projeto-final.jar"
[ -f "$JAR" ] || { echo "JAR nao encontrado. Execute 'make jar' primeiro."; exit 1; }
java -jar "$JAR"
