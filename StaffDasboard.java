package thread;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class StaffDashboard extends JFrame {

    private final String dbUrl, dbUser, dbPass;
    private final int    userId;
    private final String userName;
    private final boolean isIncharge;

    private static final Color BG_TOP       = new Color(8,   8,  18);
    private static final Color BG_BOT       = new Color(18,  8,  40);
    private static final Color ACCENT       = new Color(130, 60, 255);
    private static final Color ACCENT_LIGHT = new Color(180,120, 255);
    private static final Color GOLD         = new Color(255, 200,  60);
    private static final Color TEXT_PRIMARY = new Color(240, 235, 255);
    private static final Color TEXT_MUTED   = new Color(160, 150, 200);
    private static final Color CARD_BG      = new Color(255, 255, 255, 14);
    private static final Color CARD_BORDER  = new Color(130,  60, 255, 80);
    private static final Color SUCCESS_COL  = new Color( 80, 220, 120);
    private static final Color ERROR_COL    = new Color(255,  80,  80);
    private static final Color WARN_COL     = new Color(255, 180,  60);
    private static final Color SIDEBAR_BG   = new Color(10,   5,  28, 240);
    private static final Color SIDEBAR_ACT  = new Color(130,  60, 255, 160);

    private static final int SIDEBAR_W_PX = 220;
    private static final int TOP_H        = 56;

    private int     curPage  = 0;
    private int     tgtPage  = 0;
    private float   slideOff = 0f;
    private int     slideDir = 1;
    private javax.swing.Timer slideTimer;
    private boolean sliding  = false;

    private JPanel rootLayer, contentArea;
    private JPanel[] pages;
    private JButton[] sideItems;

    // Leave-request badge on sidebar button
    private JLabel leaveRequestBadge;

    private String staffName="", staffEmail="", staffGender="", staffAddress="";
    private String staffDOB="", staffAge="0", staffDesignation="", deptName="";

    private javax.swing.Timer autoRefreshTimer;

    // ── page count: incharge gets extra pages for Create Club Event,
    //   Create Staff Event, AND Leave Requests  (9 + 1 = 10)
    // non-incharge: 7 pages (unchanged)
    private static final int INCHARGE_PAGE_COUNT    = 10;
    private static final int NON_INCHARGE_PAGE_COUNT = 7;

    // ── page indices for incharge ──
    // 0 Overview | 1 MyClubs | 2 ManagedEvents | 3 CreateClubEvent
    // 4 CreateStaffEvent | 5 Participants | 6 Attendance | 7 Certificates
    // 8 LeaveRequests | (was 8 before, now 9 is unused / or MyAttendance if needed)
    // Note: non-incharge has MyAttendance at slot 6

    public StaffDashboard(int userId, String userName,
                          String dbUrl, String dbUser, String dbPass) {
        this(userId, userName, dbUrl, dbUser, dbPass, true);
    }

    public StaffDashboard(int userId, String userName,
                          String dbUrl, String dbUser, String dbPass,
                          boolean isIncharge) {
        this.userId     = userId;
        this.userName   = userName;
        this.dbUrl      = dbUrl;
        this.dbUser     = dbUser;
        this.dbPass     = dbPass;
        this.isIncharge = isIncharge;

        setTitle("Staff Dashboard — " + userName);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();

        rootLayer = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int W=getWidth(),H=getHeight();
                g2.setPaint(new GradientPaint(0,0,BG_TOP,W,H,BG_BOT)); g2.fillRect(0,0,W,H);
                g2.setPaint(new RadialGradientPaint(W/2f,H*0.3f,W*0.5f,new float[]{0f,1f},
                    new Color[]{new Color(100,40,220,40),new Color(0,0,0,0)}));
                g2.fillRect(0,0,W,H);
            }
        };
        rootLayer.setOpaque(true);
        setContentPane(rootLayer);

        buildTopBar(scr);
        buildSidebar(scr);

        int cW=scr.width-SIDEBAR_W_PX, cH=scr.height-TOP_H;
        contentArea=new JPanel(null); contentArea.setOpaque(false);
        contentArea.setBounds(SIDEBAR_W_PX,TOP_H,cW,cH);
        rootLayer.add(contentArea);

        int pageCount = isIncharge ? INCHARGE_PAGE_COUNT : NON_INCHARGE_PAGE_COUNT;
        pages=new JPanel[pageCount];
        for (int i=0;i<pageCount;i++){
            pages[i]=new JPanel(null); pages[i].setOpaque(false);
            pages[i].setBounds(i==0?0:cW,0,cW,cH);
            contentArea.add(pages[i]);
        }

        loadStaffData(()->{
            buildOverviewPage(scr);
            buildMyClubsPage(scr);
            buildManagedEventsPage(scr);

            if (isIncharge) {
                buildCreateClubEventPage(scr);   // page 3
                buildCreateStaffEventPage(scr);  // page 4
                buildParticipantsPage(scr);      // page 5
                buildAttendancePage(scr);        // page 6
                buildCertificatesPage(scr);      // page 7
                buildLeaveRequestsPage(scr);     // page 8  ← NEW
            } else {
                buildParticipantsPage(scr);      // page 3
                buildAttendancePage(scr);        // page 4
                buildCertificatesPage(scr);      // page 5
                buildMyAttendancePage(scr);      // page 6
            }

            startAutoRefresh();
        });

        setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════
    //  AUTO-REFRESH
    // ══════════════════════════════════════════════════════════════
    private void startAutoRefresh(){
        autoRefreshTimer = new javax.swing.Timer(10000, e -> {
            if (curPage == 1) refreshMyClubsPage();
            if (curPage == 2) refreshManagedEventsPage();
            int attendanceIdx = isIncharge ? 6 : 4;
            if (curPage == attendanceIdx) refreshAttendancePage();
            if (isIncharge && curPage == 8) refreshLeaveRequestsPage();
        });
        autoRefreshTimer.start();

        // poll pending count every 20 s to keep badge fresh
        if (isIncharge) {
            new javax.swing.Timer(20000, e -> updateLeaveRequestBadge()).start();
        }
    }

    private void refreshMyClubsPage()       { buildMyClubsPage(Toolkit.getDefaultToolkit().getScreenSize()); }
    private void refreshManagedEventsPage() { buildManagedEventsPage(Toolkit.getDefaultToolkit().getScreenSize()); }
    private void refreshAttendancePage()    { buildAttendancePage(Toolkit.getDefaultToolkit().getScreenSize()); }
    private void refreshLeaveRequestsPage() { buildLeaveRequestsPage(Toolkit.getDefaultToolkit().getScreenSize()); }

    // ══════════════════════════════════════════════════════════════
    //  TOP BAR
    // ══════════════════════════════════════════════════════════════
    private void buildTopBar(Dimension scr) {
        JPanel top=new JPanel(null){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setColor(new Color(8,4,22,235)); g2.fillRect(0,0,getWidth(),getHeight());
                g2.setPaint(new LinearGradientPaint(0,TOP_H-2,getWidth(),TOP_H-2,
                    new float[]{0f,0.5f,1f},
                    new Color[]{new Color(0,0,0,0),ACCENT,new Color(0,0,0,0)}));
                g2.fillRect(0,TOP_H-2,getWidth(),2);
            }
        };
        top.setOpaque(false); top.setBounds(0,0,scr.width,TOP_H);
        top.add(lblAt(new JLabel(buildLogoIcon(34)),16,(TOP_H-34)/2,34,34));
        top.add(lblAt(makeLabel("Campus Event System",new Font("Georgia",Font.BOLD,16),TEXT_PRIMARY),58,(TOP_H-20)/2,230,20));

        String roleLabel = isIncharge ? "Staff Incharge" : "Staff Member";
        top.add(lblAt(makeLabel("Welcome, "+userName+" ("+roleLabel+")",new Font("Georgia",Font.ITALIC,13),TEXT_MUTED),scr.width-420,(TOP_H-18)/2,290,18));

        JButton logout=buildButton("Logout",false);
        logout.setBounds(scr.width-126,(TOP_H-34)/2,110,34);
        logout.addActionListener(e->{
            if (autoRefreshTimer != null) autoRefreshTimer.stop();
            dispose();
            SwingUtilities.invokeLater(WelcomePage::new);
        });
        top.add(logout);
        rootLayer.add(top);
    }

    // ══════════════════════════════════════════════════════════════
    //  SIDEBAR
    // ══════════════════════════════════════════════════════════════
    private void buildSidebar(Dimension scr) {
        JPanel sidebar=new JPanel(null){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SIDEBAR_BG); g2.fillRect(0,0,getWidth(),getHeight());
                g2.setPaint(new LinearGradientPaint(SIDEBAR_W_PX-2,0,SIDEBAR_W_PX-2,getHeight(),
                    new float[]{0f,0.5f,1f},
                    new Color[]{new Color(0,0,0,0),ACCENT,new Color(0,0,0,0)}));
                g2.fillRect(SIDEBAR_W_PX-2,0,2,getHeight());
            }
        };
        sidebar.setOpaque(false);
        sidebar.setBounds(0,TOP_H,SIDEBAR_W_PX,scr.height-TOP_H);

        JPanel badge=avatarPanel(staffName,64);
        badge.setBounds(SIDEBAR_W_PX/2-32,20,64,64); sidebar.add(badge);
        JLabel uName=makeLabel(staffName,new Font("Georgia",Font.BOLD,14),TEXT_PRIMARY);
        uName.setHorizontalAlignment(SwingConstants.CENTER);
        uName.setBounds(0,90,SIDEBAR_W_PX,20); sidebar.add(uName);
        JLabel uRole=makeLabel(isIncharge?"Staff Incharge":"Staff Member",new Font("SansSerif",Font.PLAIN,11),ACCENT_LIGHT);
        uRole.setHorizontalAlignment(SwingConstants.CENTER);
        uRole.setBounds(0,112,SIDEBAR_W_PX,16); sidebar.add(uRole);
        JSeparator sepLine = buildSeparator();
        sepLine.setBounds(20,136,SIDEBAR_W_PX-40,2); sidebar.add(sepLine);

        String[][] menu = isIncharge
            ? new String[][]{
                {"\uD83C\uDFE0","Overview"},
                {"\uD83C\uDFEB","My Clubs"},
                {"\uD83D\uDCCB","Managed Events"},
                {"\uD83C\uDFAD","Create Club Event"},
                {"\uD83C\uDFDB","Create Staff Event"},
                {"\uD83D\uDC65","Participants"},
                {"\u270D","Mark Attendance"},
                {"\uD83C\uDFAB","Certificates"},
                {"\uD83D\uDCEC","Leave Requests"},
              }
            : new String[][]{
                {"\uD83C\uDFE0","Overview"},
                {"\uD83C\uDFEB","My Clubs"},
                {"\uD83D\uDCCB","Managed Events"},
                {"\uD83D\uDC65","Participants"},
                {"\u270D","Mark Attendance"},
                {"\uD83C\uDFAB","Certificates"},
                {"\uD83D\uDCC8","My Attendance"},
              };

        sideItems=new JButton[menu.length];
        int my=150;
        for (int i=0;i<menu.length;i++){
            final int idx=i;
            sideItems[i]=sidebarButton(menu[i][0]+" "+menu[i][1],i==0);
            sideItems[i].setBounds(12,my,SIDEBAR_W_PX-24,40);
            sideItems[i].addActionListener(e->navigateTo(idx));
            sidebar.add(sideItems[i]);

            // Attach badge label to the "Leave Requests" button (incharge, index 8)
            if (isIncharge && i == 8) {
                leaveRequestBadge = new JLabel("") {
                    @Override protected void paintComponent(Graphics g) {
                        if (getText().isEmpty()) return;
                        Graphics2D g2 = (Graphics2D)g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(255, 60, 60));
                        g2.fillOval(0, 0, getWidth()-1, getHeight()-1);
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                        FontMetrics fm = g2.getFontMetrics();
                        String t = getText();
                        g2.drawString(t, getWidth()/2 - fm.stringWidth(t)/2,
                            getHeight()/2 + fm.getAscent()/2 - 2);
                    }
                };
                leaveRequestBadge.setOpaque(false);
                leaveRequestBadge.setBounds(SIDEBAR_W_PX - 34, my + 10, 18, 18);
                sidebar.add(leaveRequestBadge);
                updateLeaveRequestBadge();
            }

            my+=46;
        }
        rootLayer.add(sidebar);
    }

    // ── Update the red badge count on the Leave Requests sidebar button ──
    private void updateLeaveRequestBadge() {
        if (!isIncharge || leaveRequestBadge == null) return;
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM club_leave_request clr " +
                    "JOIN club c ON clr.club_id = c.club_id " +
                    "JOIN members_in mi ON c.club_id = mi.club_id " +
                    "WHERE mi.user_id = ? AND mi.role_type = 'staff_incharge' " +
                    "AND clr.status = 'Pending'");
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                int count = rs.next() ? rs.getInt(1) : 0;
                SwingUtilities.invokeLater(() -> {
                    leaveRequestBadge.setText(count > 0 ? String.valueOf(count) : "");
                    leaveRequestBadge.repaint();
                });
            } catch (SQLException ignored) {}
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  NAVIGATION
    // ══════════════════════════════════════════════════════════════
    private void navigateTo(int page) {
        if (sliding||page==curPage) return;
        tgtPage=page; slideDir=(page>curPage)?1:-1; slideOff=0f; sliding=true;
        int cW=contentArea.getWidth();
        JPanel from=pages[curPage], to=pages[tgtPage];
        to.setBounds(slideDir*cW,0,cW,contentArea.getHeight());
        for (int i=0;i<sideItems.length;i++) setSideActive(sideItems[i],i==page);
        slideTimer=new javax.swing.Timer(12,e->{
            slideOff=Math.min(1f,slideOff+0.065f);
            float ease=easeInOut(slideOff);
            from.setBounds((int)(-slideDir*cW*ease),0,cW,contentArea.getHeight());
            to.setBounds((int)(slideDir*cW*(1f-ease)),0,cW,contentArea.getHeight());
            rootLayer.repaint();
            if (slideOff>=1f){
                ((javax.swing.Timer)e.getSource()).stop(); sliding=false; curPage=tgtPage;
                from.setBounds(slideDir*cW,0,cW,contentArea.getHeight());
            }
        });
        slideTimer.start();
    }
    private float easeInOut(float t){ return t<0.5f?2*t*t:(float)(-1+(4-2*t)*t); }

    // ══════════════════════════════════════════════════════════════
    //  LOAD STAFF DATA
    // ══════════════════════════════════════════════════════════════
    private void loadStaffData(Runnable onDone) {
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "SELECT u.name,u.email,u.gender,u.address,u.dob,s.designation,d.dept_name "+
                    "FROM users u JOIN staff s ON u.user_id=s.user_id "+
                    "JOIN department d ON s.dept_id=d.dept_id WHERE u.user_id=?");
                ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                if (rs.next()){
                    staffName=rs.getString("name"); staffEmail=rs.getString("email");
                    staffGender=rs.getString("gender"); staffAddress=rs.getString("address");
                    staffDesignation=rs.getString("designation"); deptName=rs.getString("dept_name");
                    java.sql.Date dob=rs.getDate("dob");
                    if (dob!=null){ staffDOB=dob.toString();
                        staffAge=String.valueOf(Period.between(dob.toLocalDate(),LocalDate.now()).getYears()); }
                }
            } catch (SQLException ex){ staffName=userName; }
            SwingUtilities.invokeLater(onDone);
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 0 — OVERVIEW
    // ══════════════════════════════════════════════════════════════
    private void buildOverviewPage(Dimension scr) {
        JPanel pg=pages[0]; int W=scr.width-SIDEBAR_W_PX, H=scr.height-TOP_H, cx=W/2;
        pg.removeAll();
        JPanel content=new JPanel(null); content.setOpaque(false);
        content.setPreferredSize(new Dimension(W,900));
        JScrollPane sc=styledScroll(content); sc.setBounds(0,0,W,H); pg.add(sc);

        JLabel ttl=makeLabel("Staff Overview",new Font("Georgia",Font.BOLD|Font.ITALIC,28),TEXT_PRIMARY);
        ttl.setHorizontalAlignment(SwingConstants.CENTER); ttl.setBounds(0,24,W,38); content.add(ttl);
        JSeparator sep=buildSeparator(); sep.setBounds(cx-W/5,68,W/5*2,2); content.add(sep);

        if (!isIncharge) {
            JPanel roleBanner = glassCard();
            roleBanner.setBounds(36, 80, W-72, 40); content.add(roleBanner);
            JLabel roleMsg = makeLabel(
                "ℹ  You are a General Staff Member — you can mark attendance, issue certificates and view events, but cannot create events.",
                new Font("SansSerif", Font.PLAIN, 12), new Color(255, 220, 100));
            roleMsg.setBounds(14, 10, W-100, 20); roleBanner.add(roleMsg);
        }

        int profileTopY = isIncharge ? 88 : 132;
        int pcW=340,pcH=380,pcX=36,pcY=profileTopY;
        JPanel pc=glassCard(); pc.setBounds(pcX,pcY,pcW,pcH); content.add(pc);
        JPanel av=avatarPanel(staffName,72); av.setBounds(pcW/2-36,20,72,72); pc.add(av);
        JLabel nm=makeLabel(staffName,new Font("Georgia",Font.BOLD,17),TEXT_PRIMARY);
        nm.setHorizontalAlignment(SwingConstants.CENTER); nm.setBounds(0,100,pcW,24); pc.add(nm);
        JLabel rb=roleBadge(staffDesignation.isEmpty()?"Staff":staffDesignation);
        rb.setBounds(pcW/2-60,128,120,22); pc.add(rb);
        String[][] rows={{"\uD83D\uDCE7",staffEmail},{"\u2640\u2642",staffGender},
            {"\uD83D\uDCC5","DOB: "+staffDOB+"  (Age "+staffAge+")"},
            {"\uD83C\uDFEB","Dept: "+deptName},{"\uD83D\uDCCD",staffAddress.isEmpty()?"—":staffAddress}};
        int ry=162;
        for (String[] r:rows){
            JLabel ic=new JLabel(r[0]); ic.setFont(new Font("Segoe UI Emoji",Font.PLAIN,13));
            ic.setBounds(18,ry,26,20); pc.add(ic);
            JLabel vl=makeLabel(r[1],new Font("SansSerif",Font.PLAIN,13),TEXT_MUTED);
            vl.setBounds(48,ry,pcW-60,20); pc.add(vl); ry+=30;
        }

        int dlX=pcX+pcW+28, dlY=pcY, dlW=(W-dlX-32)/2, dlH=120;
        JPanel dlCard=glassCard(); dlCard.setBounds(dlX,dlY,dlW,dlH); content.add(dlCard);
        JLabel dlTtl=makeLabel("⏰ Attendance Deadline",new Font("Georgia",Font.BOLD,14),GOLD);
        dlTtl.setBounds(14,10,dlW-28,20); dlCard.add(dlTtl);
        JLabel dlVal=makeLabel("Ends in: — (Check events)",new Font("SansSerif",Font.PLAIN,12),TEXT_MUTED);
        dlVal.setBounds(14,36,dlW-28,60); dlCard.add(dlVal);
        loadAttendanceDeadline(dlVal);

        JPanel certCard=glassCard(); certCard.setBounds(dlX+dlW+8,dlY,dlW,dlH); content.add(certCard);
        JLabel certTtl=makeLabel("📜 Certificate Deadline",new Font("Georgia",Font.BOLD,14),ACCENT_LIGHT);
        certTtl.setBounds(14,10,dlW-28,20); certCard.add(certTtl);
        JLabel certVal=makeLabel("Ends in: — (Check events)",new Font("SansSerif",Font.PLAIN,12),TEXT_MUTED);
        certVal.setBounds(14,36,dlW-28,60); certCard.add(certVal);
        loadCertificateDeadline(certVal);

        int scX=dlX, scY=dlY+dlH+16, scW=(W-scX-32)/3, scH=120, scGap=14;

        String[][] stats = isIncharge
            ? new String[][]{
                {"\uD83D\uDCCB","Events Managed","SELECT COUNT(*) FROM event_incharge WHERE user_id=?"},
                {"\uD83C\uDFEB","Clubs as Incharge","SELECT COUNT(*) FROM members_in WHERE user_id=? AND role_type='staff_incharge'"},
                {"\uD83C\uDFAB","Certificates Issued","SELECT COUNT(*) FROM certificate WHERE event_id IN (SELECT event_id FROM event_incharge WHERE user_id=?)"},
              }
            : new String[][]{
                {"\uD83C\uDFEB","My Clubs","SELECT COUNT(*) FROM members_in WHERE user_id=?"},
                {"\uD83C\uDFAB","Certificates Issued","SELECT COUNT(*) FROM certificate WHERE event_id IN (SELECT event_id FROM staff_event_registration WHERE user_id=?)"},
                {"\uD83D\uDCC8","Events Attended","SELECT COUNT(*) FROM student_attendance WHERE user_id=? AND attendance_status='Present'"},
              };

        for (int i=0;i<stats.length;i++){
            JPanel st=statCard(stats[i][0],stats[i][1],"0");
            st.setBounds(scX+i*(scW+scGap),scY,scW,scH); content.add(st);
            loadStatCount(stats[i][2],st);
        }

        // Pending leave requests mini-card for incharge
        if (isIncharge) {
            JPanel lrCard = glassCard();
            lrCard.setBounds(scX, scY + scH + 16, W - scX - 32, 70); content.add(lrCard);
            JLabel lrIcon = makeLabel("📨", new Font("Segoe UI Emoji", Font.PLAIN, 20), WARN_COL);
            lrIcon.setBounds(16, 20, 30, 30); lrCard.add(lrIcon);
            JLabel lrTxt = makeLabel("Pending leave requests from students — click 'Leave Requests' in the sidebar to review.",
                new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
            lrTxt.setBounds(54, 22, W - scX - 100, 26); lrCard.add(lrTxt);
            JButton lrBtn = buildButton("Go to Leave Requests →", true);
            lrBtn.setBounds(W - scX - 230, 18, 196, 34); lrCard.add(lrBtn);
            lrBtn.addActionListener(e -> navigateTo(8));
        }

        content.revalidate(); content.repaint();
    }

    private void loadAttendanceDeadline(JLabel lbl){
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "SELECT MIN(end_date + INTERVAL '10' MINUTE - SYSDATE) FROM staff_event "+
                    "WHERE organiser_id=? AND end_date + INTERVAL '10' MINUTE > SYSDATE");
                ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                if (rs.next() && rs.getObject(1) != null){
                    double daysLeft = rs.getDouble(1);
                    String display = String.format("Ends in: %.1f hours", daysLeft * 24);
                    SwingUtilities.invokeLater(()->lbl.setText(display));
                }
            } catch (SQLException ignored){}
        }).start();
    }

    private void loadCertificateDeadline(JLabel lbl){
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "SELECT MIN(end_date + INTERVAL '30' DAY - SYSDATE) FROM staff_event "+
                    "WHERE organiser_id=? AND end_date + INTERVAL '30' DAY > SYSDATE");
                ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                if (rs.next() && rs.getObject(1) != null){
                    double daysLeft = rs.getDouble(1);
                    String display = String.format("Ends in: %.1f days", daysLeft);
                    SwingUtilities.invokeLater(()->lbl.setText(display));
                }
            } catch (SQLException ignored){}
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 1 — MY CLUBS
    // ══════════════════════════════════════════════════════════════
    private void buildMyClubsPage(Dimension scr) {
        JPanel pg=pages[1]; int W=scr.width-SIDEBAR_W_PX, H=scr.height-TOP_H;
        pg.removeAll();
        pageTitle(pg,"My Clubs",W);

        JButton refreshBtn=buildButton("↻ Refresh",false);
        refreshBtn.setBounds(W-130,18,110,32);
        refreshBtn.addActionListener(e->refreshMyClubsPage());
        pg.add(refreshBtn);

        String[] c1={"Club ID","Club Name","Created Date","Total Members","My Role"};
        DefaultTableModel m1=noEditModel(c1); JTable t1=new JTable(m1); styleTable(t1);
        String[] c2={"Club","Member Name","Reg No","Year","Section","User Type","Role"};
        DefaultTableModel m2=noEditModel(c2); JTable t2=new JTable(m2); styleTable(t2);

        int half=(H-170)/2;
        pg.add(spAt(styledTableScroll(t1),24,72,W-48,half));
        pg.add(lblAt(makeLabel("Club Members (where I'm involved)",new Font("Georgia",Font.BOLD|Font.ITALIC,14),ACCENT_LIGHT),24,72+half+8,W-48,20));
        pg.add(spAt(styledTableScroll(t2),24,72+half+32,W-48,half-10));

        t1.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){
                int row=t1.rowAtPoint(e.getPoint());
                if (row>=0){
                    t1.setRowSelectionInterval(row,row);
                    int clubId=(int)m1.getValueAt(row,0);
                    loadClubMembers(clubId,m2);
                }
            }
        });

        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement p1=con.prepareStatement(
                    "SELECT c.club_id,c.club_name,c.created_date,"+
                    "(SELECT COUNT(*) FROM members_in m2 WHERE m2.club_id=c.club_id) AS tot,"+
                    "NVL((SELECT role_type FROM members_in WHERE club_id=c.club_id AND user_id=?),'—') AS role "+
                    "FROM club c WHERE c.club_id IN "+
                    "(SELECT club_id FROM members_in WHERE user_id=?) "+
                    "GROUP BY c.club_id,c.club_name,c.created_date ORDER BY c.club_name");
                p1.setInt(1,userId); p1.setInt(2,userId);
                ResultSet r1=p1.executeQuery();
                while (r1.next()){ Object[] row={r1.getInt(1),r1.getString(2),r1.getDate(3),r1.getInt(4),r1.getString(5)};
                    SwingUtilities.invokeLater(()->m1.addRow(row)); }
            } catch (SQLException ex){ SwingUtilities.invokeLater(()->
                m1.addRow(new Object[]{"—","Error","—","—","—"})); }
        }).start();
    }

    private void loadClubMembers(int clubId, DefaultTableModel model){
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement p2=con.prepareStatement(
                    "SELECT c.club_name,u.name,NVL(s.reg_num,'—'),NVL(TO_CHAR(s.year),'—'),"+
                    "NVL(s.section,'—'),u.user_type,mi.role_type "+
                    "FROM members_in mi JOIN club c ON mi.club_id=c.club_id "+
                    "JOIN users u ON mi.user_id=u.user_id LEFT JOIN student s ON mi.user_id=s.user_id "+
                    "WHERE c.club_id=? ORDER BY u.name");
                p2.setInt(1,clubId); ResultSet r2=p2.executeQuery();
                SwingUtilities.invokeLater(()->model.setRowCount(0));
                while (r2.next()){ Object[] row={r2.getString(1),r2.getString(2),r2.getString(3),
                    r2.getString(4),r2.getString(5),r2.getString(6),r2.getString(7)};
                    SwingUtilities.invokeLater(()->model.addRow(row)); }
            } catch (SQLException ex){}
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 2 — MANAGED EVENTS
    // ══════════════════════════════════════════════════════════════
    private void buildManagedEventsPage(Dimension scr) {
        JPanel pg=pages[2]; int W=scr.width-SIDEBAR_W_PX, H=scr.height-TOP_H;
        pg.removeAll();
        pageTitle(pg, isIncharge ? "Managed Events" : "My Events", W);

        if (!isIncharge) {
            JPanel infoBanner = glassCard(); infoBanner.setBounds(24, 66, W-48, 36); pg.add(infoBanner);
            JLabel infoMsg = makeLabel(
                "ℹ  Showing staff events you are registered for, and club events where you are the club incharge.",
                new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
            infoMsg.setBounds(14, 9, W-76, 18); infoBanner.add(infoMsg);
        }

        JButton refreshBtn=buildButton("↻ Refresh",false);
        refreshBtn.setBounds(W-130,18,110,32);
        refreshBtn.addActionListener(e->refreshManagedEventsPage());
        pg.add(refreshBtn);

        String[] cols={"Event ID","Title","Type","Club/Organiser","Start Date","End Date","Status","Registrations"};
        DefaultTableModel model=noEditModel(cols); JTable table=new JTable(model); styleTable(table);

        int tableY = isIncharge ? 72 : 112;
        int tableH = isIncharge ? H-100 : H-140;
        pg.add(spAt(styledTableScroll(table),24,tableY,W-48,tableH));

        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                if (isIncharge) {
                    PreparedStatement ps=con.prepareStatement(
                        "SELECT e.event_id,e.event_title,'Club Event' AS etype,c.club_name,"+
                        "e.start_date,e.end_date,"+
                        "CASE WHEN e.end_date < TRUNC(SYSDATE) THEN 'Completed' ELSE 'Ongoing' END AS status,"+
                        "(SELECT COUNT(*) FROM registers r WHERE r.event_id=e.event_id) AS regcnt "+
                        "FROM event_incharge ei JOIN event e ON ei.event_id=e.event_id "+
                        "JOIN club c ON e.club_id=c.club_id WHERE ei.user_id=? ORDER BY e.start_date DESC");
                    ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                    while (rs.next()){ Object[] row={rs.getInt(1),rs.getString(2),rs.getString(3),
                        rs.getString(4),rs.getDate(5),rs.getDate(6),rs.getString(7),rs.getInt(8)};
                        SwingUtilities.invokeLater(()->model.addRow(row)); }

                    PreparedStatement ps2=con.prepareStatement(
                        "SELECT se.event_id,se.event_title,se.event_type,'Staff Organised',"+
                        "se.start_date,se.end_date,"+
                        "CASE WHEN se.end_date < TRUNC(SYSDATE) THEN 'Completed' ELSE 'Ongoing' END AS status,"+
                        "(SELECT COUNT(*) FROM staff_event_registration ser WHERE ser.event_id=se.event_id) "+
                        "FROM staff_event se WHERE se.organiser_id=? ORDER BY se.start_date DESC");
                    ps2.setInt(1,userId); ResultSet rs2=ps2.executeQuery();
                    while (rs2.next()){ Object[] row={rs2.getInt(1),rs2.getString(2),rs2.getString(3),
                        rs2.getString(4),rs2.getDate(5),rs2.getDate(6),rs2.getString(7),rs2.getInt(8)};
                        SwingUtilities.invokeLater(()->model.addRow(row)); }
                } else {
                    PreparedStatement ps2=con.prepareStatement(
                        "SELECT se.event_id,se.event_title,se.event_type,'Staff Organised',"+
                        "se.start_date,se.end_date,"+
                        "CASE WHEN se.end_date < TRUNC(SYSDATE) THEN 'Completed' ELSE 'Ongoing' END AS status,"+
                        "(SELECT COUNT(*) FROM staff_event_registration ser WHERE ser.event_id=se.event_id) "+
                        "FROM staff_event se "+
                        "WHERE se.event_id IN (SELECT event_id FROM staff_event_registration WHERE user_id=?) "+
                        "OR se.event_id IN (SELECT event_id FROM student_attendance WHERE user_id=?) "+
                        "ORDER BY se.start_date DESC");
                    ps2.setInt(1,userId); ps2.setInt(2,userId);
                    ResultSet rs2=ps2.executeQuery();
                    while (rs2.next()){ Object[] row={rs2.getInt(1),rs2.getString(2),rs2.getString(3),
                        rs2.getString(4),rs2.getDate(5),rs2.getDate(6),rs2.getString(7),rs2.getInt(8)};
                        SwingUtilities.invokeLater(()->model.addRow(row)); }

                    PreparedStatement ps3=con.prepareStatement(
                        "SELECT e.event_id,e.event_title,'Club Event' AS etype,c.club_name,"+
                        "e.start_date,e.end_date,"+
                        "CASE WHEN e.end_date < TRUNC(SYSDATE) THEN 'Completed' ELSE 'Ongoing' END AS status,"+
                        "(SELECT COUNT(*) FROM registers r WHERE r.event_id=e.event_id) AS regcnt "+
                        "FROM event e JOIN club c ON e.club_id=c.club_id "+
                        "WHERE c.club_id IN (SELECT club_id FROM members_in WHERE user_id=? AND role_type='staff_incharge') "+
                        "ORDER BY e.start_date DESC");
                    ps3.setInt(1,userId); ResultSet rs3=ps3.executeQuery();
                    while (rs3.next()){ Object[] row={rs3.getInt(1),rs3.getString(2),rs3.getString(3),
                        rs3.getString(4),rs3.getDate(5),rs3.getDate(6),rs3.getString(7),rs3.getInt(8)};
                        SwingUtilities.invokeLater(()->model.addRow(row)); }
                }
            } catch (SQLException ex){ SwingUtilities.invokeLater(()->
                model.addRow(new Object[]{"—","Error: "+ex.getMessage().substring(0,Math.min(40,ex.getMessage().length())),"—","—","—","—","—","—"})); }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 3 (INCHARGE) — CREATE CLUB EVENT
    // ══════════════════════════════════════════════════════════════
    private void buildCreateClubEventPage(Dimension scr) {
        JPanel pg=pages[3]; int W=scr.width-SIDEBAR_W_PX, cx=W/2;
        pg.removeAll();
        pageTitle(pg,"Create Club Event",W);

        int cw=540, ch=480, cardX=cx-cw/2, cardY=70;
        JPanel card=glassCard(); card.setBounds(cardX,cardY,cw,ch); pg.add(card);

        int fx=36, fw=cw-72, fy=24;
        card.add(fldLbl("Event Title (max 50 chars)",fx,fy,fw)); fy+=18;
        JTextField f1=styledField(); f1.setBounds(fx,fy,fw,40); card.add(f1); fy+=52;

        card.add(fldLbl("Select Club (only where you are staff_incharge)",fx,fy,fw)); fy+=18;
        JComboBox<String> clubCombo=styledCombo(new String[]{"-- Loading clubs --"});
        clubCombo.setBounds(fx,fy,fw,40); card.add(clubCombo); fy+=52;
        loadStaffInchargeClubsCombo(clubCombo);

        card.add(fldLbl("Start Date (YYYY-MM-DD)",fx,fy,fw/2-8));
        card.add(fldLbl("End Date",fx+fw/2+8,fy,fw/2-8)); fy+=18;
        JTextField f3=styledField(); f3.setBounds(fx,fy,fw/2-8,40); card.add(f3);
        JTextField f4=styledField(); f4.setBounds(fx+fw/2+8,fy,fw/2-8,40); card.add(f4); fy+=52;

        card.add(fldLbl("Start Time (HH:MM)",fx,fy,fw/2-8));
        card.add(fldLbl("End Time",fx+fw/2+8,fy,fw/2-8)); fy+=18;
        JTextField f5=styledField(); f5.setBounds(fx,fy,fw/2-8,40); card.add(f5);
        JTextField f6=styledField(); f6.setBounds(fx+fw/2+8,fy,fw/2-8,40); card.add(f6); fy+=52;

        JLabel statusLbl=makeLabel("",new Font("SansSerif",Font.BOLD,12),ERROR_COL);
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);
        statusLbl.setBounds(0,fy,cw,20); card.add(statusLbl);

        JButton btn=buildButton("Create Club Event",true);
        btn.setBounds(fx,fy+28,fw,46); card.add(btn);

        btn.addActionListener(e->{
            String title=f1.getText().trim();
            String club=(String)clubCombo.getSelectedItem();
            String sd=f3.getText().trim(), ed=f4.getText().trim();
            String st=f5.getText().trim(), et=f6.getText().trim();
            if (title.isEmpty()){ statusLbl.setForeground(ERROR_COL); statusLbl.setText("Event title is required."); return; }
            if (club==null||club.startsWith("--")){ statusLbl.setForeground(ERROR_COL); statusLbl.setText("Select a club where you are staff_incharge."); return; }
            if (sd.isEmpty()||ed.isEmpty()||st.isEmpty()||et.isEmpty()){ statusLbl.setForeground(ERROR_COL); statusLbl.setText("Fill all date/time fields."); return; }
            try {
                int clubId=Integer.parseInt(club.split("\\|")[0].trim());
                createClubEvent(title,clubId,sd,ed,st,et,statusLbl,f1,f3,f4,f5,f6);
            } catch (Exception ex){ statusLbl.setForeground(ERROR_COL); statusLbl.setText("Error: "+ex.getMessage()); }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 4 (INCHARGE) — CREATE STAFF EVENT
    // ══════════════════════════════════════════════════════════════
    private void buildCreateStaffEventPage(Dimension scr) {
        JPanel pg=pages[4]; int W=scr.width-SIDEBAR_W_PX, cx=W/2;
        pg.removeAll();
        pageTitle(pg,"Create Staff Event",W);

        JPanel banner=glassCard(); banner.setBounds(24,66,W-48,44); pg.add(banner);
        JLabel bannerLbl=makeLabel(
            "Organise institution-level events. Both internal students and external guests can register.",
            new Font("SansSerif",Font.PLAIN,12),ACCENT_LIGHT);
        bannerLbl.setBounds(14,12,W-76,20); banner.add(bannerLbl);

        int cw=560, ch=620, cardX=cx-cw/2, cardY=118;
        JPanel card=glassCard(); card.setBounds(cardX,cardY,cw,ch); pg.add(card);

        int fx=36, fw=cw-72, fy=20;
        card.add(fldLbl("Event Title (max 100 chars)",fx,fy,fw)); fy+=18;
        JTextField fTitle=styledField(); fTitle.setBounds(fx,fy,fw,40); card.add(fTitle); fy+=52;

        card.add(fldLbl("Event Type (e.g., College Day, Symposium)",fx,fy,fw));
        JTextField typeField=styledField(); typeField.setBounds(fx,fy+18,fw,40); card.add(typeField); fy+=70;

        card.add(fldLbl("Venue",fx,fy,fw/2-8));
        JComboBox<String> venueCombo=styledCombo(new String[]{"-- Loading venues --"});
        venueCombo.setBounds(fx,fy+18,fw/2-8,40); card.add(venueCombo);
        loadVenueCombo(venueCombo); fy+=70;

        card.add(fldLbl("Start Date (YYYY-MM-DD)",fx,fy,fw/2-8));
        card.add(fldLbl("End Date (YYYY-MM-DD)",fx+fw/2+8,fy,fw/2-8)); fy+=18;
        JTextField fSd=styledField(); fSd.setBounds(fx,fy,fw/2-8,40); card.add(fSd);
        JTextField fEd=styledField(); fEd.setBounds(fx+fw/2+8,fy,fw/2-8,40); card.add(fEd); fy+=52;

        card.add(fldLbl("Start Time (HH:MM)",fx,fy,fw/2-8));
        card.add(fldLbl("End Time (HH:MM)",fx+fw/2+8,fy,fw/2-8)); fy+=18;
        JTextField fSt=styledField(); fSt.setBounds(fx,fy,fw/2-8,40); card.add(fSt);
        JTextField fEt=styledField(); fEt.setBounds(fx+fw/2+8,fy,fw/2-8,40); card.add(fEt); fy+=52;

        card.add(fldLbl("Description (optional, max 200 chars)",fx,fy,fw)); fy+=18;
        JTextArea fDesc=new JTextArea();
        fDesc.setBackground(new Color(30,16,60)); fDesc.setForeground(TEXT_PRIMARY);
        fDesc.setFont(new Font("SansSerif",Font.PLAIN,13)); fDesc.setLineWrap(true);
        fDesc.setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
        fDesc.setBounds(fx,fy,fw,60); card.add(fDesc); fy+=72;

        JLabel statusLbl=makeLabel("",new Font("SansSerif",Font.BOLD,12),ERROR_COL);
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);
        statusLbl.setBounds(0,fy,cw,20); card.add(statusLbl);

        JButton btn=buildButton("Create Staff Event",true);
        btn.setBounds(fx,fy+28,fw,46); card.add(btn);

        btn.addActionListener(e->{
            String title=fTitle.getText().trim(), evType=typeField.getText().trim();
            String venue=(String)venueCombo.getSelectedItem();
            String sd=fSd.getText().trim(), ed=fEd.getText().trim();
            String st=fSt.getText().trim(), et=fEt.getText().trim();
            String desc=fDesc.getText().trim();
            if (title.isEmpty()){ statusLbl.setForeground(ERROR_COL); statusLbl.setText("Event title is required."); return; }
            if (evType.isEmpty()){ statusLbl.setForeground(ERROR_COL); statusLbl.setText("Event type is required."); return; }
            if (sd.isEmpty()||ed.isEmpty()||st.isEmpty()||et.isEmpty()){ statusLbl.setForeground(ERROR_COL); statusLbl.setText("Fill all date/time fields."); return; }
            if (venue==null||venue.startsWith("--")){ statusLbl.setForeground(ERROR_COL); statusLbl.setText("Select a venue."); return; }
            try {
                int venueId=Integer.parseInt(venue.split("\\|")[0].trim());
                createStaffEvent(title,evType,venueId,sd,ed,st,et,desc,statusLbl,fTitle,typeField,fSd,fEd,fSt,fEt,fDesc);
            } catch (Exception ex){ statusLbl.setForeground(ERROR_COL); statusLbl.setText("Error: "+ex.getMessage()); }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  PARTICIPANTS PAGE
    // ══════════════════════════════════════════════════════════════
    private void buildParticipantsPage(Dimension scr) {
        int slot = isIncharge ? 5 : 3;
        JPanel pg=pages[slot]; int W=scr.width-SIDEBAR_W_PX, H=scr.height-TOP_H;
        pg.removeAll();
        pageTitle(pg,"Event Participants",W);

        // ── Row 1: Club filter (incharge only) ───────────────────
        int evRow = 62;
        if (isIncharge) {
            JLabel clubLbl=makeLabel("Select Club:",new Font("SansSerif",Font.PLAIN,12),TEXT_MUTED);
            clubLbl.setBounds(24,66,110,24); pg.add(clubLbl);
            JComboBox<String> clubCombo=styledCombo(new String[]{"-- Loading --"});
            clubCombo.setBounds(140,62,280,38); pg.add(clubCombo);

            JLabel evLbl=makeLabel("Select Event:",new Font("SansSerif",Font.PLAIN,12),TEXT_MUTED);
            evLbl.setBounds(434,66,110,24); pg.add(evLbl);
            JComboBox<String> evCombo=styledCombo(new String[]{"-- Select Club First --"});
            evCombo.setBounds(550,62,380,38); pg.add(evCombo);

            JLabel intLbl=makeLabel("Internal Participants (Students)",
                new Font("Georgia",Font.BOLD|Font.ITALIC,15),ACCENT_LIGHT);
            intLbl.setBounds(24,112,W-48,22); pg.add(intLbl);
            String[] c1={"User ID","Name","Reg No","Year","Section","Email","Reg Date","Status"};
            DefaultTableModel m1=noEditModel(c1); JTable t1=new JTable(m1); styleTable(t1);
            int tableH=(H-200)/2;
            pg.add(spAt(styledTableScroll(t1),24,138,W-48,tableH));
            JLabel extLbl=makeLabel("External Participants",
                new Font("Georgia",Font.BOLD|Font.ITALIC,15),GOLD);
            extLbl.setBounds(24,138+tableH+10,W-48,22); pg.add(extLbl);
            String[] c2={"User ID","Name","Organisation","Role Type","Phone","Reg Date"};
            DefaultTableModel m2=noEditModel(c2); JTable t2=new JTable(m2); styleTable(t2);
            pg.add(spAt(styledTableScroll(t2),24,138+tableH+36,W-48,tableH-20));

            loadStaffInchargeClubsCombo(clubCombo);

            clubCombo.addActionListener(e -> {
                String csel=(String)clubCombo.getSelectedItem();
                m1.setRowCount(0); m2.setRowCount(0);
                evCombo.removeAllItems();
                if (csel==null||csel.startsWith("--")) {
                    evCombo.addItem("-- Select Club First --"); return;
                }
                try {
                    int clubId=Integer.parseInt(csel.split("\\|")[0].trim());
                    loadEventsForClub(clubId, evCombo);
                } catch (Exception ex) { evCombo.addItem("-- Error --"); }
            });

            evCombo.addActionListener(e -> {
                String sel=(String)evCombo.getSelectedItem();
                if (sel==null||sel.startsWith("--")) return;
                try {
                    int evId=Integer.parseInt(sel.split("\\|")[0].trim());
                    m1.setRowCount(0); m2.setRowCount(0);
                    loadClubInternalParticipants(evId, m1);
                } catch (Exception ex){}
            });

        } else {
            // Non-incharge: original single event dropdown
            JLabel evLbl=makeLabel("Select Event:",new Font("SansSerif",Font.PLAIN,12),TEXT_MUTED);
            evLbl.setBounds(24,66,130,24); pg.add(evLbl);
            JComboBox<String> evCombo=styledCombo(new String[]{"-- Loading --"});
            evCombo.setBounds(160,62,400,38); pg.add(evCombo);
            JLabel intLbl=makeLabel("Internal Participants (Students)",
                new Font("Georgia",Font.BOLD|Font.ITALIC,15),ACCENT_LIGHT);
            intLbl.setBounds(24,112,W-48,22); pg.add(intLbl);
            String[] c1={"User ID","Name","Reg No","Year","Section","Email","Reg Date","Status"};
            DefaultTableModel m1=noEditModel(c1); JTable t1=new JTable(m1); styleTable(t1);
            int tableH=(H-200)/2;
            pg.add(spAt(styledTableScroll(t1),24,138,W-48,tableH));
            JLabel extLbl=makeLabel("External Participants (Guests — Staff Events Only)",
                new Font("Georgia",Font.BOLD|Font.ITALIC,15),GOLD);
            extLbl.setBounds(24,138+tableH+10,W-48,22); pg.add(extLbl);
            String[] c2={"User ID","Name","Organisation","Role Type","Phone","Reg Date"};
            DefaultTableModel m2=noEditModel(c2); JTable t2=new JTable(m2); styleTable(t2);
            pg.add(spAt(styledTableScroll(t2),24,138+tableH+36,W-48,tableH-20));
            boolean[] loading = {true};
            loadEventsCombo(evCombo, () -> { loading[0] = false; });
            evCombo.addActionListener(e -> {
                if (loading[0]) return;
                String sel=(String)evCombo.getSelectedItem();
                if (sel==null||sel.startsWith("--")) return;
                try {
                    int evId=Integer.parseInt(sel.split("\\|")[0].trim());
                    m1.setRowCount(0); m2.setRowCount(0);
                    if (sel.contains("[Club]")) {
                        loadClubInternalParticipants(evId, m1);
                    } else {
                        loadInternalParticipants(evId, m1);
                        loadExternalParticipants(evId, m2);
                    }
                } catch (Exception ex){}
            });
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE — MARK ATTENDANCE
    // ══════════════════════════════════════════════════════════════
    private void buildAttendancePage(Dimension scr) {
        int slot = isIncharge ? 6 : 4;
        JPanel pg = pages[slot];
        int W = scr.width - SIDEBAR_W_PX, H = scr.height - TOP_H;
        pg.removeAll();
        pageTitle(pg, "Mark Attendance", W);

        // ── Club filter (incharge only) ───────────────────────────
        JComboBox<String> evCombo;
        if (isIncharge) {
            JLabel clubLbl = makeLabel("Select Club:", new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
            clubLbl.setBounds(24, 66, 110, 24); pg.add(clubLbl);
            JComboBox<String> clubCombo = styledCombo(new String[]{"-- Loading --"});
            clubCombo.setBounds(140, 62, 260, 38); pg.add(clubCombo);

            JLabel evLbl = makeLabel("Select Event:", new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
            evLbl.setBounds(412, 66, 110, 24); pg.add(evLbl);
            evCombo = styledCombo(new String[]{"-- Select Club First --"});
            evCombo.setBounds(528, 62, 360, 38); pg.add(evCombo);

            loadStaffInchargeClubsCombo(clubCombo);
            JComboBox<String> finalEvCombo = evCombo;
            clubCombo.addActionListener(e -> {
                String csel = (String) clubCombo.getSelectedItem();
                finalEvCombo.removeAllItems();
                if (csel == null || csel.startsWith("--")) {
                    finalEvCombo.addItem("-- Select Club First --"); return;
                }
                try {
                    int clubId = Integer.parseInt(csel.split("\\|")[0].trim());
                    loadEventsForClub(clubId, finalEvCombo);
                } catch (Exception ex) { finalEvCombo.addItem("-- Error --"); }
            });
        } else {
            JLabel evLbl = makeLabel("Select Event:", new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
            evLbl.setBounds(24, 66, 130, 24); pg.add(evLbl);
            evCombo = styledCombo(new String[]{"-- Loading --"});
            evCombo.setBounds(160, 62, 440, 38); pg.add(evCombo);
        }

        JLabel counterLbl = makeLabel("", new Font("SansSerif", Font.BOLD, 13), TEXT_MUTED);
        counterLbl.setBounds(24, 106, W - 48, 22); pg.add(counterLbl);

        JButton selAllBtn   = buildButton("☑ All Present", false);
        JButton deselAllBtn = buildButton("☐ All Absent",  false);
        JButton refreshBtn  = buildButton("↻ Refresh",     false);
        selAllBtn  .setBounds(24,  134, 140, 34);
        deselAllBtn.setBounds(172, 134, 140, 34);
        refreshBtn .setBounds(320, 134, 120, 34);
        pg.add(selAllBtn); pg.add(deselAllBtn); pg.add(refreshBtn);

        JLabel infoLbl = makeLabel("Select a club and event to mark attendance",
            new Font("SansSerif", Font.BOLD, 12), TEXT_MUTED);
        infoLbl.setBounds(24, H - 80, W - 48, 70);
        infoLbl.setVerticalAlignment(SwingConstants.TOP);
        pg.add(infoLbl);

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"User ID","Name","Reg No","Year","Section","Email","Present ✓"}, 0) {
            @Override public Class<?> getColumnClass(int c) {
                return c == 6 ? Boolean.class : Object.class;
            }
            @Override public boolean isCellEditable(int r, int c) { return c == 6; }
            @Override public void setValueAt(Object v, int row, int col) {
                super.setValueAt(v, row, col);
                if (col == 6) updateCounter(this, counterLbl);
            }
        };

        JTable table = new JTable(model);
        styleTable(table);
        table.setRowHeight(36);

        table.getColumn("Present ✓").setCellRenderer(new TableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                JCheckBox cb = new JCheckBox();
                cb.setSelected(Boolean.TRUE.equals(v));
                cb.setHorizontalAlignment(SwingConstants.CENTER);
                cb.setBackground(sel ? new Color(100, 50, 200, 120)
                    : row % 2 == 0 ? new Color(12, 6, 30) : new Color(20, 10, 45));
                cb.setForeground(Boolean.TRUE.equals(v) ? SUCCESS_COL : ERROR_COL);
                cb.setOpaque(true);
                return cb;
            }
        });

        JCheckBox editorCb = new JCheckBox();
        editorCb.setHorizontalAlignment(SwingConstants.CENTER);
        editorCb.setBackground(new Color(20, 10, 45));
        editorCb.setOpaque(true);
        table.getColumn("Present ✓").setCellEditor(new DefaultCellEditor(editorCb) {
            @Override public Object getCellEditorValue() { return editorCb.isSelected(); }
            @Override public Component getTableCellEditorComponent(
                    JTable t, Object v, boolean sel, int row, int col) {
                editorCb.setSelected(Boolean.TRUE.equals(v));
                return editorCb;
            }
        });
        table.getColumn("Present ✓").setPreferredWidth(90);
        table.getColumn("User ID")  .setPreferredWidth(70);

        pg.add(spAt(styledTableScroll(table), 24, 174, W - 48, H - 300));

        JButton saveBtn = buildButton("💾 Save Attendance", true);
        saveBtn.setBounds(24, H - 130, 220, 48); pg.add(saveBtn);

        int[] currentEventId = {-1};

        if (!isIncharge) {
            boolean[] loading = {true};
            loadEventsCombo(evCombo, () -> { loading[0] = false; });
            evCombo.addActionListener(e -> {
                if (loading[0]) return;
                String sel = (String) evCombo.getSelectedItem();
                if (sel == null || sel.startsWith("--")) {
                    infoLbl.setForeground(TEXT_MUTED);
                    infoLbl.setText("Select an event to mark attendance");
                    model.setRowCount(0); counterLbl.setText(""); currentEventId[0] = -1; return;
                }
                stopEditing(table); model.setRowCount(0); counterLbl.setText("");
                try {
                    int evId = Integer.parseInt(sel.split("\\|")[0].trim());
                    currentEventId[0] = evId;
                    if (sel.contains("[Club]"))
                        loadClubParticipantsForAttendance(evId, model, infoLbl, counterLbl);
                    else
                        loadParticipantsForAttendance(evId, model, infoLbl, counterLbl);
                } catch (Exception ex) { infoLbl.setForeground(ERROR_COL); infoLbl.setText("Error: "+ex.getMessage()); }
            });
        } else {
            evCombo.addActionListener(e -> {
                String sel = (String) evCombo.getSelectedItem();
                if (sel == null || sel.startsWith("--")) {
                    infoLbl.setForeground(TEXT_MUTED);
                    infoLbl.setText("Select a club and event to mark attendance");
                    model.setRowCount(0); counterLbl.setText(""); currentEventId[0] = -1; return;
                }
                stopEditing(table); model.setRowCount(0); counterLbl.setText("");
                try {
                    int evId = Integer.parseInt(sel.split("\\|")[0].trim());
                    currentEventId[0] = evId;
                    loadClubParticipantsForAttendance(evId, model, infoLbl, counterLbl);
                } catch (Exception ex) { infoLbl.setForeground(ERROR_COL); infoLbl.setText("Error: "+ex.getMessage()); }
            });
        }

        selAllBtn.addActionListener(e -> {
            stopEditing(table);
            for (int i = 0; i < model.getRowCount(); i++) model.setValueAt(true, i, 6);
            table.repaint();
        });
        deselAllBtn.addActionListener(e -> {
            stopEditing(table);
            for (int i = 0; i < model.getRowCount(); i++) model.setValueAt(false, i, 6);
            table.repaint();
        });
        refreshBtn.addActionListener(e -> {
            if (currentEventId[0] < 0) return;
            stopEditing(table); model.setRowCount(0); counterLbl.setText("");
            loadClubParticipantsForAttendance(currentEventId[0], model, infoLbl, counterLbl);
        });

        saveBtn.addActionListener(e -> {
            if (currentEventId[0] < 0) {
                infoLbl.setForeground(ERROR_COL); infoLbl.setText("Select an event first."); return;
            }
            stopEditing(table);
            if (model.getRowCount() == 0) {
                infoLbl.setForeground(ERROR_COL); infoLbl.setText("No participants loaded — cannot save."); return;
            }
            Map<Integer, String> attendanceMap = new LinkedHashMap<>();
            for (int i = 0; i < model.getRowCount(); i++) {
                int uid = (int) model.getValueAt(i, 0);
                boolean present = Boolean.TRUE.equals(model.getValueAt(i, 6));
                attendanceMap.put(uid, present ? "Present" : "Absent");
            }
            saveAttendance(currentEventId[0], attendanceMap, infoLbl, counterLbl, model);
        });
    }

    private void stopEditing(JTable table) {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
    }

    private void updateCounter(DefaultTableModel model, JLabel counterLbl) {
        int present = 0, absent = 0;
        for (int i = 0; i < model.getRowCount(); i++) {
            if (Boolean.TRUE.equals(model.getValueAt(i, 6))) present++;
            else absent++;
        }
        int total = model.getRowCount();
        counterLbl.setForeground(present > 0 ? SUCCESS_COL : TEXT_MUTED);
        counterLbl.setText(present + " Present   " + absent + " Absent   of " + total + " total");
    }

    private void loadParticipantsForAttendance(int eventId, DefaultTableModel model,
                                            JLabel infoLbl, JLabel counterLbl) {
    new Thread(() -> {
        try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {

            // Verify this staff member is the organiser of this staff event
            PreparedStatement verify = con.prepareStatement(
                "SELECT COUNT(*) FROM staff_event WHERE event_id = ? AND organiser_id = ?");
            verify.setInt(1, eventId); verify.setInt(2, userId);
            ResultSet vRs = verify.executeQuery();
            if (!vRs.next() || vRs.getInt(1) == 0) {
                SwingUtilities.invokeLater(() -> {
                    infoLbl.setForeground(ERROR_COL);
                    infoLbl.setText("Access denied: you are not the organiser of this event.");
                });
                return;
            }

            PreparedStatement ps = con.prepareStatement(
                "SELECT u.user_id, u.name, " +
                "NVL(s.reg_num,'—'), NVL(TO_CHAR(s.year),'—'), NVL(s.section,'—'), u.email, " +
                "NVL((SELECT sa.attendance_status FROM student_attendance sa " +
                "     WHERE sa.event_id=? AND sa.user_id=u.user_id), 'Not Marked') " +
                "FROM staff_event_registration ser " +
                "JOIN users u ON ser.user_id=u.user_id " +
                "LEFT JOIN student s ON ser.user_id=s.user_id " +
                "WHERE ser.event_id=? ORDER BY u.name");
            ps.setInt(1, eventId); ps.setInt(2, eventId);
            ResultSet rs = ps.executeQuery();

            java.util.List<Object[]> rows = new java.util.ArrayList<>();
            while (rs.next()) {
                boolean present = "Present".equalsIgnoreCase(rs.getString(7));
                rows.add(new Object[]{ rs.getInt(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), present });
            }

            SwingUtilities.invokeLater(() -> {
                for (Object[] row : rows) model.addRow(row);
                updateCounter(model, counterLbl);
                if (rows.isEmpty()) {
                    infoLbl.setForeground(new Color(255, 160, 60));
                    infoLbl.setText("⚠ No registrations found for this event.");
                } else {
                    infoLbl.setForeground(SUCCESS_COL);
                    infoLbl.setText("✅ " + rows.size() + " participant(s) loaded — tick = Present, untick = Absent, then Save.");
                }
            });
        } catch (SQLException ex) {
            SwingUtilities.invokeLater(() -> {
                infoLbl.setForeground(ERROR_COL);
                infoLbl.setText("DB Error: " + ex.getMessage());
            });
        }
    }).start();
}

    private void loadClubParticipantsForAttendance(int eventId, DefaultTableModel model,
                                                    JLabel infoLbl, JLabel counterLbl) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                PreparedStatement verify = con.prepareStatement(
                    "SELECT COUNT(*) FROM event e " +
                    "JOIN members_in mi ON e.club_id = mi.club_id " +
                    "WHERE e.event_id=? AND mi.user_id=? AND mi.role_type='staff_incharge'");
                verify.setInt(1, eventId); verify.setInt(2, userId);
                ResultSet vRs = verify.executeQuery();
                if (!vRs.next() || vRs.getInt(1) == 0) {
                    SwingUtilities.invokeLater(() -> {
                        infoLbl.setForeground(ERROR_COL);
                        infoLbl.setText("Access denied: you are not the club incharge for this event.");
                    });
                    return;
                }
                PreparedStatement ps = con.prepareStatement(
                    "SELECT u.user_id, u.name, " +
                    "NVL(s.reg_num,'—'), NVL(TO_CHAR(s.year),'—'), NVL(s.section,'—'), u.email, " +
                    "NVL((SELECT sa.attendance_status FROM student_attendance sa " +
                    "     WHERE sa.event_id=? AND sa.user_id=u.user_id), 'Not Marked') " +
                    "FROM registers r " +
                    "JOIN users u ON r.user_id=u.user_id " +
                    "LEFT JOIN student s ON r.user_id=s.user_id " +
                    "WHERE r.event_id=? ORDER BY u.name");
                ps.setInt(1, eventId); ps.setInt(2, eventId);
                ResultSet rs = ps.executeQuery();

                java.util.List<Object[]> rows = new java.util.ArrayList<>();
                while (rs.next()) {
                    boolean present = "Present".equalsIgnoreCase(rs.getString(7));
                    rows.add(new Object[]{ rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), present });
                }

                SwingUtilities.invokeLater(() -> {
                    for (Object[] row : rows) model.addRow(row);
                    updateCounter(model, counterLbl);
                    if (rows.isEmpty()) {
                        infoLbl.setForeground(new Color(255, 160, 60));
                        infoLbl.setText("⚠ No students registered for this club event yet.");
                    } else {
                        infoLbl.setForeground(SUCCESS_COL);
                        infoLbl.setText("✅ " + rows.size() + " student(s) loaded — tick = Present, untick = Absent, then Save.");
                    }
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    infoLbl.setForeground(ERROR_COL);
                    infoLbl.setText("DB Error: " + ex.getMessage());
                });
            }
        }).start();
    }

    private void saveAttendance(int eventId,
                                 Map<Integer, String> attendanceMap,
                                 JLabel infoLbl,
                                 JLabel counterLbl,
                                 DefaultTableModel model) {
        new Thread(() -> {
            Connection con = null;
            try {
                con = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                con.setAutoCommit(false);

                String mergeSql =
                    "MERGE INTO student_attendance sa " +
                    "USING dual " +
                    "ON (sa.event_id = ? AND sa.user_id = ?) " +
                    "WHEN MATCHED THEN " +
                    "  UPDATE SET sa.attendance_status = ?, " +
                    "    sa.attendance_date = TRUNC(SYSDATE), " +
                    "    sa.attendance_time = SYSTIMESTAMP " +
                    "WHEN NOT MATCHED THEN " +
                    "  INSERT (event_id, user_id, attendance_date, attendance_time, attendance_status) " +
                    "  VALUES (?, ?, TRUNC(SYSDATE), SYSTIMESTAMP, ?)";

                int saved = 0, present = 0;
                for (Map.Entry<Integer, String> entry : attendanceMap.entrySet()) {
                    int uid = entry.getKey();
                    String status = entry.getValue();
                    PreparedStatement merge = con.prepareStatement(mergeSql);
                    merge.setInt(1, eventId);
                    merge.setInt(2, uid);
                    merge.setString(3, status);
                    merge.setInt(4, eventId);
                    merge.setInt(5, uid);
                    merge.setString(6, status);
                    merge.executeUpdate();
                    merge.close();
                    saved++;
                    if ("Present".equalsIgnoreCase(status)) present++;
                }
                con.commit();

                final int fs = saved, fp = present;
                SwingUtilities.invokeLater(() -> {
                    infoLbl.setForeground(SUCCESS_COL);
                    infoLbl.setText("✅ Saved!  " + fp + " Present  " + (fs - fp) +
                        " Absent  — reflected in Student Dashboard.");
                    updateCounter(model, counterLbl);
                });

            } catch (SQLException ex) {
                if (con != null) {
                    try { con.rollback(); } catch (SQLException ignored) {}
                }
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    infoLbl.setForeground(ERROR_COL);
                    String msg = ex.getMessage() == null ? "DB error" : ex.getMessage();
                    infoLbl.setText("Error: " + msg);
                });
            } finally {
                if (con != null) {
                    try { con.setAutoCommit(true); con.close(); } catch (SQLException ignored) {}
                }
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  CERTIFICATES PAGE
    // ══════════════════════════════════════════════════════════════
    private void buildCertificatesPage(Dimension scr) {
        int slot = isIncharge ? 7 : 5;
        JPanel pg = pages[slot];
        int W = scr.width - SIDEBAR_W_PX, H = scr.height - TOP_H;
        pg.removeAll();
        pageTitle(pg, "Issue Certificates", W);

        // ── Club filter (incharge only) ───────────────────────────
        JComboBox<String> evCombo;
        if (isIncharge) {
            JLabel clubLbl = makeLabel("Select Club:", new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
            clubLbl.setBounds(24, 66, 110, 24); pg.add(clubLbl);
            JComboBox<String> clubCombo = styledCombo(new String[]{"-- Loading --"});
            clubCombo.setBounds(140, 62, 240, 38); pg.add(clubCombo);

            JLabel evLbl = makeLabel("Select Event:", new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
            evLbl.setBounds(392, 66, 110, 24); pg.add(evLbl);
            evCombo = styledCombo(new String[]{"-- Select Club First --"});
            evCombo.setBounds(508, 62, 320, 38); pg.add(evCombo);

            loadStaffInchargeClubsCombo(clubCombo);
            JComboBox<String> finalEvCombo = evCombo;
            clubCombo.addActionListener(e -> {
                String csel = (String) clubCombo.getSelectedItem();
                finalEvCombo.removeAllItems();
                if (csel == null || csel.startsWith("--")) {
                    finalEvCombo.addItem("-- Select Club First --"); return;
                }
                try {
                    int clubId = Integer.parseInt(csel.split("\\|")[0].trim());
                    loadEventsForClub(clubId, finalEvCombo);
                } catch (Exception ex) { finalEvCombo.addItem("-- Error --"); }
            });
        } else {
            JLabel evLbl = makeLabel("Select Event:", new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
            evLbl.setBounds(24, 66, 130, 24); pg.add(evLbl);
            evCombo = styledCombo(new String[]{"-- Loading --"});
            evCombo.setBounds(160, 62, 400, 38); pg.add(evCombo);
        }

        JLabel typeLbl = makeLabel("Certificate Type:", new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
        typeLbl.setBounds(W - 320, 66, 130, 24); pg.add(typeLbl);
        JComboBox<String> typeCombo = styledCombo(new String[]{"Participant", "Winner", "Runner-up"});
        typeCombo.setBounds(W - 184, 62, 160, 38); pg.add(typeCombo);

        JButton selAllBtn = buildButton("☑ Select All", false);
        selAllBtn.setBounds(24, 108, 140, 34); pg.add(selAllBtn);
        JButton deselAllBtn = buildButton("☐ Deselect All", false);
        deselAllBtn.setBounds(172, 108, 150, 34); pg.add(deselAllBtn);
        JButton selEligibleBtn = buildButton("☑ Select Eligible", false);
        selEligibleBtn.setBounds(330, 108, 160, 34); pg.add(selEligibleBtn);

        String[] cols = {"User ID","Name","Reg No","Year","Section","Email","Attendance","Issue ✓"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public Class<?> getColumnClass(int c) {
                return c == 7 ? Boolean.class : Object.class;
            }
            @Override public boolean isCellEditable(int r, int c) { return c == 7; }
        };
        JTable table = new JTable(model);
        styleTable(table);
        table.setRowHeight(36);

        table.getColumn("Attendance").setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                String s = v == null ? "" : v.toString();
                lbl.setForeground("Present".equalsIgnoreCase(s) ? SUCCESS_COL
                    : "Absent".equalsIgnoreCase(s) ? ERROR_COL : new Color(255, 180, 60));
                lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
                lbl.setBackground(sel ? new Color(100, 50, 200, 120)
                    : row % 2 == 0 ? new Color(12, 6, 30) : new Color(20, 10, 45));
                lbl.setOpaque(true); lbl.setBorder(new EmptyBorder(0, 12, 0, 12)); return lbl;
            }
        });

        table.getColumn("Issue ✓").setCellRenderer(new TableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                JCheckBox cb = new JCheckBox();
                cb.setSelected(Boolean.TRUE.equals(v));
                cb.setHorizontalAlignment(SwingConstants.CENTER);
                cb.setBackground(sel ? new Color(100, 50, 200, 120)
                    : row % 2 == 0 ? new Color(12, 6, 30) : new Color(20, 10, 45));
                cb.setForeground(GOLD); cb.setOpaque(true); return cb;
            }
        });

        JCheckBox certEditorCb = new JCheckBox();
        certEditorCb.setHorizontalAlignment(SwingConstants.CENTER);
        certEditorCb.setBackground(new Color(20, 10, 45));
        certEditorCb.setForeground(GOLD);
        certEditorCb.setOpaque(true);
        table.getColumn("Issue ✓").setCellEditor(new DefaultCellEditor(certEditorCb));
        table.getColumn("Issue ✓").setPreferredWidth(70);
        table.getColumn("User ID").setPreferredWidth(70);

        final int CONTROLS_BOTTOM = 150;
        final int BTN_H = 46;
        final int INFO_H = 40;
        int tableAreaH = H - 320;
        int INFO_Y = CONTROLS_BOTTOM + tableAreaH + 10;
        int BTN_Y = INFO_Y + INFO_H + 5;

        pg.add(spAt(styledTableScroll(table), 24, CONTROLS_BOTTOM, W - 48, tableAreaH));

        JLabel infoLbl = makeLabel("Select a club and event to view participants",
            new Font("SansSerif", Font.BOLD, 12), TEXT_MUTED);
        infoLbl.setBounds(24, INFO_Y, W - 48, INFO_H);
        infoLbl.setVerticalAlignment(SwingConstants.TOP);
        pg.add(infoLbl);

        JButton issueBtn = buildButton("🎓 Issue Selected Certificates", true);
        issueBtn.setBounds(W - 360, BTN_Y, 336, BTN_H);
        pg.add(issueBtn);

        selAllBtn.addActionListener(e -> {
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            for (int i = 0; i < model.getRowCount(); i++) model.setValueAt(true, i, 7);
        });
        deselAllBtn.addActionListener(e -> {
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            for (int i = 0; i < model.getRowCount(); i++) model.setValueAt(false, i, 7);
        });
        selEligibleBtn.addActionListener(e -> {
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            int selected = 0;
            for (int i = 0; i < model.getRowCount(); i++) {
                Object attVal = model.getValueAt(i, 6);
                boolean present = "Present".equalsIgnoreCase(attVal == null ? "" : attVal.toString());
                model.setValueAt(present, i, 7);
                if (present) selected++;
            }
            infoLbl.setForeground(selected > 0 ? SUCCESS_COL : new Color(255, 160, 60));
            infoLbl.setText(selected > 0
                ? "✓ " + selected + " eligible student(s) selected — click Issue to confirm."
                : "⚠ No Present students found. Mark attendance first.");
        });

        int[] currentEvId = {-1};
        boolean[] isClubArr = {false};

        if (!isIncharge) {
            boolean[] loading = {true};
            loadEventsCombo(evCombo, () -> { loading[0] = false; });
            evCombo.addActionListener(e -> {
                if (loading[0]) return;
                String sel = (String) evCombo.getSelectedItem();
                if (sel == null || sel.startsWith("--")) {
                    model.setRowCount(0); currentEvId[0] = -1;
                    infoLbl.setForeground(TEXT_MUTED);
                    infoLbl.setText("Select an event to view participants"); return;
                }
                try {
                    int evId = Integer.parseInt(sel.split("\\|")[0].trim());
                    currentEvId[0] = evId; isClubArr[0] = sel.contains("[Club]");
                    model.setRowCount(0);
                    infoLbl.setForeground(TEXT_MUTED); infoLbl.setText("Loading participants…");
                    loadCertificateParticipants(evId, isClubArr[0], model, infoLbl);
                } catch (Exception ex) { infoLbl.setForeground(ERROR_COL); infoLbl.setText("Error: "+ex.getMessage()); }
            });
        } else {
            evCombo.addActionListener(e -> {
                String sel = (String) evCombo.getSelectedItem();
                if (sel == null || sel.startsWith("--")) {
                    model.setRowCount(0); currentEvId[0] = -1;
                    infoLbl.setForeground(TEXT_MUTED);
                    infoLbl.setText("Select a club and event to view participants"); return;
                }
                try {
                    int evId = Integer.parseInt(sel.split("\\|")[0].trim());
                    currentEvId[0] = evId; isClubArr[0] = true;
                    model.setRowCount(0);
                    infoLbl.setForeground(TEXT_MUTED); infoLbl.setText("Loading participants…");
                    loadCertificateParticipants(evId, true, model, infoLbl);
                } catch (Exception ex) { infoLbl.setForeground(ERROR_COL); infoLbl.setText("Error: "+ex.getMessage()); }
            });
        }

        issueBtn.addActionListener(e -> {
            if (currentEvId[0] < 0) {
                infoLbl.setForeground(ERROR_COL); infoLbl.setText("Select an event first."); return;
            }
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            String certType = (String) typeCombo.getSelectedItem();
            java.util.List<Integer> selected = new java.util.ArrayList<>();
            for (int i = 0; i < model.getRowCount(); i++)
                if (Boolean.TRUE.equals(model.getValueAt(i, 7)))
                    selected.add((Integer) model.getValueAt(i, 0));
            if (selected.isEmpty()) {
                infoLbl.setForeground(ERROR_COL); infoLbl.setText("Tick at least one student in the 'Issue ✓' column."); return;
            }
            infoLbl.setForeground(TEXT_MUTED); infoLbl.setText("Issuing " + selected.size() + " certificate(s)…");
            issueBtn.setEnabled(false);
            issueCertificates(currentEvId[0], selected, certType, infoLbl, issueBtn);
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  MY ATTENDANCE PAGE
    // ══════════════════════════════════════════════════════════════
    private void buildMyAttendancePage(Dimension scr) {
        int slot = isIncharge ? 8 : 6;
        JPanel pg = pages[slot];
        int W = scr.width - SIDEBAR_W_PX, H = scr.height - TOP_H;
        pg.removeAll();
        pageTitle(pg, "My Attendance", W);

        String[] cols = {"Date","Event Name","Type","Status"};
        DefaultTableModel model = noEditModel(cols);
        JTable table = new JTable(model);
        styleTable(table);
        table.setRowHeight(36);

        table.getColumn("Status").setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                String s = v == null ? "" : v.toString();
                if ("Present".equalsIgnoreCase(s)) {
                    lbl.setForeground(SUCCESS_COL); lbl.setText("✓  Present");
                } else if ("Absent".equalsIgnoreCase(s)) {
                    lbl.setForeground(ERROR_COL); lbl.setText("✗  Absent");
                } else {
                    lbl.setForeground(new Color(255,180,60)); lbl.setText("—  "+s);
                }
                lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
                lbl.setBackground(sel ? new Color(100,50,200,120)
                    : row%2==0 ? new Color(12,6,30) : new Color(20,10,45));
                lbl.setOpaque(true); lbl.setBorder(new EmptyBorder(0,12,0,12));
                return lbl;
            }
        });

        table.getColumn("Status")    .setPreferredWidth(120);
        table.getColumn("Event Name").setPreferredWidth(340);
        table.getColumn("Type")      .setPreferredWidth(160);
        table.getColumn("Date")      .setPreferredWidth(110);

        JButton refreshBtn = buildButton("↻ Refresh", false);
        refreshBtn.setBounds(W-130, 18, 110, 32); pg.add(refreshBtn);

        pg.add(spAt(styledTableScroll(table), 24, 72, W-48, H-100));

        Runnable loader = () -> {
            model.setRowCount(0);
            new Thread(() -> {
                try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                    PreparedStatement ps = con.prepareStatement(
                        "SELECT sa.attendance_date, se.event_title, se.event_type, sa.attendance_status " +
                        "FROM student_attendance sa " +
                        "JOIN staff_event se ON sa.event_id = se.event_id " +
                        "WHERE sa.user_id = ? " +
                        "UNION ALL " +
                        "SELECT sa.attendance_date, e.event_title, 'Club Event', sa.attendance_status " +
                        "FROM student_attendance sa " +
                        "JOIN event e ON sa.event_id = e.event_id " +
                        "WHERE sa.user_id = ? " +
                        "ORDER BY 1 DESC");
                    ps.setInt(1, userId); ps.setInt(2, userId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        Object[] row = { rs.getDate(1), rs.getString(2), rs.getString(3), rs.getString(4) };
                        SwingUtilities.invokeLater(() -> model.addRow(row));
                    }
                } catch (SQLException ex) {
                    SwingUtilities.invokeLater(() ->
                        model.addRow(new Object[]{"—", "Error: "+ex.getMessage(), "—", "—"}));
                }
            }).start();
        };
        loader.run();
        refreshBtn.addActionListener(e -> loader.run());
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 8 (INCHARGE ONLY) — LEAVE REQUESTS  ← NEW
    // ══════════════════════════════════════════════════════════════
    private void buildLeaveRequestsPage(Dimension scr) {
        int slot = 8; // incharge only
        JPanel pg = pages[slot];
        int W = scr.width - SIDEBAR_W_PX, H = scr.height - TOP_H;
        pg.removeAll();
        pageTitle(pg, "Student Leave Requests", W);

        // ── info banner ───────────────────────────────────────────
        JPanel banner = glassCard();
        banner.setBounds(24, 66, W - 48, 44); pg.add(banner);
        JLabel bannerLbl = makeLabel(
            "ℹ  Approve to remove the student from the club. Reject to keep them. " +
            "Students must always remain in at least 1 club (enforced by the stored procedure).",
            new Font("SansSerif", Font.PLAIN, 12), WARN_COL);
        bannerLbl.setBounds(14, 12, W - 76, 20); banner.add(bannerLbl);

        // ── Pending vs History tabs via radio ─────────────────────
        JToggleButton showPending  = new JToggleButton("⏳ Pending");
        JToggleButton showAll      = new JToggleButton("📋 All History");
        styleToggleBtn(showPending, true);
        styleToggleBtn(showAll, false);
        showPending.setBounds(24, 118, 140, 32); pg.add(showPending);
        showAll    .setBounds(172, 118, 160, 32); pg.add(showAll);

        JButton refreshBtn = buildButton("↻ Refresh", false);
        refreshBtn.setBounds(W - 130, 18, 110, 32); pg.add(refreshBtn);

        // ── Status label ──────────────────────────────────────────
        JLabel statusLbl = makeLabel("", new Font("SansSerif", Font.BOLD, 13), SUCCESS_COL);
        statusLbl.setBounds(24, H - 36, W - 48, 24); pg.add(statusLbl);

        // ── Table ─────────────────────────────────────────────────
        String[] cols = {"Req ID", "Student Name", "Reg No", "Club Name",
                         "Requested On", "Status", "Approve", "Reject"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 6 || c == 7; }
        };
        JTable table = new JTable(model);
        styleTable(table);
        table.setRowHeight(42);

        // ── Status column renderer (colour-coded) ─────────────────
        table.getColumn("Status").setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                String s = v == null ? "—" : v.toString();
                switch (s) {
                    case "Pending"  -> { lbl.setForeground(WARN_COL);    lbl.setText("⏳ Pending"); }
                    case "Approved" -> { lbl.setForeground(SUCCESS_COL); lbl.setText("✓ Approved"); }
                    case "Rejected" -> { lbl.setForeground(ERROR_COL);   lbl.setText("✗ Rejected"); }
                    default         -> { lbl.setForeground(TEXT_MUTED);  }
                }
                lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
                lbl.setBackground(sel ? new Color(100, 50, 200, 120)
                    : row % 2 == 0 ? new Color(12, 6, 30) : new Color(20, 10, 45));
                lbl.setOpaque(true);
                lbl.setBorder(new EmptyBorder(0, 12, 0, 12));
                return lbl;
            }
        });

        // ── Approve button column ─────────────────────────────────
        table.getColumn("Approve").setCellRenderer(new TableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                String status = (String) t.getValueAt(row, 5);
                if ("Pending".equals(status)) {
                    JButton btn = buildButton("✓ Approve", true);
                    btn.setFont(new Font("Georgia", Font.BOLD, 11));
                    return btn;
                }
                JLabel done = makeLabel("—", new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
                done.setHorizontalAlignment(SwingConstants.CENTER);
                done.setOpaque(true);
                done.setBackground(row % 2 == 0 ? new Color(12, 6, 30) : new Color(20, 10, 45));
                return done;
            }
        });
        table.getColumn("Approve").setCellEditor(new DefaultCellEditor(new JCheckBox()) {
            @Override public Component getTableCellEditorComponent(
                    JTable t, Object v, boolean sel, int row, int col) {
                String status = (String) t.getValueAt(row, 5);
                if (!"Pending".equals(status)) {
                    fireEditingStopped();
                    return new JLabel("—");
                }
                JButton btn = buildButton("✓ Approve", true);
                btn.setFont(new Font("Georgia", Font.BOLD, 11));
                btn.addActionListener(e -> {
                    fireEditingStopped();
                    int reqId  = (Integer) t.getValueAt(row, 0);
                    int stuId  = getUserIdForRequest(reqId);
                    int clubId = getClubIdForRequest(reqId);
                    String stuName  = (String) t.getValueAt(row, 1);
                    String clubName = (String) t.getValueAt(row, 3);
                    approveLeaveRequest(reqId, stuId, clubId, stuName, clubName,
                        model, statusLbl);
                });
                return btn;
            }
            @Override public Object getCellEditorValue() { return ""; }
        });

        // ── Reject button column ──────────────────────────────────
        table.getColumn("Reject").setCellRenderer(new TableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                String status = (String) t.getValueAt(row, 5);
                if ("Pending".equals(status)) {
                    JButton btn = buildButton("✗ Reject", false);
                    btn.setFont(new Font("Georgia", Font.BOLD, 11));
                    return btn;
                }
                JLabel done = makeLabel("—", new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
                done.setHorizontalAlignment(SwingConstants.CENTER);
                done.setOpaque(true);
                done.setBackground(row % 2 == 0 ? new Color(12, 6, 30) : new Color(20, 10, 45));
                return done;
            }
        });
        table.getColumn("Reject").setCellEditor(new DefaultCellEditor(new JCheckBox()) {
            @Override public Component getTableCellEditorComponent(
                    JTable t, Object v, boolean sel, int row, int col) {
                String status = (String) t.getValueAt(row, 5);
                if (!"Pending".equals(status)) {
                    fireEditingStopped();
                    return new JLabel("—");
                }
                JButton btn = buildButton("✗ Reject", false);
                btn.setFont(new Font("Georgia", Font.BOLD, 11));
                btn.addActionListener(e -> {
                    fireEditingStopped();
                    int reqId  = (Integer) t.getValueAt(row, 0);
                    String stuName  = (String) t.getValueAt(row, 1);
                    String clubName = (String) t.getValueAt(row, 3);
                    rejectLeaveRequest(reqId, stuName, clubName, model, statusLbl);
                });
                return btn;
            }
            @Override public Object getCellEditorValue() { return ""; }
        });

        // ── Column widths ─────────────────────────────────────────
        table.getColumn("Req ID")        .setPreferredWidth(60);
        table.getColumn("Student Name")  .setPreferredWidth(200);
        table.getColumn("Reg No")        .setPreferredWidth(100);
        table.getColumn("Club Name")     .setPreferredWidth(180);
        table.getColumn("Requested On")  .setPreferredWidth(110);
        table.getColumn("Status")        .setPreferredWidth(110);
        table.getColumn("Approve")       .setPreferredWidth(110);
        table.getColumn("Reject")        .setPreferredWidth(100);

        pg.add(spAt(styledTableScroll(table), 24, 158, W - 48, H - 206));

        // ── Toggle logic ──────────────────────────────────────────
        boolean[] showingPending = {true};

        Runnable loader = () -> {
            model.setRowCount(0);
            statusLbl.setText("");
            loadLeaveRequests(model, showingPending[0]);
        };

        showPending.addActionListener(e -> {
            if (!showingPending[0]) {
                showingPending[0] = true;
                styleToggleBtn(showPending, true);
                styleToggleBtn(showAll, false);
                loader.run();
            }
        });
        showAll.addActionListener(e -> {
            if (showingPending[0]) {
                showingPending[0] = false;
                styleToggleBtn(showPending, false);
                styleToggleBtn(showAll, true);
                loader.run();
            }
        });

        refreshBtn.addActionListener(e -> loader.run());
        loader.run();
    }

    // ── Helper: style a toggle button ────────────────────────────
    private void styleToggleBtn(JToggleButton btn, boolean active) {
        btn.setBackground(active ? new Color(130, 60, 255, 180) : new Color(255, 255, 255, 18));
        btn.setForeground(active ? Color.WHITE : TEXT_MUTED);
        btn.setFont(new Font("Georgia", Font.BOLD, 12));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ── Load leave requests for clubs where I am staff_incharge ──
    private void loadLeaveRequests(DefaultTableModel model, boolean pendingOnly) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                String sql =
                    "SELECT clr.request_id, u.name, NVL(s.reg_num,'—'), c.club_name, " +
                    "       clr.request_date, clr.status " +                          // ← fixed
                    "FROM club_leave_request clr " +
                    "JOIN users u ON clr.user_id = u.user_id " +
                    "LEFT JOIN student s ON clr.user_id = s.user_id " +
                    "JOIN club c ON clr.club_id = c.club_id " +
                    "JOIN members_in mi ON mi.club_id = clr.club_id " +
                    "WHERE mi.user_id = ? AND mi.role_type = 'staff_incharge' " +
                    (pendingOnly ? "AND clr.status = 'Pending' " : "") +
                    "ORDER BY clr.request_date DESC";                                  // ← fixed
                PreparedStatement ps = con.prepareStatement(sql);
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                java.util.List<Object[]> rows = new java.util.ArrayList<>();
                while (rs.next()) {
                    rows.add(new Object[]{
                        rs.getInt(1),    // Req ID
                        rs.getString(2), // Student Name
                        rs.getString(3), // Reg No
                        rs.getString(4), // Club Name
                        rs.getDate(5),   // Requested On
                        rs.getString(6), // Status
                        "",              // Approve placeholder
                        ""               // Reject placeholder
                    });
                }
                SwingUtilities.invokeLater(() -> {
                    for (Object[] row : rows) model.addRow(row);
                    if (rows.isEmpty()) {
                        // Show a ghost row so the table isn't blank
                        model.addRow(new Object[]{"—",
                            pendingOnly ? "No pending leave requests" : "No leave requests found",
                            "—", "—", "—", "—", "", ""});
                    }
                    updateLeaveRequestBadge();
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() ->
                    model.addRow(new Object[]{"—", "Error: " + ex.getMessage(),
                        "—", "—", "—", "—", "", ""}));
            }
        }).start();
    }

    // ── Helper: get student user_id from request_id ───────────────
    private int getUserIdForRequest(int reqId) {
        try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            PreparedStatement ps = con.prepareStatement(
                "SELECT user_id FROM club_leave_request WHERE request_id = ?");
            ps.setInt(1, reqId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {}
        return -1;
    }

    // ── Helper: get club_id from request_id ───────────────────────
    private int getClubIdForRequest(int reqId) {
        try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            PreparedStatement ps = con.prepareStatement(
                "SELECT club_id FROM club_leave_request WHERE request_id = ?");
            ps.setInt(1, reqId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {}
        return -1;
    }

    // ── Approve: update status + remove from members_in ───────────
    private void approveLeaveRequest(int reqId, int studentUserId, int clubId,
                                      String stuName, String clubName,
                                      DefaultTableModel model, JLabel statusLbl) {
        int confirm = JOptionPane.showConfirmDialog(this,
            "<html>Approve leave for <b>" + stuName + "</b> from <b>" + clubName + "</b>?<br><br>" +
            "This will <b>permanently remove</b> them from the club.</html>",
            "Confirm Approval", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            Connection con = null;
            try {
                con = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                con.setAutoCommit(false);

                // 1. Verify student still has at least 2 clubs (so they'll still have 1 after removal)
                PreparedStatement clubCountPs = con.prepareStatement(
                    "SELECT COUNT(*) FROM members_in WHERE user_id = ?");
                clubCountPs.setInt(1, studentUserId);
                ResultSet ccRs = clubCountPs.executeQuery();
                int clubCount = ccRs.next() ? ccRs.getInt(1) : 0;

                if (clubCount <= 1) {
                    con.rollback();
                    SwingUtilities.invokeLater(() -> {
                        statusLbl.setForeground(ERROR_COL);
                        statusLbl.setText("Cannot approve — " + stuName +
                            " must remain in at least 1 club.");
                        JOptionPane.showMessageDialog(this,
                            "<html><b>" + stuName + "</b> is only a member of 1 club.<br>" +
                            "Cannot remove them — students must be in at least 1 club.</html>",
                            "Cannot Approve", JOptionPane.WARNING_MESSAGE);
                    });
                    return;
                }

                // 2. Update leave request status to Approved
                PreparedStatement updatePs = con.prepareStatement(
                	    "UPDATE club_leave_request SET status = 'Approved', " +
                	    "handled_by = ?, handled_date = SYSDATE " +           // ← fixed
                	    "WHERE request_id = ?");
                updatePs.setInt(1, userId);
                updatePs.setInt(2, reqId);
                updatePs.executeUpdate();

                // 3. Remove the student from members_in
                PreparedStatement deletePs = con.prepareStatement(
                    "DELETE FROM members_in WHERE user_id = ? AND club_id = ?");
                deletePs.setInt(1, studentUserId);
                deletePs.setInt(2, clubId);
                deletePs.executeUpdate();

                con.commit();

                SwingUtilities.invokeLater(() -> {
                    statusLbl.setForeground(SUCCESS_COL);
                    statusLbl.setText("✅ Approved — " + stuName +
                        " has been removed from " + clubName + ".");
                    // Refresh the table
                    model.setRowCount(0);
                    loadLeaveRequests(model, true);
                    updateLeaveRequestBadge();
                });

            } catch (SQLException ex) {
                if (con != null) try { con.rollback(); } catch (SQLException ignored) {}
                SwingUtilities.invokeLater(() -> {
                    statusLbl.setForeground(ERROR_COL);
                    String msg = ex.getMessage() == null ? "DB error" : ex.getMessage();
                    statusLbl.setText("Error: " + msg.substring(0, Math.min(80, msg.length())));
                });
            } finally {
                if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignored) {}
            }
        }).start();
    }

    // ── Reject: update status only ────────────────────────────────
    private void rejectLeaveRequest(int reqId, String stuName, String clubName,
                                     DefaultTableModel model, JLabel statusLbl) {
        int confirm = JOptionPane.showConfirmDialog(this,
            "<html>Reject the leave request from <b>" + stuName +
            "</b> for club <b>" + clubName + "</b>?<br><br>" +
            "They will remain a member of the club.</html>",
            "Confirm Rejection", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            	PreparedStatement ps = con.prepareStatement(
            		    "UPDATE club_leave_request SET status = 'Rejected', " +
            		    "handled_by = ?, handled_date = SYSDATE " +           // ← fixed
            		    "WHERE request_id = ?");
                ps.setInt(1, userId);
                ps.setInt(2, reqId);
                ps.executeUpdate();

                SwingUtilities.invokeLater(() -> {
                    statusLbl.setForeground(WARN_COL);
                    statusLbl.setText("✗ Rejected — " + stuName +
                        " will remain in " + clubName + ".");
                    model.setRowCount(0);
                    loadLeaveRequests(model, true);
                    updateLeaveRequestBadge();
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLbl.setForeground(ERROR_COL);
                    String msg = ex.getMessage() == null ? "DB error" : ex.getMessage();
                    statusLbl.setText("Error: " + msg.substring(0, Math.min(80, msg.length())));
                });
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  COMBO LOADER
    // ══════════════════════════════════════════════════════════════
    private void loadEventsCombo(JComboBox<String> combo, Runnable onDone) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
                m.addElement("-- Select Event --");

                if (isIncharge) {
                    // Staff events organised by this incharge only
                    PreparedStatement ps1 = con.prepareStatement(
                        "SELECT se.event_id, se.event_title, se.event_type " +
                        "FROM staff_event se " +
                        "WHERE se.organiser_id = ? " +
                        "ORDER BY se.start_date DESC");
                    ps1.setInt(1, userId);
                    ResultSet rs1 = ps1.executeQuery();
                    while (rs1.next())
                        m.addElement(rs1.getInt(1)+" | "+rs1.getString(2)+" ("+rs1.getString(3)+") [Staff]");

                    // Club events where this staff is incharge of that club
                    PreparedStatement ps2 = con.prepareStatement(
                        "SELECT e.event_id, e.event_title, c.club_name " +
                        "FROM event e JOIN club c ON e.club_id = c.club_id " +
                        "WHERE c.club_id IN (" +
                        "  SELECT club_id FROM members_in " +
                        "  WHERE user_id = ? AND role_type = 'staff_incharge') " +
                        "ORDER BY e.start_date DESC");
                    ps2.setInt(1, userId);
                    ResultSet rs2 = ps2.executeQuery();
                    while (rs2.next())
                        m.addElement(rs2.getInt(1)+" | "+rs2.getString(2)+" ("+rs2.getString(3)+") [Club]");

                } else {
                    // Non-incharge: only staff events they registered for
                    PreparedStatement ps1 = con.prepareStatement(
                        "SELECT se.event_id, se.event_title, se.event_type " +
                        "FROM staff_event se " +
                        "WHERE se.event_id IN (" +
                        "  SELECT event_id FROM staff_event_registration WHERE user_id = ?) " +
                        "ORDER BY se.start_date DESC");
                    ps1.setInt(1, userId);
                    ResultSet rs1 = ps1.executeQuery();
                    while (rs1.next())
                        m.addElement(rs1.getInt(1)+" | "+rs1.getString(2)+" ("+rs1.getString(3)+") [Staff]");
                }

                SwingUtilities.invokeLater(() -> {
                    combo.setModel(m);
                    if (onDone != null) onDone.run();
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    combo.removeAllItems();
                    combo.addItem("-- Error loading events --");
                    if (onDone != null) onDone.run();
                });
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  CLUB-ONLY EVENTS COMBO
    //  Used by Participants, Mark Attendance, and Certificates pages.
    //  For an incharge: shows only events belonging to clubs where
    //  THIS staff member is staff_incharge (no staff events shown).
    //  For a non-incharge: falls back to the same staff-event list.
    // ══════════════════════════════════════════════════════════════
    private void loadClubOnlyEventsCombo(JComboBox<String> combo, Runnable onDone) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
                m.addElement("-- Select Event --");

                if (isIncharge) {
                    // Only club events for clubs where this staff is staff_incharge
                    PreparedStatement ps = con.prepareStatement(
                        "SELECT e.event_id, e.event_title, c.club_name " +
                        "FROM event e JOIN club c ON e.club_id = c.club_id " +
                        "WHERE c.club_id IN (" +
                        "  SELECT club_id FROM members_in " +
                        "  WHERE user_id = ? AND role_type = 'staff_incharge') " +
                        "ORDER BY e.start_date DESC");
                    ps.setInt(1, userId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next())
                        m.addElement(rs.getInt(1) + " | " + rs.getString(2)
                            + " (" + rs.getString(3) + ") [Club]");
                } else {
                    // Non-incharge: staff events they registered for
                    PreparedStatement ps = con.prepareStatement(
                        "SELECT se.event_id, se.event_title, se.event_type " +
                        "FROM staff_event se " +
                        "WHERE se.event_id IN (" +
                        "  SELECT event_id FROM staff_event_registration WHERE user_id = ?) " +
                        "ORDER BY se.start_date DESC");
                    ps.setInt(1, userId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next())
                        m.addElement(rs.getInt(1) + " | " + rs.getString(2)
                            + " (" + rs.getString(3) + ") [Staff]");
                }

                SwingUtilities.invokeLater(() -> {
                    combo.setModel(m);
                    if (onDone != null) onDone.run();
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    combo.removeAllItems();
                    combo.addItem("-- Error loading events --");
                    if (onDone != null) onDone.run();
                });
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  EVENTS FOR A SPECIFIC CLUB (used by cascading dropdowns)
    // ══════════════════════════════════════════════════════════════
    private void loadEventsForClub(int clubId, JComboBox<String> combo) {
        combo.removeAllItems();
        combo.addItem("-- Loading events --");
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                // Verify caller is actually staff_incharge of this club
                PreparedStatement verify = con.prepareStatement(
                    "SELECT COUNT(*) FROM members_in " +
                    "WHERE user_id = ? AND club_id = ? AND role_type = 'staff_incharge'");
                verify.setInt(1, userId); verify.setInt(2, clubId);
                ResultSet vr = verify.executeQuery();
                if (!vr.next() || vr.getInt(1) == 0) {
                    SwingUtilities.invokeLater(() -> {
                        combo.removeAllItems();
                        combo.addItem("-- Access denied --");
                    });
                    return;
                }
                PreparedStatement ps = con.prepareStatement(
                    "SELECT e.event_id, e.event_title " +
                    "FROM event e " +
                    "WHERE e.club_id = ? " +
                    "ORDER BY e.start_date DESC");
                ps.setInt(1, clubId);
                ResultSet rs = ps.executeQuery();
                DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
                m.addElement("-- Select Event --");
                while (rs.next())
                    m.addElement(rs.getInt(1) + " | " + rs.getString(2));
                SwingUtilities.invokeLater(() -> combo.setModel(m));
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    combo.removeAllItems();
                    combo.addItem("-- Error loading events --");
                });
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  DB HELPERS
    // ══════════════════════════════════════════════════════════════
    private void loadClubInternalParticipants(int eventId, DefaultTableModel model) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT u.user_id,u.name,NVL(s.reg_num,'—'),NVL(s.year,0),"+
                    "NVL(s.section,'—'),u.email,r.reg_date,'Registered' "+
                    "FROM registers r JOIN users u ON r.user_id=u.user_id "+
                    "LEFT JOIN student s ON r.user_id=s.user_id "+
                    "WHERE r.event_id=? ORDER BY u.name");
                ps.setInt(1, eventId); ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Object[] row={rs.getInt(1),rs.getString(2),rs.getString(3),
                        rs.getInt(4),rs.getString(5),rs.getString(6),rs.getDate(7),rs.getString(8)};
                    SwingUtilities.invokeLater(()->model.addRow(row));
                }
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(()->model.addRow(new Object[]{"—","Error: "+ex.getMessage(),"—","—","—","—","—","—"}));
            }
        }).start();
    }

    private void loadInternalParticipants(int eventId, DefaultTableModel model) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT u.user_id,u.name,NVL(s.reg_num,'—'),NVL(s.year,0),"+
                    "NVL(s.section,'—'),u.email,ser.reg_date,'Registered' "+
                    "FROM staff_event_registration ser JOIN users u ON ser.user_id=u.user_id "+
                    "LEFT JOIN student s ON ser.user_id=s.user_id "+
                    "WHERE ser.event_id=? ORDER BY u.name");
                ps.setInt(1, eventId); ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Object[] row={rs.getInt(1),rs.getString(2),rs.getString(3),
                        rs.getInt(4),rs.getString(5),rs.getString(6),rs.getDate(7),rs.getString(8)};
                    SwingUtilities.invokeLater(()->model.addRow(row));
                }
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(()->model.addRow(new Object[]{"—","Error: "+ex.getMessage(),"—","—","—","—","—","—"}));
            }
        }).start();
    }

    private void loadExternalParticipants(int eventId, DefaultTableModel model) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT u.user_id,u.name,eg.organisation_name,eg.role_type,u.phone,ser.reg_date "+
                    "FROM staff_event_registration ser JOIN users u ON ser.user_id=u.user_id "+
                    "JOIN external_guest eg ON ser.user_id=eg.user_id "+
                    "WHERE ser.event_id=? ORDER BY u.name");
                ps.setInt(1, eventId); ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Object[] row={rs.getInt(1),rs.getString(2),rs.getString(3),
                        rs.getString(4),rs.getString(5),rs.getDate(6)};
                    SwingUtilities.invokeLater(()->model.addRow(row));
                }
            } catch (SQLException ex) {}
        }).start();
    }

    private void loadCertificateParticipants(int eventId, boolean isClub,
                                          DefaultTableModel model, JLabel infoLbl) {
    new Thread(() -> {
        try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {

            // ── Access check ──────────────────────────────────────
            if (isClub) {
                PreparedStatement verify = con.prepareStatement(
                    "SELECT COUNT(*) FROM event e " +
                    "JOIN members_in mi ON e.club_id = mi.club_id " +
                    "WHERE e.event_id = ? AND mi.user_id = ? AND mi.role_type = 'staff_incharge'");
                verify.setInt(1, eventId); verify.setInt(2, userId);
                ResultSet vRs = verify.executeQuery();
                if (!vRs.next() || vRs.getInt(1) == 0) {
                    SwingUtilities.invokeLater(() -> {
                        infoLbl.setForeground(ERROR_COL);
                        infoLbl.setText("Access denied: you are not the club incharge for this event.");
                    });
                    return;
                }
            } else {
                PreparedStatement verify = con.prepareStatement(
                    "SELECT COUNT(*) FROM staff_event WHERE event_id = ? AND organiser_id = ?");
                verify.setInt(1, eventId); verify.setInt(2, userId);
                ResultSet vRs = verify.executeQuery();
                if (!vRs.next() || vRs.getInt(1) == 0) {
                    SwingUtilities.invokeLater(() -> {
                        infoLbl.setForeground(ERROR_COL);
                        infoLbl.setText("Access denied: you are not the organiser of this event.");
                    });
                    return;
                }
            }

            // ── Rest of the method unchanged from here ────────────
            PreparedStatement attCheck = con.prepareStatement(
                "SELECT COUNT(*) FROM student_attendance WHERE event_id=?");
            attCheck.setInt(1, eventId);
            ResultSet attRs = attCheck.executeQuery();
            boolean attendanceMarked = attRs.next() && attRs.getInt(1) > 0;

            java.util.List<Object[]> rows = new java.util.ArrayList<>();

            if (!attendanceMarked) {
                String sql = isClub
                    ? "SELECT u.user_id,u.name,NVL(s.reg_num,'—'),NVL(TO_CHAR(s.year),'—'),NVL(s.section,'—')," +
                      "u.email,'Not Marked Yet' " +
                      "FROM registers r JOIN users u ON r.user_id=u.user_id " +
                      "LEFT JOIN student s ON u.user_id=s.user_id " +
                      "WHERE r.event_id=? ORDER BY u.name"
                    : "SELECT u.user_id,u.name,NVL(s.reg_num,'—'),NVL(TO_CHAR(s.year),'—'),NVL(s.section,'—')," +
                      "u.email,'Not Marked Yet' " +
                      "FROM staff_event_registration ser JOIN users u ON ser.user_id=u.user_id " +
                      "LEFT JOIN student s ON u.user_id=s.user_id " +
                      "WHERE ser.event_id=? ORDER BY u.name";
                PreparedStatement ps = con.prepareStatement(sql);
                ps.setInt(1, eventId);
                ResultSet rs = ps.executeQuery();
                while (rs.next())
                    rows.add(new Object[]{rs.getInt(1),rs.getString(2),rs.getString(3),
                        rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),false});

                SwingUtilities.invokeLater(() -> {
                    for (Object[] r : rows) model.addRow(r);
                    infoLbl.setForeground(new Color(255, 160, 60));
                    infoLbl.setText("⚠ Attendance not marked yet — showing all " + rows.size() +
                        " registrant(s). You can still issue certificates.");
                });

            } else {
                String sql = isClub
                    ? "SELECT u.user_id,u.name,NVL(s.reg_num,'—'),NVL(TO_CHAR(s.year),'—'),NVL(s.section,'—')," +
                      "u.email," +
                      "NVL((SELECT sa.attendance_status FROM student_attendance sa " +
                      "     WHERE sa.event_id=? AND sa.user_id=u.user_id),'Absent') " +
                      "FROM registers r JOIN users u ON r.user_id=u.user_id " +
                      "LEFT JOIN student s ON u.user_id=s.user_id " +
                      "WHERE r.event_id=? " +
                      "AND u.user_id IN (SELECT user_id FROM student_attendance " +
                      "                  WHERE event_id=? AND attendance_status='Present') " +
                      "ORDER BY u.name"
                    : "SELECT u.user_id,u.name,NVL(s.reg_num,'—'),NVL(TO_CHAR(s.year),'—'),NVL(s.section,'—')," +
                      "u.email," +
                      "NVL((SELECT sa.attendance_status FROM student_attendance sa " +
                      "     WHERE sa.event_id=? AND sa.user_id=u.user_id),'Absent') " +
                      "FROM staff_event_registration ser JOIN users u ON ser.user_id=u.user_id " +
                      "LEFT JOIN student s ON u.user_id=s.user_id " +
                      "WHERE ser.event_id=? " +
                      "AND u.user_id IN (SELECT user_id FROM student_attendance " +
                      "                  WHERE event_id=? AND attendance_status='Present') " +
                      "ORDER BY u.name";
                PreparedStatement ps = con.prepareStatement(sql);
                ps.setInt(1, eventId); ps.setInt(2, eventId); ps.setInt(3, eventId);
                ResultSet rs = ps.executeQuery();
                while (rs.next())
                    rows.add(new Object[]{rs.getInt(1),rs.getString(2),rs.getString(3),
                        rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),false});

                SwingUtilities.invokeLater(() -> {
                    for (Object[] r : rows) model.addRow(r);
                    if (rows.isEmpty()) {
                        infoLbl.setForeground(new Color(255, 160, 60));
                        infoLbl.setText("⚠ No Present students found. Check attendance has been saved.");
                    } else {
                        infoLbl.setForeground(SUCCESS_COL);
                        infoLbl.setText("✅ " + rows.size() + " Present student(s) — tick and click Issue, or use '☑ Select Eligible'.");
                    }
                });
            }

        } catch (SQLException ex) {
            SwingUtilities.invokeLater(() -> {
                infoLbl.setForeground(ERROR_COL);
                infoLbl.setText("Error: " + ex.getMessage());
            });
        }
    }).start();
}
    private void issueCertificates(int eventId, java.util.List<Integer> userIds,
                                   String certType, JLabel infoLbl, JButton issueBtn) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                String eventName = "Event " + eventId;
                PreparedStatement getSe = con.prepareStatement(
                    "SELECT event_title FROM staff_event WHERE event_id=?");
                getSe.setInt(1, eventId);
                ResultSet seRs = getSe.executeQuery();
                if (seRs.next()) {
                    eventName = seRs.getString(1);
                } else {
                    PreparedStatement getCe = con.prepareStatement(
                        "SELECT event_title FROM event WHERE event_id=?");
                    getCe.setInt(1, eventId);
                    ResultSet ceRs = getCe.executeQuery();
                    if (ceRs.next()) eventName = ceRs.getString(1);
                }

                int issued = 0, skipped = 0;
                for (int uid : userIds) {
                    PreparedStatement dupChk = con.prepareStatement(
                        "SELECT COUNT(*) FROM certificate " +
                        "WHERE user_id=? AND event_id=? AND certificate_type=?");
                    dupChk.setInt(1, uid); dupChk.setInt(2, eventId);
                    dupChk.setString(3, certType);
                    ResultSet dupRs = dupChk.executeQuery();
                    if (dupRs.next() && dupRs.getInt(1) > 0) { skipped++; continue; }

                    PreparedStatement nm = con.prepareStatement(
                        "SELECT name FROM users WHERE user_id=?");
                    nm.setInt(1, uid);
                    ResultSet nmRs = nm.executeQuery();
                    String studentName = nmRs.next() ? nmRs.getString(1) : "Student";

                    PreparedStatement ins = con.prepareStatement(
                        "INSERT INTO certificate(certificate_id,user_id,event_id,certificate_type," +
                        "student_name,event_name,issue_date,template_used) " +
                        "VALUES((SELECT NVL(MAX(certificate_id),0)+1 FROM certificate)," +
                        "?,?,?,?,?,SYSDATE,'Default')");
                    ins.setInt(1, uid);
                    ins.setInt(2, eventId);
                    ins.setString(3, certType);
                    ins.setString(4, studentName);
                    ins.setString(5, eventName);
                    ins.executeUpdate();
                    issued++;
                }

                final int fi = issued, fs = skipped;
                SwingUtilities.invokeLater(() -> {
                    infoLbl.setForeground(SUCCESS_COL);
                    infoLbl.setText("🎓 " + fi + " certificate(s) issued!" +
                        (fs > 0 ? "  (" + fs + " already existed — skipped)" : ""));
                    issueBtn.setEnabled(true);
                });

            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    infoLbl.setForeground(ERROR_COL);
                    String msg = ex.getMessage() == null ? "Database error" : ex.getMessage();
                    infoLbl.setText("Error: " + msg.substring(0, Math.min(80, msg.length())));
                    issueBtn.setEnabled(true);
                });
            }
        }).start();
    }

    private void createClubEvent(String title,int clubId,String sd,String ed,String st,String et,
            JLabel statusLbl,JTextField f1,JTextField f3,JTextField f4,JTextField f5,JTextField f6){
        new Thread(()->{
            try {
                java.sql.Date.valueOf(sd); java.sql.Date.valueOf(ed);
                LocalTime.parse(st); LocalTime.parse(et);
                String finalTitle=title.length()>100?title.substring(0,100):title;
                try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                    PreparedStatement verify=con.prepareStatement(
                        "SELECT COUNT(*) FROM members_in WHERE club_id=? AND user_id=? AND role_type='staff_incharge'");
                    verify.setInt(1,clubId); verify.setInt(2,userId);
                    ResultSet vRs=verify.executeQuery();
                    if (!vRs.next()||vRs.getInt(1)==0){
                        SwingUtilities.invokeLater(()->{statusLbl.setForeground(ERROR_COL);
                            statusLbl.setText("You are not assigned as staff_incharge for this club.");}); return;
                    }
                    ResultSet seq=con.createStatement().executeQuery("SELECT NVL(MAX(event_id),0)+1 FROM event");
                    int evId=seq.next()?seq.getInt(1):1;
                    PreparedStatement ps=con.prepareStatement(
                        "INSERT INTO event(event_id,event_title,start_date,end_date,start_time,end_time,club_id) VALUES(?,?,?,?,?,?,?)");
                    ps.setInt(1,evId); ps.setString(2,finalTitle);
                    ps.setDate(3,java.sql.Date.valueOf(sd)); ps.setDate(4,java.sql.Date.valueOf(ed));
                    ps.setString(5,st); ps.setString(6,et); ps.setInt(7,clubId); ps.executeUpdate();
                    PreparedStatement ps2=con.prepareStatement(
                        "INSERT INTO event_incharge(event_id,user_id,role_type,assigned_year) VALUES(?,?,'Coordinator',?)");
                    ps2.setInt(1,evId); ps2.setInt(2,userId); ps2.setInt(3,LocalDate.now().getYear()); ps2.executeUpdate();
                    SwingUtilities.invokeLater(()->{statusLbl.setForeground(SUCCESS_COL);
                        statusLbl.setText("Club event created! ID: "+evId);
                        f1.setText(""); f3.setText(""); f4.setText(""); f5.setText(""); f6.setText("");});
                }
            } catch (java.time.format.DateTimeParseException dpe){
                SwingUtilities.invokeLater(()->{statusLbl.setForeground(ERROR_COL);
                    statusLbl.setText("Invalid date/time format. Use YYYY-MM-DD and HH:MM");});
            } catch (SQLException ex){
                SwingUtilities.invokeLater(()->{statusLbl.setForeground(ERROR_COL);
                    String msg=ex.getMessage()==null?"DB error":ex.getMessage();
                    statusLbl.setText("Error: "+msg.substring(0,Math.min(60,msg.length())));});
            } catch (Exception ex){
                SwingUtilities.invokeLater(()->{statusLbl.setForeground(ERROR_COL);
                    statusLbl.setText("Error: invalid date and time");});
            }
        }).start();
    }

    private void createStaffEvent(String title,String evType,int venueId,String sd,String ed,
            String st,String et,String desc,JLabel statusLbl,
            JTextField fTitle,JTextField typeField,JTextField fSd,JTextField fEd,
            JTextField fSt,JTextField fEt,JTextArea fDesc){
        new Thread(()->{
            try {
                java.sql.Date.valueOf(sd); java.sql.Date.valueOf(ed);
                LocalTime.parse(st); LocalTime.parse(et);
                try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                    ResultSet seq=con.createStatement().executeQuery("SELECT NVL(MAX(event_id),0)+1 FROM staff_event");
                    int evId=seq.next()?seq.getInt(1):1;
                    PreparedStatement ps=con.prepareStatement(
                        "INSERT INTO staff_event(event_id,event_title,event_type,venue_id,"+
                        "start_date,end_date,start_time,end_time,description,organiser_id) VALUES(?,?,?,?,?,?,?,?,?,?)");
                    ps.setInt(1,evId);
                    ps.setString(2,title.length()>100?title.substring(0,100):title);
                    ps.setString(3,evType.length()>30?evType.substring(0,30):evType);
                    ps.setInt(4,venueId);
                    ps.setDate(5,java.sql.Date.valueOf(sd)); ps.setDate(6,java.sql.Date.valueOf(ed));
                    ps.setString(7,st); ps.setString(8,et);
                    ps.setString(9,desc.length()>200?desc.substring(0,200):desc);
                    ps.setInt(10,userId); ps.executeUpdate();
                    SwingUtilities.invokeLater(()->{statusLbl.setForeground(SUCCESS_COL);
                        statusLbl.setText("Staff event created! ID: "+evId);
                        fTitle.setText(""); typeField.setText(""); fSd.setText(""); fEd.setText("");
                        fSt.setText(""); fEt.setText(""); fDesc.setText("");});
                }
            } catch (java.time.format.DateTimeParseException dpe){
                SwingUtilities.invokeLater(()->{statusLbl.setForeground(ERROR_COL);
                    statusLbl.setText("Invalid date/time format. Use YYYY-MM-DD and HH:MM");});
            } catch (Exception ex){
                SwingUtilities.invokeLater(()->{statusLbl.setForeground(ERROR_COL);
                    String msg=ex.getMessage()==null?"error":ex.getMessage();
                    statusLbl.setText("Error: "+msg.substring(0,Math.min(60,msg.length())));});
            }
        }).start();
    }

    private void loadStaffInchargeClubsCombo(JComboBox<String> combo){
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "SELECT DISTINCT c.club_id,c.club_name FROM club c "+
                    "JOIN members_in mi ON c.club_id=mi.club_id "+
                    "WHERE mi.user_id=? AND mi.role_type='staff_incharge' ORDER BY c.club_name");
                ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                DefaultComboBoxModel<String> m=new DefaultComboBoxModel<>();
                m.addElement("-- Select Club (your incharges only) --");
                int count=0;
                while (rs.next()){m.addElement(rs.getInt(1)+" | "+rs.getString(2));count++;}
                final int fc=count;
                SwingUtilities.invokeLater(()->{combo.setModel(m);if(fc==0)combo.setEnabled(false);});
            } catch (SQLException ex){
                SwingUtilities.invokeLater(()->{combo.removeAllItems();
                    combo.addItem("-- Error loading clubs --");combo.setEnabled(false);});
            }
        }).start();
    }

    private void loadVenueCombo(JComboBox<String> combo){
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement("SELECT venue_id,venue_name,capacity FROM venue ORDER BY venue_name");
                ResultSet rs=ps.executeQuery();
                DefaultComboBoxModel<String> m=new DefaultComboBoxModel<>();
                m.addElement("-- Select Venue --");
                while (rs.next()) m.addElement(rs.getInt(1)+" | "+rs.getString(2)+" (cap: "+rs.getInt(3)+")");
                SwingUtilities.invokeLater(()->combo.setModel(m));
            } catch (SQLException ex){
                SwingUtilities.invokeLater(()->{combo.removeAllItems();combo.addItem("-- Error --");});
            }
        }).start();
    }

    private void loadStatCount(String sql, JPanel card){
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass);
                 PreparedStatement ps=con.prepareStatement(sql)){
                ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                String val=rs.next()?String.valueOf(rs.getInt(1)):"0";
                SwingUtilities.invokeLater(()->{
                    int cnt=0;
                    for (Component c:card.getComponents()){
                        if (c instanceof JLabel){cnt++;if(cnt==2){((JLabel)c).setText(val);break;}}
                    }
                    card.repaint();
                });
            } catch (SQLException ex){
                SwingUtilities.invokeLater(()->{
                    int cnt=0;
                    for (Component c:card.getComponents()){
                        if (c instanceof JLabel){cnt++;if(cnt==2){((JLabel)c).setText("0");break;}}
                    }
                });
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════════════════════════
    private void pageTitle(JPanel pg,String text,int W){
        JLabel t=makeLabel(text,new Font("Georgia",Font.BOLD|Font.ITALIC,26),TEXT_PRIMARY);
        t.setBounds(24,18,W-48,36); pg.add(t);
        JSeparator s=buildSeparator(); s.setBounds(24,58,W/3,2); pg.add(s);
    }
    private JLabel fldLbl(String t,int x,int y,int w){
        return lblAt(makeLabel(t,new Font("SansSerif",Font.PLAIN,12),TEXT_MUTED),x,y,w,18);
    }
    private JLabel lblAt(JLabel l,int x,int y,int w,int h){l.setBounds(x,y,w,h);return l;}
    private JScrollPane spAt(JScrollPane sp,int x,int y,int w,int h){sp.setBounds(x,y,w,h);return sp;}
    private DefaultTableModel noEditModel(String[] cols){
        return new DefaultTableModel(cols,0){@Override public boolean isCellEditable(int r,int c){return false;}};
    }
    private JPanel glassCard(){
        JPanel c=new JPanel(null){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG); g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,18,18);
                g2.setColor(CARD_BORDER); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,18,18);
                g2.setPaint(new GradientPaint(0,0,new Color(150,80,255,70),getWidth(),0,new Color(0,0,0,0)));
                g2.fillRoundRect(0,0,getWidth(),3,3,3);
            }
        };
        c.setOpaque(false); return c;
    }
    private JPanel statCard(String emoji,String label,String value){
        JPanel c=new JPanel(null){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(130,60,255,28)); g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
                g2.setColor(CARD_BORDER); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
            }
        };
        c.setOpaque(false);
        JLabel ic=new JLabel(emoji); ic.setFont(new Font("Segoe UI Emoji",Font.PLAIN,22));
        ic.setBounds(12,12,30,28); c.add(ic);
        JLabel vl=makeLabel(value,new Font("Georgia",Font.BOLD,28),TEXT_PRIMARY);
        vl.setBounds(12,40,120,36); c.add(vl);
        JLabel lb=makeLabel(label,new Font("SansSerif",Font.PLAIN,11),TEXT_MUTED);
        lb.setBounds(12,76,160,18); c.add(lb);
        return c;
    }
    private JPanel avatarPanel(String name,int size){
        JPanel av=new JPanel(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new RadialGradientPaint(getWidth()/2f,getHeight()/2f,getWidth()/2f,
                    new float[]{0f,1f},new Color[]{new Color(130,60,255,200),new Color(80,20,180,200)}));
                g2.fillOval(0,0,getWidth()-1,getHeight()-1);
                String init=name.isEmpty()?"S":String.valueOf(name.charAt(0)).toUpperCase();
                g2.setColor(Color.WHITE); g2.setFont(new Font("Georgia",Font.BOLD,(int)(size*0.42)));
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString(init,getWidth()/2-fm.stringWidth(init)/2,getHeight()/2+fm.getAscent()/2-2);
            }
        };
        av.setOpaque(false); return av;
    }
    private JLabel roleBadge(String text){
        JLabel l=new JLabel(text,SwingConstants.CENTER){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0,0,new Color(130,60,255),getWidth(),0,new Color(80,20,180)));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,10,10); super.paintComponent(g);
            }
        };
        l.setFont(new Font("SansSerif",Font.BOLD,11)); l.setForeground(Color.WHITE); l.setOpaque(false); return l;
    }
    private JButton sidebarButton(String text,boolean active){
        JButton btn=new JButton(text){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                if (getClientProperty("active")==Boolean.TRUE){
                    g2.setColor(SIDEBAR_ACT); g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                    g2.setColor(new Color(180,120,255)); g2.fillRoundRect(0,6,4,getHeight()-12,4,4);
                }
                super.paintComponent(g);
            }
        };
        btn.putClientProperty("active",active);
        btn.setContentAreaFilled(false); btn.setBorderPainted(false);
        btn.setFocusPainted(false); btn.setOpaque(false);
        btn.setForeground(active?TEXT_PRIMARY:TEXT_MUTED);
        btn.setFont(new Font("SansSerif",Font.PLAIN+(active?Font.BOLD:0),13));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){if(btn.getClientProperty("active")!=Boolean.TRUE)btn.setForeground(TEXT_PRIMARY);}
            public void mouseExited(MouseEvent e){if(btn.getClientProperty("active")!=Boolean.TRUE)btn.setForeground(TEXT_MUTED);}
        });
        return btn;
    }
    private void setSideActive(JButton btn,boolean active){
        btn.putClientProperty("active",active);
        btn.setForeground(active?TEXT_PRIMARY:TEXT_MUTED);
        btn.setFont(new Font("SansSerif",Font.PLAIN+(active?Font.BOLD:0),13));
        btn.repaint();
    }
    private JButton buildButton(String text,boolean primary){
        JButton btn=new JButton(text){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                if (primary){
                    g2.setPaint(new GradientPaint(0,0,new Color(140,60,255),getWidth(),getHeight(),new Color(90,20,200)));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                } else {
                    g2.setColor(new Color(255,255,255,18)); g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                    g2.setColor(CARD_BORDER); g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                }
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false); btn.setBorderPainted(false);
        btn.setFocusPainted(false); btn.setOpaque(false);
        btn.setForeground(Color.WHITE); btn.setFont(new Font("Georgia",Font.BOLD,13));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){btn.setForeground(GOLD);btn.repaint();}
            public void mouseExited(MouseEvent e){btn.setForeground(Color.WHITE);btn.repaint();}
        });
        return btn;
    }
    @SuppressWarnings("unchecked")
    private JComboBox<String> styledCombo(String[] items){
        JComboBox<String> cb=new JComboBox<>(items);
        cb.setBackground(new Color(30,16,60)); cb.setForeground(TEXT_PRIMARY);
        cb.setFont(new Font("SansSerif",Font.PLAIN,13));
        cb.setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
        cb.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){
                JLabel lb=(JLabel)super.getListCellRendererComponent(l,v,i,s,f);
                lb.setBackground(s?new Color(80,40,160):new Color(25,12,50));
                lb.setForeground(TEXT_PRIMARY); lb.setBorder(new EmptyBorder(4,10,4,10)); return lb;
            }
        });
        return cb;
    }
    private JTextField styledField(){
        JTextField f=new JTextField();
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER,1),new EmptyBorder(4,12,4,12)));
        f.setBackground(new Color(30,16,60)); f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(ACCENT_LIGHT); f.setFont(new Font("SansSerif",Font.PLAIN,13)); return f;
    }
    private JScrollPane styledScroll(JPanel content){
        JScrollPane sp=new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setBackground(new Color(30,16,60)); return sp;
    }
    private JScrollPane styledTableScroll(JTable table){
        JScrollPane sp=new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
        sp.setOpaque(false); sp.getViewport().setBackground(new Color(12,6,30));
        sp.getVerticalScrollBar().setBackground(new Color(30,16,60)); return sp;
    }
    private void styleTable(JTable t){
        t.setBackground(new Color(12,6,30)); t.setForeground(TEXT_PRIMARY);
        t.setFont(new Font("SansSerif",Font.PLAIN,13)); t.setRowHeight(32);
        t.setShowGrid(false); t.setIntercellSpacing(new Dimension(0,0));
        t.setSelectionBackground(new Color(100,50,200,120)); t.setSelectionForeground(Color.WHITE);
        t.getTableHeader().setBackground(new Color(25,12,55));
        t.getTableHeader().setForeground(ACCENT_LIGHT);
        t.getTableHeader().setFont(new Font("Georgia",Font.BOLD,13));
        t.getTableHeader().setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
        t.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable tbl,Object v,boolean sel,boolean foc,int row,int col){
                JLabel l=(JLabel)super.getTableCellRendererComponent(tbl,v,sel,foc,row,col);
                l.setOpaque(true);
                l.setBackground(sel?new Color(100,50,200,120):row%2==0?new Color(12,6,30):new Color(20,10,45));
                l.setForeground(TEXT_PRIMARY); l.setFont(new Font("SansSerif",Font.PLAIN,13));
                l.setBorder(new EmptyBorder(0,12,0,12)); return l;
            }
        });
    }
    private JSeparator buildSeparator(){
        return new JSeparator(){
            @Override protected void paintComponent(Graphics g){
                int w=getWidth(); if(w<=0)return;
                Graphics2D g2=(Graphics2D)g;
                g2.setPaint(new LinearGradientPaint(0,0,w,0,new float[]{0f,0.5f,1f},
                    new Color[]{new Color(0,0,0,0),ACCENT,new Color(0,0,0,0)}));
                g2.fillRect(0,0,w,getHeight());
            }
        };
    }
    private JLabel makeLabel(String text,Font font,Color color){
        JLabel l=new JLabel(text); l.setFont(font); l.setForeground(color); l.setOpaque(false); return l;
    }
    private ImageIcon buildLogoIcon(int size){
        BufferedImage img=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(130,60,255,55)); g.fillOval(0,0,size,size);
        g.setColor(ACCENT); g.setStroke(new BasicStroke(size*0.04f));
        g.drawOval((int)(size*0.03),(int)(size*0.03),(int)(size*0.94),(int)(size*0.94));
        g.setPaint(new RadialGradientPaint(size/2f,size/2f,size*0.42f,new float[]{0f,1f},
            new Color[]{new Color(160,80,255,120),new Color(0,0,0,0)}));
        g.fillOval((int)(size*0.08),(int)(size*0.08),(int)(size*0.84),(int)(size*0.84));
        g.setColor(ACCENT_LIGHT); int s2=size/2,cap=(int)(size*0.26);
        g.fillPolygon(new int[]{s2,s2+cap,s2,s2-cap},
            new int[]{(int)(size*0.28),(int)(size*0.40),(int)(size*0.52),(int)(size*0.40)},4);
        g.setColor(new Color(220,180,255)); int tw=(int)(size*0.36),th=(int)(size*0.10);
        g.fillRoundRect(s2-tw/2,(int)(size*0.22),tw,th,6,6);
        g.setColor(GOLD); g.setStroke(new BasicStroke(size*0.025f));
        int tx=s2+tw/2-(int)(size*0.04);
        g.drawLine(tx,(int)(size*0.27),tx,(int)(size*0.52));
        g.fillOval(tx-(int)(size*0.04),(int)(size*0.51),(int)(size*0.08),(int)(size*0.08));
        g.dispose(); return new ImageIcon(img);
    }
}
