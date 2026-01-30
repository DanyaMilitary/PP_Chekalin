
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;

/**
 * МОДЕЛЬ ДАННЫХ
 */
class Transaction {
    int id;
    LocalDate date;
    String category;
    String description;
    double amount;
    boolean isIncome;
    String member;

    public Transaction(int id, LocalDate date, String category, String description, double amount, boolean isIncome, String member) {
        this.id = id; this.date = date; this.category = category;
        this.description = description; this.amount = amount;
        this.isIncome = isIncome; this.member = member;
    }
}

/**
 * РАБОТА С БАЗОЙ ДАННЫХ
 */
class DBManager {
    private static final String URL = "jdbc:sqlite:family_budget_v3.db";

    public DBManager() {
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT, category TEXT, description TEXT, amount REAL, is_income INTEGER, member TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS members (name TEXT PRIMARY KEY)");
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM members");
            if (rs.next() && rs.getInt(1) == 0) {
                st.execute("INSERT INTO members VALUES ('Папа'), ('Мама')");
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public Connection connect() throws SQLException { return DriverManager.getConnection(URL); }

    public void saveTransaction(Transaction t) {
        String sql = "INSERT INTO transactions (date, category, description, amount, is_income, member) VALUES (?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, t.date.toString());
            pst.setString(2, t.category);
            pst.setString(3, t.description);
            pst.setDouble(4, t.amount);
            pst.setInt(5, t.isIncome ? 1 : 0);
            pst.setString(6, t.member);
            pst.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void updateTransaction(Transaction t) {
        String sql = "UPDATE transactions SET date=?, category=?, description=?, amount=?, is_income=?, member=? WHERE id=?";
        try (Connection conn = connect(); PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, t.date.toString());
            pst.setString(2, t.category);
            pst.setString(3, t.description);
            pst.setDouble(4, t.amount);
            pst.setInt(5, t.isIncome ? 1 : 0);
            pst.setString(6, t.member);
            pst.setInt(7, t.id);
            pst.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public List<Transaction> getTransactions() {
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM transactions ORDER BY date DESC")) {
            while (rs.next()) {
                list.add(new Transaction(rs.getInt("id"), LocalDate.parse(rs.getString("date")), rs.getString("category"), rs.getString("description"), rs.getDouble("amount"), rs.getInt("is_income") == 1, rs.getString("member")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public void deleteTransaction(int id) {
        try (Connection conn = connect(); PreparedStatement pst = conn.prepareStatement("DELETE FROM transactions WHERE id = ?")) {
            pst.setInt(1, id); pst.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public List<String> getMembers() {
        List<String> list = new ArrayList<>();
        try (Connection conn = connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT name FROM members")) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public void addMember(String name) {
        try (Connection conn = connect(); PreparedStatement pst = conn.prepareStatement("INSERT OR IGNORE INTO members VALUES (?)")) {
            pst.setString(1, name); pst.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void deleteMember(String name) {
        try (Connection conn = connect(); PreparedStatement pst = conn.prepareStatement("DELETE FROM members WHERE name = ?")) {
            pst.setString(1, name); pst.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}

/**
 * ИНТЕРФЕЙС
 */
public class FamilyBudgetApp extends JFrame {
    private DBManager db = new DBManager();
    private JTable table;
    private DefaultTableModel model;
    private JLabel lblBalance, lblIncome, lblExpense;
    private JComboBox<YearMonth> comboMonth;
    private JComboBox<String> comboFilterCat, comboFilterMem;
    private JTextField txtMinSum, txtMaxSum;

    private final String[] allCats = {"Все категории", "Зарплата", "Фриланс", "Продукты", "Жилье", "Транспорт", "Развлечения", "Здоровье", "Другое"};

    public FamilyBudgetApp() {
        setTitle("Учет семейного бюджета v3.0");
        setSize(1250, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Color.WHITE);

        initUI();
        refreshAll();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // --- ВЕРХНИЕ КАРТОЧКИ ---
        JPanel topPanel = new JPanel(new GridLayout(1, 3, 20, 0));
        topPanel.setBackground(Color.WHITE);
        topPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        lblIncome = createStatCard("ДОХОДЫ", new Color(245, 255, 245), new Color(0, 120, 0));
        lblExpense = createStatCard("РАСХОДЫ", new Color(255, 245, 245), new Color(150, 0, 0));
        lblBalance = createStatCard("ОСТАТОК", new Color(245, 250, 255), new Color(0, 236, 244));

        topPanel.add(lblIncome); topPanel.add(lblExpense); topPanel.add(lblBalance);
        add(topPanel, BorderLayout.NORTH);

        // --- ПАНЕЛЬ ФИЛЬТРОВ (СВЕРХУ ТАБЛИЦЫ) ---
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(Color.WHITE);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        filterPanel.setBackground(new Color(250, 250, 250));

        comboMonth = new JComboBox<>();
        for (int i = 0; i < 24; i++) comboMonth.addItem(YearMonth.now().minusMonths(i));
        comboMonth.addActionListener(e -> refreshAll());

        comboFilterCat = new JComboBox<>(allCats);
        comboFilterCat.addActionListener(e -> refreshAll());

        comboFilterMem = new JComboBox<>();
        updateMemberCombos();
        comboFilterMem.addActionListener(e -> refreshAll());

        txtMinSum = new JTextField(5);
        txtMaxSum = new JTextField(5);
        // Слушатели для фильтрации по сумме при вводе
        txtMinSum.addKeyListener(new java.awt.event.KeyAdapter() { public void keyReleased(java.awt.event.KeyEvent e) { refreshAll(); } });
        txtMaxSum.addKeyListener(new java.awt.event.KeyAdapter() { public void keyReleased(java.awt.event.KeyEvent e) { refreshAll(); } });

        filterPanel.add(new JLabel("Период:")); filterPanel.add(comboMonth);
        filterPanel.add(new JLabel("Категория:")); filterPanel.add(comboFilterCat);
        filterPanel.add(new JLabel("Кто:")); filterPanel.add(comboFilterMem);
        filterPanel.add(new JLabel("Мин. сумма:")); filterPanel.add(txtMinSum);
        filterPanel.add(new JLabel("Макс. сумма:")); filterPanel.add(txtMaxSum);

        centerPanel.add(filterPanel, BorderLayout.NORTH);

        // --- ТАБЛИЦА ---
        model = new DefaultTableModel(new String[]{"ID", "Дата", "Участник", "Категория", "Сумма", "Тип", "Описание"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setRowHeight(35);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        centerPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // --- БОКОВЫЕ КНОПКИ ---
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setBackground(Color.WHITE);
        sidePanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 20));

        sidePanel.add(createBtn("➕ Доход", e -> showTransactionDialog(null, true)));
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(createBtn("➖ Расход", e -> showTransactionDialog(null, false)));
        sidePanel.add(Box.createVerticalStrut(30));
        sidePanel.add(createBtn("✏️ Изменить", e -> editSelected()));
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(createBtn("🗑 Удалить", e -> deleteSelected()));
        sidePanel.add(Box.createVerticalStrut(30));
        sidePanel.add(createBtn("📊 Excel Экспорт", e -> exportCSV()));
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(createBtn("👥 Семья", e -> showMemberManager()));

        add(sidePanel, BorderLayout.EAST);
    }

    private JLabel createStatCard(String title, Color bg, Color fg) {
        JLabel label = new JLabel("<html><center>" + title + "<br><span style='font-size:16pt;'>0.00</span></center></html>", SwingConstants.CENTER);
        label.setOpaque(true); label.setBackground(bg); label.setForeground(fg);
        label.setBorder(BorderFactory.createLineBorder(new Color(230,230,230)));
        label.setPreferredSize(new Dimension(150, 70));
        return label;
    }

    private JButton createBtn(String text, java.awt.event.ActionListener al) {
        JButton b = new JButton(text);
        b.setMaximumSize(new Dimension(180, 40));
        b.setBackground(Color.WHITE);
        b.setForeground(Color.BLACK);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        b.addActionListener(al);
        return b;
    }

    private void updateMemberCombos() {
        comboFilterMem.removeAllItems();
        comboFilterMem.addItem("Все участники");
        for (String m : db.getMembers()) comboFilterMem.addItem(m);
    }

    private void refreshAll() {
        YearMonth selMonth = (YearMonth) comboMonth.getSelectedItem();
        String selCat = (String) comboFilterCat.getSelectedItem();
        String selMem = (String) comboFilterMem.getSelectedItem();

        double minLimit = 0, maxLimit = Double.MAX_VALUE;
        try { if(!txtMinSum.getText().isEmpty()) minLimit = Double.parseDouble(txtMinSum.getText()); } catch(Exception e){}
        try { if(!txtMaxSum.getText().isEmpty()) maxLimit = Double.parseDouble(txtMaxSum.getText()); } catch(Exception e){}

        List<Transaction> all = db.getTransactions();
        model.setRowCount(0);
        double inc = 0, exp = 0;

        for (Transaction t : all) {
            boolean monthM = YearMonth.from(t.date).equals(selMonth);
            boolean catM = selCat.equals("Все категории") || t.category.equals(selCat);
            boolean memM = selMem.equals("Все участники") || t.member.equals(selMem);
            boolean sumM = t.amount >= minLimit && t.amount <= maxLimit;

            if (monthM && catM && memM && sumM) {
                model.addRow(new Object[]{t.id, t.date, t.member, t.category, String.format("%.2f", t.amount), t.isIncome ? "Доход" : "Расход", t.description});
                if (t.isIncome) inc += t.amount; else exp += t.amount;
            }
        }
        lblIncome.setText("<html><center>ДОХОДЫ<br><span style='font-size:16pt;'>" + String.format("%.2f", inc) + "</span></center></html>");
        lblExpense.setText("<html><center>РАСХОДЫ<br><span style='font-size:16pt;'>" + String.format("%.2f", exp) + "</span></center></html>");
        lblBalance.setText("<html><center>ОСТАТОК<br><span style='font-size:16pt;'>" + String.format("%.2f", inc - exp) + "</span></center></html>");
    }

    // Универсальное окно добавления и редактирования
    private void showTransactionDialog(Transaction existing, boolean isIncIfNew) {
        String title = (existing == null) ? "Новая запись" : "Редактирование";
        JDialog d = new JDialog(this, title, true);
        d.setLayout(new GridLayout(6, 2, 10, 10));

        JTextField tDate = new JTextField((existing == null) ? LocalDate.now().toString() : existing.date.toString());
        JTextField tSum = new JTextField((existing == null) ? "" : String.valueOf(existing.amount));
        JComboBox<String> cCat = new JComboBox<>(Arrays.copyOfRange(allCats, 1, allCats.length));
        if(existing != null) cCat.setSelectedItem(existing.category);

        JComboBox<String> cMem = new JComboBox<>(db.getMembers().toArray(new String[0]));
        if(existing != null) cMem.setSelectedItem(existing.member);

        JTextField tDesc = new JTextField((existing == null) ? "" : existing.description);

        d.add(new JLabel(" Дата (ГГГГ-ММ-ДД):")); d.add(tDate);
        d.add(new JLabel(" Сумма:")); d.add(tSum);
        d.add(new JLabel(" Категория:")); d.add(cCat);
        d.add(new JLabel(" Кто:")); d.add(cMem);
        d.add(new JLabel(" Описание:")); d.add(tDesc);

        JButton btnSave = new JButton("Сохранить");
        btnSave.addActionListener(e -> {
            try {
                LocalDate date = LocalDate.parse(tDate.getText());
                double amount = Double.parseDouble(tSum.getText());
                boolean inc = (existing == null) ? isIncIfNew : existing.isIncome;

                if (existing == null) {
                    db.saveTransaction(new Transaction(0, date, (String)cCat.getSelectedItem(), tDesc.getText(), amount, inc, (String)cMem.getSelectedItem()));
                } else {
                    db.updateTransaction(new Transaction(existing.id, date, (String)cCat.getSelectedItem(), tDesc.getText(), amount, inc, (String)cMem.getSelectedItem()));
                }
                refreshAll(); d.dispose();
            } catch (DateTimeParseException ex) { JOptionPane.showMessageDialog(d, "Неверный формат даты! Используйте ГГГГ-ММ-ДД");
            } catch (Exception ex) { JOptionPane.showMessageDialog(d, "Ошибка в данных!"); }
        });

        d.add(new JLabel()); d.add(btnSave);
        d.pack(); d.setLocationRelativeTo(this); d.setVisible(true);
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int id = (int) model.getValueAt(row, 0);
        Transaction target = db.getTransactions().stream().filter(t -> t.id == id).findFirst().orElse(null);
        if (target != null) showTransactionDialog(target, target.isIncome);
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row >= 0 && JOptionPane.showConfirmDialog(this, "Удалить выбранную запись?") == 0) {
            db.deleteTransaction((int) model.getValueAt(row, 0));
            refreshAll();
        }
    }

    private void showMemberManager() {
        JDialog d = new JDialog(this, "Участники", true);
        d.setLayout(new BorderLayout());
        DefaultListModel<String> lModel = new DefaultListModel<>();
        db.getMembers().forEach(lModel::addElement);
        JList<String> list = new JList<>(lModel);

        JButton addB = new JButton("Добавить");
        addB.addActionListener(e -> {
            String n = JOptionPane.showInputDialog("Имя:");
            if(n != null && !n.isEmpty()) { db.addMember(n); lModel.addElement(n); updateMemberCombos(); }
        });

        JButton delB = new JButton("Удалить");
        delB.addActionListener(e -> {
            String s = list.getSelectedValue();
            if(s != null) { db.deleteMember(s); lModel.removeElement(s); updateMemberCombos(); }
        });

        JPanel p = new JPanel(); p.add(addB); p.add(delB);
        d.add(new JScrollPane(list), BorderLayout.CENTER);
        d.add(p, BorderLayout.SOUTH);
        d.setSize(250, 350); d.setLocationRelativeTo(this); d.setVisible(true);
    }

    private void exportCSV() {
        JFileChooser jfc = new JFileChooser();
        if (jfc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = jfc.getSelectedFile();
            if (!f.getName().endsWith(".csv")) f = new File(f.getAbsolutePath() + ".csv");
            try (FileWriter fw = new FileWriter(f)) {
                fw.write('\ufeff'); // Excel UTF-8
                for (int i = 0; i < model.getColumnCount(); i++) fw.write(model.getColumnName(i) + ";");
                fw.write("\n");
                for (int i = 0; i < model.getRowCount(); i++) {
                    for (int j = 0; j < model.getColumnCount(); j++) fw.write(model.getValueAt(i, j).toString() + ";");
                    fw.write("\n");
                }
                JOptionPane.showMessageDialog(this, "Готово!");
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new FamilyBudgetApp().setVisible(true));
    }
}
