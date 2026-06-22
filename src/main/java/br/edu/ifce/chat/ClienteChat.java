package br.edu.ifce.chat;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/*
 * Cliente do sistema de mensagens, identificado por um "nome de contato"
 * (requisito 1). Toda a comunicacao com o Servidor de Mensagens acontece por
 * RMI (stub ServicoMensagens); o caminho inverso (mensagens e avisos de
 * presenca) chega pelo objeto remoto CallbackImpl exportado por esta janela.
 *
 * Funcionalidades na UI:
 *  - lista de contatos SEMPRE visivel a esquerda, com bolinha de status e
 *    contador de mensagens nao lidas (requisito 1);
 *  - botoes para adicionar/remover contatos, persistidos em
 *    "contatos-<nome>.txt" para sobreviverem entre execucoes (requisito 8);
 *  - botao para alternar o proprio status entre ONLINE e OFFLINE
 *    (requisito 2) - ao voltar, o servidor entrega o que ficou na fila;
 *  - area de conversa por contato, com marcacao das mensagens que estavam
 *    na fila offline.
 *
 * Threading: chamadas RMI sao feitas fora da EDT (threads proprias) e os
 * callbacks RMI chegam em threads do pool RMI - tudo que toca a UI passa por
 * SwingUtilities.invokeLater, como nos projetos anteriores.
 */
