package br.edu.ifce.chat;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;

/*
 * Servidor de Mensagens offline - o "servidor remoto" do requisito 4.
 *
 * Componentes que ele reune em um unico processo:
 *
 *  1. Broker ActiveMQ embarcado (MOM) - gerencia uma fila JMS por cliente
 *     ("fila.<nome>"), onde ficam as mensagens enviadas a quem esta offline
 *     (requisito 5). E o mesmo broker usado no Projeto MOM, agora com Queues
 *     (ponto-a-ponto) em vez de Topics, pois cada mensagem tem UM destinatario.
 *
 *  2. Servico RMI (ServicoMensagens) - unica porta de entrada dos clientes:
 *     conectar/criar fila, enviar mensagem, mudar status, sair. A entrega de
 *     mensagens no sentido servidor -> cliente usa os callbacks remotos
 *     (ClienteCallback) registrados na conexao.
 *
 *  3. UI Swing - mostra o log de eventos e um painel com todas as filas,
 *     status (online/offline) e quantidade de mensagens pendentes de cada
 *     cliente, util para acompanhar o sistema durante a apresentacao.
 *
 * Logica central de roteamento (metodo rotear): se o destinatario esta ONLINE
 * a mensagem vai direto pelo callback (requisito 3); senao, e depositada na
 * fila JMS dele (requisitos 4 e 6). Quando um cliente entra ou volta a ficar
 * online, sua fila e drenada e tudo que estava guardado e entregue.
 */
