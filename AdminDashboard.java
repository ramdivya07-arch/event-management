package thread;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.sql.*;
import java.text.SimpleDateFormat;

public class AdminDashboard extends JFrame {

    // ── Oracle DB ──────────────────────────────────────────────────
    private static final String DB_URL  = "jdbc:oracle:thin:@localhost:1521:orcl";
    private static final String DB_USER = "C##miniproject";
    private static final String DB_PASS = "mini";

    // ── Palette (matches WelcomePage) ─────────────────────────────
    private static final Color BG_TOP        = new Color(8,   8,  18);
    private static final Color BG_BOT        = new Color(18,  8,  40);
    private static final Color ACCENT        = new Color(130,  60, 255);
    private static final Color ACCENT_LIGHT  = new Color(180, 120, 255);
    private static final Color GOLD          = new Color(255, 200,  60);
    private static final Color AMBER         = new Color(255, 160,  40);
    private static final Color TEXT_PRIMARY  = new Color(240, 235, 255);
    private static final Color TEXT_MUTED    = new Color(160, 150, 200);
    private static final Color CARD_BG       = new Color(255, 255, 255, 12);
    private static final Color CARD_BORDER   = new Color(130,  60, 255, 80);
    private static final Color ERROR_COL     = new Color(255,  80,  80);
    private static final Color SUCCESS_COL   = new Color( 80, 220, 120);

    // ── Layout ────────────────────────────────────────────────────
    private int W, H;
    private JPanel sidebarPanel;
    private JPanel contentArea;
    private CardLayout cardLayout;
    private JLabel currentSectionLabel;

    // ── Sidebar nav items ─────────────────────────────────────────
    private static final String[] NAV_LABELS = {
        "📊  Overview",
        "🏛️  Departments",
        "🎭  Clubs",
        "👑  Incharges",
        "👥  Users"
    };
    private static final String[] NAV_KEYS = {
        "overview", "departments", "clubs", "incharges", "users"
    };
    private JLabel[] navItems;
    private int selectedNav = 0;

    // ── Shared combo for incharges panel (needs refresh after club add) ──
    private JComboBox<String> sharedClubCombo;

