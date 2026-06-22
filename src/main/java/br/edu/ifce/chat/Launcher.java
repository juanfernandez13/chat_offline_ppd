package br.edu.ifce.chat;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * Janela inicial do Projeto Final - permite iniciar o Servidor de Mensagens
 * (broker MOM embarcado + servico RMI) e abrir varios Clientes a partir de
 * uma unica interface, no mesmo estilo do Launcher do Projeto MOM.
 *
 * Para a apresentacao: inicie o servidor, abra dois ou mais clientes com
 * nomes diferentes, adicione um ao outro como contato e troque mensagens.
 * Coloque um deles OFFLINE para ver as mensagens se acumularem na fila dele
 * no servidor e serem entregues quando ele voltar a ficar online.
 *
 * O cliente tambem pode ser aberto em outra maquina apontando para o host do
 * servidor (campo "Servidor (host)" do login).
 */
public class Launcher extends JFrame {

    private static final long serialVersionUID = 1L;

    private ServidorMensagens janelaServidor;
    private JLabel lblStatusServidor;
    private JButton btnServidor;

    public Launcher() {
        super("Projeto Final - Mensagens com Controle Offline");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(Tema.FUNDO);

        // ---------- Cabecalho ----------
        JPanel cab = Tema.cabecalho(
                "Projeto Final - Mensagens com Controle Offline",
                "Servidor remoto via RMI  •  fila MOM (JMS/ActiveMQ) por cliente",
                Tema.SECUNDARIA);
        cab.setBorder(BorderFactory.createEmptyBorder(16, 16, 4, 16));
        add(cab, BorderLayout.NORTH);

        JPanel pilha = new JPanel();
        pilha.setOpaque(false);
        pilha.setLayout(new javax.swing.BoxLayout(pilha, javax.swing.BoxLayout.Y_AXIS));

        // ---- Card do Servidor ----
        JPanel cardServidor = Tema.card("Servidor de Mensagens");
        JPanel corpoServidor = new JPanel();
        corpoServidor.setOpaque(false);
        corpoServidor.setLayout(new javax.swing.BoxLayout(corpoServidor, javax.swing.BoxLayout.Y_AXIS));

        JPanel linhaInfo = Tema.linha();
        JLabel lblInfo = new JLabel("RMI: porta " + Config.getRmiPort()
                + "   •   Broker MOM: " + Config.getConnectorUrl());
        lblInfo.setFont(Tema.FONTE_BOLD);
        lblInfo.setForeground(Tema.TEXTO);
        linhaInfo.add(lblInfo);
        linhaInfo.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        corpoServidor.add(linhaInfo);

        JPanel linhaBtns = Tema.linha();
        btnServidor = Tema.botao("Iniciar servidor", Tema.SUCESSO);
        btnServidor.addActionListener(e -> iniciarServidor());
        linhaBtns.add(btnServidor);
        linhaBtns.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        corpoServidor.add(linhaBtns);

        JPanel linhaStatus = Tema.linha();
        lblStatusServidor = Tema.statusDot("parado (inicie o servidor antes de criar clientes)", Tema.ALERTA);
        linhaStatus.add(lblStatusServidor);
        linhaStatus.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        corpoServidor.add(linhaStatus);

        cardServidor.add(corpoServidor, BorderLayout.CENTER);
        cardServidor.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        limitarAltura(cardServidor);
        pilha.add(cardServidor);
        pilha.add(javax.swing.Box.createVerticalStrut(12));

        // ---- Card de Clientes ----
        JPanel cardClientes = Tema.card("Clientes");
        JPanel pnlAcoes = new JPanel(new GridLayout(1, 2, 10, 0));
        pnlAcoes.setOpaque(false);
        JButton btnCliente = Tema.botao("+ Novo Cliente", Tema.PRIMARIA);
        btnCliente.addActionListener(e -> ClienteChat.abrirDialogoLogin());
        JButton btnPing = Tema.botao("Testar conexao", Tema.CINZA);
        btnPing.addActionListener(e -> testarConexao());
        pnlAcoes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        pnlAcoes.setPreferredSize(new Dimension(0, 52));
        pnlAcoes.add(btnCliente);
        pnlAcoes.add(btnPing);
        cardClientes.add(pnlAcoes, BorderLayout.CENTER);
        cardClientes.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        limitarAltura(cardClientes);
        pilha.add(cardClientes);

        JPanel centro = new JPanel(new BorderLayout());
        centro.setOpaque(false);
        centro.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        centro.add(pilha, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);

        JLabel rodape = new JLabel(
            "<html><div style='padding:2px;'>"
            + "Dica: inicie o servidor, abra 2+ clientes com nomes diferentes e adicione um ao outro"
            + " como contato. Coloque um deles offline para ver a fila em acao."
            + "</div></html>");
        rodape.setForeground(Tema.TEXTO_FRACO);
        rodape.setBorder(BorderFactory.createEmptyBorder(0, 18, 12, 18));
        add(rodape, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    /* Fixa a altura maxima de um componente na sua altura preferida (largura livre). */
    private static void limitarAltura(JComponent c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
    }

    /* Abre a janela do servidor (broker + RMI sobem com ela). */
    private void iniciarServidor() {
        if (janelaServidor != null && janelaServidor.isDisplayable()) {
            janelaServidor.toFront();
            return;
        }
        janelaServidor = new ServidorMensagens();
        janelaServidor.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                lblStatusServidor.setText("● parado");
                lblStatusServidor.setForeground(Tema.PERIGO);
                btnServidor.setText("Iniciar servidor");
            }
        });
        janelaServidor.setVisible(true);
        lblStatusServidor.setText("● em execucao nesta maquina (janela do servidor aberta)");
        lblStatusServidor.setForeground(Tema.SUCESSO);
        btnServidor.setText("Mostrar janela do servidor");
    }

    /* Verifica se o servico RMI esta acessivel em localhost. */
    private void testarConexao() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", Config.getRmiPort());
            registry.lookup(Config.RMI_SERVICO);
            JOptionPane.showMessageDialog(this,
                    "Servidor de Mensagens acessivel em localhost:" + Config.getRmiPort(),
                    "Teste de conexao", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Servidor nao encontrado: " + ex.getMessage(),
                    "Teste de conexao", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "servidor": ServidorMensagens.main(args); return;
                case "cliente":
                    String[] resto = new String[args.length - 1];
                    System.arraycopy(args, 1, resto, 0, resto.length);
                    ClienteChat.main(resto);
                    return;
                default: break;
            }
        }
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();
            Launcher l = new Launcher();
            l.setMinimumSize(l.getSize());
            l.setVisible(true);
        });
    }
}
