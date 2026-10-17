import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Container;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.RowFilter;

final class MallPortal extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Color NAVY = new Color(24, 42, 64);
    private static final Color TEAL = new Color(13, 125, 115);
    private static final Color CANVAS = new Color(241, 245, 249);
    private static final Color SURFACE = Color.WHITE;
    private static final Color TEXT = new Color(37, 52, 69);
    private static final Color MUTED = new Color(104, 120, 137);
    private static final Color BORDER = new Color(220, 228, 236);
    private static final Color PALE_TEAL = new Color(226, 245, 241);

    static {
        installTheme();
    }
    private static final String HOME = "home";
    private static final String VISITOR_MENU = "visitor-menu";
    private static final String SECURITY_LOGIN = "security-login";
    private static final String ADMIN_HOME = "admin-home";
    private static final String SEARCH = "search";
    private static final String LOST_REPORT = "lost-report";
    private static final String CLAIM = "claim";
    private static final String ITEMS = "items";
    private static final String CLAIMS = "claims";
    private static final String REPORTS = "reports";

    private static final String[] CATEGORIES = {
            "Electronics", "Wallets", "Documents", "Bags", "Jewelry", "Clothing", "Other"
    };
    private final transient Database database;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel screens = new JPanel(cardLayout);
    private final JTable searchTable = table(
            "Item ID", "Category", "Description", "Found location", "Found at", "Status");
    private final JTable itemsTable = table(
            "Item ID", "Category", "Description", "Found location", "Found at", "Status",
            "Ownership verification details");
    private final JTable claimsTable = table(
            "Claim ID", "Item ID", "Category", "Claimant", "Contact",
            "Proof of ownership", "Status", "Submitted");
    private final JTable reportsTable = table(
            "Report ID", "Category", "Description", "Last known location",
            "Visitor", "Contact", "Submitted");
    private final JTextField claimItemId = new JTextField();
    private final JTextField searchKeyword = new JTextField();
    private final JTextField searchLocation = new JTextField();
    private final JTextField itemsFilter = new JTextField(20);
    private final JTextField claimsFilter = new JTextField(20);
    private final JTextField reportsFilter = new JTextField(20);
    private final JComboBox<String> searchCategory = new JComboBox<>(categoryOptions());
    private final JLabel claimsCount = new JLabel(" ");
    private final JLabel reportsCount = new JLabel(" ");
    private final JLabel activeItemsMetric = metricValueLabel();
    private final JLabel pendingClaimsMetric = metricValueLabel();
    private final JLabel lostReportsMetric = metricValueLabel();
    private final JLabel staffGreeting = new JLabel("", SwingConstants.CENTER);
    private String loggedInStaff;

    MallPortal(Database database) {
        super("Mall Lost and Found Portal");
        this.database = database;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 620));
        setSize(1200, 760);
        setLocationRelativeTo(null);
        getContentPane().setBackground(CANVAS);

        screens.add(buildHomePage(), HOME);
        screens.add(buildVisitorMenu(), VISITOR_MENU);
        screens.add(buildSecurityLoginPage(), SECURITY_LOGIN);
        screens.add(buildAdminHome(), ADMIN_HOME);
        screens.add(buildSearchPage(), SEARCH);
        screens.add(buildLostReportPage(), LOST_REPORT);
        screens.add(buildClaimPage(), CLAIM);
        screens.add(buildItemsPage(), ITEMS);
        screens.add(buildClaimsPage(), CLAIMS);
        screens.add(buildReportsPage(), REPORTS);
        add(screens);
        showPage(HOME);
    }

    private JPanel buildHomePage() {
        JPanel content = new JPanel(new BorderLayout(0, 28));
        content.setBorder(BorderFactory.createEmptyBorder(36, 48, 44, 48));
        JPanel hero = new JPanel(new GridLayout(0, 1, 8, 8));
        hero.setBackground(NAVY);
        hero.setBorder(BorderFactory.createEmptyBorder(30, 36, 30, 36));
        JLabel eyebrow = new JLabel("MALL SERVICES  /  LOST & FOUND");
        eyebrow.setForeground(new Color(144, 225, 214));
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 12f));
        JLabel welcome = new JLabel("A little help finding what matters.");
        welcome.setForeground(Color.WHITE);
        welcome.setFont(welcome.getFont().deriveFont(Font.BOLD, 30f));
        JLabel subtitle = new JLabel("Search for a found item or let our security team know what you lost.");
        subtitle.setForeground(new Color(219, 229, 237));
        subtitle.setFont(subtitle.getFont().deriveFont(15f));
        hero.add(eyebrow);
        hero.add(welcome);
        hero.add(subtitle);

        JPanel options = new JPanel(new GridLayout(1, 2, 20, 0));
        options.setOpaque(false);
        options.add(homeCard("Visitor", "Search found property, report a loss, or submit a claim.",
                "Continue as visitor", () -> showPage(VISITOR_MENU)));
        options.add(homeCard("Security team", "Securely manage found items and review visitor requests.",
                "Staff sign in", () -> showPage(SECURITY_LOGIN)));
        content.add(hero, BorderLayout.NORTH);
        content.add(options, BorderLayout.CENTER);
        return page("Home", null, content);
    }

    private JPanel homeCard(String title, String description, String actionLabel, Runnable action) {
        JPanel card = new JPanel(new GridLayout(0, 1, 12, 12));
        card.setBackground(SURFACE);
        card.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true),
                new EmptyBorder(24, 24, 24, 24)));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 21f));
        JLabel detail = new JLabel("<html><div style='width:300px'>" + description + "</div></html>");
        detail.setForeground(MUTED);
        JButton proceed = button(actionLabel, true);
        proceed.addActionListener(event -> action.run());
        card.add(heading);
        card.add(detail);
        card.add(proceed);
        return card;
    }

    private JPanel buildVisitorMenu() {
        JPanel content = new JPanel(new BorderLayout(0, 22));
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 24, 24));
        JLabel intro = sectionHeading("How can we help?", "Choose an option to get started.");
        JPanel options = new JPanel(new GridLayout(1, 3, 16, 0));
        options.setOpaque(false);
        options.add(homeCard("Find an item", "Search found property by description, category, or location.",
                "Search items", () -> showPage(SEARCH)));
        options.add(homeCard("Report a loss", "Tell mall security what you lost and where you last saw it.",
                "Create report", () -> showPage(LOST_REPORT)));
        options.add(homeCard("Claim an item", "Submit identifying details so staff can verify ownership.",
                "Start claim", () -> showPage(CLAIM)));
        content.add(intro, BorderLayout.NORTH);
        content.add(options, BorderLayout.CENTER);
        return page("Visitor Portal", () -> showPage(HOME), content);
    }

    private JPanel buildSecurityLoginPage() {
        JPanel content = formPanel();
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        addFormRow(content, "Staff username", username);
        addFormRow(content, "Password", password);
        JButton login = new JButton("Sign in");
        login.addActionListener(event -> {
            char[] chars = password.getPassword();
            try {
                if (database.authenticate(username.getText(), chars)) {
                    loggedInStaff = username.getText().trim();
                    password.setText("");
                    showPage(ADMIN_HOME);
                } else {
                    showError("The username or password is incorrect.");
                }
            } catch (SQLException exception) {
                showError(exception);
            } finally {
                java.util.Arrays.fill(chars, '\0');
                password.setText("");
            }
        });
        content.add(new JLabel(""));
        content.add(login);
        return page("Security Staff Sign In", () -> showPage(HOME), content);
    }

    private JPanel buildAdminHome() {
        JPanel content = new JPanel(new BorderLayout(0, 24));
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 24, 24));
        JPanel overview = new JPanel(new BorderLayout(0, 6));
        overview.setOpaque(false);
        JLabel heading = sectionHeading("Security overview", "A live snapshot of the lost-and-found desk.");
        staffGreeting.setForeground(MUTED);
        staffGreeting.setHorizontalAlignment(SwingConstants.RIGHT);
        JPanel overviewHeader = new JPanel(new BorderLayout());
        overviewHeader.setOpaque(false);
        overviewHeader.add(heading, BorderLayout.CENTER);
        overviewHeader.add(staffGreeting, BorderLayout.EAST);
        JPanel metrics = new JPanel(new GridLayout(1, 3, 16, 0));
        metrics.setOpaque(false);
        metrics.add(metricCard("Active found items", activeItemsMetric, PALE_TEAL));
        metrics.add(metricCard("Claims awaiting review", pendingClaimsMetric, new Color(255, 245, 224)));
        metrics.add(metricCard("Visitor lost reports", lostReportsMetric, new Color(235, 240, 250)));

        JLabel actionsHeading = sectionHeading("Quick actions", "Open a workspace to manage records and requests.");
        JPanel actions = new JPanel(new GridLayout(1, 3, 16, 0));
        actions.setOpaque(false);
        actions.add(homeCard("Found items", "Register items, update their status, and archive claimed property.",
                "Manage items", () -> showPage(ITEMS)));
        actions.add(homeCard("Claim requests", "Review visitor proof and approve or reject ownership claims.",
                "Review claims", () -> showPage(CLAIMS)));
        actions.add(homeCard("Lost reports", "View visitor reports and contact details for possible matches.",
                "View reports", () -> showPage(REPORTS)));

        JPanel dashboardBody = new JPanel(new BorderLayout(0, 18));
        dashboardBody.setOpaque(false);
        dashboardBody.add(metrics, BorderLayout.NORTH);
        JPanel quickActions = new JPanel(new BorderLayout(0, 12));
        quickActions.setOpaque(false);
        quickActions.add(actionsHeading, BorderLayout.NORTH);
        quickActions.add(actions, BorderLayout.CENTER);
        dashboardBody.add(quickActions, BorderLayout.CENTER);
        overview.add(overviewHeader, BorderLayout.NORTH);
        content.add(overview, BorderLayout.NORTH);
        content.add(dashboardBody, BorderLayout.CENTER);
        return page("Security Dashboard", () -> signOut(), content);
    }

    private JPanel buildSearchPage() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel filters = new JPanel(new GridLayout(2, 4, 8, 6));
        filters.add(new JLabel("Keyword"));
        filters.add(new JLabel("Category"));
        filters.add(new JLabel("Found location"));
        filters.add(new JLabel(""));
        filters.add(searchKeyword);
        filters.add(searchCategory);
        filters.add(searchLocation);
        JButton search = new JButton("Search");
        search.addActionListener(event -> refreshSearch());
        filters.add(search);
        content.add(filters, BorderLayout.NORTH);
        content.add(new JScrollPane(searchTable), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(new JLabel("Select a result and click below to start a claim."));
        JButton claimSelected = new JButton("Claim selected item");
        claimSelected.addActionListener(event -> {
            Long id = selectedId(searchTable, 0);
            if (id != null) {
                claimItemId.setText(String.valueOf(id));
                showPage(CLAIM);
            }
        });
        bottom.add(claimSelected);
        content.add(bottom, BorderLayout.SOUTH);
        return page("Search Found Items", () -> showPage(VISITOR_MENU), content);
    }

    private JPanel buildLostReportPage() {
        JPanel content = formPanel();
        JComboBox<String> category = new JComboBox<>(CATEGORIES);
        JTextField description = new JTextField();
        JTextField location = new JTextField();
        JTextField name = new JTextField();
        JTextField contact = new JTextField();
        addFormRow(content, "Category", category);
        addFormRow(content, "Item description", description);
        addFormRow(content, "Last known location", location);
        addFormRow(content, "Your name", name);
        addFormRow(content, "Phone or email", contact);
        JButton submit = new JButton("Submit lost-item report");
        submit.addActionListener(event -> {
            try {
                database.reportLost((String) category.getSelectedItem(), description.getText(),
                        location.getText(), name.getText(), contact.getText());
                clear(description, location, name, contact);
                showMessage("Your lost-item report has been recorded. Mall security can contact you if there is a match.");
            } catch (SQLException | EmptyFieldException exception) {
                showError(exception);
            }
        });
        content.add(new JLabel(""));
        content.add(submit);
        return page("Report a Lost Item", () -> showPage(VISITOR_MENU), content);
    }

    private JPanel buildClaimPage() {
        JPanel content = formPanel();
        JTextField name = new JTextField();
        JTextField contact = new JTextField();
        JTextArea proof = new JTextArea(5, 30);
        proof.setLineWrap(true);
        proof.setWrapStyleWord(true);
        addFormRow(content, "Found item ID", claimItemId);
        addFormRow(content, "Your name", name);
        addFormRow(content, "Phone or email", contact);
        addFormRow(content, "Proof of ownership", new JScrollPane(proof));
        JButton submit = new JButton("Submit claim request");
        submit.addActionListener(event -> {
            try {
                long itemId = Long.parseLong(claimItemId.getText().trim());
                database.submitClaim(itemId, name.getText(), contact.getText(), proof.getText());
                clear(claimItemId, name, contact);
                proof.setText("");
                showMessage("Your claim request has been submitted for security verification.");
            } catch (NumberFormatException exception) {
                showError("Enter a valid numeric item ID.");
            } catch (SQLException | EmptyFieldException | InvalidItemException exception) {
                showError(exception);
            }
        });
        content.add(new JLabel("Describe details only the true owner would know. Security staff will verify your claim."));
        content.add(submit);
        return page("Request a Claim", () -> showPage(VISITOR_MENU), content);
    }

    private JPanel buildItemsPage() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel register = formPanel();
        JComboBox<String> category = new JComboBox<>(CATEGORIES);
        JTextField description = new JTextField();
        JTextField location = new JTextField();
        JTextArea verification = new JTextArea(3, 24);
        verification.setLineWrap(true);
        verification.setWrapStyleWord(true);
        addFormRow(register, "Category", category);
        addFormRow(register, "Description", description);
        addFormRow(register, "Found location", location);
        addFormRow(register, "Ownership verification details", new JScrollPane(verification));
        JButton addItem = new JButton("Register found item");
        addItem.addActionListener(event -> {
            try {
                long itemId = database.registerFoundItem((String) category.getSelectedItem(),
                        description.getText(), location.getText(), verification.getText());
                clear(description, location);
                verification.setText("");
                refreshItems();
                showMessage("Found item registered. Item ID: " + itemId);
            } catch (SQLException | EmptyFieldException exception) {
                showError(exception);
            }
        });
        register.add(new JLabel(""));
        register.add(addItem);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<String> status = new JComboBox<>(new String[]{"Unclaimed", "Under Verification", "Claimed"});
        JButton update = new JButton("Update selected item status");
        update.addActionListener(event -> {
            Long id = selectedId(itemsTable, 0);
            if (id == null) {
                return;
            }
            try {
                database.updateItemStatus(id, (String) status.getSelectedItem());
                refreshItems();
                refreshSearch();
            } catch (SQLException | InvalidItemException exception) {
                showError(exception);
            }
        });
        JButton archive = new JButton("Archive all claimed items");
        archive.addActionListener(event -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Archive claimed items? They will no longer appear in active lists.",
                    "Confirm archive", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                int count = database.archiveClaimedItems();
                refreshItems();
                refreshSearch();
                showMessage(count + " claimed item(s) archived.");
            } catch (SQLException exception) {
                showError(exception);
            }
        });
        JButton refresh = new JButton("Refresh items");
        refresh.addActionListener(event -> refreshItems());
        actions.add(refresh);
        actions.add(new JLabel("Selected item status:"));
        actions.add(status);
        actions.add(update);
        actions.add(archive);
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterBar.add(new JLabel("Filter items"));
        filterBar.add(itemsFilter);
        installTableFilter(itemsTable, itemsFilter, null, -1, null, "item");
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(itemsTable), register);
        split.setResizeWeight(0.58);
        content.add(filterBar, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        return page("Found Items", () -> showPage(ADMIN_HOME), content);
    }

    private JPanel buildClaimsPage() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JComboBox<String> statusFilter = new JComboBox<>(
                new String[]{"All statuses", "Pending", "Approved", "Rejected"});
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterBar.add(new JLabel("Search requests"));
        filterBar.add(claimsFilter);
        filterBar.add(new JLabel("Status"));
        filterBar.add(statusFilter);
        filterBar.add(claimsCount);
        installTableFilter(claimsTable, claimsFilter, statusFilter, 6, claimsCount, "claim");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refresh = new JButton("Refresh claim requests");
        refresh.addActionListener(event -> refreshClaims());
        JButton approve = new JButton("Approve selected claim");
        approve.addActionListener(event -> decideSelectedClaim("Approved"));
        JButton reject = new JButton("Reject selected claim");
        reject.addActionListener(event -> decideSelectedClaim("Rejected"));
        actions.add(refresh);
        actions.add(new JLabel("Verify the proof before approving."));
        actions.add(approve);
        actions.add(reject);
        content.add(filterBar, BorderLayout.NORTH);
        content.add(new JScrollPane(claimsTable), BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        return page("Visitor Claim Requests", () -> showPage(ADMIN_HOME), content);
    }

    private JPanel buildReportsPage() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterBar.add(new JLabel("Search reports"));
        filterBar.add(reportsFilter);
        installTableFilter(reportsTable, reportsFilter, null, -1, reportsCount, "report");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refresh = new JButton("Refresh lost-item reports");
        refresh.addActionListener(event -> refreshLostReports());
        actions.add(refresh);
        actions.add(reportsCount);
        content.add(filterBar, BorderLayout.NORTH);
        content.add(new JScrollPane(reportsTable), BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        return page("Visitor Lost-Item Reports", () -> showPage(ADMIN_HOME), content);
    }

    private JPanel page(String title, Runnable backAction, JPanel content) {
        JPanel page = new JPanel(new BorderLayout(8, 8));
        page.setBackground(CANVAS);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 18));
        JLabel brand = new JLabel("  MALL  /  LOST & FOUND");
        brand.setForeground(new Color(165, 226, 218));
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 12f));
        JLabel heading = new JLabel(title);
        heading.setForeground(Color.WHITE);
        heading.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 17f));
        header.add(heading, BorderLayout.CENTER);
        header.add(brand, BorderLayout.WEST);
        if (backAction != null) {
            JButton back = button("< Back", false);
            back.addActionListener(event -> backAction.run());
            header.add(back, BorderLayout.EAST);
        }
        page.add(header, BorderLayout.NORTH);
        styleComponents(content);
        content.setBackground(SURFACE);
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(SURFACE);
        card.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true),
                new EmptyBorder(12, 12, 12, 12)));
        card.add(content, BorderLayout.CENTER);
        JPanel padded = new JPanel(new BorderLayout());
        padded.setBackground(CANVAS);
        padded.setBorder(new EmptyBorder(22, 26, 26, 26));
        padded.add(card, BorderLayout.CENTER);
        page.add(padded, BorderLayout.CENTER);
        return page;
    }

    private static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        styleButton(button, primary);
        return button;
    }

    private static void styleButton(JButton button, boolean primary) {
        String label = button.getText().toLowerCase();
        boolean destructive = label.contains("reject") || label.contains("archive");
        boolean secondary = !primary;
        button.setBackground(destructive ? new Color(179, 61, 66)
                : secondary ? new Color(232, 239, 244) : TEAL);
        button.setForeground(secondary ? NAVY : Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setBorder(new CompoundBorder(new LineBorder(
                destructive ? new Color(179, 61, 66) : secondary ? BORDER : TEAL, 1, true),
                new EmptyBorder(9, 15, 9, 15)));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setMargin(new Insets(5, 10, 5, 10));
    }

    private static void styleComponents(Component component) {
        if (component instanceof JButton button) {
            String label = button.getText().toLowerCase();
            styleButton(button, !label.equals("back") && !label.startsWith("< back")
                    && !label.startsWith("sign out"));
        } else if (component instanceof JPasswordField field) {
            field.setFont(field.getFont().deriveFont(14f));
            field.setBackground(SURFACE);
            field.setForeground(TEXT);
            field.setCaretColor(TEAL);
            field.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true),
                    new EmptyBorder(7, 9, 7, 9)));
        } else if (component instanceof JTextField field) {
            field.setFont(field.getFont().deriveFont(14f));
            field.setBackground(SURFACE);
            field.setForeground(TEXT);
            field.setCaretColor(TEAL);
            field.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true),
                    new EmptyBorder(7, 9, 7, 9)));
        } else if (component instanceof JTextArea area) {
            area.setFont(area.getFont().deriveFont(14f));
            area.setBackground(SURFACE);
            area.setForeground(TEXT);
            area.setCaretColor(TEAL);
            area.setBorder(new EmptyBorder(7, 9, 7, 9));
        } else if (component instanceof JComboBox<?> combo) {
            combo.setFont(combo.getFont().deriveFont(14f));
            combo.setBackground(SURFACE);
            combo.setForeground(TEXT);
        } else if (component instanceof JTable table) {
            table.setFont(table.getFont().deriveFont(13f));
            table.setRowHeight(30);
            table.setGridColor(BORDER);
            table.setSelectionBackground(PALE_TEAL);
            table.setSelectionForeground(NAVY);
            table.setShowVerticalLines(false);
            table.setShowHorizontalLines(true);
            table.setIntercellSpacing(new Dimension(1, 1));
            table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
            table.getTableHeader().setBackground(new Color(232, 238, 244));
            table.getTableHeader().setForeground(NAVY);
            table.getTableHeader().setPreferredSize(new Dimension(0, 36));
        } else if (component instanceof JScrollPane scrollPane) {
            scrollPane.setBorder(new LineBorder(BORDER, 1, true));
            scrollPane.getViewport().setBackground(SURFACE);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleComponents(child);
            }
        }
    }

    private static void installTheme() {
        Font defaultFont = new Font("Segoe UI", Font.PLAIN, 14);
        javax.swing.UIManager.put("defaultFont", defaultFont);
        javax.swing.UIManager.put("Panel.background", CANVAS);
        javax.swing.UIManager.put("Label.foreground", TEXT);
        javax.swing.UIManager.put("Label.font", defaultFont);
        javax.swing.UIManager.put("Button.font", defaultFont.deriveFont(Font.BOLD));
        javax.swing.UIManager.put("TextField.font", defaultFont);
        javax.swing.UIManager.put("PasswordField.font", defaultFont);
        javax.swing.UIManager.put("TextArea.font", defaultFont);
        javax.swing.UIManager.put("ComboBox.font", defaultFont);
        javax.swing.UIManager.put("Table.font", defaultFont.deriveFont(13f));
        javax.swing.UIManager.put("Table.rowHeight", 30);
        javax.swing.UIManager.put("Table.gridColor", BORDER);
        javax.swing.UIManager.put("Table.selectionBackground", PALE_TEAL);
        javax.swing.UIManager.put("Table.selectionForeground", NAVY);
        javax.swing.UIManager.put("TableHeader.background", new Color(232, 238, 244));
        javax.swing.UIManager.put("TableHeader.foreground", NAVY);
    }

    private void showPage(String pageName) {
        cardLayout.show(screens, pageName);
        if (ADMIN_HOME.equals(pageName)) {
            staffGreeting.setText("Signed in as " + loggedInStaff);
            refreshDashboardSummary();
        }
        switch (pageName) {
            case SEARCH -> refreshSearch();
            case ITEMS -> refreshItems();
            case CLAIMS -> refreshClaims();
            case REPORTS -> refreshLostReports();
            default -> {
            }
        }
    }

    private void signOut() {
        loggedInStaff = null;
        showPage(HOME);
    }

    private void decideSelectedClaim(String decision) {
        Long id = selectedId(claimsTable, 0);
        if (id == null) {
            return;
        }
        try {
            database.decideClaim(id, decision);
            refreshClaims();
            refreshItems();
            refreshSearch();
            showMessage("Claim request " + decision.toLowerCase() + ".");
        } catch (SQLException | InvalidItemException exception) {
            showError(exception);
        }
    }

    private void refreshSearch() {
        try {
            setRows(searchTable, database.searchItems(searchKeyword.getText(),
                    (String) searchCategory.getSelectedItem(), searchLocation.getText()));
        } catch (SQLException exception) {
            showError(exception);
        }
    }

    private void refreshItems() {
        try {
            setRows(itemsTable, database.listActiveItems());
        } catch (SQLException exception) {
            showError(exception);
        }
    }

    private void refreshClaims() {
        try {
            List<Object[]> rows = database.listClaims();
            setRows(claimsTable, rows);
            updateTableCount(claimsTable, claimsCount, "claim");
        } catch (SQLException exception) {
            showError(exception);
        }
    }

    private void refreshLostReports() {
        try {
            List<Object[]> rows = database.listLostReports();
            setRows(reportsTable, rows);
            updateTableCount(reportsTable, reportsCount, "report");
        } catch (SQLException exception) {
            showError(exception);
        }
    }

    private void refreshDashboardSummary() {
        try {
            long[] summary = database.dashboardSummary();
            activeItemsMetric.setText(String.valueOf(summary[0]));
            pendingClaimsMetric.setText(String.valueOf(summary[1]));
            lostReportsMetric.setText(String.valueOf(summary[2]));
        } catch (SQLException exception) {
            showError(exception);
        }
    }

    private void installTableFilter(JTable table, JTextField searchField,
                                    JComboBox<String> statusFilter, int statusColumn,
                                    JLabel countLabel, String rowName) {
        @SuppressWarnings("unchecked")
        TableRowSorter<DefaultTableModel> sorter =
                (TableRowSorter<DefaultTableModel>) table.getRowSorter();
        Runnable applyFilter = () -> {
            String query = searchField.getText().trim();
            String status = statusFilter == null ? "All statuses" : (String) statusFilter.getSelectedItem();
            java.util.ArrayList<RowFilter<Object, Object>> filters = new java.util.ArrayList<>();
            if (!query.isEmpty()) {
                filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(query)));
            }
            if (statusFilter != null && !"All statuses".equals(status)) {
                filters.add(RowFilter.regexFilter("^" + Pattern.quote(status) + "$", statusColumn));
            }
            sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
            updateTableCount(table, countLabel, rowName);
        };
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                applyFilter.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                applyFilter.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                applyFilter.run();
            }
        });
        if (statusFilter != null) {
            statusFilter.addActionListener(event -> applyFilter.run());
        }
    }

    private void updateTableCount(JTable table, JLabel countLabel, String rowName) {
        if (countLabel != null) {
            countLabel.setText(table.getRowCount() + " " + rowName + "(s)");
        }
    }

    private Long selectedId(JTable table, int column) {
        int row = table.getSelectedRow();
        if (row < 0) {
            showError("Select a row first.");
            return null;
        }
        return ((Number) table.getValueAt(row, column)).longValue();
    }

    private static String[] categoryOptions() {
        String[] options = new String[CATEGORIES.length + 1];
        options[0] = "All categories";
        System.arraycopy(CATEGORIES, 0, options, 1, CATEGORIES.length);
        return options;
    }

    private static JLabel sectionHeading(String title, String subtitle) {
        JLabel heading = new JLabel("<html><div style='padding:3px 0'>"
                + title + "<br><span style='font-size:12px;color:#687889;font-weight:normal'>"
                + subtitle + "</span></div></html>");
        heading.setForeground(NAVY);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 21f));
        return heading;
    }

    private static JLabel metricValueLabel() {
        JLabel value = new JLabel("—");
        value.setForeground(NAVY);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 30f));
        return value;
    }

    private JPanel metricCard(String title, JLabel value, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(SURFACE);
        card.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true),
                new EmptyBorder(17, 18, 17, 18)));
        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        value.setOpaque(true);
        value.setBackground(accent);
        value.setBorder(new EmptyBorder(9, 12, 9, 12));
        card.add(label, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private static JPanel formPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return panel;
    }

    private static void addFormRow(JPanel panel, String label, java.awt.Component input) {
        panel.add(new JLabel(label));
        panel.add(input);
    }

    private static JTable table(String... columns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        return table;
    }

    private static void setRows(JTable table, List<Object[]> rows) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        rows.forEach(model::addRow);
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Mall Lost and Found Portal", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(Exception exception) {
        showError(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Operation failed", JOptionPane.ERROR_MESSAGE);
    }

    private static void clear(JTextField... fields) {
        for (JTextField field : fields) {
            field.setText("");
        }
    }
}