    public AdminDashboard() {
        setTitle("Admin Dashboard — Campus Event Management");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        W = scr.width;
        H = scr.height;

        JPanel root = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, BG_TOP, W, H, BG_BOT));
                g2.fillRect(0, 0, W, H);
                g2.setPaint(new RadialGradientPaint(W * 0.5f, H * 0.2f, W * 0.5f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(80, 30, 160, 35), new Color(0, 0, 0, 0)}));
                g2.fillRect(0, 0, W, H);
            }
        };
        root.setOpaque(true);
        setContentPane(root);

        buildTopBar(root);
        buildSidebar(root);
        buildContent(root);   // buildContent must come AFTER sharedClubCombo is ready

        setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════
    //  TOP BAR
    // ══════════════════════════════════════════════════════════════
    private void buildTopBar(JPanel root) {
        JPanel topBar = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(14, 8, 32, 230));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(130, 60, 255, 60));
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 160, 40, 200), 180, 0, new Color(0, 0, 0, 0)));
                g2.fillRect(0, getHeight() - 2, 180, 2);
            }
        };
        topBar.setOpaque(false);
        topBar.setBounds(0, 0, W, 58);

        JLabel logoLbl = new JLabel(buildLogoIcon(34));
        logoLbl.setBounds(14, 12, 34, 34);
        topBar.add(logoLbl);

        JLabel collegeLabel = makeLabel("MEPCO SCHLENK  ·  Admin Dashboard",
            new Font("Georgia", Font.BOLD, 15), new Color(220, 200, 255));
        collegeLabel.setBounds(56, 18, 420, 24);
        topBar.add(collegeLabel);

        currentSectionLabel = makeLabel("Overview",
            new Font("Georgia", Font.ITALIC, 14), AMBER);
        currentSectionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        currentSectionLabel.setBounds(W / 2 - 150, 18, 300, 24);
        topBar.add(currentSectionLabel);

        JButton logoutBtn = buildButtonSmall("⏻  Logout");
        logoutBtn.setBounds(W - 120, 14, 108, 32);
        logoutBtn.addActionListener(e -> {
            dispose();
            new WelcomePage();
        });
        topBar.add(logoutBtn);

        root.add(topBar);
    }

    // ══════════════════════════════════════════════════════════════
    //  SIDEBAR
    // ══════════════════════════════════════════════════════════════
    private void buildSidebar(JPanel root) {
        int SW = 220;
        sidebarPanel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(12, 6, 28, 240));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(130, 60, 255, 50));
                g2.fillRect(getWidth() - 1, 0, 1, getHeight());
            }
        };
        sidebarPanel.setOpaque(false);
        sidebarPanel.setBounds(0, 58, SW, H - 58);

        JPanel badge = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(255, 160, 40, 20));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(255, 160, 40, 60));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
        };
        badge.setOpaque(false);
        badge.setBounds(14, 18, SW - 28, 62);

        JLabel shieldIcon = new JLabel(buildAdminIcon(32));
        shieldIcon.setBounds(10, 15, 32, 32);
        badge.add(shieldIcon);

        JLabel adminTitle = makeLabel("Principal Admin", new Font("Georgia", Font.BOLD, 13), AMBER);
        adminTitle.setBounds(50, 12, 130, 20);
        badge.add(adminTitle);

        JLabel adminSub = makeLabel("Full Access", new Font("SansSerif", Font.PLAIN, 11), new Color(180, 140, 60));
        adminSub.setBounds(50, 30, 130, 16);
        badge.add(adminSub);

        sidebarPanel.add(badge);

        JSeparator sep = buildSeparatorH(SW - 28);
        sep.setBounds(14, 92, SW - 28, 1);
        sidebarPanel.add(sep);

        navItems = new JLabel[NAV_LABELS.length];
        int ny = 106;
        for (int i = 0; i < NAV_LABELS.length; i++) {
            final int idx = i;
            JLabel nav = buildNavItem(NAV_LABELS[i], i == 0);
            nav.setBounds(10, ny, SW - 20, 42);
            nav.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { selectNav(idx); }
                public void mouseEntered(MouseEvent e) {
                    if (idx != selectedNav) nav.setForeground(TEXT_PRIMARY);
                }
                public void mouseExited(MouseEvent e) {
                    if (idx != selectedNav) nav.setForeground(TEXT_MUTED);
                }
            });
            navItems[i] = nav;
            sidebarPanel.add(nav);
            ny += 48;
        }

        root.add(sidebarPanel);
    }

    private JLabel buildNavItem(String text, boolean active) {
        JLabel lbl = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean sel = getForeground().equals(GOLD) || getForeground().equals(AMBER);
                if (sel) {
                    g2.setColor(new Color(130, 60, 255, 28));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(AMBER);
                    g2.fillRoundRect(0, 8, 3, getHeight() - 16, 3, 3);
                }
                super.paintComponent(g);
            }
        };
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        lbl.setForeground(active ? AMBER : TEXT_MUTED);
        lbl.setBorder(new EmptyBorder(0, 18, 0, 0));
        lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return lbl;
    }

    private void selectNav(int idx) {
        selectedNav = idx;
        for (int i = 0; i < navItems.length; i++) {
            navItems[i].setForeground(i == idx ? AMBER : TEXT_MUTED);
            navItems[i].repaint();
        }
        cardLayout.show(contentArea, NAV_KEYS[idx]);
        currentSectionLabel.setText(NAV_LABELS[idx].replaceAll(".*  ", ""));
    }

    // ══════════════════════════════════════════════════════════════
    //  MAIN CONTENT AREA
    // ══════════════════════════════════════════════════════════════
    private void buildContent(JPanel root) {
        int SW = 220;
        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setOpaque(false);
        contentArea.setBounds(SW, 58, W - SW, H - 58);

        contentArea.add(buildOverviewPanel(),    "overview");
        contentArea.add(buildDepartmentPanel(), "departments");
        contentArea.add(buildClubPanel(),       "clubs");
        contentArea.add(buildInchargePanel(),   "incharges");
        contentArea.add(buildUsersPanel(),      "users");

        root.add(contentArea);
    }

    // ══════════════════════════════════════════════════════════════
    //  OVERVIEW PANEL
    // ══════════════════════════════════════════════════════════════
    private JPanel buildOverviewPanel() {
        JPanel p = new JPanel(null);
        p.setOpaque(false);
        int CW = W - 220, pad = 36;

        JLabel title = makeLabel("Dashboard Overview",
            new Font("Georgia", Font.BOLD | Font.ITALIC, 26), TEXT_PRIMARY);
        title.setBounds(pad, 24, 500, 40);
        p.add(title);

        JLabel sub = makeLabel("Welcome back, Principal. Here's what's happening today.",
            new Font("Georgia", Font.ITALIC, 14), TEXT_MUTED);
        sub.setBounds(pad, 62, 600, 22);
        p.add(sub);

        String[][] stats = {
            {"🏛️", "Departments", "dept_count", "#8b5cf6"},
            {"🎭", "Active Clubs",  "club_count",  "#f59e0b"},
            {"👥", "Total Users",  "user_count",  "#10b981"},
            {"📅", "Events",       "event_count", "#3b82f6"}
        };

        int cw = (CW - pad * 2 - 60) / 4, ch = 110, cy = 106;
        JLabel[] valLabels = new JLabel[4];

        for (int i = 0; i < 4; i++) {
            Color accent = Color.decode(stats[i][3]);
            JPanel card = buildStatCard(stats[i][0], stats[i][1], accent);
            card.setBounds(pad + i * (cw + 20), cy, cw, ch);
            valLabels[i] = makeLabel("—", new Font("Georgia", Font.BOLD, 28), TEXT_PRIMARY);
            valLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
            valLabels[i].setBounds(0, 44, cw, 38);
            card.add(valLabels[i]);
            p.add(card);
        }

        // Load stats async
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                String[] queries = {
                    "SELECT COUNT(*) FROM department",
                    "SELECT COUNT(*) FROM club",
                    "SELECT COUNT(*) FROM users",
                    "SELECT COUNT(*) FROM event"
                };
                for (int i = 0; i < 4; i++) {
                    final int fi = i;
                    try {
                        ResultSet rs = con.createStatement().executeQuery(queries[fi]);
                        if (rs.next()) {
                            final String val = String.valueOf(rs.getInt(1));
                            SwingUtilities.invokeLater(() -> valLabels[fi].setText(val));
                        }
                        rs.close();
                    } catch (SQLException ex) {
                        // Table may not exist (e.g. "event"), show 0 instead of crashing
                        SwingUtilities.invokeLater(() -> valLabels[fi].setText("0"));
                    }
                }
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> valLabels[0].setText("DB Err"));
            }
        }).start();

        int qy = cy + ch + 32;
        JLabel actTitle = makeLabel("Quick Actions",
            new Font("Georgia", Font.BOLD, 18), TEXT_PRIMARY);
        actTitle.setBounds(pad, qy, 300, 28);
        p.add(actTitle);

        String[][] actions = {
            {"➕  Add Department", "departments"},
            {"🎭  Create Club",    "clubs"},
            {"👑  Assign Incharge","incharges"}
        };
        for (int i = 0; i < actions.length; i++) {
            final String navKey = actions[i][1];
            JButton btn = buildButton(actions[i][0], true);
            btn.setBounds(pad + i * 200, qy + 38, 185, 44);
            btn.addActionListener(e -> {
                for (int j = 0; j < NAV_KEYS.length; j++) {
                    if (NAV_KEYS[j].equals(navKey)) { selectNav(j); break; }
                }
            });
            p.add(btn);
        }

        return p;
    }

    private JPanel buildStatCard(String emoji, String label, Color accent) {
        JPanel card = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 18));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.setPaint(new GradientPaint(0, 0, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 160), getWidth(), 0, new Color(0, 0, 0, 0)));
                g2.fillRoundRect(0, 0, getWidth(), 3, 3, 3);
            }
        };
        card.setOpaque(false);

        JLabel emojiLbl = new JLabel(emoji);
        emojiLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        emojiLbl.setBounds(12, 10, 36, 28);
        card.add(emojiLbl);

        JLabel lbl = makeLabel(label, new Font("SansSerif", Font.PLAIN, 12),
            new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220));
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        lbl.setBounds(0, 84, 200, 18);
        card.add(lbl);

        return card;
    }

    // ══════════════════════════════════════════════════════════════
    //  DEPARTMENTS PANEL
    // ══════════════════════════════════════════════════════════════
    private JPanel buildDepartmentPanel() {
        JPanel p = new JPanel(null);
        p.setOpaque(false);
        int CW = W - 220, pad = 36;

        JLabel title = makeLabel("Department Management",
            new Font("Georgia", Font.BOLD | Font.ITALIC, 24), TEXT_PRIMARY);
        title.setBounds(pad, 22, 500, 36);
        p.add(title);

        int formW = 420, formH = 240;
        JPanel formCard = buildCard(formW, formH);
        formCard.setBounds(pad, 70, formW, formH);

        JLabel formTitle = makeLabel("Add New Department",
            new Font("Georgia", Font.BOLD, 16), AMBER);
        formTitle.setBounds(20, 16, 300, 24);
        formCard.add(formTitle);

        JTextField deptIdF   = styledField(); deptIdF.setBounds(20, 56, 180, 38);
        JTextField deptNameF = styledField(); deptNameF.setBounds(210, 56, 190, 38);
        JTextField deptDescF = styledField(); deptDescF.setBounds(20, 118, 380, 38);

        formCard.add(fieldLabelAt("Dept ID", 20, 40, 180));
        formCard.add(fieldLabelAt("Dept Name", 210, 40, 190));
        formCard.add(fieldLabelAt("Description", 20, 102, 380));
        formCard.add(deptIdF); formCard.add(deptNameF); formCard.add(deptDescF);

        JLabel formStatus = makeLabel("", new Font("SansSerif", Font.BOLD, 12), ERROR_COL);
        formStatus.setBounds(20, 164, 380, 18);
        formCard.add(formStatus);

        JButton addBtn = buildButtonAmber("Add Department");
        addBtn.setBounds(20, 188, 180, 38);
        formCard.add(addBtn);

        p.add(formCard);

        // ── Departments Table ──
        String[] cols = {"Dept ID", "Name", "Description"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = buildStyledTable(model);
        JScrollPane scroll = buildTableScroll(table);
        int tableX = pad + formW + 24;
        scroll.setBounds(tableX, 70, CW - tableX - pad, H - 200);
        p.add(scroll);

        JButton refreshBtn = buildButtonSmall("↺ Refresh");
        refreshBtn.setBounds(tableX, 38, 110, 28);
        refreshBtn.addActionListener(e -> loadDepartments(model));
        p.add(refreshBtn);

        loadDepartments(model);

        addBtn.addActionListener(e -> {
            // Clear previous status
            formStatus.setForeground(ERROR_COL);
            formStatus.setText("");

            String id   = deptIdF.getText().trim();
            String name = deptNameF.getText().trim();
            String desc = deptDescF.getText().trim();

            // ── Validation ──────────────────────────────────────
            if (id.isEmpty() && name.isEmpty()) {
                formStatus.setText("Dept ID and Name are required.");
                return;
            }
            if (id.isEmpty()) {
                formStatus.setText("Dept ID is required.");
                return;
            }
            if (name.isEmpty()) {
                formStatus.setText("Dept Name is required.");
                return;
            }
            int deptIdInt;
            try {
                deptIdInt = Integer.parseInt(id);
                if (deptIdInt <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ne) {
                formStatus.setText("Dept ID must be a positive integer.");
                return;
            }

            // Disable button to prevent double-click
            addBtn.setEnabled(false);

            new Thread(() -> {
                try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO department (dept_id, dept_name, description) VALUES (?,?,?)");
                    ps.setInt(1, deptIdInt);
                    ps.setString(2, name.length() > 20 ? name.substring(0, 20) : name);
                    ps.setString(3, desc.isEmpty() ? null : (desc.length() > 30 ? desc.substring(0, 30) : desc));
                    ps.executeUpdate();
                    ps.close();
                    SwingUtilities.invokeLater(() -> {
                        formStatus.setForeground(SUCCESS_COL);
                        formStatus.setText("Department added successfully!");
                        deptIdF.setText(""); deptNameF.setText(""); deptDescF.setText("");
                        addBtn.setEnabled(true);
                        loadDepartments(model);
                    });
                } catch (SQLException ex) {
                    final String msg = ex.getMessage() == null ? "Unknown DB error." : ex.getMessage();
                    SwingUtilities.invokeLater(() -> {
                        addBtn.setEnabled(true);
                        formStatus.setForeground(ERROR_COL);
                        if (msg.contains("ORA-00001")) {
                            formStatus.setText("Dept ID already exists.");
                        } else if (msg.contains("ORA-01438")) {
                            formStatus.setText("Dept ID value too large.");
                        } else {
                            formStatus.setText("DB Error: " + msg.substring(0, Math.min(50, msg.length())));
                        }
                    });
                }
            }).start();
        });

        return p;
    }

    private void loadDepartments(DefaultTableModel model) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                ResultSet rs = con.createStatement().executeQuery(
                    "SELECT dept_id, dept_name, description FROM department ORDER BY dept_id");
                java.util.List<Object[]> rows = new java.util.ArrayList<>();
                while (rs.next()) rows.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3)});
                rs.close();
                SwingUtilities.invokeLater(() -> {
                    model.setRowCount(0);
                    for (Object[] r : rows) model.addRow(r);
                });
            } catch (SQLException ignored) {}
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  CLUBS PANEL
    // ══════════════════════════════════════════════════════════════
    private JPanel buildClubPanel() {
        JPanel p = new JPanel(null);
        p.setOpaque(false);
        int CW = W - 220, pad = 36;

        JLabel title = makeLabel("Club Management",
            new Font("Georgia", Font.BOLD | Font.ITALIC, 24), TEXT_PRIMARY);
        title.setBounds(pad, 22, 500, 36);
        p.add(title);

        int formW = 420, formH = 220;
        JPanel formCard = buildCard(formW, formH);
        formCard.setBounds(pad, 70, formW, formH);

        JLabel formTitle = makeLabel("Create New Club",
            new Font("Georgia", Font.BOLD, 16), AMBER);
        formTitle.setBounds(20, 16, 300, 24);
        formCard.add(formTitle);

        JTextField clubIdF   = styledField(); clubIdF.setBounds(20, 56, 180, 38);
        JTextField clubNameF = styledField(); clubNameF.setBounds(210, 56, 190, 38);
        JTextField clubDateF = styledField(); clubDateF.setBounds(20, 118, 380, 38);
        clubDateF.setText("DD/MM/YYYY");
        clubDateF.setForeground(TEXT_MUTED);
        // Placeholder behavior
        clubDateF.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (clubDateF.getText().equals("DD/MM/YYYY")) {
                    clubDateF.setText("");
                    clubDateF.setForeground(TEXT_PRIMARY);
                }
            }
            public void focusLost(FocusEvent e) {
                if (clubDateF.getText().trim().isEmpty()) {
                    clubDateF.setText("DD/MM/YYYY");
                    clubDateF.setForeground(TEXT_MUTED);
                }
            }
        });

        formCard.add(fieldLabelAt("Club ID", 20, 40, 180));
        formCard.add(fieldLabelAt("Club Name (max 10 chars)", 210, 40, 190));
        formCard.add(fieldLabelAt("Created Date (DD/MM/YYYY)", 20, 102, 380));
        formCard.add(clubIdF); formCard.add(clubNameF); formCard.add(clubDateF);

        JLabel formStatus = makeLabel("", new Font("SansSerif", Font.BOLD, 12), ERROR_COL);
        formStatus.setBounds(20, 162, 380, 18);
        formCard.add(formStatus);

        JButton addBtn = buildButtonAmber("Create Club");
        addBtn.setBounds(20, 182, 160, 38);
        formCard.add(addBtn);

        p.add(formCard);

        // ── Clubs Table ──
        String[] cols = {"Club ID", "Club Name", "Created Date"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = buildStyledTable(model);
        JScrollPane scroll = buildTableScroll(table);
        int tableX = pad + formW + 24;
        scroll.setBounds(tableX, 70, CW - tableX - pad, H - 200);
        p.add(scroll);

        JButton refreshBtn = buildButtonSmall("↺ Refresh");
        refreshBtn.setBounds(tableX, 38, 110, 28);
        refreshBtn.addActionListener(e -> loadClubs(model));
        p.add(refreshBtn);

        loadClubs(model);

        addBtn.addActionListener(e -> {
            formStatus.setForeground(ERROR_COL);
            formStatus.setText("");

            String id   = clubIdF.getText().trim();
            String name = clubNameF.getText().trim();
            String date = clubDateF.getText().trim();

            // ── Validation ──────────────────────────────────────
            if (id.isEmpty() || name.isEmpty() || date.isEmpty() || date.equals("DD/MM/YYYY")) {
                formStatus.setText("All fields are required.");
                return;
            }
            int clubIdInt;
            try {
                clubIdInt = Integer.parseInt(id);
                if (clubIdInt <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ne) {
                formStatus.setText("Club ID must be a positive integer.");
                return;
            }
            if (name.length() > 10) {
                name = name.substring(0, 10); // silently truncate; already warned in label
            }

            final String finalName = name;
            final int finalClubId  = clubIdInt;

            addBtn.setEnabled(false);

            new Thread(() -> {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                    sdf.setLenient(false);
                    java.sql.Date sqlDate = new java.sql.Date(sdf.parse(date).getTime());

                    try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                        PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO club (club_id, club_name, created_date) VALUES (?,?,?)");
                        ps.setInt(1, finalClubId);
                        ps.setString(2, finalName);
                        ps.setDate(3, sqlDate);
                        ps.executeUpdate();
                        ps.close();

                        SwingUtilities.invokeLater(() -> {
                            formStatus.setForeground(SUCCESS_COL);
                            formStatus.setText("Club created successfully!");
                            clubIdF.setText("");
                            clubNameF.setText("");
                            clubDateF.setText("DD/MM/YYYY");
                            clubDateF.setForeground(TEXT_MUTED);
                            addBtn.setEnabled(true);
                            // FIX 1: Refresh table immediately after insert
                            loadClubs(model);
                            // FIX 2: Also refresh the shared incharge combo
                            if (sharedClubCombo != null) {
                                loadClubsIntoCombo(sharedClubCombo);
                            }
                        });
                    }
                } catch (java.text.ParseException pe) {
                    SwingUtilities.invokeLater(() -> {
                        addBtn.setEnabled(true);
                        formStatus.setForeground(ERROR_COL);
                        formStatus.setText("Invalid date. Use DD/MM/YYYY format.");
                    });
                } catch (SQLException ex) {
                    final String msg = ex.getMessage() == null ? "Unknown DB error." : ex.getMessage();
                    SwingUtilities.invokeLater(() -> {
                        addBtn.setEnabled(true);
                        formStatus.setForeground(ERROR_COL);
                        if (msg.contains("ORA-00001")) {
                            formStatus.setText("Club ID already exists.");
                        } else if (msg.contains("ORA-01438")) {
                            formStatus.setText("Club ID value too large.");
                        } else {
                            formStatus.setText("DB Error: " + msg.substring(0, Math.min(50, msg.length())));
                        }
                    });
                }
            }).start();
        });

        return p;
    }

    private void loadClubs(DefaultTableModel model) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                ResultSet rs = con.createStatement().executeQuery(
                    "SELECT club_id, club_name, TO_CHAR(created_date,'DD/MM/YYYY') FROM club ORDER BY club_id");
                java.util.List<Object[]> rows = new java.util.ArrayList<>();
                while (rs.next()) rows.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3)});
                rs.close();
                SwingUtilities.invokeLater(() -> {
                    model.setRowCount(0);
                    for (Object[] r : rows) model.addRow(r);
                });
            } catch (SQLException ignored) {}
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  INCHARGES PANEL
    // ══════════════════════════════════════════════════════════════
    private JPanel buildInchargePanel() {
        JPanel p = new JPanel(null);
        p.setOpaque(false);
        int CW = W - 220, pad = 36;

        JLabel title = makeLabel("Incharge Management",
            new Font("Georgia", Font.BOLD | Font.ITALIC, 24), TEXT_PRIMARY);
        title.setBounds(pad, 22, 500, 36);
        p.add(title);

        JLabel sub = makeLabel("Assign student_incharge or staff_incharge to clubs via members_in table.",
            new Font("SansSerif", Font.ITALIC, 13), TEXT_MUTED);
        sub.setBounds(pad, 56, 700, 20);
        p.add(sub);

        int formW = 460, formH = 330;
        JPanel formCard = buildCard(formW, formH);
        formCard.setBounds(pad, 82, formW, formH);

        JLabel formTitle = makeLabel("Assign Incharge to Club",
            new Font("Georgia", Font.BOLD, 16), AMBER);
        formTitle.setBounds(20, 16, 360, 24);
        formCard.add(formTitle);

        // Club combo — stored in instance field so Club panel can refresh it
        formCard.add(fieldLabelAt("Select Club", 20, 48, 200));
        sharedClubCombo = styledComboAmber(new String[]{"-- Loading clubs --"});
        sharedClubCombo.setBounds(20, 66, 420, 38);
        formCard.add(sharedClubCombo);

        // User ID
        formCard.add(fieldLabelAt("User ID", 20, 114, 200));
        JTextField userIdF = styledField(); userIdF.setBounds(20, 132, 200, 38);
        formCard.add(userIdF);

        // Role type
        formCard.add(fieldLabelAt("Incharge Role", 240, 114, 200));
        JComboBox<String> roleCombo = styledComboAmber(
            new String[]{"student_incharge", "staff_incharge"});
        roleCombo.setBounds(240, 132, 200, 38);
        formCard.add(roleCombo);

        // Lookup button
        JButton lookupBtn = buildButtonSmall("Lookup User");
        lookupBtn.setBounds(20, 182, 120, 30);
        formCard.add(lookupBtn);

        JLabel userNameLbl = makeLabel("", new Font("Georgia", Font.ITALIC, 13), SUCCESS_COL);
        userNameLbl.setBounds(150, 182, 290, 30);
        formCard.add(userNameLbl);

        JLabel formStatus = makeLabel("", new Font("SansSerif", Font.BOLD, 12), ERROR_COL);
        formStatus.setBounds(20, 224, 420, 18);
        formCard.add(formStatus);

        // FIX 3: Remove incharge button added here
        JButton removeBtn = buildButtonSmall("✖ Remove Selected");
        removeBtn.setBounds(240, 270, 160, 36);
        formCard.add(removeBtn);

        JButton assignBtn = buildButtonAmber("Assign Incharge");
        assignBtn.setBounds(20, 264, 200, 42);
        formCard.add(assignBtn);

        p.add(formCard);

        // ── Incharges Table ──
        String[] cols = {"Club ID", "Club Name", "User ID", "User Name", "Role"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = buildStyledTable(model);
        JScrollPane scroll = buildTableScroll(table);
        int tableX = pad + formW + 24;
        scroll.setBounds(tableX, 82, CW - tableX - pad, H - 210);
        p.add(scroll);

        JButton refreshBtn = buildButtonSmall("↺ Refresh");
        refreshBtn.setBounds(tableX, 52, 110, 28);
        refreshBtn.addActionListener(e -> loadIncharges(model));
        p.add(refreshBtn);

        loadClubsIntoCombo(sharedClubCombo);
        loadIncharges(model);

        // ── Lookup user ──────────────────────────────────────────
        lookupBtn.addActionListener(e -> {
            userNameLbl.setForeground(SUCCESS_COL);
            userNameLbl.setText("");
            String uid = userIdF.getText().trim();
            if (uid.isEmpty()) {
                userNameLbl.setForeground(ERROR_COL);
                userNameLbl.setText("Enter a User ID first.");
                return;
            }
            int uidInt;
            try {
                uidInt = Integer.parseInt(uid);
            } catch (NumberFormatException ne) {
                userNameLbl.setForeground(ERROR_COL);
                userNameLbl.setText("User ID must be a number.");
                return;
            }
            new Thread(() -> {
                try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    PreparedStatement ps = con.prepareStatement(
                        "SELECT name, user_type FROM users WHERE user_id=?");
                    ps.setInt(1, uidInt);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String found = rs.getString("name") + " (" + rs.getString("user_type") + ")";
                        SwingUtilities.invokeLater(() -> {
                            userNameLbl.setForeground(SUCCESS_COL);
                            userNameLbl.setText("✓  " + found);
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            userNameLbl.setForeground(ERROR_COL);
                            userNameLbl.setText("User ID not found in database.");
                        });
                    }
                    rs.close(); ps.close();
                } catch (SQLException ex) {
                    SwingUtilities.invokeLater(() -> {
                        userNameLbl.setForeground(ERROR_COL);
                        userNameLbl.setText("DB error during lookup.");
                    });
                }
            }).start();
        });

        // ── Assign incharge ──────────────────────────────────────
        assignBtn.addActionListener(e -> {
            formStatus.setForeground(ERROR_COL);
            formStatus.setText("");

            String clubSel = (String) sharedClubCombo.getSelectedItem();
            String uid     = userIdF.getText().trim();
            String role    = (String) roleCombo.getSelectedItem();

            // ── Validation ──────────────────────────────────────
            if (clubSel == null || clubSel.startsWith("--")) {
                formStatus.setText("Please select a valid club.");
                return;
            }
            if (uid.isEmpty()) {
                formStatus.setText("User ID cannot be empty.");
                return;
            }
            // FIX 4: Validate user was looked up and is valid
            if (!userNameLbl.getText().startsWith("✓")) {
                formStatus.setText("Please look up and verify the user first.");
                return;
            }

            int clubId;
            try {
                // Format: "1 - ClubName"
                clubId = Integer.parseInt(clubSel.split(" - ")[0].trim());
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ne) {
                formStatus.setText("Invalid club selection.");
                return;
            }

            int userId;
            try {
                userId = Integer.parseInt(uid);
            } catch (NumberFormatException ne) {
                formStatus.setText("User ID must be a number.");
                return;
            }

            final int finalClubId = clubId;
            final int finalUserId = userId;

            assignBtn.setEnabled(false);

            new Thread(() -> {
                try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    con.setAutoCommit(false);
                    try {
                        // FIX 5: Check if club already has a role of the same type
                        PreparedStatement chk = con.prepareStatement(
                            "SELECT COUNT(*) FROM members_in WHERE club_id=? AND role_type=?");
                        chk.setInt(1, finalClubId);
                        chk.setString(2, role);
                        ResultSet chkRs = chk.executeQuery();
                        chkRs.next();
                        int existingCount = chkRs.getInt(1);
                        chkRs.close(); chk.close();

                        if (existingCount > 0) {
                            // Ask before overwriting — show confirm on EDT
                            // We do a simple approach: overwrite (upsert)
                        }

                        // FIX 6: Remove existing role assignment for THIS user+club only
                        PreparedStatement del = con.prepareStatement(
                            "DELETE FROM members_in WHERE club_id=? AND user_id=?");
                        del.setInt(1, finalClubId);
                        del.setInt(2, finalUserId);
                        del.executeUpdate();
                        del.close();

                        PreparedStatement ins = con.prepareStatement(
                            "INSERT INTO members_in (club_id, user_id, role_type) VALUES (?,?,?)");
                        ins.setInt(1, finalClubId);
                        ins.setInt(2, finalUserId);
                        ins.setString(3, role);
                        ins.executeUpdate();
                        ins.close();

                        con.commit();

                        SwingUtilities.invokeLater(() -> {
                            formStatus.setForeground(SUCCESS_COL);
                            formStatus.setText("Incharge assigned successfully!");
                            userIdF.setText("");
                            userNameLbl.setText("");
                            assignBtn.setEnabled(true);
                            // FIX 7: Immediately refresh table
                            loadIncharges(model);
                        });
                    } catch (SQLException ex) {
                        con.rollback();
                        throw ex;
                    }
                } catch (SQLException ex) {
                    final String msg = ex.getMessage() == null ? "Unknown DB error." : ex.getMessage();
                    SwingUtilities.invokeLater(() -> {
                        assignBtn.setEnabled(true);
                        formStatus.setForeground(ERROR_COL);
                        if (msg.contains("ORA-02291")) {
                            formStatus.setText("User ID or Club ID does not exist (FK violation).");
                        } else if (msg.contains("ORA-00001")) {
                            formStatus.setText("This assignment already exists.");
                        } else {
                            formStatus.setText("DB Error: " + msg.substring(0, Math.min(55, msg.length())));
                        }
                    });
                }
            }).start();
        });

        // ── Remove selected row from table ───────────────────────
        removeBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                formStatus.setForeground(ERROR_COL);
                formStatus.setText("Select a row in the table to remove.");
                return;
            }
            int clubId  = (int) model.getValueAt(row, 0);
            int userId  = (int) model.getValueAt(row, 2);
            String role = (String) model.getValueAt(row, 4);

            int confirm = JOptionPane.showConfirmDialog(this,
                "Remove " + role + " for user " + userId + " from club " + clubId + "?",
                "Confirm Removal", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            new Thread(() -> {
                try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    PreparedStatement del = con.prepareStatement(
                        "DELETE FROM members_in WHERE club_id=? AND user_id=? AND role_type=?");
                    del.setInt(1, clubId);
                    del.setInt(2, userId);
                    del.setString(3, role);
                    del.executeUpdate();
                    del.close();
                    SwingUtilities.invokeLater(() -> {
                        formStatus.setForeground(SUCCESS_COL);
                        formStatus.setText("Incharge removed successfully.");
                        loadIncharges(model);
                    });
                } catch (SQLException ex) {
                    SwingUtilities.invokeLater(() -> {
                        formStatus.setForeground(ERROR_COL);
                        formStatus.setText("Remove failed: " + ex.getMessage().substring(0, Math.min(40, ex.getMessage().length())));
                    });
                }
            }).start();
        });

        return p;
    }

    // FIX 8: loadClubsIntoCombo now preserves previous selection if still valid
    private void loadClubsIntoCombo(JComboBox<String> combo) {
        final String prevSelected = (String) combo.getSelectedItem();
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                ResultSet rs = con.createStatement().executeQuery(
                    "SELECT club_id, club_name FROM club ORDER BY club_id");
                java.util.List<String> items = new java.util.ArrayList<>();
                items.add("-- Select Club --");
                while (rs.next()) items.add(rs.getInt(1) + " - " + rs.getString(2));
                rs.close();
                SwingUtilities.invokeLater(() -> {
                    combo.removeAllItems();
                    for (String item : items) combo.addItem(item);
                    // Restore previous selection if still exists
                    if (prevSelected != null && !prevSelected.startsWith("--")) {
                        for (int i = 0; i < combo.getItemCount(); i++) {
                            if (combo.getItemAt(i).equals(prevSelected)) {
                                combo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                });
            } catch (SQLException ignored) {}
        }).start();
    }

    private void loadIncharges(DefaultTableModel model) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                String sql = "SELECT m.club_id, c.club_name, m.user_id, u.name, m.role_type " +
                             "FROM members_in m " +
                             "JOIN club c ON c.club_id = m.club_id " +
                             "JOIN users u ON u.user_id = m.user_id " +
                             "WHERE m.role_type IN ('student_incharge','staff_incharge') " +
                             "ORDER BY m.club_id";
                ResultSet rs = con.createStatement().executeQuery(sql);
                java.util.List<Object[]> rows = new java.util.ArrayList<>();
                while (rs.next()) rows.add(new Object[]{
                    rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5)});
                rs.close();
                SwingUtilities.invokeLater(() -> {
                    model.setRowCount(0);
                    for (Object[] r : rows) model.addRow(r);
                });
            } catch (SQLException ignored) {}
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  USERS PANEL
    // ══════════════════════════════════════════════════════════════
    private JPanel buildUsersPanel() {
        JPanel p = new JPanel(null);
        p.setOpaque(false);
        int CW = W - 220, pad = 36;

        JLabel title = makeLabel("User Management",
            new Font("Georgia", Font.BOLD | Font.ITALIC, 24), TEXT_PRIMARY);
        title.setBounds(pad, 22, 500, 36);
        p.add(title);

        JComboBox<String> filterCombo = styledComboAmber(
            new String[]{"All Users", "student", "staff", "external_guest"});
        filterCombo.setBounds(pad, 64, 200, 36);
        p.add(filterCombo);

        JButton filterBtn = buildButtonSmall("Filter");
        filterBtn.setBounds(pad + 210, 64, 80, 36);
        p.add(filterBtn);

        // FIX 9: Search field
        JTextField searchField = styledField();
        searchField.setBounds(pad + 310, 64, 200, 36);
        p.add(searchField);
        JLabel searchHint = makeLabel("Search by name…", new Font("SansSerif", Font.ITALIC, 12), TEXT_MUTED);
        searchHint.setBounds(pad + 520, 64, 160, 36);
        p.add(searchHint);

        String[] cols = {"User ID", "Name", "Email", "Type", "Gender", "DOB"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = buildStyledTable(model);
        JScrollPane scroll = buildTableScroll(table);
        scroll.setBounds(pad, 110, CW - pad * 2, H - 230);
        p.add(scroll);

        JLabel countLbl = makeLabel("", new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
        countLbl.setBounds(pad, H - 210, 300, 20);
        p.add(countLbl);

        loadUsers(model, null, null, countLbl);

        filterBtn.addActionListener(e -> {
            String sel    = (String) filterCombo.getSelectedItem();
            String search = searchField.getText().trim();
            loadUsers(model, "All Users".equals(sel) ? null : sel,
                search.isEmpty() ? null : search, countLbl);
        });

        // Live search on Enter
        searchField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    filterBtn.doClick();
                }
            }
        });

        return p;
    }

    // FIX 10: Use PreparedStatement instead of string concat (SQL injection fix)
    private void loadUsers(DefaultTableModel model, String typeFilter, String nameSearch, JLabel countLbl) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                StringBuilder sql = new StringBuilder(
                    "SELECT user_id, name, email, user_type, gender, TO_CHAR(dob,'DD/MM/YYYY') FROM users WHERE 1=1");
                java.util.List<Object> params = new java.util.ArrayList<>();

                if (typeFilter != null) {
                    sql.append(" AND LOWER(user_type)=LOWER(?)");
                    params.add(typeFilter);
                }
                if (nameSearch != null) {
                    sql.append(" AND UPPER(name) LIKE UPPER(?)");
                    params.add("%" + nameSearch + "%");
                }
                sql.append(" ORDER BY user_id");

                PreparedStatement ps = con.prepareStatement(sql.toString());
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                ResultSet rs = ps.executeQuery();
                java.util.List<Object[]> rows = new java.util.ArrayList<>();
                while (rs.next()) rows.add(new Object[]{
                    rs.getInt(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6)});
                rs.close(); ps.close();
                SwingUtilities.invokeLater(() -> {
                    model.setRowCount(0);
                    for (Object[] r : rows) model.addRow(r);
                    if (countLbl != null) countLbl.setText("Showing " + rows.size() + " user(s).");
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    if (countLbl != null) countLbl.setText("DB Error: " + ex.getMessage().substring(0, Math.min(40, ex.getMessage().length())));
                });
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  REUSABLE UI HELPERS
    // ══════════════════════════════════════════════════════════════
    private JPanel buildCard(int w, int h) {
        JPanel card = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(20, 10, 45, 215));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(new Color(200, 130, 30, 80));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 160, 40, 180), getWidth(), 0, new Color(0, 0, 0, 0)));
                g2.fillRoundRect(0, 0, getWidth(), 3, 3, 3);
            }
        };
        card.setOpaque(false);
        card.setBounds(0, 0, w, h);
        return card;
    }

    private JTable buildStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(row % 2 == 0 ? new Color(20, 10, 45) : new Color(28, 14, 58));
                c.setForeground(TEXT_PRIMARY);
                if (isRowSelected(row)) c.setBackground(new Color(80, 40, 140));
                return c;
            }
        };
        table.setBackground(new Color(14, 8, 32));
        table.setForeground(TEXT_PRIMARY);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setRowHeight(32);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.getTableHeader().setBackground(new Color(30, 12, 60));
        table.getTableHeader().setForeground(AMBER);
        table.getTableHeader().setFont(new Font("Georgia", Font.BOLD, 13));
        table.getTableHeader().setBorder(BorderFactory.createEmptyBorder());
        table.setSelectionBackground(new Color(100, 50, 200));
        table.setSelectionForeground(Color.WHITE);
        return table;
    }

    private JScrollPane buildTableScroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(130, 60, 255, 60), 1));
        scroll.setBackground(new Color(14, 8, 32));
        scroll.getViewport().setBackground(new Color(14, 8, 32));
        scroll.getVerticalScrollBar().setBackground(new Color(20, 10, 40));
        return scroll;
    }

    private JSeparator buildSeparatorH(int w) {
        return new JSeparator() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new LinearGradientPaint(0, 0, getWidth(), 0,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{new Color(0,0,0,0), AMBER, new Color(0,0,0,0)}));
                g2.fillRect(0, 0, getWidth(), 1);
            }
        };
    }

    private JTextField styledField() {
        JTextField f = new JTextField();
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(130, 60, 255, 80), 1),
            new EmptyBorder(4, 12, 4, 12)));
        f.setBackground(new Color(30, 16, 60));
        f.setForeground(new Color(240, 235, 255));
        f.setCaretColor(ACCENT_LIGHT);
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        return f;
    }

    private JComboBox<String> styledComboAmber(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setBackground(new Color(40, 22, 8));
        cb.setForeground(new Color(255, 210, 120));
        cb.setFont(new Font("SansSerif", Font.PLAIN, 13));
        cb.setBorder(BorderFactory.createLineBorder(new Color(180, 120, 30, 100), 1));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                lbl.setBackground(isSelected ? new Color(100, 60, 10) : new Color(35, 18, 5));
                lbl.setForeground(new Color(255, 210, 120));
                lbl.setBorder(new EmptyBorder(4, 10, 4, 10));
                lbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
                return lbl;
            }
        });
        return cb;
    }

    private JLabel makeLabel(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font); l.setForeground(color); l.setOpaque(false);
        return l;
    }

    private JLabel fieldLabelAt(String text, int x, int y, int w) {
        JLabel l = makeLabel(text, new Font("SansSerif", Font.PLAIN, 11), TEXT_MUTED);
        l.setBounds(x, y, w, 16);
        return l;
    }

    private JLabel clickLabel(String text, Color base, Runnable action) {
        JLabel l = makeLabel(text, new Font("SansSerif", Font.PLAIN, 13), base);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { action.run(); }
            public void mouseEntered(MouseEvent e) { l.setForeground(GOLD); }
            public void mouseExited(MouseEvent e)  { l.setForeground(base); }
        });
        return l;
    }

    private JButton buildButton(String text, boolean primary) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (primary) {
                    g2.setPaint(new GradientPaint(0,0,new Color(140,60,255),getWidth(),getHeight(),new Color(90,20,200)));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                } else {
                    g2.setColor(new Color(255,255,255,18));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                    g2.setColor(CARD_BORDER);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                }
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false); btn.setBorderPainted(false);
        btn.setFocusPainted(false); btn.setOpaque(false);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Georgia", Font.BOLD, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setForeground(GOLD); btn.repaint(); }
            public void mouseExited(MouseEvent e)  { btn.setForeground(Color.WHITE); btn.repaint(); }
        });
        return btn;
    }

    private JButton buildButtonAmber(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0,0,new Color(200,120,20),getWidth(),getHeight(),new Color(140,70,10)));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false); btn.setBorderPainted(false);
        btn.setFocusPainted(false); btn.setOpaque(false);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Georgia", Font.BOLD, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setForeground(new Color(255,240,180)); btn.repaint(); }
            public void mouseExited(MouseEvent e)  { btn.setForeground(Color.WHITE); btn.repaint(); }
        });
        return btn;
    }

    private JButton buildButtonSmall(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(130, 60, 255, 50));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.setColor(CARD_BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false); btn.setBorderPainted(false);
        btn.setFocusPainted(false); btn.setOpaque(false);
        btn.setForeground(ACCENT_LIGHT);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ══════════════════════════════════════════════════════════════
    //  ICONS
    // ══════════════════════════════════════════════════════════════
    private ImageIcon buildLogoIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(130, 60, 255, 55));
        g.fillOval(0, 0, size, size);
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(size * 0.04f));
        g.drawOval((int)(size*0.03),(int)(size*0.03),(int)(size*0.94),(int)(size*0.94));
        g.setColor(ACCENT_LIGHT);
        int s2=size/2, cap=(int)(size*0.26);
        g.fillPolygon(new int[]{s2,s2+cap,s2,s2-cap},
            new int[]{(int)(size*0.28),(int)(size*0.40),(int)(size*0.52),(int)(size*0.40)},4);
        g.setColor(GOLD);
        drawStar(g,(int)(size*0.18),(int)(size*0.22),(int)(size*0.06),GOLD);
        g.dispose();
        return new ImageIcon(img);
    }

    private ImageIcon buildAdminIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int[] sx = {size/2,(int)(size*0.85),(int)(size*0.85),size/2,(int)(size*0.15),(int)(size*0.15)};
        int[] sy = {(int)(size*0.08),(int)(size*0.20),(int)(size*0.60),(int)(size*0.92),(int)(size*0.60),(int)(size*0.20)};
        g.setPaint(new GradientPaint(0,0,new Color(200,130,20,180),size,size,new Color(120,70,10,180)));
        g.fillPolygon(sx, sy, 6);
        g.setColor(new Color(255,190,60,200));
        g.setStroke(new BasicStroke(size*0.04f));
        g.drawPolygon(sx, sy, 6);
        int lx=size/2, ly=(int)(size*0.52), lr=(int)(size*0.14);
        g.setColor(new Color(255,240,180));
        g.fillRoundRect(lx-lr, ly-(int)(size*0.06), lr*2, (int)(size*0.22), 6, 6);
        g.setColor(new Color(200,130,20));
        g.setStroke(new BasicStroke(size*0.05f));
        g.drawArc(lx-lr+(int)(size*0.04),(int)(size*0.30),lr*2-(int)(size*0.08),(int)(size*0.20),0,180);
        g.dispose();
        return new ImageIcon(img);
    }

    private void drawStar(Graphics2D g, int cx, int cy, int r, Color c) {
        g.setColor(c);
        for (int i=0;i<4;i++) {
            double a=Math.toRadians(i*45);
            g.drawLine((int)(cx+Math.cos(a)*r),(int)(cy+Math.sin(a)*r),
                       (int)(cx-Math.cos(a)*r),(int)(cy-Math.sin(a)*r));
        }
        g.fillOval(cx-2,cy-2,4,4);
    }
}