public class ClienteChat extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final SimpleDateFormat HORA = new SimpleDateFormat("HH:mm");

    private final String nome;
    private final transient ServicoMensagens servico;
    private transient CallbackImpl callback;

    private boolean conectado = false;   // ja completou conectar() no servidor
    private boolean onlineLocal = true;  // status escolhido pelo usuario (on/off)

    // ---------- Dados das conversas ----------
    private final DefaultListModel<String> modeloContatos = new DefaultListModel<>();
    private final Map<String, Boolean> statusContatos = new HashMap<>();
    private final Map<String, Integer> naoLidas = new HashMap<>();
    private final Map<String, StringBuilder> historicos = new HashMap<>();

    // ---------- UI ----------
    private JList<String> listaContatos;
    private JLabel lblMeuStatus;
    private JButton btnStatus;
    private JLabel lblTituloConversa;
    private JTextArea areaConversa;
    private JTextField campoMensagem;
    private JButton btnEnviar;

    public ClienteChat(String nome, ServicoMensagens servico) {
        super("Cliente - " + nome);
        this.nome = nome.toLowerCase();
        this.servico = servico;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Tema.FUNDO);

        // ---------- Cabecalho ----------
        JPanel cab = Tema.cabecalho(
                "Mensagens - " + nome,
                "Contatos e conversas  •  fila offline no servidor (RMI + MOM)",
                Tema.PRIMARIA);
        cab.setBorder(BorderFactory.createEmptyBorder(16, 16, 4, 16));
        add(cab, BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(12, 0));
        centro.setOpaque(false);
        centro.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        // ---------- Coluna esquerda: meu status + contatos ----------
        JPanel colunaEsq = new JPanel(new BorderLayout(0, 12));
        colunaEsq.setOpaque(false);
        colunaEsq.setPreferredSize(new Dimension(260, 0));

        JPanel cardStatus = Tema.card("Meu status");
        JPanel corpoStatus = new JPanel();
        corpoStatus.setOpaque(false);
        corpoStatus.setLayout(new javax.swing.BoxLayout(corpoStatus, javax.swing.BoxLayout.Y_AXIS));
        JPanel linhaPill = Tema.linha();
        lblMeuStatus = Tema.statusDot("conectando...", Tema.ALERTA);
        linhaPill.add(lblMeuStatus);
        linhaPill.setAlignmentX(Component.LEFT_ALIGNMENT);
        corpoStatus.add(linhaPill);
        JPanel linhaBtn = Tema.linha();
        btnStatus = Tema.botao("Ficar offline", Tema.CINZA);
        btnStatus.setEnabled(false);
        btnStatus.addActionListener(e -> alternarStatus());
        linhaBtn.add(btnStatus);
        linhaBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        corpoStatus.add(linhaBtn);
        cardStatus.add(corpoStatus, BorderLayout.CENTER);
        colunaEsq.add(cardStatus, BorderLayout.NORTH);

        JPanel cardContatos = Tema.card("Contatos");
        listaContatos = new JList<>(modeloContatos);
        listaContatos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaContatos.setFont(Tema.FONTE);
        listaContatos.setBackground(Tema.CARD);
        listaContatos.setCellRenderer(new RenderizadorContato());
        listaContatos.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selecionarContato(listaContatos.getSelectedValue());
            }
        });
        JScrollPane spContatos = new JScrollPane(listaContatos);
        spContatos.setBorder(null);
        cardContatos.add(spContatos, BorderLayout.CENTER);

        JPanel botoesContato = new JPanel(new GridLayout(1, 2, 8, 0));
        botoesContato.setOpaque(false);
        JButton btnAdd = Tema.botao("+ Adicionar", Tema.PRIMARIA);
        btnAdd.addActionListener(e -> adicionarContato());
        JButton btnRem = Tema.botao("Remover", Tema.PERIGO);
        btnRem.addActionListener(e -> removerContato());
        botoesContato.add(btnAdd);
        botoesContato.add(btnRem);
        cardContatos.add(botoesContato, BorderLayout.SOUTH);
        colunaEsq.add(cardContatos, BorderLayout.CENTER);

        centro.add(colunaEsq, BorderLayout.WEST);

        // ---------- Coluna direita: conversa ----------
        JPanel cardConversa = Tema.card(null);
        lblTituloConversa = new JLabel("Selecione um contato para conversar");
        lblTituloConversa.setFont(Tema.FONTE_BOLD);
        lblTituloConversa.setForeground(Tema.TEXTO);
        cardConversa.add(lblTituloConversa, BorderLayout.NORTH);

        areaConversa = new JTextArea();
        areaConversa.setEditable(false);
        areaConversa.setLineWrap(true);
        areaConversa.setWrapStyleWord(true);
        areaConversa.setFont(Tema.FONTE);
        areaConversa.setForeground(Tema.TEXTO);
        areaConversa.setBackground(Tema.CARD);
        JScrollPane spConversa = new JScrollPane(areaConversa);
        spConversa.setBorder(null);
        cardConversa.add(spConversa, BorderLayout.CENTER);

        JPanel envio = new JPanel(new BorderLayout(8, 0));
        envio.setOpaque(false);
        campoMensagem = new JTextField();
        Tema.campo(campoMensagem);
        campoMensagem.addActionListener(e -> enviarMensagem());
        btnEnviar = Tema.botao("Enviar", Tema.SUCESSO);
        btnEnviar.setEnabled(false);
        btnEnviar.addActionListener(e -> enviarMensagem());
        envio.add(campoMensagem, BorderLayout.CENTER);
        envio.add(btnEnviar, BorderLayout.EAST);
        cardConversa.add(envio, BorderLayout.SOUTH);

        centro.add(cardConversa, BorderLayout.CENTER);
        add(centro, BorderLayout.CENTER);

        setSize(920, 580);
        setMinimumSize(new Dimension(780, 480));
        setLocationByPlatform(true);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { sair(); }
        });

        carregarContatos();
        conectarAoServidor();
    }

    // -------------------------------------------------------------------------
    // Conexao com o servidor (RMI)
    // -------------------------------------------------------------------------

    /*
     * Exporta o callback e chama conectar() no servidor em uma thread propria.
     * O servidor cria a fila do cliente (requisito 7) e ja entrega, via
     * callback, o que estava aguardando nela.
     */
    private void conectarAoServidor() {
        Thread t = new Thread(() -> {
            try {
                callback = new CallbackImpl();
                servico.conectar(nome, callback);
                SwingUtilities.invokeLater(() -> {
                    conectado = true;
                    onlineLocal = true;
                    btnEnviar.setEnabled(true);
                    btnStatus.setEnabled(true);
                    atualizarMeuStatus();
                });
                consultarStatusContatos();
            } catch (RemoteException ex) {
                String motivo = ex.getMessage() != null && ex.getMessage().contains("NOME_EM_USO")
                        ? "Ja existe um cliente conectado com o nome '" + nome + "'."
                        : "Falha ao conectar ao servidor: " + ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, motivo, "Erro", JOptionPane.ERROR_MESSAGE);
                    dispose();
                });
                if (callback != null) {
                    try { UnicastRemoteObject.unexportObject(callback, true); } catch (Exception ignored) { }
                }
            }
        }, "conectar-" + nome);
        t.start();
    }

    /* Consulta o status atual de todos os contatos (apos conectar/voltar online). */
    private void consultarStatusContatos() {
        // DefaultListModel usa Vector internamente; leitura fora da EDT e segura.
        List<String> contatos = new ArrayList<>();
        for (int i = 0; i < modeloContatos.size(); i++) {
            contatos.add(modeloContatos.get(i));
        }
        for (String contato : contatos) {
            try {
                boolean onl = servico.estaOnline(contato);
                SwingUtilities.invokeLater(() -> {
                    statusContatos.put(contato, onl);
                    listaContatos.repaint();
                });
            } catch (RemoteException ignored) { }
        }
    }

    /* Fecha a sessao: avisa o servidor e desfaz a exportacao do callback. */
    private void sair() {
        Thread t = new Thread(() -> {
            try { servico.desconectar(nome); } catch (RemoteException ignored) { }
            if (callback != null) {
                try { UnicastRemoteObject.unexportObject(callback, true); } catch (Exception ignored) { }
            }
        }, "sair-" + nome);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Status online/offline (requisito 2)
    // -------------------------------------------------------------------------

    private void alternarStatus() {
        boolean novo = !onlineLocal;
        btnStatus.setEnabled(false);
        Thread t = new Thread(() -> {
            try {
                servico.mudarStatus(nome, novo);
                SwingUtilities.invokeLater(() -> {
                    onlineLocal = novo;
                    atualizarMeuStatus();
                    btnStatus.setEnabled(true);
                });
                if (novo) {
                    consultarStatusContatos();
                }
            } catch (RemoteException ex) {
                SwingUtilities.invokeLater(() -> {
                    btnStatus.setEnabled(true);
                    JOptionPane.showMessageDialog(this,
                            "Falha ao mudar status: " + ex.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "status-" + nome);
        t.start();
    }

    private void atualizarMeuStatus() {
        if (onlineLocal) {
            lblMeuStatus.setText("● " + nome + " - online");
            lblMeuStatus.setForeground(Tema.SUCESSO);
            btnStatus.setText("Ficar offline");
        } else {
            lblMeuStatus.setText("● " + nome + " - offline");
            lblMeuStatus.setForeground(Tema.PERIGO);
            btnStatus.setText("Ficar online");
        }
    }

    // -------------------------------------------------------------------------
    // Contatos (requisitos 1 e 8)
    // -------------------------------------------------------------------------

    private void adicionarContato() {
        String novo = JOptionPane.showInputDialog(this,
                "Nome de contato do amigo:", "Adicionar contato",
                JOptionPane.PLAIN_MESSAGE);
        if (novo == null) {
            return;
        }
        novo = novo.trim().toLowerCase();
        if (novo.isEmpty() || !novo.matches("[A-Za-z0-9_-]+")) {
            JOptionPane.showMessageDialog(this,
                    "Use apenas letras, numeros, '-' ou '_'.",
                    "Nome invalido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (novo.equals(nome)) {
            JOptionPane.showMessageDialog(this,
                    "Voce nao pode adicionar a si mesmo.",
                    "Nome invalido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (modeloContatos.contains(novo)) {
            JOptionPane.showMessageDialog(this,
                    "'" + novo + "' ja esta na sua lista.",
                    "Contato repetido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        modeloContatos.addElement(novo);
        salvarContatos();
        consultarStatusDeUmContato(novo);
    }

    private void removerContato() {
        String selecionado = listaContatos.getSelectedValue();
        if (selecionado == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecione na lista o contato que deseja remover.",
                    "Remover contato", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int op = JOptionPane.showConfirmDialog(this,
                "Remover '" + selecionado + "' da lista de contatos?",
                "Remover contato", JOptionPane.YES_NO_OPTION);
        if (op != JOptionPane.YES_OPTION) {
            return;
        }
        modeloContatos.removeElement(selecionado);
        naoLidas.remove(selecionado);
        statusContatos.remove(selecionado);
        salvarContatos();
        selecionarContato(listaContatos.getSelectedValue());
    }

    /* Garante que 'contato' esta na lista (auto-inclusao ao receber mensagem). */
    private void garantirContato(String contato) {
        if (!modeloContatos.contains(contato)) {
            modeloContatos.addElement(contato);
            salvarContatos();
            consultarStatusDeUmContato(contato);
        }
    }

    private void consultarStatusDeUmContato(String contato) {
        Thread t = new Thread(() -> {
            try {
                boolean onl = servico.estaOnline(contato);
                SwingUtilities.invokeLater(() -> {
                    statusContatos.put(contato, onl);
                    listaContatos.repaint();
                });
            } catch (RemoteException ignored) { }
        }, "status-contato-" + contato);
        t.start();
    }

    private Path arquivoContatos() {
        return Paths.get("contatos-" + nome + ".txt");
    }

    /* Carrega a lista de contatos salva na execucao anterior. */
    private void carregarContatos() {
        Path arq = arquivoContatos();
        if (!Files.exists(arq)) {
            return;
        }
        try {
            for (String linha : Files.readAllLines(arq, StandardCharsets.UTF_8)) {
                String contato = linha.trim();
                if (!contato.isEmpty() && !modeloContatos.contains(contato)) {
                    modeloContatos.addElement(contato);
                }
            }
        } catch (IOException ignored) { }
    }

    private void salvarContatos() {
        List<String> linhas = new ArrayList<>();
        for (int i = 0; i < modeloContatos.size(); i++) {
            linhas.add(modeloContatos.get(i));
        }
        try {
            Files.write(arquivoContatos(), linhas, StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }

    // -------------------------------------------------------------------------
    // Conversa (envio e recebimento)
    // -------------------------------------------------------------------------

    private void enviarMensagem() {
        String destinatario = listaContatos.getSelectedValue();
        if (destinatario == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecione um contato para enviar a mensagem.",
                    "Enviar", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String texto = campoMensagem.getText().trim();
        if (texto.isEmpty() || !conectado) {
            return;
        }
        campoMensagem.setText("");
        anexarLinha(destinatario, "[" + HORA.format(new Date()) + "] Voce: " + texto);

        Thread t = new Thread(() -> {
            try {
                boolean entregue = servico.enviar(nome, destinatario, texto);
                if (!entregue) {
                    SwingUtilities.invokeLater(() -> anexarLinha(destinatario,
                            "        >> '" + destinatario
                            + "' esta offline - mensagem guardada na fila dele no servidor"));
                }
            } catch (RemoteException ex) {
                SwingUtilities.invokeLater(() -> anexarLinha(destinatario,
                        "        >> falha ao enviar: " + ex.getMessage()));
            }
        }, "enviar-" + nome);
        t.start();
    }

    /* Chamado (na EDT) quando o servidor entrega uma mensagem via callback. */
    private void aoReceberMensagem(String remetente, String texto, long timestamp, boolean daFila) {
        garantirContato(remetente);
        String hora = HORA.format(new Date(timestamp));
        String linha = daFila
                ? "[" + hora + "] " + remetente + " (estava na sua fila offline): " + texto
                : "[" + hora + "] " + remetente + ": " + texto;
        anexarLinha(remetente, linha);
        if (!remetente.equals(listaContatos.getSelectedValue())) {
            naoLidas.merge(remetente, 1, Integer::sum);
            listaContatos.repaint();
        }
    }

    /* Acrescenta uma linha ao historico do contato e, se selecionado, a tela. */
    private void anexarLinha(String contato, String linha) {
        historicos.computeIfAbsent(contato, k -> new StringBuilder())
                .append(linha).append('\n');
        if (contato.equals(listaContatos.getSelectedValue())) {
            areaConversa.append(linha + "\n");
            areaConversa.setCaretPosition(areaConversa.getDocument().getLength());
        }
    }

    /* Troca a conversa exibida ao clicar em um contato. */
    private void selecionarContato(String contato) {
        if (contato == null) {
            lblTituloConversa.setText("Selecione um contato para conversar");
            areaConversa.setText("");
            return;
        }
        lblTituloConversa.setText("Conversa com " + contato);
        StringBuilder h = historicos.get(contato);
        areaConversa.setText(h == null ? "" : h.toString());
        areaConversa.setCaretPosition(areaConversa.getDocument().getLength());
        if (naoLidas.remove(contato) != null) {
            listaContatos.repaint();
        }
    }

    // -------------------------------------------------------------------------
    // Callback RMI (servidor -> cliente)
    // -------------------------------------------------------------------------

    /*
     * Objeto remoto exportado pelo cliente. Os metodos chegam em threads do
     * pool RMI, entao apenas repassam o trabalho para a EDT.
     */
    private class CallbackImpl extends UnicastRemoteObject implements ClienteCallback {

        private static final long serialVersionUID = 1L;

        CallbackImpl() throws RemoteException {
            super();
        }

        @Override
        public void receberMensagem(String remetente, String texto, long timestamp, boolean daFila) {
            SwingUtilities.invokeLater(() ->
                    aoReceberMensagem(remetente, texto, timestamp, daFila));
        }

        @Override
        public void statusContato(String contato, boolean online) {
            SwingUtilities.invokeLater(() -> {
                if (modeloContatos.contains(contato)) {
                    statusContatos.put(contato, online);
                    listaContatos.repaint();
                }
            });
        }
    }

    /* Renderiza cada contato com bolinha de status e contador de nao lidas. */
    private class RenderizadorContato extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            String contato = String.valueOf(value);
            Boolean onl = statusContatos.get(contato);
            String cor = Boolean.TRUE.equals(onl) ? "#10B981" : "#94A3B8";
            Integer n = naoLidas.get(contato);
            String badge = (n == null || n == 0)
                    ? ""
                    : " &nbsp;<b><font color='#EF4444'>(" + n + ")</font></b>";
            l.setText("<html><font color='" + cor + "'>●</font> " + contato + badge + "</html>");
            l.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            return l;
        }
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    /* Dialogo de login: nome de contato + host do servidor. */
    public static void abrirDialogoLogin() {
        JTextField campoNome = new JTextField(14);
        JTextField campoHost = new JTextField("localhost", 14);
        Tema.campo(campoNome);
        Tema.campo(campoHost);

        JPanel painel = new JPanel(new GridLayout(2, 2, 8, 8));
        painel.setOpaque(false);
        painel.add(new JLabel("Nome de contato:"));
        painel.add(campoNome);
        painel.add(new JLabel("Servidor (host):"));
        painel.add(campoHost);

        int op = JOptionPane.showConfirmDialog(null, painel,
                "Novo Cliente - Entrar no sistema",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (op != JOptionPane.OK_OPTION) {
            return;
        }
        String nome = campoNome.getText().trim();
        String host = campoHost.getText().trim();
        if (!nome.matches("[A-Za-z0-9_-]+")) {
            JOptionPane.showMessageDialog(null,
                    "Nome invalido: use apenas letras, numeros, '-' ou '_'.",
                    "Login", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (host.isEmpty()) {
            host = "localhost";
        }
        abrir(nome, host);
    }

    /* Localiza o servico RMI e abre a janela do cliente. */
    public static void abrir(String nome, String host) {
        final String hostFinal = host;
        Thread t = new Thread(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(hostFinal, Config.getRmiPort());
                ServicoMensagens servico = (ServicoMensagens) registry.lookup(Config.RMI_SERVICO);
                SwingUtilities.invokeLater(() ->
                        new ClienteChat(nome, servico).setVisible(true));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        "Nao foi possivel localizar o Servidor de Mensagens em '"
                        + hostFinal + ":" + Config.getRmiPort() + "'.\n"
                        + "Inicie o servidor primeiro (Launcher > Iniciar servidor).\n\n"
                        + "Detalhe: " + ex.getMessage(),
                        "Erro de conexao", JOptionPane.ERROR_MESSAGE));
            }
        }, "login-" + nome);
        t.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();
            if (args.length >= 1) {
                abrir(args[0], args.length >= 2 ? args[1] : "localhost");
            } else {
                abrirDialogoLogin();
            }
        });
    }
}
