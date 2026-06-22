package br.edu.ifce.chat;

import java.rmi.Remote;
import java.rmi.RemoteException;

/*
 * Contrato RMI do Servidor de Mensagens (requisito 4: o servidor de mensagens
 * offline e um servidor remoto acessado via RMI).
 *
 * Todos os metodos sao chamados PELOS CLIENTES. O caminho inverso (servidor
 * entregando mensagens e avisos de status ao cliente) usa a interface
 * ClienteCallback, que cada cliente exporta e registra ao conectar.
 */
public interface ServicoMensagens extends Remote {

    /*
     * Entrada do cliente no sistema. O cliente informa seu nome de contato e o
     * stub de callback por onde recebera mensagens.
     *
     * Ao atender, o servidor:
     *  - cria a fila de mensagens offline do cliente no MOM, caso ainda nao
     *    exista (requisito 7);
     *  - marca o cliente como ONLINE e avisa os demais clientes conectados;
     *  - entrega imediatamente, via callback, todas as mensagens que estavam
     *    aguardando na fila.
     *
     * Lanca RemoteException com a mensagem "NOME_EM_USO" se ja existir um
     * cliente conectado com esse nome.
     */
    void conectar(String nome, ClienteCallback callback) throws RemoteException;

    /*
     * Muda o status do cliente entre online e offline (requisito 2).
     * Ao voltar para ONLINE, o servidor drena a fila do cliente e entrega as
     * mensagens pendentes via callback.
     */
    void mudarStatus(String nome, boolean online) throws RemoteException;

    /*
     * Envia uma mensagem de texto para outro cliente.
     *  - Destinatario ONLINE: entrega instantanea via callback (requisito 3).
     *  - Destinatario OFFLINE: a mensagem e depositada na fila JMS dele no
     *    servidor (requisitos 4 e 6).
     * Retorna true se foi entregue na hora, false se foi para a fila.
     */
    boolean enviar(String remetente, String destinatario, String texto) throws RemoteException;

    /* Consulta se um cliente esta online (usado para pintar a lista de contatos). */
    boolean estaOnline(String nome) throws RemoteException;

    /*
     * Saida do cliente (fechou a janela). O servidor descarta o callback e o
     * marca como offline; a fila permanece guardando mensagens futuras.
     */
    void desconectar(String nome) throws RemoteException;
}
