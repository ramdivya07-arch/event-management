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
import java.time.Period;

/**
 * External Guest Dashboard — mirrors student dashboard structure.
 * Pages: Overview | View Events | Register Event | My Events | Feedback
 *
 * Key difference from Student:
 * - No club membership
 * - Can only register for staff_event (institution-level events)
 * - Mobile number is collected at event registration
 * - Inserts into staff_event_registration (not registers table)
 */
public class ExternalGuestDashboard extends JFrame {

    private final String dbUrl, dbUser, dbPass;
    private final int    userId;
    private final String userName;

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
    private static final Color SIDEBAR_BG   = new Color(10,   5,  28, 240);
    private static final Color SIDEBAR_ACT  = new Color(130,  60, 255, 160);

    private static final int SIDEBAR_W_PX = 220;
    private static final int TOP_H        = 56;

    // Pages: 0=Overview 1=ViewEvents 2=RegisterEvent 3=MyEvents 4=Feedback
    private int     curPage  = 0, tgtPage = 0;
    private float   slideOff = 0f;
    private int     slideDir = 1;
    private Timer   slideTimer;
    private boolean sliding  = false;

    private JPanel rootLayer, contentArea;
    private JPanel[] pages;
    private JButton[] sideItems;

    // Guest profile data
    private String guestName="", guestEmail="", guestGender="", guestAddress="";
    private String guestDOB="", guestAge="0", orgName="", roleType="";

    public ExternalGuestDashboard(int userId, String userName,
                                  String dbUrl, String dbUser, String dbPass) {
        this.userId = userId; this.userName = userName;
        this.dbUrl = dbUrl; this.dbUser = dbUser; this.dbPass = dbPass;

        setTitle("Guest Dashboard — " + userName);
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

        pages=new JPanel[5];
        for (int i=0;i<5;i++){
            pages[i]=new JPanel(null); pages[i].setOpaque(false);
            pages[i].setBounds(i==0?0:cW,0,cW,cH);
            contentArea.add(pages[i]);
        }

        loadGuestData(()->{
            buildOverviewPage(scr);
            buildViewEventsPage(scr);
            buildRegisterEventPage(scr);
            buildMyEventsPage(scr);
            buildFeedbackPage(scr);
        });

        setVisible(true);
    }

