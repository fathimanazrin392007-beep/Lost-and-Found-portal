import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App {
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    private static final String DEFAULT_DATABASE_URL =
            "jdbc:mysql://localhost:3306/mall_lost_found?serverTimezone=UTC";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::start);
    }

    private static void start() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            Database database = connectToDatabase();
            if (database == null) {
                return;
            }
            database.initialize();
            if (!database.hasStaffUsers() && !createInitialStaffAccount(database)) {
                return;
            }
            new MallPortal(database).setVisible(true);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | javax.swing.UnsupportedLookAndFeelException | SQLException
                 | IllegalStateException | EmptyFieldException exception) {
            String message = "Unable to start the Mall Lost and Found Portal: "
                    + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            LOGGER.log(Level.SEVERE, message, exception);
            JOptionPane.showMessageDialog(null, message, "Startup error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Database connectToDatabase() {
        String username = environment("MALL_DB_USER");
        String password = environment("MALL_DB_PASSWORD");
        String url = environment("MALL_DB_URL");
        if (username == null) {
            DatabaseCredentials credentials = requestDatabaseCredentials(url);
            if (credentials == null) {
                return null;
            }
            try {
                return new Database(credentials.url(), credentials.username(), new String(credentials.password()));
            } finally {
                Arrays.fill(credentials.password(), '\0');
            }
        }
        return new Database(url == null ? DEFAULT_DATABASE_URL : url, username, password);
    }

    private static DatabaseCredentials requestDatabaseCredentials(String configuredUrl) {
        JTextField url = new JTextField(configuredUrl == null ? DEFAULT_DATABASE_URL : configuredUrl);
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        JPanel form = formPanel();
        addRow(form, "MySQL JDBC URL", url);
        addRow(form, "MySQL username", username);
        addRow(form, "MySQL password", password);
        int choice = JOptionPane.showConfirmDialog(null, form, "Connect to MySQL",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        char[] passwordChars = password.getPassword();
        password.setText("");
        if (username.getText().isBlank()) {
            Arrays.fill(passwordChars, '\0');
            JOptionPane.showMessageDialog(null, "Enter your MySQL username.",
                    "Missing database username", JOptionPane.ERROR_MESSAGE);
            return requestDatabaseCredentials(url.getText());
        }
        return new DatabaseCredentials(url.getText().trim(), username.getText().trim(), passwordChars);
    }

    private static boolean createInitialStaffAccount(Database database) throws SQLException, EmptyFieldException {
        JTextField username = new JTextField(valueOrDefault(environment("MALL_ADMIN_USERNAME"), "security"));
        String configuredPassword = environment("MALL_ADMIN_PASSWORD");
        JPasswordField password = new JPasswordField(configuredPassword == null ? "" : configuredPassword);
        JPanel form = formPanel();
        addRow(form, "Initial security username", username);
        addRow(form, "Initial security password", password);
        int choice = JOptionPane.showConfirmDialog(null, form,
                "Create the first security account", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        char[] passwordChars = password.getPassword();
        password.setText("");
        if (choice != JOptionPane.OK_OPTION) {
            Arrays.fill(passwordChars, '\0');
            return false;
        }
        try {
            database.createInitialAdmin(username.getText(), passwordChars);
            return true;
        } catch (EmptyFieldException exception) {
            JOptionPane.showMessageDialog(null, exception.getMessage(),
                    "Security account required", JOptionPane.ERROR_MESSAGE);
            return createInitialStaffAccount(database);
        }
    }

    private static JPanel formPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return panel;
    }

    private static void addRow(JPanel panel, String label, JComponent input) {
        panel.add(new JLabel(label));
        panel.add(input);
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private record DatabaseCredentials(String url, String username, char[] password) {
    }
}
