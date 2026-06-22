package br.edu.ifce.chat;

/*
 * Configuracoes centralizadas do sistema de mensagens.
 *
 * O sistema possui dois "enderecos" importantes:
 *
 *  1. O servico RMI do Servidor de Mensagens (porta 1099 por padrao) - e por
 *     ele que TODO cliente acessa o servidor remoto (requisito 4 do projeto):
 *     registrar-se/criar fila, enviar mensagens, mudar status on/off etc.
 *
 *  2. O broker ActiveMQ (tcp://localhost:61616 por padrao) - usado APENAS
 *     internamente pelo servidor para gerenciar as filas de mensagens offline
 *     (uma fila JMS por cliente - requisito 5). Os clientes nunca falam JMS
 *     diretamente; tudo passa pelo servico RMI.
 *
 * Ambos podem ser sobrescritos por propriedade de sistema ou variavel de
 * ambiente (util para rodar o servidor em outra maquina/porta):
 *   -Dchat.rmi.port=2000            ou  CHAT_RMI_PORT=2000
 *   -Dchat.broker.url=tcp://...    ou  CHAT_BROKER_URL=tcp://...
 */
public final class Config {

    /* Nome com que o servico e publicado no Registry RMI. */
    public static final String RMI_SERVICO = "ServidorMensagens";

    /* Prefixo das filas JMS: cada cliente "fulano" possui a fila "fila.fulano". */
    public static final String FILA_PREFIXO = "fila.";

    private static final String DEFAULT_TCP = "tcp://localhost:61616";
    private static final int DEFAULT_RMI_PORT = 1099;

    private Config() { }

    /* Porta do Registry RMI, considerando os overrides. */
    public static int getRmiPort() {
        String p = System.getProperty("chat.rmi.port");
        if (p == null || p.isEmpty()) {
            p = System.getenv("CHAT_RMI_PORT");
        }
        if (p != null && !p.isEmpty()) {
            try {
                return Integer.parseInt(p.trim());
            } catch (NumberFormatException ignored) { }
        }
        return DEFAULT_RMI_PORT;
    }

    /* URL de transporte pura (tcp://...), considerando os overrides. */
    private static String configuredUrl() {
        String url = System.getProperty("chat.broker.url");
        if (url == null || url.isEmpty()) {
            url = System.getenv("CHAT_BROKER_URL");
        }
        if (url == null || url.isEmpty()) {
            url = DEFAULT_TCP;
        }
        return url;
    }

    /*
     * URL para o broker embarcado escutar (addConnector). Precisa ser uma URI
     * de transporte pura - se o usuario configurou failover, extrai o tcp://.
     */
    public static String getConnectorUrl() {
        String url = configuredUrl();
        if (url.startsWith("failover:")) {
            int i = url.indexOf("tcp://");
            if (i >= 0) {
                String inner = url.substring(i);
                int end = inner.indexOf(')');
                if (end >= 0) inner = inner.substring(0, end);
                return inner.trim();
            }
            return DEFAULT_TCP;
        }
        return url;
    }

    /*
     * URL usada pelo servidor para abrir a conexao JMS com o proprio broker,
     * com failover para reconexao automatica.
     */
    public static String getBrokerUrl() {
        String url = configuredUrl();
        if (url.startsWith("failover:")) {
            return url;
        }
        return "failover:(" + url + ")";
    }

    /* Nome da fila de mensagens offline de um cliente (requisitos 5 e 7). */
    public static String nomeFila(String cliente) {
        return FILA_PREFIXO + cliente;
    }
}
