package com.mkylm.intunebulk.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

final class GuiResultsPanel {
  static JPanel buildSearchPanel(GuiRuntime runtime) {
    JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.setBorder(BorderFactory.createTitledBorder("Search Query Results"));

    JLabel searchLabel = new JLabel("Filter:");
    JTextField searchField = new JTextField();

    JPanel filterRow = new JPanel(new BorderLayout(8, 0));
    filterRow.add(searchLabel, BorderLayout.WEST);
    filterRow.add(searchField, BorderLayout.CENTER);

    panel.add(filterRow, BorderLayout.NORTH);
    panel.setPreferredSize(new Dimension(860, 80));

    runtime.resultsFilterField = searchField;
    runtime.applyResultsFilter();
    searchField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                runtime.applyResultsFilter();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                runtime.applyResultsFilter();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                runtime.applyResultsFilter();
              }
            });

    return panel;
  }

  static JPanel buildResultsPanel(GuiRuntime runtime, Consumer<Component> exportHandler) {
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.setBorder(BorderFactory.createTitledBorder("Query Results"));

    runtime.resultsModel = createModel(new String[] {"Name", "Value 1", "Value 2"});
    runtime.resultsTable = new JTable(runtime.resultsModel);
    runtime.resultsSorter = new TableRowSorter<>(runtime.resultsModel);
    runtime.resultsTable.setRowSorter(runtime.resultsSorter);
    runtime.applyResultsFilter();
    runtime.resultsTable.setRowHeight(24);
    ResultsContextMenu.install(runtime);
    JScrollPane pane = new JScrollPane(runtime.resultsTable);
    pane.setPreferredSize(new Dimension(860, 320));
    panel.add(pane, BorderLayout.CENTER);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    JButton exportButton = new JButton("Export CSV");
    exportButton.addActionListener(event -> exportHandler.accept(exportButton));
    buttons.add(exportButton);
    panel.add(buttons, BorderLayout.SOUTH);
    return panel;
  }

  private static DefaultTableModel createModel(String[] columns) {
    return new DefaultTableModel(columns, 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };
  }

  private GuiResultsPanel() {}
}
