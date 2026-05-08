package thread;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * CertificateViewer — Blue template design.
 *
 * PNG fix: TYPE_INT_RGB + white fill + Renderer.draw() called directly
 * (not via certPanel.paint() which needs a realized Swing window).
 */
public class CertificateViewer extends JDialog {

    // ── certificate data ──────────────────────────────────────────
    public static class CertData {
        public int    certId;
        public String studentName;
        public String eventName;
        public String certType;   // Participant | Winner | Runner-up
        public String issueDate;
        public String deptName;
    }

    // ── shared colours ────────────────────────────────────────────
    static final Color BLUE_DARK  = new Color(26,  58, 107);
    static final Color BLUE_MID   = new Color(58, 114, 181);
    static final Color BLUE_LIGHT = new Color(74,  90, 128);
    static final Color BLUE_MUTED = new Color(122, 138, 170);
    static final Color WHITE      = Color.WHITE;
    static final Color PAGE_BG    = new Color(240, 242, 246);

    static final int CERT_W = 900;
    static final int CERT_H = 636;
    static final int BORDER = 22;

    private final CertData data;

    // ── static factory ────────────────────────────────────────────
    public static void show(Frame owner, CertData data) {
        new CertificateViewer(owner, data).setVisible(true);
    }

    // ── constructor ───────────────────────────────────────────────
    public CertificateViewer(Frame owner, CertData data) {
        super(owner, "Certificate — " + data.certType, true);
        this.data = data;

        setSize(990, 810);
        setLocationRelativeTo(owner);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAGE_BG);

