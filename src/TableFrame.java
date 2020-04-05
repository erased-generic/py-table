import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.Buffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// File filter for specific extension class
class ExtFilter extends FileFilter {
    private String extension;

    ExtFilter(String extension) {
        this.extension = extension;
    }

    public boolean accept(File f) {
        // Accept directories
        if (f.isDirectory())
            return true;
        String s = f.getName();
        int i = s.lastIndexOf('.');

        // If extension exists, check equality
        if (i > 0 && i < s.length() - 1) {
            return s.substring(i + 1).equalsIgnoreCase(extension);
        }

        return false;
    }

    public String getDescription() {
        return "*." + extension + " files";
    }
}

// Main frame class
public class TableFrame extends JFrame {
    // Displays message box with message and title
    private void messageBox(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    // Logs message
    private void log(String message) {
        System.out.println(message);
    }
    // Logs operation result
    private boolean log(boolean res) {
        System.out.println(res ? "Success!" : "Failed!");
        return res;
    }

    // Closes this window immediately
    private void closeWindow() {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    // Prints input to python process standard input, returns its standard output
    private String executePrint(Writer writer, BufferedReader reader, BufferedReader error) throws IOException {
        writer.write("\n");
        writer.flush();
        StringBuilder accum = new StringBuilder();
        // Wait for python to print
        long startTime = System.currentTimeMillis();
        while (!reader.ready()) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed >= 5) {
                // Error occurred, read error
                String s;
                while ((s = error.readLine()) != null) {
                    accum.append(s).append('\n');
                }
                exitWithError(accum.toString());
            }
        }
        // Read all
        boolean isFirst = true;
        while (reader.ready()) {
            // every line break creates empty line, read it and append line break
            if (!isFirst) {
                reader.readLine();
                accum.append('\n');
            }
            isFirst = false;
            accum.append(reader.readLine());
        }
        return accum.toString();
    }

    // Checks if file is in environment path
    private boolean checkFileInPath(String name) {
        log("Checking if '" + name + "' exists in path...");
        String[] paths = System.getenv("PATH").split(";");
        for (String path : paths) {
            if (new File(path + "\\" + name).exists()) {
                return log(true);
            }
        }
        return log(false);
    }

    // Checks if python interpreter has pandas installed
    private boolean checkHasPandas(String pythonPath) throws IOException, InterruptedException {
        log("Checking if pandas for python is installed...");
        return log(Runtime.getRuntime().exec(new String[]{pythonPath, "-c", "import pandas"}).waitFor() == 0);
    }

    // Installs pandas
    private boolean installPandas(String pythonPath) throws IOException, InterruptedException {
        log("Installing pandas for python...");
        return log(Runtime.getRuntime().exec(new String[]{pythonPath, "-m", "pip", "install", "pandas"}).waitFor() == 0);
    }

