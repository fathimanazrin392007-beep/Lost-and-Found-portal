import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

final class Database {
    private static final String JDBC_PREFIX = "jdbc:mysql://";
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/mall_lost_found?serverTimezone=UTC";
    private final String url;
    private final String serverUrl;
    private final String databaseName;
    private final String username;
    private final String password;

    Database(String jdbcUrl, String databaseUser, String databasePassword) {
        url = jdbcUrl == null || jdbcUrl.isBlank() ? DEFAULT_URL : jdbcUrl.trim();
        String[] urlParts = parseUrl(url);
        serverUrl = urlParts[0];
        databaseName = urlParts[1];
        username = databaseUser;
        password = databasePassword == null ? "" : databasePassword;
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("A MySQL database username is required.");
        }
    }

    private static String[] parseUrl(String jdbcUrl) {
        if (!jdbcUrl.startsWith(JDBC_PREFIX)) {
            throw new IllegalStateException("MALL_DB_URL must start with " + JDBC_PREFIX);
        }
        int pathStart = jdbcUrl.indexOf('/', JDBC_PREFIX.length());
        int queryStart = jdbcUrl.indexOf('?', JDBC_PREFIX.length());
        int pathEnd = queryStart < 0 ? jdbcUrl.length() : queryStart;
        if (pathStart < 0 || pathStart >= pathEnd || pathStart == JDBC_PREFIX.length()) {
            throw new IllegalStateException(
                    "MALL_DB_URL must include a host and database name, for example " + DEFAULT_URL);
        }
        String databaseName = jdbcUrl.substring(pathStart + 1, pathEnd);
        if (!databaseName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException("The database name in MALL_DB_URL may contain only letters, digits, and underscores.");
        }
        String serverUrl = jdbcUrl.substring(0, pathStart)
                + (queryStart < 0 ? "" : jdbcUrl.substring(queryStart));
        return new String[]{serverUrl, databaseName};
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    void initialize() throws SQLException {
        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + databaseName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS staff_users (
                        username VARCHAR(80) PRIMARY KEY,
                        password_salt VARCHAR(64) NOT NULL,
                        password_hash VARCHAR(128) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS items (
                        item_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        category VARCHAR(80) NOT NULL,
                        description TEXT NOT NULL,
                        found_location VARCHAR(200) NOT NULL,
                        found_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        status VARCHAR(30) NOT NULL DEFAULT 'Unclaimed',
                        verification_details TEXT NOT NULL,
                        archived BOOLEAN NOT NULL DEFAULT FALSE,
                        INDEX idx_items_status_archived (status, archived),
                        INDEX idx_items_category (category)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS lost_reports (
                        report_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        category VARCHAR(80) NOT NULL,
                        description TEXT NOT NULL,
                        lost_location VARCHAR(200) NOT NULL,
                        contact_name VARCHAR(120) NOT NULL,
                        contact_info VARCHAR(200) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS claim_requests (
                        claim_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        item_id BIGINT NOT NULL,
                        claimant_name VARCHAR(120) NOT NULL,
                        contact_info VARCHAR(200) NOT NULL,
                        proof_details TEXT NOT NULL,
                        status VARCHAR(30) NOT NULL DEFAULT 'Pending',
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_claim_item FOREIGN KEY (item_id)
                            REFERENCES items (item_id) ON DELETE RESTRICT,
                        INDEX idx_claim_status (status)
                    )
                    """);
        }
    }

    boolean hasStaffUsers() throws SQLException {
        try (Connection connection = connect();
             PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM staff_users");
             ResultSet result = count.executeQuery()) {
            result.next();
            return result.getLong(1) != 0;
        }
    }

    void createInitialAdmin(String adminName, char[] adminPassword)
            throws SQLException, EmptyFieldException {
        if (adminPassword == null) {
            throw new EmptyFieldException("Security password cannot be empty.");
        }
        try {
            if (adminName == null || adminName.isBlank()) {
                throw new EmptyFieldException("Security username cannot be empty.");
            }
            if (adminPassword.length == 0) {
                throw new EmptyFieldException("Security password cannot be empty.");
            }
            if (hasStaffUsers()) {
                return;
            }
            PasswordUtil.PasswordHash hash = PasswordUtil.hash(adminPassword);
            try (Connection connection = connect();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO staff_users (username, password_salt, password_hash) VALUES (?, ?, ?)")) {
                insert.setString(1, adminName.trim());
                insert.setString(2, PasswordUtil.encode(hash.salt()));
                insert.setString(3, PasswordUtil.encode(hash.hash()));
                insert.executeUpdate();
            } catch (SQLException exception) {
                throw new SQLException("Could not create initial staff account: " + exception.getMessage(), exception);
            }
        } finally {
            java.util.Arrays.fill(adminPassword, '\0');
        }
    }

    boolean authenticate(String staffName, char[] candidate) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password_salt, password_hash FROM staff_users WHERE username = ?")) {
            statement.setString(1, staffName.trim());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                return PasswordUtil.verify(candidate,
                        PasswordUtil.decode(result.getString("password_salt")),
                        PasswordUtil.decode(result.getString("password_hash")));
            }
        }
    }

    List<Object[]> searchItems(String keyword, String category, String location) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT item_id, category, description, found_location, found_at, status
                FROM items WHERE archived = FALSE AND status <> 'Claimed'
                """);
        List<String> parameters = new ArrayList<>();
        if (!keyword.isBlank()) {
            sql.append(" AND (description LIKE ? OR category LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            parameters.add(like);
            parameters.add(like);
        }
        if (!category.isBlank() && !category.equalsIgnoreCase("All categories")) {
            sql.append(" AND category = ?");
            parameters.add(category.trim());
        }
        if (!location.isBlank()) {
            sql.append(" AND found_location LIKE ?");
            parameters.add("%" + location.trim() + "%");
        }
        sql.append(" ORDER BY found_at DESC");
        return queryRows(sql.toString(), parameters, 6);
    }

    List<Object[]> listActiveItems() throws SQLException {
        return queryRows("""
                SELECT item_id, category, description, found_location, found_at, status, verification_details
                FROM items WHERE archived = FALSE ORDER BY found_at DESC
                """, List.of(), 7);
    }

    List<Object[]> listClaims() throws SQLException {
        return queryRows("""
                SELECT c.claim_id, c.item_id, i.category, c.claimant_name, c.contact_info,
                       c.proof_details, c.status, c.created_at
                FROM claim_requests c
                JOIN items i ON i.item_id = c.item_id
                ORDER BY c.created_at DESC
                """, List.of(), 8);
    }

    List<Object[]> listLostReports() throws SQLException {
        return queryRows("""
                SELECT report_id, category, description, lost_location, contact_name, contact_info, created_at
                FROM lost_reports ORDER BY created_at DESC
                """, List.of(), 7);
    }

    long[] dashboardSummary() throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                         (SELECT COUNT(*) FROM items WHERE archived = FALSE) AS active_items,
                         (SELECT COUNT(*) FROM claim_requests WHERE status = 'Pending') AS pending_claims,
                         (SELECT COUNT(*) FROM lost_reports) AS lost_reports
                     """);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("The database did not return the security dashboard summary.");
            }
            return new long[]{
                    result.getLong("active_items"),
                    result.getLong("pending_claims"),
                    result.getLong("lost_reports")
            };
        }
    }

    private List<Object[]> queryRows(String sql, List<String> parameters, int columns) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setString(index + 1, parameters.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Object[] row = new Object[columns];
                    for (int column = 0; column < columns; column++) {
                        row[column] = result.getObject(column + 1);
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    long registerFoundItem(String category, String description, String location, String verification)
            throws SQLException, EmptyFieldException {
        requireFields(category, "Category", description, "Description",
                location, "Found location", verification, "Ownership verification details");
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO items (category, description, found_location, verification_details)
                     VALUES (?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.trim());
            statement.setString(2, description.trim());
            statement.setString(3, location.trim());
            statement.setString(4, verification.trim());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("The database did not return an ID for the new item.");
    }

    void reportLost(String category, String description, String location, String contactName, String contactInfo)
            throws SQLException, EmptyFieldException {
        requireFields(category, "Category", description, "Description", location, "Last known location",
                contactName, "Your name", contactInfo, "Contact details");
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO lost_reports (category, description, lost_location, contact_name, contact_info)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, category.trim());
            statement.setString(2, description.trim());
            statement.setString(3, location.trim());
            statement.setString(4, contactName.trim());
            statement.setString(5, contactInfo.trim());
            statement.executeUpdate();
        }
    }

    void submitClaim(long itemId, String name, String contact, String proof)
            throws SQLException, EmptyFieldException, InvalidItemException {
        requireFields(name, "Your name", contact, "Contact details", proof, "Proof of ownership");
        try (Connection connection = connect();
             PreparedStatement exists = connection.prepareStatement(
                     "SELECT status FROM items WHERE item_id = ? AND archived = FALSE FOR UPDATE")) {
            connection.setAutoCommit(false);
            try {
                exists.setLong(1, itemId);
                try (ResultSet result = exists.executeQuery()) {
                    if (!result.next()) {
                        throw new InvalidItemException("No active item exists with ID " + itemId + ".");
                    }
                    if ("Claimed".equals(result.getString(1))) {
                        throw new InvalidItemException("This item has already been claimed.");
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO claim_requests (item_id, claimant_name, contact_info, proof_details)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    insert.setLong(1, itemId);
                    insert.setString(2, name.trim());
                    insert.setString(3, contact.trim());
                    insert.setString(4, proof.trim());
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | InvalidItemException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    void updateItemStatus(long itemId, String status) throws SQLException, InvalidItemException {
        if (!List.of("Unclaimed", "Under Verification", "Claimed").contains(status)) {
            throw new IllegalArgumentException("Unsupported item status: " + status);
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE items SET status = ? WHERE item_id = ? AND archived = FALSE")) {
            statement.setString(1, status);
            statement.setLong(2, itemId);
            if (statement.executeUpdate() == 0) {
                throw new InvalidItemException("No active item exists with ID " + itemId + ".");
            }
        }
    }

    void decideClaim(long claimId, String decision) throws SQLException, InvalidItemException {
        if (!List.of("Approved", "Rejected").contains(decision)) {
            throw new IllegalArgumentException("Unsupported claim decision: " + decision);
        }
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long itemId;
                String currentStatus;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT item_id, status FROM claim_requests WHERE claim_id = ? FOR UPDATE")) {
                    select.setLong(1, claimId);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            throw new InvalidItemException("No claim request exists with ID " + claimId + ".");
                        }
                        itemId = result.getLong("item_id");
                        currentStatus = result.getString("status");
                    }
                }
                if (!"Pending".equals(currentStatus)) {
                    throw new InvalidItemException("This claim request has already been reviewed.");
                }
                if ("Approved".equals(decision)) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE items SET status = 'Claimed'
                            WHERE item_id = ? AND status <> 'Claimed'
                            """)) {
                        update.setLong(1, itemId);
                        if (update.executeUpdate() == 0) {
                            throw new InvalidItemException("This item has already been claimed.");
                        }
                    }
                }
                try (PreparedStatement updateClaim = connection.prepareStatement(
                        "UPDATE claim_requests SET status = ? WHERE claim_id = ?")) {
                    updateClaim.setString(1, decision);
                    updateClaim.setLong(2, claimId);
                    updateClaim.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | InvalidItemException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    int archiveClaimedItems() throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE items SET archived = TRUE WHERE status = 'Claimed' AND archived = FALSE")) {
            return statement.executeUpdate();
        }
    }

    private static void requireFields(String... labelValuePairs) throws EmptyFieldException {
        for (int index = 0; index < labelValuePairs.length; index += 2) {
            if (labelValuePairs[index] == null || labelValuePairs[index].isBlank()) {
                throw new EmptyFieldException(labelValuePairs[index + 1] + " cannot be empty.");
            }
        }
    }
}