public class ServidorMensagens extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final SimpleDateFormat HORA = new SimpleDateFormat("HH:mm:ss");

    // ---------- Nucleo (broker MOM + RMI + JMS) ----------
    private transient BrokerService broker;
    private transient Registry registry;
    private transient ServicoImpl servico;
    private transient javax.jms.Connection conexaoJms;

    /* Callbacks de todos os clientes CONECTADOS (janela aberta), por nome. */
    private final transient Map<String, ClienteCallback> callbacks = new ConcurrentHashMap<>();
    /* Nomes dos clientes atualmente com status ONLINE. */
    private final transient Set<String> online = ConcurrentHashMap.newKeySet();
    /* Nomes de todos os clientes que ja tiveram fila criada nesta execucao. */
    private final transient Set<String> filas = ConcurrentHashMap.newKeySet();

    // ---------- UI ----------
    private JLabel lblStatus;
    private JTextArea areaLog;
    private JPanel painelFilas;
    private javax.swing.Timer timerFilas;

    public ServidorMensagens() {
        super("Servidor de Mensagens Offline");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Tema.FUNDO);

        // ---------- Cabecalho ----------
        JPanel cab = Tema.cabecalho(
                "Servidor de Mensagens Offline",
                "Acesso remoto via RMI  •  uma fila MOM (JMS/ActiveMQ) por cliente",
                Tema.SECUNDARIA);
        cab.setBorder(BorderFactory.createEmptyBorder(16, 16, 4, 16));
        add(cab, BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(0, 12));
        centro.setOpaque(false);
        centro.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        // ---- Card de status ----
        JPanel cardStatus = Tema.card("Status do servidor");
        JPanel linhaStatus = Tema.linha();
        lblStatus = Tema.statusDot("iniciando...", Tema.ALERTA);
        linhaStatus.add(lblStatus);
        JLabel lblEnderecos = new JLabel("RMI: porta " + Config.getRmiPort()
                + "  •  Broker MOM: " + Config.getConnectorUrl());
        lblEnderecos.setForeground(Tema.TEXTO_FRACO);
        linhaStatus.add(lblEnderecos);
        cardStatus.add(linhaStatus, BorderLayout.CENTER);
        centro.add(cardStatus, BorderLayout.NORTH);

        // ---- Card das filas (esquerda) + card de log (centro) ----
        JPanel cardFilas = Tema.card("Filas dos clientes (MOM)");
        painelFilas = new JPanel();
        painelFilas.setOpaque(false);
        painelFilas.setLayout(new javax.swing.BoxLayout(painelFilas, javax.swing.BoxLayout.Y_AXIS));
        JScrollPane spFilas = new JScrollPane(painelFilas);
        spFilas.setBorder(null);
        spFilas.getViewport().setBackground(Tema.CARD);
        spFilas.setOpaque(false);
        cardFilas.add(spFilas, BorderLayout.CENTER);
        cardFilas.setPreferredSize(new Dimension(330, 0));

        JPanel cardLog = Tema.card("Log de eventos");
        areaLog = new JTextArea();
        areaLog.setEditable(false);
        areaLog.setFont(Tema.MONO);
        areaLog.setForeground(Tema.TEXTO);
        areaLog.setBackground(Tema.CARD);
        JScrollPane spLog = new JScrollPane(areaLog);
        spLog.setBorder(null);
        cardLog.add(spLog, BorderLayout.CENTER);

        JPanel meio = new JPanel(new BorderLayout(12, 0));
        meio.setOpaque(false);
        meio.add(cardFilas, BorderLayout.WEST);
        meio.add(cardLog, BorderLayout.CENTER);
        centro.add(meio, BorderLayout.CENTER);

        add(centro, BorderLayout.CENTER);

        setSize(860, 540);
        setMinimumSize(new Dimension(720, 420));
        setLocationByPlatform(true);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { encerrar(); }
        });

        atualizarPainelFilas();
        iniciar();
    }

    // -------------------------------------------------------------------------
    // Inicializacao e encerramento
    // -------------------------------------------------------------------------

    /* Sobe broker MOM, conexao JMS e servico RMI em uma thread de fundo. */
    private void iniciar() {
        Thread t = new Thread(() -> {
            try {
                log("Iniciando broker ActiveMQ embarcado (MOM)...");
                broker = new BrokerService();
                broker.setPersistent(false);
                broker.setUseJmx(false);
                broker.addConnector(Config.getConnectorUrl());
                broker.start();
                broker.waitUntilStarted();
                log("Broker MOM no ar em " + Config.getConnectorUrl());

                conexaoJms = new ActiveMQConnectionFactory(Config.getBrokerUrl()).createConnection();
                conexaoJms.start();
                log("Conexao JMS do servidor aberta");

                registry = LocateRegistry.createRegistry(Config.getRmiPort());
                servico = new ServicoImpl();
                registry.rebind(Config.RMI_SERVICO, servico);
                log("Servico RMI '" + Config.RMI_SERVICO + "' publicado na porta "
                        + Config.getRmiPort());

                setStatus("em execucao - aguardando clientes", Tema.SUCESSO);

                timerFilas = new javax.swing.Timer(2000, e -> atualizarPainelFilas());
                timerFilas.start();
            } catch (Exception ex) {
                log("ERRO ao iniciar: " + ex.getMessage());
                setStatus("falha ao iniciar (porta em uso?)", Tema.PERIGO);
            }
        }, "servidor-init");
        t.setDaemon(true);
        t.start();
    }

    /* Derruba RMI, JMS e broker ao fechar a janela. */
    private void encerrar() {
        if (timerFilas != null) timerFilas.stop();
        try { if (registry != null) registry.unbind(Config.RMI_SERVICO); } catch (Exception ignored) { }
        try { if (servico != null) UnicastRemoteObject.unexportObject(servico, true); } catch (Exception ignored) { }
        try { if (registry != null) UnicastRemoteObject.unexportObject(registry, true); } catch (Exception ignored) { }
        try { if (conexaoJms != null) conexaoJms.close(); } catch (Exception ignored) { }
        try {
            if (broker != null) {
                broker.stop();
                broker.waitUntilStopped();
            }
        } catch (Exception ignored) { }
    }

    // -------------------------------------------------------------------------
    // Implementacao do servico RMI
    // -------------------------------------------------------------------------

    /*
     * Objeto remoto exportado no Registry. Os metodos rodam em threads do pool
     * RMI; por isso o estado compartilhado usa colecoes concorrentes e cada
     * operacao JMS abre a propria Session (Sessions JMS nao sao thread-safe).
     */
    private class ServicoImpl extends UnicastRemoteObject implements ServicoMensagens {

        private static final long serialVersionUID = 1L;

        ServicoImpl() throws RemoteException {
            super();
        }

        @Override
        public synchronized void conectar(String nome, ClienteCallback callback)
                throws RemoteException {
            nome = nome.toLowerCase();
            if (callbacks.containsKey(nome)) {
                throw new RemoteException("NOME_EM_USO");
            }
            log("Cliente '" + nome + "' entrou no sistema");
            try {
                garantirFila(nome); // requisito 7: cliente solicita a criacao da sua fila
            } catch (JMSException e) {
                throw new RemoteException("Falha ao criar fila no MOM: " + e.getMessage());
            }
            callbacks.put(nome, callback);
            online.add(nome);
            notificarStatus(nome, true);
            int n = drenarFila(nome, callback);
            if (n > 0) {
                log(n + " mensagem(ns) da fila '" + Config.nomeFila(nome)
                        + "' entregue(s) a '" + nome + "'");
            }
            atualizarPainelFilas();
        }

        @Override
        public void mudarStatus(String nome, boolean ficarOnline) throws RemoteException {
            nome = nome.toLowerCase();
            if (ficarOnline) {
                online.add(nome);
                log("Cliente '" + nome + "' ficou ONLINE");
                notificarStatus(nome, true);
                ClienteCallback cb = callbacks.get(nome);
                if (cb != null) {
                    int n = drenarFila(nome, cb);
                    if (n > 0) {
                        log(n + " mensagem(ns) da fila '" + Config.nomeFila(nome)
                                + "' entregue(s) a '" + nome + "'");
                    }
                }
            } else {
                online.remove(nome);
                log("Cliente '" + nome + "' ficou OFFLINE (mensagens irao para a fila)");
                notificarStatus(nome, false);
            }
            atualizarPainelFilas();
        }

        @Override
        public boolean enviar(String remetente, String destinatario, String texto)
                throws RemoteException {
            return rotear(remetente.toLowerCase(), destinatario.toLowerCase(), texto);
        }

        @Override
        public boolean estaOnline(String nome) {
            return online.contains(nome.toLowerCase());
        }

        @Override
        public void desconectar(String nome) {
            nome = nome.toLowerCase();
            callbacks.remove(nome);
            boolean estavaOnline = online.remove(nome);
            log("Cliente '" + nome + "' saiu do sistema (fila preservada)");
            if (estavaOnline) {
                notificarStatus(nome, false);
            }
            atualizarPainelFilas();
        }
    }

    // -------------------------------------------------------------------------
    // Roteamento das mensagens (online x offline)
    // -------------------------------------------------------------------------

    /*
     * Decide o destino de uma mensagem:
     *  - destinatario ONLINE -> entrega instantanea pelo callback RMI;
     *  - destinatario OFFLINE (ou callback morto) -> fila JMS no MOM.
     * Retorna true se entregou na hora, false se enfileirou.
     */
    private boolean rotear(String remetente, String destinatario, String texto)
            throws RemoteException {
        long timestamp = System.currentTimeMillis();

        if (online.contains(destinatario)) {
            ClienteCallback cb = callbacks.get(destinatario);
            if (cb != null) {
                try {
                    cb.receberMensagem(remetente, texto, timestamp, false);
                    log("Mensagem de '" + remetente + "' entregue na hora a '"
                            + destinatario + "' (online)");
                    return true;
                } catch (RemoteException e) {
                    // Cliente caiu sem desconectar: marca offline e enfileira.
                    callbacks.remove(destinatario);
                    online.remove(destinatario);
                    notificarStatus(destinatario, false);
                    log("Callback de '" + destinatario + "' nao responde; tratando como offline");
                }
            }
        }

        try {
            enfileirar(remetente, destinatario, texto, timestamp);
        } catch (JMSException e) {
            throw new RemoteException("Falha ao enfileirar no MOM: " + e.getMessage());
        }
        log("Mensagem de '" + remetente + "' guardada na fila '"
                + Config.nomeFila(destinatario) + "' (destinatario offline)");
        atualizarPainelFilas();
        return false;
    }

    // -------------------------------------------------------------------------
    // Operacoes JMS sobre as filas (uma Session curta por operacao)
    // -------------------------------------------------------------------------

    /* Cria a fila do cliente no broker caso ainda nao exista (requisito 7). */
    private void garantirFila(String nome) throws JMSException {
        boolean nova = filas.add(nome);
        Session s = conexaoJms.createSession(false, Session.AUTO_ACKNOWLEDGE);
        try {
            // Abrir (e fechar) um consumidor materializa a Queue no broker.
            s.createConsumer(s.createQueue(Config.nomeFila(nome))).close();
        } finally {
            s.close();
        }
        if (nova) {
            log("Fila '" + Config.nomeFila(nome) + "' criada no MOM para '" + nome + "'");
        } else {
            log("Fila '" + Config.nomeFila(nome) + "' ja existia; sera reutilizada");
        }
    }

    /* Deposita uma mensagem na fila offline do destinatario (requisito 6). */
    private void enfileirar(String remetente, String destinatario, String texto, long timestamp)
            throws JMSException {
        try {
            garantirFila(destinatario);
        } catch (JMSException ignored) {
            // Falha ao "materializar" a fila nao impede o envio: o send cria a Queue.
        }
        Session s = conexaoJms.createSession(false, Session.AUTO_ACKNOWLEDGE);
        try {
            Queue fila = s.createQueue(Config.nomeFila(destinatario));
            MessageProducer produtor = s.createProducer(fila);
            TextMessage msg = s.createTextMessage(texto);
            msg.setStringProperty("remetente", remetente);
            msg.setLongProperty("timestamp", timestamp);
            produtor.send(msg);
            produtor.close();
        } finally {
            s.close();
        }
    }

    /*
     * Consome a fila do cliente entregando cada mensagem pelo callback.
     * Usa CLIENT_ACKNOWLEDGE: o ack acontece somente APOS o callback ter
     * aceitado a mensagem - se o cliente cair no meio, o que nao foi
     * confirmado volta para a fila quando a Session fecha.
     */
    private int drenarFila(String nome, ClienteCallback cb) {
        int entregues = 0;
        try {
            Session s = conexaoJms.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            try {
                MessageConsumer consumidor = s.createConsumer(s.createQueue(Config.nomeFila(nome)));
                Message m;
                while ((m = consumidor.receive(300)) != null) {
                    if (m instanceof TextMessage) {
                        TextMessage tm = (TextMessage) m;
                        cb.receberMensagem(tm.getStringProperty("remetente"), tm.getText(),
                                tm.getLongProperty("timestamp"), true);
                        m.acknowledge();
                        entregues++;
                    }
                }
                consumidor.close();
            } finally {
                s.close();
            }
        } catch (JMSException | RemoteException e) {
            log("ERRO ao entregar fila de '" + nome + "': " + e.getMessage());
        }
        return entregues;
    }

    /* Conta as mensagens paradas na fila de um cliente (sem consumir). */
    private int contarPendentes(String nome) {
        try {
            Session s = conexaoJms.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try {
                QueueBrowser browser = s.createBrowser(s.createQueue(Config.nomeFila(nome)));
                int n = 0;
                for (Enumeration<?> e = browser.getEnumeration(); e.hasMoreElements(); e.nextElement()) {
                    n++;
                }
                browser.close();
                return n;
            } finally {
                s.close();
            }
        } catch (JMSException e) {
            return -1;
        }
    }

    // -------------------------------------------------------------------------
    // Notificacao de presenca
    // -------------------------------------------------------------------------

    /* Avisa os demais clientes ONLINE que 'nome' mudou de status. */
    private void notificarStatus(String nome, boolean ficouOnline) {
        for (Map.Entry<String, ClienteCallback> e : callbacks.entrySet()) {
            if (e.getKey().equals(nome) || !online.contains(e.getKey())) {
                continue;
            }
            try {
                e.getValue().statusContato(nome, ficouOnline);
            } catch (RemoteException ignored) {
                // Quem nao responder sera tratado como offline no proximo envio.
            }
        }
    }

    // -------------------------------------------------------------------------
    // UI (log e painel de filas)
    // -------------------------------------------------------------------------

    private void log(String texto) {
        String linha = "[" + HORA.format(new Date()) + "] " + texto + "\n";
        SwingUtilities.invokeLater(() -> {
            areaLog.append(linha);
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
        });
    }

    private void setStatus(String texto, java.awt.Color cor) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("● " + texto);
            lblStatus.setForeground(cor);
        });
    }

    /* Reconstroi o painel lateral: fila, status do dono e mensagens pendentes. */
    private void atualizarPainelFilas() {
        // Conta fora da EDT seria ideal, mas as filas sao locais e pequenas.
        SwingUtilities.invokeLater(() -> {
            painelFilas.removeAll();
            if (filas.isEmpty()) {
                JLabel vazio = new JLabel("Nenhuma fila criada ainda.");
                vazio.setForeground(Tema.TEXTO_FRACO);
                vazio.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                vazio.setAlignmentX(LEFT_ALIGNMENT);
                painelFilas.add(vazio);
            } else {
                List<String> nomes = new ArrayList<>(filas);
                java.util.Collections.sort(nomes);
                for (String nome : nomes) {
                    boolean onl = online.contains(nome);
                    boolean conectado = callbacks.containsKey(nome);
                    int pendentes = contarPendentes(nome);
                    String corStatus = onl ? "#10B981" : (conectado ? "#F59E0B" : "#94A3B8");
                    String txtStatus = onl ? "online" : (conectado ? "offline" : "ausente");
                    JLabel l = new JLabel("<html><b>" + Config.nomeFila(nome) + "</b>"
                            + " &nbsp;<font color='" + corStatus + "'>● " + txtStatus + "</font>"
                            + " &nbsp;<font color='#64748B'>"
                            + (pendentes < 0 ? "?" : pendentes) + " pendente(s)</font></html>");
                    l.setFont(Tema.FONTE);
                    l.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                    l.setAlignmentX(LEFT_ALIGNMENT);
                    painelFilas.add(l);
                }
            }
            painelFilas.revalidate();
            painelFilas.repaint();
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();
            new ServidorMensagens().setVisible(true);
        });
    }
}
