package br.edu.ifce.chat;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;

/*
 * Design system compartilhado pelas telas Swing do projeto (Launcher, Servidor e
 * Cliente). Centraliza a paleta de cores, fontes e fabrica componentes
 * estilizados (botoes e paineis com cantos arredondados, cabecalhos com
 * gradiente, cards com sombra suave e badges de status) para dar uma aparencia
 * consistente e moderna usando apenas Swing puro (sem dependencias externas).
 */
public final class Tema {

    // ---------- Paleta (estilo "moderno" inspirado em Tailwind/slate+indigo) --
    public static final Color FUNDO       = new Color(0xF1, 0xF5, 0xF9); // slate-100
    public static final Color CARD        = Color.WHITE;
    public static final Color PRIMARIA    = new Color(0x4F, 0x46, 0xE5); // indigo-600
    public static final Color SECUNDARIA  = new Color(0x7C, 0x3A, 0xED); // violet-600
    public static final Color TEXTO       = new Color(0x0F, 0x17, 0x2A); // slate-900
    public static final Color TEXTO_FRACO = new Color(0x64, 0x74, 0x8B); // slate-500
    public static final Color SUCESSO     = new Color(0x10, 0xB9, 0x81); // emerald-500
    public static final Color PERIGO      = new Color(0xEF, 0x44, 0x44); // red-500
    public static final Color ALERTA      = new Color(0xF5, 0x9E, 0x0B); // amber-500
    public static final Color BORDA       = new Color(0xE2, 0xE8, 0xF0); // slate-200
    public static final Color CINZA       = new Color(0x47, 0x55, 0x69); // slate-600

    // ---------- Fontes ----------
    public static final Font FONTE        = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font FONTE_BOLD   = new Font("SansSerif", Font.BOLD, 13);
    public static final Font FONTE_TITULO = new Font("SansSerif", Font.BOLD, 19);
    public static final Font FONTE_GRANDE = new Font("SansSerif", Font.BOLD, 34);
    public static final Font MONO         = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private static final int RAIO_CARD   = 18;
    private static final int RAIO_BOTAO  = 12;
    private static final int SOMBRA      = 8;

    private Tema() { }

    /* Ajusta defaults globais do Swing (fonte e cores base). */
    public static void aplicar() {
        UIManager.put("Label.font", FONTE);
        UIManager.put("CheckBox.font", FONTE);
        UIManager.put("ComboBox.font", FONTE);
        UIManager.put("TextField.font", FONTE);
        UIManager.put("Spinner.font", FONTE);
        UIManager.put("OptionPane.messageFont", FONTE);
        UIManager.put("OptionPane.buttonFont", FONTE_BOLD);
        UIManager.put("Panel.background", FUNDO);
        UIManager.put("OptionPane.background", FUNDO);
        UIManager.put("CheckBox.background", CARD);
        UIManager.put("ToolTip.font", FONTE);
    }

    // -------------------------------------------------------------------------
    // Fabricas de componentes
    // -------------------------------------------------------------------------

    /* Botao plano com cantos arredondados e estados de hover/clique. */
    public static JButton botao(String texto, Color cor) {
        return new BotaoArredondado(texto, cor);
    }

    /* Cabecalho com gradiente diagonal, titulo e subtitulo em branco. */
    public static JPanel cabecalho(String titulo, String subtitulo, Color cor) {
        // Duotone diagonal: mistura a cor base com o outro acento (indigo<->violeta).
        Color acento = aproxIgual(cor, SECUNDARIA) ? PRIMARIA : SECUNDARIA;
        PainelGradiente p = new PainelGradiente(cor, misturar(cor, acento, 0.85f), 0);
        p.setLayout(new java.awt.BorderLayout(0, 3));
        p.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JLabel t = new JLabel(titulo);
        t.setFont(FONTE_TITULO);
        t.setForeground(Color.WHITE);
        p.add(t, java.awt.BorderLayout.NORTH);
        if (subtitulo != null) {
            JLabel s = new JLabel(subtitulo);
            s.setFont(FONTE);
            s.setForeground(new Color(255, 255, 255, 220));
            p.add(s, java.awt.BorderLayout.SOUTH);
        }
        return p;
    }