        // top bar
        JPanel topBar = new JPanel(null);
        topBar.setBackground(WHITE);
        topBar.setPreferredSize(new Dimension(990, 56));
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 210, 225)));

        JLabel titleLbl = new JLabel("Certificate of " + data.certType);
        titleLbl.setFont(new Font("Georgia", Font.BOLD | Font.ITALIC, 18));
        titleLbl.setForeground(BLUE_DARK);
        titleLbl.setBounds(20, 14, 500, 28);
        topBar.add(titleLbl);

        JButton closeBtn = new JButton("Close");
        closeBtn.setBackground(PAGE_BG);
        closeBtn.setForeground(BLUE_DARK);
        closeBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorder(BorderFactory.createLineBorder(new Color(180, 200, 220), 1));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.setBounds(888, 14, 74, 28);
        closeBtn.addActionListener(e -> dispose());
        topBar.add(closeBtn);

        // certificate display panel
        CertificatePanel certPanel = new CertificatePanel(data);
        certPanel.setPreferredSize(new Dimension(CERT_W, CERT_H));

        JPanel holder = new JPanel(new GridBagLayout());
        holder.setOpaque(false);
        holder.setBorder(new EmptyBorder(20, 30, 10, 30));
        holder.add(certPanel);

        // bottom buttons
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 14));
        bottomBar.setBackground(PAGE_BG);

        JButton btnPng   = buildBtn("Download PNG",    true);
        JButton btnPrint = buildBtn("Print / Save PDF", false);
        btnPng  .addActionListener(e -> downloadPNG());
        btnPrint.addActionListener(e -> printCert());
        bottomBar.add(btnPng);
        bottomBar.add(btnPrint);

        root.add(topBar,    BorderLayout.NORTH);
        root.add(holder,    BorderLayout.CENTER);
        root.add(bottomBar, BorderLayout.SOUTH);
        setContentPane(root);
    }

    // ── PNG download ──────────────────────────────────────────────
    private void downloadPNG() {
        JFileChooser fc = new JFileChooser();
        String safe = (data.studentName == null ? "student"
                        : data.studentName).replaceAll("[^a-zA-Z0-9]", "_");
        fc.setSelectedFile(new File("Certificate_" + safe + ".png"));
        fc.setDialogTitle("Save Certificate as PNG");
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png"))
            file = new File(file.getAbsolutePath() + ".png");

        try {
            ImageIO.write(renderToImage(2), "PNG", file);
            JOptionPane.showMessageDialog(this,
                "Saved:\n" + file.getAbsolutePath(),
                "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── print / save PDF ──────────────────────────────────────────
    private void printCert() {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat pf  = job.defaultPage();
        Paper paper    = pf.getPaper();
        double pw = 842, ph = 595;
        paper.setSize(pw, ph);
        paper.setImageableArea(18, 18, pw - 36, ph - 36);
        pf.setOrientation(PageFormat.LANDSCAPE);
        pf.setPaper(paper);

        job.setPrintable((gfx, fmt, idx) -> {
            if (idx > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) gfx;
            g2.setColor(WHITE);
            g2.fillRect(0, 0, (int) fmt.getWidth(), (int) fmt.getHeight());
            g2.translate(fmt.getImageableX(), fmt.getImageableY());
            double sx = fmt.getImageableWidth()  / CERT_W;
            double sy = fmt.getImageableHeight() / CERT_H;
            g2.scale(Math.min(sx, sy), Math.min(sx, sy));
            Renderer.draw(g2, data);
            return Printable.PAGE_EXISTS;
        }, pf);

        if (job.printDialog()) {
            try { job.print(); }
            catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, "Print error: " + ex.getMessage());
            }
        }
    }

    // ── render to image ───────────────────────────────────────────
    // TYPE_INT_RGB = no alpha channel, so no black background ever.
    // Calls Renderer.draw() directly — does NOT use certPanel.paint()
    // which requires the Swing component to be visible/realized.
    private BufferedImage renderToImage(int scale) {
        BufferedImage img = new BufferedImage(
            CERT_W * scale, CERT_H * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(WHITE);
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2.scale(scale, scale);
        Renderer.draw(g2, data);
        g2.dispose();
        return img;
    }

    // =============================================================
    //  Renderer — all drawing logic, pure static, no Swing state
    //  Used by BOTH paintComponent (screen) and renderToImage (file)
    // =============================================================
    static class Renderer {

        static void draw(Graphics2D g, CertData d) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

            final int W = CERT_W, H = CERT_H, B = BORDER;

            // 1. white fill
            g.setColor(WHITE);
            g.fillRect(0, 0, W, H);

            // 2. blue outer border
            g.setColor(BLUE_MID);
            g.fillRect(0, 0, W, B);
            g.fillRect(0, H - B, W, B);
            g.fillRect(0, 0, B, H);
            g.fillRect(W - B, 0, B, H);

            // 3. diagonal accent triangles (corners)
            g.setColor(new Color(100, 150, 210, 160));
            g.fillPolygon(new int[]{0, B*5, 0},   new int[]{0, 0, B*5},   3);
            g.fillPolygon(new int[]{W, W-B*5, W}, new int[]{0, 0, B*5},   3);
            g.setColor(new Color(80, 120, 180, 100));
            g.fillPolygon(new int[]{W, W-B*3, W}, new int[]{H, H, H-B*3}, 3);
            g.fillPolygon(new int[]{0, B*3, 0},   new int[]{H, H, H-B*3}, 3);

            // 4. faint watermark
            g.setFont(new Font("Georgia", Font.BOLD, 160));
            g.setColor(new Color(58, 114, 181, 12));
            FontMetrics wmFm = g.getFontMetrics();
            g.drawString("CES", W/2 - wmFm.stringWidth("CES")/2,
                                 H/2 + wmFm.getAscent()/3);

            int iX = B + 14, iY = B + 12;
            int iRight = W - B - 14;

            // 5. header left: CES logo box + org text
            g.setColor(BLUE_MID);
            g.fillRoundRect(iX, iY, 28, 28, 5, 5);
            g.setColor(WHITE);
            g.setFont(new Font("Georgia", Font.BOLD, 10));
            FontMetrics lFm = g.getFontMetrics();
            g.drawString("CES", iX + 14 - lFm.stringWidth("CES")/2, iY + 19);

            g.setColor(BLUE_DARK);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString("Campus Event System", iX + 34, iY + 12);
            g.setColor(BLUE_MUTED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.drawString("Certification Authority", iX + 34, iY + 24);

            // 5. header right: cert ID + awarded date
            String sId   = "Certificate ID: CES-" + String.format("%04d", d.certId);
            String sDate = "Awarded on: " + nvl(d.issueDate);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(BLUE_MUTED);
            FontMetrics rFm = g.getFontMetrics();
            g.drawString(sId,   iRight - rFm.stringWidth(sId),   iY + 12);
            g.drawString(sDate, iRight - rFm.stringWidth(sDate), iY + 26);

            // 6. big title
            String titleStr = titleFor(d.certType);
            Font titleFont  = new Font("Georgia", Font.BOLD, 34);
            g.setFont(titleFont);
            g.setColor(BLUE_DARK);
            FontMetrics tFm = g.getFontMetrics();
            g.drawString(titleStr, W/2 - tFm.stringWidth(titleStr)/2, B + 110);

            // 7. "This is to certify that"
            g.setFont(new Font("Georgia", Font.ITALIC, 14));
            g.setColor(BLUE_LIGHT);
            FontMetrics cFm = g.getFontMetrics();
            String certify = "This is to certify that";
            g.drawString(certify, W/2 - cFm.stringWidth(certify)/2, B + 150);

            // 8. student name
            String name = nvl(d.studentName, "________________");
            g.setFont(new Font("Georgia", Font.BOLD, 30));
            g.setColor(BLUE_DARK);
            FontMetrics nFm = g.getFontMetrics();
            g.drawString(name, W/2 - nFm.stringWidth(name)/2, B + 198);
            // underline
            g.setColor(BLUE_MID);
            g.setStroke(new BasicStroke(1.5f));
            int uH = nFm.stringWidth(name)/2 + 30;
            g.drawLine(W/2 - uH, B + 207, W/2 + uH, B + 207);

            // 9. body line
            String body = bodyFor(d.certType);
            g.setFont(new Font("Georgia", Font.PLAIN, 14));
            g.setColor(BLUE_LIGHT);
            FontMetrics bFm = g.getFontMetrics();
            g.drawString(body, W/2 - bFm.stringWidth(body)/2, B + 240);

            // 10. event name
            String evStr = "\u201C" + nvl(d.eventName, "________________") + "\u201D";
            g.setFont(new Font("Georgia", Font.BOLD | Font.ITALIC, 17));
            g.setColor(BLUE_DARK);
            FontMetrics eFm = g.getFontMetrics();
            g.drawString(evStr, W/2 - eFm.stringWidth(evStr)/2, B + 268);

            // 11. department (if present)
            if (d.deptName != null && !d.deptName.isEmpty()) {
                String dept = "Department of " + d.deptName;
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(BLUE_MUTED);
                FontMetrics dFm = g.getFontMetrics();
                g.drawString(dept, W/2 - dFm.stringWidth(dept)/2, B + 295);
            }

            // 12. divider
            g.setColor(new Color(58, 114, 181, 55));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(W/2 - 180, B + 316, W/2 + 180, B + 316);

            // 13. footer cert info
            String footer = "Certificate No: CES-" + String.format("%04d", d.certId)
                          + "       Date of Issue: " + nvl(d.issueDate);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(BLUE_MUTED);
            FontMetrics fFm = g.getFontMetrics();
            g.drawString(footer, W/2 - fFm.stringWidth(footer)/2, B + 336);

            // 14. signature lines
            int sigY = H - B - 60;
            drawSigLine(g, W/4,     sigY, "Organiser");
            drawSigLine(g, 3*W/4,   sigY, "Principal");

            // 15. type badge (centre)
            drawBadge(g, W/2, sigY - 6, d.certType);

            // 16. winner / runner-up ribbon
            if (!"Participant".equalsIgnoreCase(d.certType))
                drawRibbon(g, W, d.certType);

            // 17. bottom tagline
            String tagline = "Campus Event System  \u00B7  Excellence in Participation";
            g.setFont(new Font("SansSerif", Font.ITALIC, 10));
            g.setColor(new Color(170, 180, 200));
            FontMetrics tagFm = g.getFontMetrics();
            g.drawString(tagline, W/2 - tagFm.stringWidth(tagline)/2, H - B/2 - 4);
        }

        // ── helpers ───────────────────────────────────────────────

        private static void drawSigLine(Graphics2D g, int cx, int y, String role) {
            g.setColor(new Color(58, 114, 181, 120));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(cx - 60, y, cx + 60, y);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(BLUE_MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(role, cx - fm.stringWidth(role)/2, y + 16);
        }

        private static void drawBadge(Graphics2D g, int cx, int y, String type) {
            Color fill    = "Winner".equalsIgnoreCase(type)    ? new Color(255, 243, 205)
                          : "Runner-up".equalsIgnoreCase(type) ? new Color(240, 240, 240)
                          :                                       new Color(230, 240, 251);
            Color textCol = "Winner".equalsIgnoreCase(type)    ? new Color(120, 90, 0)
                          : "Runner-up".equalsIgnoreCase(type) ? new Color(80, 80, 90)
                          :                                       BLUE_DARK;
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            int bw = fm.stringWidth(type) + 24, bh = 24;
            g.setColor(fill);
            g.fillRoundRect(cx - bw/2, y - bh + 4, bw, bh, 20, 20);
            g.setColor(textCol);
            g.drawString(type, cx - fm.stringWidth(type)/2, y + 3);
        }

        private static void drawRibbon(Graphics2D g, int W, String type) {
            Color c1 = "Winner".equalsIgnoreCase(type) ? new Color(200, 155, 20)
                                                        : new Color(136, 136, 136);
            Color c2 = "Winner".equalsIgnoreCase(type) ? new Color(230, 185, 50)
                                                        : new Color(170, 170, 170);
            int rx = W - BORDER, ry = BORDER, size = 88;
            g.setPaint(new GradientPaint(rx - size, ry, c1, rx, ry + size, c2));
            g.fillPolygon(new int[]{rx-size, rx, rx},
                          new int[]{ry,      ry, ry+size}, 3);
            g.setColor(new Color(20, 10, 40));
            g.setFont(new Font("Georgia", Font.BOLD, 10));
            Graphics2D gr = (Graphics2D) g.create();
            gr.translate(rx - 18, ry + 18);
            gr.rotate(Math.PI / 4);
            String lbl = type.toUpperCase();
            gr.drawString(lbl, -gr.getFontMetrics().stringWidth(lbl)/2, 0);
            gr.dispose();
        }

        private static String titleFor(String type) {
            if ("Winner".equalsIgnoreCase(type))    return "CERTIFICATE OF EXCELLENCE";
            if ("Runner-up".equalsIgnoreCase(type)) return "CERTIFICATE OF ACHIEVEMENT";
            return "CERTIFICATE OF PARTICIPATION";
        }

        private static String bodyFor(String type) {
            if ("Winner".equalsIgnoreCase(type))    return "has successfully WON the event";
            if ("Runner-up".equalsIgnoreCase(type)) return "has achieved Runner-up position in the event";
            return "has successfully participated in the event";
        }

        private static String nvl(String s) {
            return (s == null || s.isEmpty()) ? "" : s;
        }

        private static String nvl(String s, String fallback) {
            return (s == null || s.isEmpty()) ? fallback : s;
        }
    }

    // =============================================================
    //  CertificatePanel — Swing display only, calls Renderer.draw()
    // =============================================================
    static class CertificatePanel extends JPanel {
        private final CertData d;
        CertificatePanel(CertData d) {
            this.d = d;
            setOpaque(false);
            setPreferredSize(new Dimension(CERT_W, CERT_H));
        }
        @Override
        protected void paintComponent(Graphics g0) {
            // Do NOT call super — Renderer fills the white background itself
            Graphics2D g = (Graphics2D) g0.create();
            Renderer.draw(g, d);
            g.dispose();
        }
    }

    // =============================================================
    //  Button helper
    // =============================================================
    private static JButton buildBtn(String text, boolean primary) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(primary ? BLUE_MID : PAGE_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                if (!primary) {
                    g2.setColor(new Color(180, 200, 220));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 10, 10);
                }
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setForeground(primary ? WHITE : BLUE_DARK);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setPreferredSize(new Dimension(230, 42));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
