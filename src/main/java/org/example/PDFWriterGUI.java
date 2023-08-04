package org.example;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PDFWriterGUI extends JFrame {

    private final TextField databaseField;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JTextArea sqlQueryArea;
    private final JTable dataTable;
    private final EditableTableModel tableModel;

    private PDDocument document;
    private final JButton executeButton = new JButton("Execute Query");
    private final JButton saveButton = new JButton("Save to PDF");
    private final JButton editHeadersButton = new JButton("Edit Headers");
    private JTextField[] headerFields;
    private GridBagConstraints gbc;

    public PDFWriterGUI(){
        setTitle("Database to PDF Converter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Connection Panel
        JPanel connectionPanel = new JPanel(new GridBagLayout());
        connectionPanel.setBorder(BorderFactory.createTitledBorder("Database Connection"));
        databaseField = new TextField("jdbc:mysql://localhost:3306/");
        usernameField = new JTextField();
        passwordField = new JPasswordField();
        gbc.gridy = 0;
        connectionPanel.add(new JLabel("Database URL: "), gbc);
        gbc.gridy = 1;
        connectionPanel.add(databaseField, gbc);
        gbc.gridy = 2;
        connectionPanel.add(new JLabel("Username: "), gbc);
        gbc.gridy = 3;
        connectionPanel.add(usernameField, gbc);
        gbc.gridy = 4;
        connectionPanel.add(new JLabel("Password"), gbc);
        gbc.gridy = 5;
        connectionPanel.add(passwordField, gbc);

        // Query Panel
        JPanel queryPanel = new JPanel(new GridBagLayout());
        queryPanel.setBorder(BorderFactory.createTitledBorder("SQL Query"));
        sqlQueryArea = new JTextArea("SELECT * FROM ", 10, 40);
        sqlQueryArea.setLineWrap(true);
        JScrollPane queryScrollPane = new JScrollPane(sqlQueryArea);
        queryScrollPane.setPreferredSize(new Dimension(400, 150));
        queryScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        gbc.gridx = 0;
        gbc.gridy = 0;
        queryPanel.add(queryScrollPane, gbc);

        // Data Table Panel
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Data Preview"));

        tableModel = new EditableTableModel();
        dataTable = new JTable();
        dataTable.setModel(tableModel);

        // Set the custom renderer and editor for the header cells
        dataTable.getTableHeader().setDefaultRenderer(new HeaderRenderer());

        JScrollPane tableScrollPane = new JScrollPane(dataTable);
        tableScrollPane.setPreferredSize(new Dimension(600, 200));
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);

        // Save Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(executeButton);
        buttonPanel.add(saveButton);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        add(connectionPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        add(queryPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        add(tablePanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        add(buttonPanel, gbc);

        executeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executeQuery();
            }
        });

        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveToPdf();
            }
        });

        document = new PDDocument();

        displayDataInTable(new ArrayList<>());

        pack();
        setLocationRelativeTo(null);
    }

    private List<List<Object>> fetchDataFromDatabase() throws Exception {
        String databaseUrl = databaseField.getText();
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        String sqlQuery = sqlQueryArea.getText();

        try (Connection connection = DriverManager.getConnection(databaseUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlQuery)) {

            ResultSetMetaData metaData = resultSet.getMetaData();
            int numColumns = metaData.getColumnCount();

            List<List<Object>> data = new ArrayList<>();

            // Step 1: Add Column Headers to the Data List
            List<Object> columnHeaders = new ArrayList<>();
            for (int colIndex = 1; colIndex <= numColumns; colIndex++) {
                columnHeaders.add(metaData.getColumnName(colIndex));
            }
            data.add(columnHeaders);

            // Step 2: Add Data Rows to the Data List
            while (resultSet.next()) {
                List<Object> row = new ArrayList<>();
                for (int colIndex = 1; colIndex <= numColumns; colIndex++) {
                    row.add(resultSet.getObject(colIndex));
                }
                data.add(row);
            }
            resultSet.close();
            statement.close();

            return data;
        }
    }

    private void executeQuery() {
        try {
            List<List<Object>> data = fetchDataFromDatabase();
            if (data.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No data retrieved from the database.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            displayDataInTable(data);

            JOptionPane.showMessageDialog(this, "Data retrieved from the database successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error Occurred: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveToPdf() {
        try {
            if (tableModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this, "No Data to save!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int result = fileChooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                String fileName = selectedFile.getAbsolutePath();

                PDPage page = new PDPage();
                document.addPage(page);
                PDPageContentStream pageContentStream = new PDPageContentStream(document, page);

                float margin = 50;
                float yStart = page.getMediaBox().getHeight() - margin;
                float tableWidth = page.getMediaBox().getWidth() - 2 * margin;
                float tableHeight = 100f;
                float yPosition = yStart;
                float rowHeight = 20f;
                int rowPerPage = 10;
                int startPage = 0;
                int endPage = (int) Math.ceil(tableModel.getRowCount() / (float) rowPerPage);

                Object[][] tableData = new Object[tableModel.getRowCount()][tableModel.getColumnCount()];
                for (int rowIndex = 0; rowIndex < tableModel.getRowCount(); rowIndex++) {
                    for (int colIndex = 0; colIndex < tableModel.getColumnCount(); colIndex++) {
                        tableData[rowIndex][colIndex] = tableModel.getValueAt(rowIndex, colIndex);
                    }
                }

                for (int pageCounter = startPage; pageCounter < endPage; pageCounter++) {
                    if (pageCounter > 0) {
                        // Start a new page
                        page = new PDPage();
                        document.addPage(page);
                        pageContentStream.close();
                        pageContentStream = new PDPageContentStream(document, page);
                    }

                    // Write data rows to Pdf
                    writeData(pageContentStream, yPosition, margin, tableWidth, rowHeight, rowPerPage, pageCounter, tableData);
                }

                pageContentStream.close();
                document.save(fileName);
                document.close();

                JOptionPane.showMessageDialog(this, "Data saved to PDF Successfully!");
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error Occurred: " + e.getMessage());
        }
    }

    private void writeData(PDPageContentStream contentStream, float yPosition, float margin, float tableWidth, float rowHeight, int rowsPerPage, int pageCounter, Object[][] tableData) throws IOException {
        int startRow = (pageCounter * rowsPerPage) - 1; // Corrected calculation
        int endRow = Math.min((pageCounter + 1) * rowsPerPage, tableData.length);

        float yStart = yPosition;
        float tableYStart = yStart - 3 * rowHeight;

        // Increase the width of the second column (column 2)
        int secondColumnIndex = 1;
        float increasedWidth = 50f; // Set the desired width for the second column

        // Set the underline color to black
        contentStream.setStrokingColor(Color.BLACK);


        for (int colIndex = 0; colIndex < columnHeaders.length; colIndex++) {
            String headerValue = String.valueOf(columnHeaders[colIndex]);
            float colWidth = tableWidth / (float) columnHeaders.length;

            // Draw the cell background
            contentStream.addRect(margin + colWidth * colIndex, tableYStart, colWidth, rowHeight);
            contentStream.setNonStrokingColor(Color.WHITE);
            contentStream.fill();

            // Set the font and text color for the header
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
            contentStream.setNonStrokingColor(Color.BLACK);

            // Move to the next line and set the position to start writing text
            contentStream.beginText();
            contentStream.newLineAtOffset(margin + colWidth * colIndex + 2, tableYStart + rowHeight / 2 - 5); // Center the text vertically
            contentStream.showText(headerValue);
            contentStream.endText();
        }

        // Draw lines at the bottom of the header row
        contentStream.setLineWidth(1f);
        contentStream.moveTo(margin, tableYStart);
        contentStream.lineTo(margin + tableWidth, tableYStart);
        contentStream.stroke();

        // Move to the next row
        tableYStart -= rowHeight;

        // Write data rows
        for (int rowIndex = startRow + 1; rowIndex < endRow; rowIndex++) {
            Object[] rowData = tableData[rowIndex];
            for (int colIndex = 0; colIndex < rowData.length; colIndex++) {
                String cellValue = String.valueOf(rowData[colIndex]);
                float colWidth = tableWidth / (float) columnHeaders.length;

                // Increase the width of the second column (column 2)
                if (colIndex == secondColumnIndex) {
                    colWidth = increasedWidth;
                }

                // Draw the cell background
                contentStream.addRect(margin + colWidth * colIndex, tableYStart, colWidth, rowHeight);
                contentStream.setNonStrokingColor(Color.LIGHT_GRAY);
                contentStream.fill();

                // Set the font and text color
                contentStream.setFont(PDType1Font.HELVETICA, 8);
                contentStream.setNonStrokingColor(Color.BLACK);

                // Move to the next line and set the position to start writing text
                contentStream.beginText();
                contentStream.newLineAtOffset(margin + colWidth * colIndex + 2, tableYStart + 2); // Add a small margin
                contentStream.showText(cellValue);
                contentStream.endText();
            }

            // Draw lines at the bottom of the row
            contentStream.setLineWidth(1f);
            contentStream.moveTo(margin, tableYStart);
            contentStream.lineTo(margin + tableWidth, tableYStart);
            contentStream.stroke();

            // Move to the next row
            tableYStart -= rowHeight;
        }

        // Draw lines at the bottom of the data rows
        contentStream.setLineWidth(1f);
        contentStream.moveTo(margin, yPosition - rowHeight); // Move to the bottom of the last row on the page
        contentStream.lineTo(margin + tableWidth, yPosition - rowHeight);
        contentStream.stroke();
    }

    private void displayDataInTable(List<List<Object>> data) {
        if (data.isEmpty() || data.get(0).isEmpty()) {
            // If there's no data or no column headers, show an empty table
            tableModel.setData(new Object[0][0], new Object[0]);
        } else {
            // Convert the List<List<Object>> to a 2D array
            Object[][] tableData = new Object[data.size() - 1][];
            for (int i = 1; i < data.size(); i++) { // Start from 1 to skip the header row
                tableData[i - 1] = data.get(i).toArray();
            }

            // Get the column headers from the first row
            List<Object> originalHeaders = data.get(0);
            List<Object> modifiedHeaders = new ArrayList<>();

            // Capitalize the first letter of each column header and add to modifiedHeaders
            for (Object header : originalHeaders) {
                String headerStr = String.valueOf(header);
                if (!headerStr.isEmpty()) {
                    modifiedHeaders.add(headerStr.substring(0, 1).toUpperCase() + headerStr.substring(1));
                } else {
                    modifiedHeaders.add(headerStr);
                }
            }

            // Update the table model with the new data and modified column headers
            tableModel.setData(tableData, modifiedHeaders.toArray());
            createHeaderEditFields(modifiedHeaders);
        }
    }

    private void createHeaderEditFields(List<Object> headers) {
        headerFields = new JTextField[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            headerFields[i] = new JTextField(headers.get(i).toString(), 10);
        }
        editHeadersButton.setEnabled(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PDFWriterGUI pdfWriterGUI = new PDFWriterGUI();
            pdfWriterGUI.setVisible(true);
        });
    }

    static class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            component.setBackground(Color.LIGHT_GRAY);
            component.setForeground(Color.BLACK);
            return component;
        }
    }
}

class EditableTableModel extends AbstractTableModel {

    private Object[][] data = new Object[0][0];
    private Object[] columnHeaders = new Object[0];

    public void setData(Object[][] data, Object[] columnHeaders) {
        this.data = data;
        this.columnHeaders = columnHeaders;
        fireTableStructureChanged(); // Use fireTableStructureChanged() instead of fireTableDataChanged()
    }

    @Override
    public int getRowCount() {
        return data.length;
    }

    @Override
    public int getColumnCount() {
        return columnHeaders.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return data[rowIndex][columnIndex];
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        data[rowIndex][columnIndex] = value;
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    @Override
    public String getColumnName(int columnIndex) {
        return String.valueOf(columnHeaders[columnIndex]);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }
}