    /*
     * Card branco com cantos arredondados, sombra suave e titulo opcional.
     * O conteudo deve ser adicionado em BorderLayout.CENTER pelo chamador.
     */
    public static JPanel card(String titulo) {
        PainelArredondado p = new PainelArredondado(CARD, BORDA, RAIO_CARD, true);
        p.setLayout(new java.awt.BorderLayout(0, 8));
        // Borda externa reserva espaco para a sombra (baixo/direita) + padding interno.
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, SOMBRA, SOMBRA),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        if (titulo != null) {
            JLabel t = new JLabel(titulo.toUpperCase());
            t.setFont(FONTE_BOLD.deriveFont(11f));
            t.setForeground(TEXTO_FRACO);
            p.add(t, java.awt.BorderLayout.NORTH);
        }
        return p;
    }

    /* Painel transparente com FlowLayout a esquerda, util dentro de cards. */
    public static JPanel linha() {
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
        p.setOpaque(false);
        return p;
    }

    /* Aplica fonte, borda arredondada e destaque de foco a um campo de texto. */
    public static JComponent campo(JComponent c) {
        c.setFont(FONTE);
        aplicarBordaCampo(c, false);
        c.addFocusListener(new FocusListener() {
            @Override public void focusGained(FocusEvent e) { aplicarBordaCampo(c, true); }
            @Override public void focusLost(FocusEvent e)   { aplicarBordaCampo(c, false); }
        });
        return c;
    }

    private static void aplicarBordaCampo(JComponent c, boolean foco) {
        c.setBorder(BorderFactory.createCompoundBorder(
                new BordaArredondada(foco ? PRIMARIA : BORDA, 10, foco ? 2 : 1),
                BorderFactory.createEmptyBorder(5, 9, 5, 9)));
    }

    /* Badge de status em formato de "pilula" (fundo translucido + texto colorido). */
    public static JLabel statusDot(String texto, Color cor) {
        PillLabel l = new PillLabel("● " + texto);
        l.setFont(FONTE_BOLD);
        l.setForeground(cor);
        return l;
    }

    // -------------------------------------------------------------------------
    // Componentes customizados
    // -------------------------------------------------------------------------

    static class BotaoArredondado extends JButton {
        private static final long serialVersionUID = 1L;
        private final Color base;

        BotaoArredondado(String texto, Color base) {
            super(texto);
            this.base = base;
            setForeground(Color.WHITE);
            setFont(FONTE_BOLD);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setRolloverEnabled(true);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = base;
            if (!isEnabled()) {
                c = new Color(0xCB, 0xD2, 0xDD);
            } else if (getModel().isPressed()) {
                c = c.darker();
            } else if (getModel().isRollover()) {
                c = clarear(c);
            }
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RAIO_BOTAO, RAIO_BOTAO);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /* Painel arredondado com fundo solido, borda e sombra opcional. */
    static class PainelArredondado extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color fundo;
        private final Color borda;
        private final int arco;
        private final boolean sombra;

        PainelArredondado(Color fundo, Color borda, int arco, boolean sombra) {
            this.fundo = fundo;
            this.borda = borda;
            this.arco = arco;
            this.sombra = sombra;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int margem = sombra ? SOMBRA : 0;
            int w = getWidth() - margem;
            int h = getHeight() - margem;
            if (sombra) {
                for (int i = SOMBRA; i > 0; i--) {
                    int alpha = 6 + (SOMBRA - i) * 3;
                    g2.setColor(new Color(15, 23, 42, alpha));
                    g2.fillRoundRect(i, i, w - 1, h - 1, arco, arco);
                }
            }
            g2.setColor(fundo);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arco, arco);
            if (borda != null) {
                g2.setColor(borda);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arco, arco);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /* Cabecalho pintado com gradiente diagonal entre duas cores. */
    static class PainelGradiente extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color c1;
        private final Color c2;
        private final int arco;

        PainelGradiente(Color c1, Color c2, int arco) {
            this.c1 = c1;
            this.c2 = c2;
            this.arco = arco;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, c1, getWidth(), getHeight(), c2));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arco, arco);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /* Rotulo com fundo translucido em formato de pilula, derivado do foreground. */
    static class PillLabel extends JLabel {
        private static final long serialVersionUID = 1L;

        PillLabel(String texto) {
            super(texto);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = getForeground();
            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 30));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /* Borda arredondada simples (linha) com espessura configuravel. */
    static class BordaArredondada implements Border {
        private final Color cor;
        private final int arco;
        private final int espessura;

        BordaArredondada(Color cor, int arco, int espessura) {
            this.cor = cor;
            this.arco = arco;
            this.espessura = espessura;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(cor);
            g2.setStroke(new java.awt.BasicStroke(espessura));
            g2.drawRoundRect(x + 1, y + 1, width - 3, height - 3, arco, arco);
            g2.dispose();
        }

        @Override
        public java.awt.Insets getBorderInsets(Component c) {
            return new java.awt.Insets(espessura + 1, espessura + 1, espessura + 1, espessura + 1);
        }

        @Override
        public boolean isBorderOpaque() { return false; }
    }

    // -------------------------------------------------------------------------
    // Utilitarios de cor
    // -------------------------------------------------------------------------

    private static Color clarear(Color c) {
        int r = Math.min(255, (int) (c.getRed() + (255 - c.getRed()) * 0.15));
        int g = Math.min(255, (int) (c.getGreen() + (255 - c.getGreen()) * 0.15));
        int b = Math.min(255, (int) (c.getBlue() + (255 - c.getBlue()) * 0.15));
        return new Color(r, g, b);
    }

    /* Mistura duas cores na proporcao t (0 = a, 1 = b). */
    private static Color misturar(Color a, Color b, float t) {
        int r = (int) (a.getRed()   + (b.getRed()   - a.getRed())   * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue()  + (b.getBlue()  - a.getBlue())  * t);
        return new Color(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static boolean aproxIgual(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) < 24
            && Math.abs(a.getGreen() - b.getGreen()) < 24
            && Math.abs(a.getBlue() - b.getBlue()) < 24;
    }
}
