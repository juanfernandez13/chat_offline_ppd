package br.edu.ifce.chat;

import java.rmi.Remote;
import java.rmi.RemoteException;

/*
 * Interface de callback do cliente. Cada ClienteChat exporta um objeto remoto
 * com esta interface e o entrega ao servidor em ServicoMensagens.conectar().
 *
 * E por aqui que o servidor "empurra" eventos para o cliente, sem o cliente
 * precisar ficar consultando o servidor (mesma ideia dos callbacks RMI do
 * projeto Dara): mensagens novas chegam na hora (requisito 3) e mudancas de
 * status dos contatos atualizam a lista exibida na UI (requisito 1).
 */
public interface ClienteCallback extends Remote {

    /*
     * Entrega uma mensagem ao cliente.
     *  - timestamp: momento em que o remetente ENVIOU (importante para as
     *    mensagens que ficaram guardadas na fila offline);
     *  - daFila: true quando a mensagem estava na fila de mensagens offline e
     *    esta sendo entregue agora (cliente acabou de ficar online).
     */
    void receberMensagem(String remetente, String texto, long timestamp, boolean daFila)
            throws RemoteException;

    /* Avisa que outro cliente mudou de status (online/offline). */
    void statusContato(String nome, boolean online) throws RemoteException;
}