    // Executes code.py with specific .csv file name and python interpreter
    private JTable executeCode(String csvName, String pythonPath) throws IOException, InterruptedException {
        log("Parsing input table...");
        Process proc = Runtime.getRuntime().exec(new String[] { pythonPath, "code.py", csvName });
        try(
                BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
                BufferedReader error = new BufferedReader(new InputStreamReader(proc.getErrorStream()))
            ) {
            // Read table width
            int w = Integer.parseInt(executePrint(writer, reader, error));
            Vector<Vector<String>> data = new Vector<>();
            Vector<String> columnNames = new Vector<>();
            // Read column names
            for (int col = 0; col < w; col++) {
                String colName = executePrint(writer, reader, error);
                columnNames.add(colName);
            }
            // Read chunks
            int row = 0;
            while (true) {
                int chunkH = Integer.parseInt(executePrint(writer, reader, error));
                if (chunkH == -1) {
                    break;
                }
                for (int chunkRow = 0; chunkRow < chunkH; chunkRow++, row++) {
                    data.add(new Vector<>(w));
                    for (int col = 0; col < w; col++) {
                        data.get(row).add(executePrint(writer, reader, error));
                    }
                }
            }
            proc.waitFor();
            log("Done!");
            // Create new table
            JTable table = new JTable(new DefaultTableModel(data, columnNames) {
                // Editable
                @Override
                public boolean isCellEditable(int row, int column) {
                    return true;
                }

                // Table of strings
                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return String.class;
                }
            });
            // Set to multiline renderer
            table.setDefaultRenderer(String.class, new MultiLineCellRenderer());
            return table;
        }
    }

    // Selects file with specific extension
    private boolean selectFile(JFileChooser chooser, String extension, String message) {
        log("Selecting '" + message + "' file...");
        chooser.setDialogTitle("Choose " + message);
        FileFilter filter = new ExtFilter(extension);
        chooser.setFileFilter(filter);
        while (true) {
            int returnVal = chooser.showOpenDialog(this);
            if (returnVal != JFileChooser.APPROVE_OPTION) {
                return log(false);
            }
            if (!filter.accept(chooser.getSelectedFile())) {
                log("Wrong extension! Trying again...");
            } else {
                return log(true);
            }
        }
    }

    // Exits from program and display error message
    private void exitWithError(String message) {
        messageBox(message, "Error");
        closeWindow();
    }

    // Class constructor
    public TableFrame() {
        super("JB Task");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Choose csv table file
        JFileChooser chooser = new JFileChooser();
        if (!selectFile(chooser, "csv", "*.csv file")) {
            exitWithError("Input table is needed");
        }
        String csv = chooser.getSelectedFile().getAbsolutePath(), pyPath;

        // Find python interpreter
        if (checkFileInPath("py.exe")) {
            pyPath = "py";
        } else if (checkFileInPath("python.exe")) {
            pyPath = "python";
        } else {
            if (!selectFile(chooser, "exe", "python interpreter")) {
                exitWithError("Python interpreter is needed");
            }
            pyPath = chooser.getSelectedFile().getAbsolutePath();
        }

        JTable table;
        try {
            // Check for pandas
            if (!checkHasPandas(pyPath)) {
                if (!installPandas(pyPath)) {
                    exitWithError("Could not install python libraries");
                }
            }

            // Read and create table
            table = executeCode(csv, pyPath);
        } catch (IOException e) {
            exitWithError("Cannot parse input table, reason: " + e.getMessage());
            return;
        } catch (InterruptedException e) {
            exitWithError("Associated process interrupted: " + e.getMessage());
            return;
        }

        // Add table to window
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        getContentPane().add(new JScrollPane(table));

        JButton unsortButton = new JButton();
        unsortButton.setVisible(true);
        unsortButton.setText("Unsort");
        unsortButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                table.getRowSorter().setSortKeys(null);
            }
        });

        getContentPane().add(unsortButton, BorderLayout.NORTH);
        //setPreferredSize(new Dimension(260, 220));
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Entry point
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame.setDefaultLookAndFeelDecorated(true);
                new TableFrame();
            }
        });
    }

    // Multiline cell renderer for JTable class
    public static class MultiLineCellRenderer extends JTextArea implements TableCellRenderer {
        // Saved cell heights for every row, column
        private List<List<Integer>> cellHeights = new ArrayList<>();

        // Class constructor
        public MultiLineCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }

        // Update cell
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            // default overridden controls
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }
            setFont(table.getFont());
            if (hasFocus) {
                setBorder( UIManager.getBorder("Table.focusCellHighlightBorder") );
                if (table.isCellEditable(row, column)) {
                    setForeground( UIManager.getColor("Table.focusCellForeground") );
                    setBackground( UIManager.getColor("Table.focusCellBackground") );
                }
            } else {
                setBorder(new EmptyBorder(1, 2, 1, 2));
            }
            setText((value == null) ? "" : value.toString());
            // Update row height based on this update
            updateRowHeight(table, row, column);
            return this;
        }

        // Updates row height based on updated element, evaluating max row cell height
        private void updateRowHeight(JTable table, int row, int column) {
            // Allocate memory for saved heights
            while (cellHeights.size() <= row) {
                cellHeights.add(new ArrayList<>());
            }
            List<Integer> heights = cellHeights.get(row);
            while (heights.size() <= column) {
                heights.add(0);
            }

            // Get cell preferred height based on its width
            int width = table.getTableHeader().getColumnModel().getColumn(column).getWidth();
            setSize(new Dimension(width, 1));
            int preferredHeight = getPreferredSize().height;
            // Save preferred height
            heights.set(column, preferredHeight);
            // Reevaluate max height for this row
            for (Integer h : heights) {
                preferredHeight = Integer.max(preferredHeight, h);
            }
            // Set row height to maximum of its cell heights
            if (table.getRowHeight(row) != preferredHeight) {
                table.setRowHeight(row, preferredHeight);
            }
        }
    }
}