    // ── Top Bar ────────────────────────────────────────────────────
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
        JLabel logo=new JLabel(buildLogoIcon(34)); logo.setBounds(16,(TOP_H-34)/2,34,34); top.add(logo);
        JLabel title=makeLabel("Campus Event System",new Font("Georgia",Font.BOLD,16),TEXT_PRIMARY);
        title.setBounds(58,(TOP_H-20)/2,230,20); top.add(title);
        JLabel greet=makeLabel("Welcome, "+userName+" (Guest)",new Font("Georgia",Font.ITALIC,13),TEXT_MUTED);
        greet.setBounds(scr.width-390,(TOP_H-18)/2,260,18); top.add(greet);
        JButton logout=buildButton("Logout",false);
        logout.setBounds(scr.width-126,(TOP_H-34)/2,110,34);
        logout.addActionListener(e->{ dispose(); SwingUtilities.invokeLater(WelcomePage::new); });
        top.add(logout);
        rootLayer.add(top);
    }

    // ── Sidebar ────────────────────────────────────────────────────
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

        JPanel badge=avatarPanel(userName,64);
        badge.setBounds(SIDEBAR_W_PX/2-32,20,64,64); sidebar.add(badge);
        JLabel uName=makeLabel(userName,new Font("Georgia",Font.BOLD,14),TEXT_PRIMARY);
        uName.setHorizontalAlignment(SwingConstants.CENTER);
        uName.setBounds(0,90,SIDEBAR_W_PX,20); sidebar.add(uName);
        JLabel uRole=makeLabel("External Guest",new Font("SansSerif",Font.PLAIN,11),ACCENT_LIGHT);
        uRole.setHorizontalAlignment(SwingConstants.CENTER);
        uRole.setBounds(0,112,SIDEBAR_W_PX,16); sidebar.add(uRole);
        JSeparator div=buildSeparator(); div.setBounds(20,136,SIDEBAR_W_PX-40,2); sidebar.add(div);

        String[][] menu={
            {"\uD83C\uDFE0","Overview"},
            {"\uD83D\uDCC5","View Events"},
            {"\u270F\uFE0F", "Register Event"},
            {"\uD83D\uDCCB","My Events"},
            {"\uD83D\uDCAC","Feedback"},
        };
        sideItems=new JButton[menu.length];
        int my=150;
        for (int i=0;i<menu.length;i++){
            final int idx=i;
            sideItems[i]=sidebarButton(menu[i][0]+" "+menu[i][1],i==0);
            sideItems[i].setBounds(12,my,SIDEBAR_W_PX-24,44);
            sideItems[i].addActionListener(e->navigateTo(idx));
            sidebar.add(sideItems[i]); my+=52;
        }
        rootLayer.add(sidebar);
    }

    // ── Navigation ─────────────────────────────────────────────────
    private void navigateTo(int page) {
        if (sliding||page==curPage) return;
        tgtPage=page; slideDir=(page>curPage)?1:-1; slideOff=0f; sliding=true;
        int cW=contentArea.getWidth();
        JPanel from=pages[curPage], to=pages[tgtPage];
        to.setBounds(slideDir*cW,0,cW,contentArea.getHeight());
        for (int i=0;i<sideItems.length;i++) setSideActive(sideItems[i],i==page);
        slideTimer=new Timer(12,e->{
            slideOff=Math.min(1f,slideOff+0.065f);
            float ease=easeInOut(slideOff);
            from.setBounds((int)(-slideDir*cW*ease),0,cW,contentArea.getHeight());
            to.setBounds((int)(slideDir*cW*(1f-ease)),0,cW,contentArea.getHeight());
            rootLayer.repaint();
            if (slideOff>=1f){ ((Timer)e.getSource()).stop(); sliding=false; curPage=tgtPage;
                from.setBounds(slideDir*cW,0,cW,contentArea.getHeight()); }
        });
        slideTimer.start();
    }
    private float easeInOut(float t){ return t<0.5f?2*t*t:(float)(-1+(4-2*t)*t); }

    // ── Load Guest Data ────────────────────────────────────────────
    private void loadGuestData(Runnable onDone) {
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "SELECT u.name,u.email,u.gender,u.address,u.dob,"+
                    "eg.organisation_name,eg.role_type "+
                    "FROM users u JOIN external_guest eg ON u.user_id=eg.user_id "+
                    "WHERE u.user_id=?");
                ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                if (rs.next()){
                    guestName=rs.getString("name"); guestEmail=rs.getString("email");
                    guestGender=rs.getString("gender"); guestAddress=rs.getString("address");
                    orgName=rs.getString("organisation_name"); roleType=rs.getString("role_type");
                    java.sql.Date dob=rs.getDate("dob");
                    if (dob!=null){ guestDOB=dob.toString();
                        guestAge=String.valueOf(Period.between(dob.toLocalDate(),LocalDate.now()).getYears()); }
                }
            } catch (SQLException ex){ guestName=userName; }
            SwingUtilities.invokeLater(onDone);
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 0 — OVERVIEW
    // ══════════════════════════════════════════════════════════════
    private void buildOverviewPage(Dimension scr) {
        JPanel pg=pages[0]; int W=scr.width-SIDEBAR_W_PX, H=scr.height-TOP_H, cx=W/2;
        JPanel content=new JPanel(null); content.setOpaque(false);
        content.setPreferredSize(new Dimension(W,800));
        JScrollPane sc=styledScroll(content); sc.setBounds(0,0,W,H); pg.add(sc);

        JLabel ttl=makeLabel("Guest Overview",new Font("Georgia",Font.BOLD|Font.ITALIC,28),TEXT_PRIMARY);
        ttl.setHorizontalAlignment(SwingConstants.CENTER); ttl.setBounds(0,24,W,38); content.add(ttl);
        JSeparator sep=buildSeparator(); sep.setBounds(cx-W/5,68,W/5*2,2); content.add(sep);

        // Profile card
        int pcW=360, pcH=380, pcX=36, pcY=88;
        JPanel pc=glassCard(); pc.setBounds(pcX,pcY,pcW,pcH); content.add(pc);
        JPanel av=avatarPanel(guestName,72); av.setBounds(pcW/2-36,20,72,72); pc.add(av);
        JLabel nm=makeLabel(guestName,new Font("Georgia",Font.BOLD,17),TEXT_PRIMARY);
        nm.setHorizontalAlignment(SwingConstants.CENTER); nm.setBounds(0,100,pcW,24); pc.add(nm);
        JLabel rb=roleBadge(roleType.isEmpty()?"External Guest":roleType);
        rb.setBounds(pcW/2-70,128,140,22); pc.add(rb);
        String[][] rows={
            {"\uD83D\uDCE7",guestEmail},
            {"\u2640\u2642",guestGender},
            {"\uD83D\uDCC5","DOB: "+guestDOB+"  (Age "+guestAge+")"},
            {"\uD83C\uDFE2","Org: "+orgName},
            {"\uD83D\uDCCD",guestAddress.isEmpty()?"—":guestAddress},
        };
        int ry=162;
        for (String[] r:rows){
            JLabel ic=new JLabel(r[0]); ic.setFont(new Font("Segoe UI Emoji",Font.PLAIN,13));
            ic.setBounds(18,ry,26,20); pc.add(ic);
            JLabel vl=makeLabel(r[1],new Font("SansSerif",Font.PLAIN,13),TEXT_MUTED);
            vl.setBounds(48,ry,pcW-60,20); pc.add(vl); ry+=30;
        }

        // Stat cards
        int scX=pcX+pcW+28, scY=pcY, scW=(W-scX-32)/2, scH=120, scGap=14;
        String[][] stats={
            {"\uD83D\uDCC5","Events Registered",
             "SELECT COUNT(*) FROM staff_event_registration WHERE user_id=?"},
            {"\uD83D\uDCAC","Feedback Given",
             "SELECT COUNT(*) FROM feedback WHERE user_id=?"},
        };
        for (int i=0;i<stats.length;i++){
            JPanel st=statCard(stats[i][0],stats[i][1],"0");
            st.setBounds(scX+i*(scW+scGap),scY,scW,scH); content.add(st);
            loadStatCount(stats[i][2],st);
        }

        // Info card
        int infoY=scY+scH+18;
        JPanel info=glassCard(); info.setBounds(scX,infoY,W-scX-32,200); content.add(info);
        JLabel iT=makeLabel("About External Guest Role",new Font("Georgia",Font.BOLD,15),ACCENT_LIGHT);
        iT.setBounds(20,14,W-scX-72,22); info.add(iT);
        String html="<html><body style='color:rgb(160,150,200);font-family:SansSerif;font-size:12px;line-height:1.7'>"
            +"External guests are individuals invited from outside the institution to participate in events. "
            +"You may be a speaker, judge, trainer, or special visitor contributing to the success of an event.<br><br>"
            +"<b style='color:rgb(240,235,255)'>Event Registration:</b> You can browse and register for institution-level events "
            +"(College Day, Symposium, Hackathon, etc.) organised by staff. Your mobile number is recorded at registration.<br><br>"
            +"<b style='color:rgb(240,235,255)'>Feedback:</b> After attending an event you can submit ratings and comments "
            +"to help organisers improve future programmes."
            +"</body></html>";
        JLabel iL=new JLabel(html); iL.setBounds(20,42,W-scX-72,160); info.add(iL);

        content.revalidate(); content.repaint();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 1 — VIEW EVENTS (all staff_event entries)
    // ══════════════════════════════════════════════════════════════
    private void buildViewEventsPage(Dimension scr) {
        JPanel pg=pages[1]; int W=scr.width-SIDEBAR_W_PX, H=scr.height-TOP_H;
        pageTitle(pg,"View Events",W);

        JPanel banner=glassCard(); banner.setBounds(24,66,W-48,44); pg.add(banner);
        JLabel bl=makeLabel("Institution-level events open to external guests are listed below.",
            new Font("SansSerif",Font.PLAIN,12),ACCENT_LIGHT);
        bl.setBounds(14,12,W-76,20); banner.add(bl);

        String[] cols={"Event ID","Title","Type","Venue","Start Date","End Date","Start Time","End Time","Organiser"};
        DefaultTableModel model=noEditModel(cols); JTable table=new JTable(model); styleTable(table);
        pg.add(spAt(styledTableScroll(table),24,118,W-48,H-148));

        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "SELECT se.event_id,se.event_title,se.event_type,v.venue_name,"+
                    "se.start_date,se.end_date,se.start_time,se.end_time,u.name "+
                    "FROM staff_event se "+
                    "JOIN venue v ON se.venue_id=v.venue_id "+
                    "JOIN users u ON se.organiser_id=u.user_id "+
                    "ORDER BY se.start_date DESC");
                ResultSet rs=ps.executeQuery();
                while (rs.next()){ Object[] row={rs.getInt(1),rs.getString(2),rs.getString(3),
                    rs.getString(4),rs.getDate(5),rs.getDate(6),rs.getString(7),rs.getString(8),rs.getString(9)};
                    SwingUtilities.invokeLater(()->model.addRow(row)); }
            } catch (SQLException ex){ SwingUtilities.invokeLater(()->
                model.addRow(new Object[]{"—","Error: "+ex.getMessage().substring(0,Math.min(40,ex.getMessage().length())),"—","—","—","—","—","—","—"})); }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 2 — REGISTER EVENT (with mobile number input)
    // ══════════════════════════════════════════════════════════════
    private void buildRegisterEventPage(Dimension scr) {
        JPanel pg=pages[2]; int W=scr.width-SIDEBAR_W_PX, H=scr.height-TOP_H, cx=W/2;
        pageTitle(pg,"Register for Event",W);

        int cw=500, ch=400, cardX=cx-cw/2, cardY=80;
        JPanel card=glassCard(); card.setBounds(cardX,cardY,cw,ch); pg.add(card);

        int fx=36, fw=cw-72, fy=24;

        // Mobile number — unique to external guest registration
        card.add(fldLblC("Mobile Number (10 digits)",fx,fy,fw)); fy+=18;
        JTextField mobileField=styledField(); mobileField.setBounds(fx,fy,fw,40); card.add(mobileField); fy+=52;

        // Event selection
        card.add(fldLblC("Select Event",fx,fy,fw)); fy+=18;
        JComboBox<String> evCombo=styledCombo(new String[]{"-- Loading events --"});
        evCombo.setBounds(fx,fy,fw,40); card.add(evCombo); fy+=52;
        loadAllStaffEventsCombo(evCombo);

        // Event details preview
        JLabel detLbl=makeLabel("",new Font("SansSerif",Font.PLAIN,13),TEXT_MUTED);
        detLbl.setBounds(fx,fy,fw,60); detLbl.setVerticalAlignment(SwingConstants.TOP); card.add(detLbl); fy+=70;

        evCombo.addActionListener(e->{
            String sel=(String)evCombo.getSelectedItem();
            if (sel==null||sel.startsWith("--")){ detLbl.setText(""); return; }
            String[] parts=sel.split("\\|");
            if (parts.length>=3)
                detLbl.setText("<html><b style='color:rgb(240,235,255)'>"+parts[1].trim()+"</b><br>"+
                    "<span style='color:rgb(160,150,200)'>Type: "+parts[2].trim()+"</span></html>");
        });

        JLabel statusLbl=makeLabel("",new Font("SansSerif",Font.BOLD,12),ERROR_COL);
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);
        statusLbl.setBounds(0,fy,cw,20); card.add(statusLbl);

        JButton regBtn=buildButton("Register",true);
        regBtn.setBounds(fx,fy+28,fw,46); card.add(regBtn);

        regBtn.addActionListener(e->{
            String mobile=mobileField.getText().trim();
            String sel=(String)evCombo.getSelectedItem();
            if (mobile.isEmpty()||mobile.length()!=10||!mobile.matches("\\d{10}")){
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Enter a valid 10-digit mobile number."); return; }
            if (sel==null||sel.startsWith("--")){
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Please select an event."); return; }
            int evId=Integer.parseInt(sel.split("\\|")[0].trim());
            registerGuestForEvent(evId,mobile,statusLbl);
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 3 — MY EVENTS (events this guest registered for)
    // ══════════════════════════════════════════════════════════════
    private void buildMyEventsPage(Dimension scr) {
        JPanel pg=pages[3]; int W=scr.width-SIDEBAR_W_PX, H=scr.height-TOP_H;
        pageTitle(pg,"My Events",W);

        String[] cols={"Event ID","Title","Type","Venue","Start Date","End Date","Reg Date","Status","Mobile"};
        DefaultTableModel model=noEditModel(cols); JTable table=new JTable(model); styleTable(table);
        pg.add(spAt(styledTableScroll(table),24,72,W-48,H-100));

        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "SELECT se.event_id,se.event_title,se.event_type,v.venue_name,"+
                    "se.start_date,se.end_date,ser.reg_date,ser.status,ser.mobile "+
                    "FROM staff_event_registration ser "+
                    "JOIN staff_event se ON ser.event_id=se.event_id "+
                    "JOIN venue v ON se.venue_id=v.venue_id "+
                    "WHERE ser.user_id=? ORDER BY se.start_date DESC");
                ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                while (rs.next()){ Object[] row={rs.getInt(1),rs.getString(2),rs.getString(3),
                    rs.getString(4),rs.getDate(5),rs.getDate(6),rs.getDate(7),rs.getString(8),rs.getString(9)};
                    SwingUtilities.invokeLater(()->model.addRow(row)); }
            } catch (SQLException ex){ SwingUtilities.invokeLater(()->
                model.addRow(new Object[]{"—","Error","—","—","—","—","—","—","—"})); }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 4 — FEEDBACK
    // ══════════════════════════════════════════════════════════════
    private void buildFeedbackPage(Dimension scr) {
        JPanel pg=pages[4]; int W=scr.width-SIDEBAR_W_PX, H=scr.height-TOP_H, cx=W/2;
        pageTitle(pg,"Feedback",W);

        // History table (top half)
        String[] cols={"Feedback ID","Event Title","Type","Rating","Comments"};
        DefaultTableModel histModel=noEditModel(cols); JTable histTable=new JTable(histModel); styleTable(histTable);
        int tableH=(H-100)/2;
        pg.add(spAt(styledTableScroll(histTable),24,72,W-48,tableH));
        loadFeedbackHistory(histModel);

        // Submit form (bottom half)
        int fy=72+tableH+14;
        int cw=560, cardX=cx-cw/2;
        JPanel card=glassCard(); card.setBounds(cardX,fy,cw,H-fy-24); pg.add(card);

        int fx=28, fw=cw-56, cy=18;
        JLabel fT=makeLabel("Submit New Feedback",new Font("Georgia",Font.BOLD,15),ACCENT_LIGHT);
        fT.setBounds(fx,cy,300,22); card.add(fT); cy+=34;

        card.add(fldLblC("Select Event",fx,cy,fw-120));
        card.add(fldLblC("Rating",fx+fw-110,cy,110)); cy+=18;
        JComboBox<String> evCombo=styledCombo(new String[]{"-- Loading --"});
        evCombo.setBounds(fx,cy,fw-120,38); card.add(evCombo);
        JComboBox<Integer> ratCombo=styledIntCombo(new Integer[]{5,4,3,2,1});
        ratCombo.setBounds(fx+fw-110,cy,110,38); card.add(ratCombo); cy+=50;

        card.add(fldLblC("Comments (max 100 chars)",fx,cy,fw)); cy+=18;
        JTextArea cmtArea=new JTextArea();
        cmtArea.setBackground(new Color(30,16,60)); cmtArea.setForeground(TEXT_PRIMARY);
        cmtArea.setFont(new Font("SansSerif",Font.PLAIN,12)); cmtArea.setLineWrap(true);
        cmtArea.setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
        cmtArea.setBounds(fx,cy,fw,70); card.add(cmtArea); cy+=80;

        JLabel statusLbl=makeLabel("",new Font("SansSerif",Font.BOLD,12),ERROR_COL);
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);
        statusLbl.setBounds(0,cy,cw,20); card.add(statusLbl);

        JButton submitBtn=buildButton("Submit Feedback",true);
        submitBtn.setBounds(fx,cy+24,180,40); card.add(submitBtn);

        loadGuestFeedbackEventCombo(evCombo);

        submitBtn.addActionListener(e->{
            String sel=(String)evCombo.getSelectedItem();
            Integer rat=(Integer)ratCombo.getSelectedItem();
            String cmt=cmtArea.getText().trim();
            if (sel==null||sel.startsWith("--")){
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Select an event."); return; }
            int evId=Integer.parseInt(sel.split("\\|")[0].trim());
            submitFeedback(evId,rat,cmt,statusLbl,histModel,cmtArea);
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  DB METHODS
    // ══════════════════════════════════════════════════════════════

    /** Register guest for a staff_event — collects mobile number */
    private void registerGuestForEvent(int eventId, String mobile, JLabel statusLbl) {
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "INSERT INTO staff_event_registration(event_id,user_id,reg_date,status,mobile) "+
                    "VALUES(?,?,SYSDATE,'Registered',?)");
                ps.setInt(1,eventId); ps.setInt(2,userId); ps.setString(3,mobile);
                ps.executeUpdate();
                SwingUtilities.invokeLater(()->{
                    statusLbl.setForeground(SUCCESS_COL);
                    statusLbl.setText("Registered successfully! Check My Events.");
                });
            } catch (SQLException ex){
                SwingUtilities.invokeLater(()->{
                    statusLbl.setForeground(ERROR_COL);
                    String msg=ex.getMessage();
                    if (msg.contains("ORA-00001")) statusLbl.setText("Already registered for this event.");
                    else statusLbl.setText("Error: "+msg.substring(0,Math.min(60,msg.length())));
                });
            }
        }).start();
    }

    private void loadAllStaffEventsCombo(JComboBox<String> combo) {
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "SELECT se.event_id,se.event_title,se.event_type,se.start_date "+
                    "FROM staff_event se "+
                    "WHERE se.event_id NOT IN "+
                    "(SELECT event_id FROM staff_event_registration WHERE user_id=?) "+
                    "ORDER BY se.start_date DESC");
                ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                DefaultComboBoxModel<String> m=new DefaultComboBoxModel<>();
                m.addElement("-- Select Event --");
                while (rs.next())
                    m.addElement(rs.getInt(1)+" | "+rs.getString(2)+" | "+rs.getString(3)+" | "+rs.getDate(4));
                SwingUtilities.invokeLater(()->combo.setModel(m));
            } catch (SQLException ex){
                SwingUtilities.invokeLater(()->{combo.removeAllItems(); combo.addItem("-- Error --");});
            }
        }).start();
    }

    private void loadGuestFeedbackEventCombo(JComboBox<String> combo) {
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "SELECT se.event_id,se.event_title FROM staff_event_registration ser "+
                    "JOIN staff_event se ON ser.event_id=se.event_id "+
                    "WHERE ser.user_id=? "+
                    "AND se.event_id NOT IN (SELECT event_id FROM feedback WHERE user_id=?) "+
                    "ORDER BY se.event_title");
                ps.setInt(1,userId); ps.setInt(2,userId); ResultSet rs=ps.executeQuery();
                DefaultComboBoxModel<String> m=new DefaultComboBoxModel<>();
                m.addElement("-- Select Event --");
                while (rs.next()) m.addElement(rs.getInt(1)+" | "+rs.getString(2));
                SwingUtilities.invokeLater(()->combo.setModel(m));
            } catch (SQLException ex){
                SwingUtilities.invokeLater(()->{combo.removeAllItems(); combo.addItem("-- Error --");});
            }
        }).start();
    }

    private void loadFeedbackHistory(DefaultTableModel model) {
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                PreparedStatement ps=con.prepareStatement(
                    "SELECT f.feedback_id,se.event_title,se.event_type,f.rating,f.comments "+
                    "FROM feedback f "+
                    "JOIN staff_event se ON f.event_id=se.event_id "+
                    "WHERE f.user_id=? ORDER BY f.feedback_id DESC");
                ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                while (rs.next()){ Object[] row={rs.getInt(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getString(5)};
                    SwingUtilities.invokeLater(()->model.addRow(row)); }
            } catch (SQLException ex){ SwingUtilities.invokeLater(()->
                model.addRow(new Object[]{"—","Error","—","—","—"})); }
        }).start();
    }

    private void submitFeedback(int eventId, int rating, String comments,
                                JLabel statusLbl, DefaultTableModel histModel, JTextArea cmtArea) {
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass)){
                ResultSet seq=con.createStatement().executeQuery("SELECT NVL(MAX(feedback_id),0)+1 FROM feedback");
                int fbId=seq.next()?seq.getInt(1):1;
                String cmt=comments.length()>100?comments.substring(0,100):comments;
                PreparedStatement ps=con.prepareStatement(
                    "INSERT INTO feedback(feedback_id,user_id,event_id,rating,comments) VALUES(?,?,?,?,?)");
                ps.setInt(1,fbId); ps.setInt(2,userId); ps.setInt(3,eventId);
                ps.setInt(4,rating); ps.setString(5,cmt);
                ps.executeUpdate();
                histModel.setRowCount(0); loadFeedbackHistory(histModel);
                SwingUtilities.invokeLater(()->{
                    statusLbl.setForeground(SUCCESS_COL); statusLbl.setText("Feedback submitted!");
                    cmtArea.setText("");
                });
            } catch (SQLException ex){
                SwingUtilities.invokeLater(()->{
                    statusLbl.setForeground(ERROR_COL);
                    statusLbl.setText("Error: "+ex.getMessage().substring(0,Math.min(50,ex.getMessage().length())));
                });
            }
        }).start();
    }

    private void loadStatCount(String sql, JPanel card) {
        new Thread(()->{
            try (Connection con=DriverManager.getConnection(dbUrl,dbUser,dbPass);
                 PreparedStatement ps=con.prepareStatement(sql)){
                ps.setInt(1,userId); ResultSet rs=ps.executeQuery();
                String val=rs.next()?String.valueOf(rs.getInt(1)):"0";
                SwingUtilities.invokeLater(()->{
                    int cnt=0;
                    for (Component c:card.getComponents()){ if (c instanceof JLabel){ cnt++;
                        if (cnt==2){((JLabel)c).setText(val);break;} } }
                    card.repaint();
                });
            } catch (SQLException ex){
                SwingUtilities.invokeLater(()->{
                    int cnt=0;
                    for (Component c:card.getComponents()){ if (c instanceof JLabel){ cnt++;
                        if (cnt==2){((JLabel)c).setText("0");break;} } }
                });
            }
        }).start();
    }

    // ── UI Helpers ─────────────────────────────────────────────────
    private void pageTitle(JPanel pg,String text,int W){
        JLabel t=makeLabel(text,new Font("Georgia",Font.BOLD|Font.ITALIC,26),TEXT_PRIMARY);
        t.setHorizontalAlignment(SwingConstants.LEFT); t.setBounds(24,18,W-48,36); pg.add(t);
        JSeparator s=buildSeparator(); s.setBounds(24,58,W/3,2); pg.add(s);
    }
    private JLabel fldLblC(String t,int x,int y,int w){
        JLabel l=makeLabel(t,new Font("SansSerif",Font.PLAIN,12),TEXT_MUTED); l.setBounds(x,y,w,18); return l;
    }
    private JScrollPane spAt(JScrollPane sp,int x,int y,int w,int h){ sp.setBounds(x,y,w,h); return sp; }
    private DefaultTableModel noEditModel(String[] cols){
        return new DefaultTableModel(cols,0){ @Override public boolean isCellEditable(int r,int c){return false;} };
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
        JLabel ic=new JLabel(emoji); ic.setFont(new Font("Segoe UI Emoji",Font.PLAIN,22)); ic.setBounds(12,12,30,28); c.add(ic);
        JLabel vl=makeLabel(value,new Font("Georgia",Font.BOLD,28),TEXT_PRIMARY); vl.setBounds(12,40,120,36); c.add(vl);
        JLabel lb=makeLabel(label,new Font("SansSerif",Font.PLAIN,11),TEXT_MUTED); lb.setBounds(12,76,160,18); c.add(lb);
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
                String init=name.isEmpty()?"G":String.valueOf(name.charAt(0)).toUpperCase();
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
        btn.setContentAreaFilled(false); btn.setBorderPainted(false); btn.setFocusPainted(false); btn.setOpaque(false);
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
                if (primary){ g2.setPaint(new GradientPaint(0,0,new Color(140,60,255),getWidth(),getHeight(),new Color(90,20,200)));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                } else { g2.setColor(new Color(255,255,255,18)); g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                    g2.setColor(CARD_BORDER); g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12); }
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false); btn.setBorderPainted(false); btn.setFocusPainted(false); btn.setOpaque(false);
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
        cb.setFont(new Font("SansSerif",Font.PLAIN,13)); cb.setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
        cb.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){
                JLabel lb=(JLabel)super.getListCellRendererComponent(l,v,i,s,f);
                lb.setBackground(s?new Color(80,40,160):new Color(25,12,50));
                lb.setForeground(TEXT_PRIMARY); lb.setBorder(new EmptyBorder(4,10,4,10)); return lb;
            }
        });
        return cb;
    }
    @SuppressWarnings("unchecked")
    private JComboBox<Integer> styledIntCombo(Integer[] items){
        JComboBox<Integer> cb=new JComboBox<>(items);
        cb.setBackground(new Color(30,16,60)); cb.setForeground(TEXT_PRIMARY);
        cb.setFont(new Font("SansSerif",Font.PLAIN,13)); cb.setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
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
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(CARD_BORDER,1),new EmptyBorder(4,12,4,12)));
        f.setBackground(new Color(30,16,60)); f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(ACCENT_LIGHT); f.setFont(new Font("SansSerif",Font.PLAIN,13)); return f;
    }
    private JScrollPane styledScroll(JPanel content){
        JScrollPane sp=new JScrollPane(content,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16); sp.getVerticalScrollBar().setBackground(new Color(30,16,60)); return sp;
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
        t.getTableHeader().setBackground(new Color(25,12,55)); t.getTableHeader().setForeground(ACCENT_LIGHT);
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
