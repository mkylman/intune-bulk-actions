package com.mkylm.intunebulk.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mkylm.intunebulk.core.ActionOptions;
import com.mkylm.intunebulk.core.ActionRequest;
import com.mkylm.intunebulk.core.ActionResult;
import com.mkylm.intunebulk.core.ActionState;
import com.mkylm.intunebulk.core.ActionType;
import com.mkylm.intunebulk.core.DeviceRef;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/** Desktop GUI shell to complement the existing CLI workflow. */
public final class IntuneBulkGuiApp {
  public static void launch() {
    System.out.println("[GUI] Launch requested.");
    System.out.println("[GUI] Headless mode: " + GraphicsEnvironment.isHeadless());

    CountDownLatch startup = new CountDownLatch(1);
    AtomicReference<Throwable> startupError = new AtomicReference<>();

    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception ignored) {
      // Fall back to default LAF if system LAF is unavailable.
    }

    SwingUtilities.invokeLater(
        () -> {
          try {
            showWindow();
            System.out.println("[GUI] Window created.");
          } catch (Throwable t) {
            startupError.set(t);
            System.err.println("[GUI] Failed to start window: " + t.getMessage());
            t.printStackTrace();
          } finally {
            startup.countDown();
          }
        });

    try {
      boolean started = startup.await(10, TimeUnit.SECONDS);
      if (!started) {
        throw new IllegalStateException("GUI startup timed out after 10 seconds.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting for GUI startup.", e);
    }

    if (startupError.get() != null) {
      throw new RuntimeException("GUI startup failed.", startupError.get());
    }

    waitUntilWindowsClose();
  }

  private static void showWindow() {
    JFrame frame = new JFrame("Intune Bulk Actions");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setMinimumSize(new Dimension(980, 700));
    frame.setLocationRelativeTo(null);
    frame.setLayout(new BorderLayout(10, 10));

    GuiRuntime runtime = new GuiRuntime();

    JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
    centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
    centerPanel.add(buildActionPanel(runtime), BorderLayout.NORTH);

    JSplitPane split =
        new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            GuiResultsPanel.buildSearchPanel(runtime),
            GuiResultsPanel.buildResultsPanel(runtime, parent -> exportResultsToCsv(runtime, parent)));
    split.setResizeWeight(0.15);
    centerPanel.add(split, BorderLayout.CENTER);

    frame.add(centerPanel, BorderLayout.CENTER);
    frame.add(buildCommandPanel(), BorderLayout.SOUTH);

    frame.pack();
    frame.setVisible(true);
  }

  private static JPanel buildActionPanel(GuiRuntime runtime) {
    GuiActionPanel.Controls controls = GuiActionPanel.build();
    JButton usersButton = controls.usersButton;
    JButton devicesButton = controls.devicesButton;
    JButton groupMembersButton = controls.groupMembersButton;
    JButton syncGroupButton = controls.syncGroupButton;
    JButton rebootGroupButton = controls.rebootGroupButton;
    JButton removePrimaryUserGroupButton = controls.removePrimaryUserGroupButton;
    JComboBox<GroupOption> groupDropdown = controls.groupDropdown;
    JLabel statusLabel = controls.statusLabel;
    runtime.attachElapsedLabel(controls.elapsedLabel);

    usersButton.addActionListener(
        event ->
            runUsers(
                runtime,
                statusLabel,
//              groupsButton,
                usersButton,
                devicesButton,
                groupMembersButton,
                syncGroupButton,
                rebootGroupButton,
                groupDropdown));
    devicesButton.addActionListener(
        event ->
            runDevices(
                runtime,
                statusLabel,
//              groupsButton,
                usersButton,
                devicesButton,
                groupMembersButton,
                syncGroupButton,
                rebootGroupButton,
                groupDropdown));
    groupMembersButton.addActionListener(
        event ->
            runGroupDevices(
                runtime,
                statusLabel,
//              groupsButton,
                usersButton,
                devicesButton,
                groupMembersButton,
                syncGroupButton,
                rebootGroupButton,
                groupDropdown));
    syncGroupButton.addActionListener(
        event ->
            runSyncGroup(
                runtime,
                statusLabel,
//              groupsButton,
                usersButton,
                devicesButton,
                groupMembersButton,
                syncGroupButton,
                rebootGroupButton,
                groupDropdown));
    rebootGroupButton.addActionListener(
        event ->
            runRebootGroup(
                runtime,
                statusLabel,
//              groupsButton,
                usersButton,
                devicesButton,
                groupMembersButton,
                syncGroupButton,
                rebootGroupButton,
                groupDropdown));
    removePrimaryUserGroupButton.addActionListener(
        event ->
            runRemovePrimaryUserGroup(
                runtime,
                statusLabel,
//              groupsButton,
                usersButton,
                devicesButton,
                groupMembersButton,
                syncGroupButton,
                rebootGroupButton,
                groupDropdown));

    loadGroupsIntoDropdown(
        runtime,
        statusLabel,
//        groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown);
    return controls.panel;
  }

  private static void exportResultsToCsv(GuiRuntime runtime, Component parent) {
    DefaultTableModel model = runtime.resultsModel;
    if (model == null || model.getRowCount() == 0) {
      JOptionPane.showMessageDialog(
          parent, "There are no query results to export.", "Export CSV", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Export Query Results");
    chooser.setSelectedFile(
        new File(
            "intune-query-results-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".csv"));
    chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));

    Window window = SwingUtilities.getWindowAncestor(parent);
    if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) {
      return;
    }

    Path path = chooser.getSelectedFile().toPath();
    String fileName = path.getFileName().toString();
    if (!fileName.toLowerCase().endsWith(".csv")) {
      path = path.resolveSibling(fileName + ".csv");
    }

    if (Files.exists(path)) {
      int overwrite =
          JOptionPane.showConfirmDialog(
              window,
              "File already exists. Overwrite?",
              "Export CSV",
              JOptionPane.YES_NO_OPTION,
              JOptionPane.WARNING_MESSAGE);
      if (overwrite != JOptionPane.YES_OPTION) {
        return;
      }
    }

    try {
      writeTableModelAsCsv(model, path);
      JOptionPane.showMessageDialog(
          window,
          "Exported " + model.getRowCount() + " row(s) to:\n" + path.toAbsolutePath(),
          "Export CSV",
          JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          window,
          "Failed to export CSV:\n" + e.getMessage(),
          "Export CSV",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private static void writeTableModelAsCsv(DefaultTableModel model, Path path) throws Exception {
    try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
      int columnCount = model.getColumnCount();
      for (int col = 0; col < columnCount; col++) {
        if (col > 0) {
          writer.write(',');
        }
        writer.write(escapeCsv(model.getColumnName(col)));
      }
      writer.newLine();

      for (int row = 0; row < model.getRowCount(); row++) {
        for (int col = 0; col < columnCount; col++) {
          if (col > 0) {
            writer.write(',');
          }
          Object value = model.getValueAt(row, col);
          writer.write(escapeCsv(value == null ? "" : String.valueOf(value)));
        }
        writer.newLine();
      }
    }
  }

  private static String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    boolean needsQuotes =
        value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0;
    if (!needsQuotes) {
      return value;
    }
    return '"' + value.replace("\"", "\"\"") + '"';
  }

  private static JPanel buildCommandPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));

    JTextArea commands = new JTextArea();
    commands.setEditable(false);
    commands.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    commands.setText(
        String.join(
            System.lineSeparator(),
            ".\\dist\\intune-bulk-actions\\intune-bulk-actions.exe",
            ".\\dist\\intune-bulk-actions\\intune-bulk-actions.exe shell",
            ".\\dist\\intune-bulk-actions\\intune-bulk-actions.exe bulk sync --groupId <GUID> --dryRun",
            ".\\dist\\intune-bulk-actions\\intune-bulk-actions.exe gui"));

    JScrollPane commandsScroll = new JScrollPane(commands);
    commandsScroll.setBorder(BorderFactory.createTitledBorder("Quick Start Commands"));
    panel.add(commandsScroll, BorderLayout.CENTER);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton copyButton = new JButton("Copy Commands");
    copyButton.addActionListener(
        event -> {
          Toolkit.getDefaultToolkit()
              .getSystemClipboard()
              .setContents(new StringSelection(commands.getText()), null);
          JOptionPane.showMessageDialog(null, "Commands copied to clipboard.");
        });
    buttons.add(copyButton);
    panel.add(buttons, BorderLayout.SOUTH);
    return panel;
  }

  private static void runGroups(
      GuiRuntime runtime,
      JLabel statusLabel,
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    runQuery(
        runtime,
        statusLabel,
//      groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown,
        "Loading groups...",
        new String[] {"Display Name", "Group ID"},
        () -> {
          String path = "/groups?$select=" + urlEncode("id,displayName");
          List<JsonNode> rows = runtime.graph().getV1PagedValues(path);
          List<String[]> tableRows = new ArrayList<>();
          for (JsonNode row : rows) {
            tableRows.add(new String[] {text(row, "displayName"), text(row, "id")});
          }
          sortRowsByColumn(tableRows, 0);
          return tableRows;
        },
        "Loaded groups.");
  }

  private static void runUsers(
      GuiRuntime runtime,
      JLabel statusLabel,
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    runQuery(
        runtime,
        statusLabel,
//      groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown,
        "Loading users...",
        new String[] {"Display Name", "UPN", "User ID"},
        runtime::loadUsersRows,
        "Loaded users.");
  }

  private static void runDevices(
      GuiRuntime runtime,
      JLabel statusLabel,
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    runQuery(
        runtime,
        statusLabel,
//      groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown,
        "Loading devices...",
        new String[] {"Device Name", "Serial", "Managed Device ID"},
        runtime::loadDevicesRows,
        "Loaded devices.");
  }

  private static void loadGroupsIntoDropdown(
      GuiRuntime runtime,
      JLabel statusLabel,
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    setActionControlsEnabled(
//      groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown,
        false);
    statusLabel.setText("Loading group names...");
    runtime.startProgressTimer();

    new SwingWorker<List<GroupOption>, Void>() {
      @Override
      protected List<GroupOption> doInBackground() {
        return fetchGroupOptions(runtime);
      }

      @Override
      protected void done() {
        try {
          List<GroupOption> groups = get();
          DefaultComboBoxModel<GroupOption> model = new DefaultComboBoxModel<>();
          for (GroupOption group : groups) {
            model.addElement(group);
          }
          groupDropdown.setModel(model);
          statusLabel.setText("Loaded groups for sync. Count: " + groups.size());
        } catch (Exception ex) {
          statusLabel.setText("Failed to load groups.");
          JOptionPane.showMessageDialog(
              null,
              "Could not load group list: " + ex.getMessage(),
              "Group Load Error",
              JOptionPane.ERROR_MESSAGE);
        } finally {
          long elapsedMs = runtime.stopProgressTimer();
          statusLabel.setText(statusLabel.getText() + " | " + elapsedMs + " ms");
          setActionControlsEnabled(
//            groupsButton,
              usersButton,
              devicesButton,
              groupMembersButton,
              syncGroupButton,
              rebootGroupButton,
              groupDropdown,
              true);
        }
      }
    }.execute();
  }

  private static List<GroupOption> fetchGroupOptions(GuiRuntime runtime) {
    String path = "/groups?$select=" + urlEncode("id,displayName");
    List<JsonNode> rows = runtime.graph().getV1PagedValues(path);
    List<GroupOption> groups = new ArrayList<>();
    for (JsonNode row : rows) {
      String name = text(row, "displayName");
      String id = text(row, "id");
      if (!id.isBlank()) {
        groups.add(new GroupOption(name.isBlank() ? id : name, id));
      }
    }
    groups.sort(Comparator.comparing(GroupOption::name, String.CASE_INSENSITIVE_ORDER));
    return groups;
  }

  private static void runSyncGroup(
      GuiRuntime runtime,
      JLabel statusLabel,
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    runGroupAction(
        runtime,
        statusLabel,
//      groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown,
        ActionType.SYNC,
        "Sync Group",
        "SYNC");
  }

  private static void runRebootGroup(
      GuiRuntime runtime,
      JLabel statusLabel,
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    runGroupAction(
        runtime,
        statusLabel,
//      groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown,
        ActionType.REBOOT,
        "Reboot Group",
        "REBOOT");
  }

  private static void runRemovePrimaryUserGroup(
      GuiRuntime runtime,
      JLabel statusLabel,
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    runGroupAction(
        runtime,
        statusLabel,
//      groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown,
        ActionType.REMOVE_PRIMARY_USER,
        "Remove Primary User Group",
        "REMOVE_PRIMARY_USER");
  }

  private static void runGroupAction(
      GuiRuntime runtime,
      JLabel statusLabel,
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown,
      ActionType actionType,
      String actionTitle,
      String actionLabel) {
    GroupOption selected = (GroupOption) groupDropdown.getSelectedItem();
    if (selected == null) {
      JOptionPane.showMessageDialog(null, "Select a group first.", "No Group Selected", JOptionPane.WARNING_MESSAGE);
      return;
    }

    int choice =
        JOptionPane.showConfirmDialog(
            null,
            "Run " + actionLabel + " on all resolvable devices in group:\n" + selected.name() + "?",
            "Confirm " + actionTitle,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice != JOptionPane.YES_OPTION) {
      return;
    }

    setActionControlsEnabled(
//      groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown,
        false);
    statusLabel.setText("Resolving devices for " + selected.name() + "...");
    runtime.startProgressTimer();

    new SwingWorker<List<ActionResult>, Void>() {
      @Override
      protected List<ActionResult> doInBackground() {
        List<com.mkylm.intunebulk.core.DeviceRef> targets =
            runtime.resolveGroupDevices(selected.id());
        ActionOptions options =
            ActionOptions.builder()
                .dryRun(false)
                .maxConcurrency(6)
                .maxRetries(6)
                .baseBackoff(java.time.Duration.ofSeconds(2))
                .continueOnError(true)
                .build();
        ActionRequest request = new ActionRequest(actionType, targets, options);
        return runtime.actionService().execute(request);
      }

      @Override
      protected void done() {
        try {
          List<ActionResult> results = get();
          setSyncResults(runtime, results);
          long succeeded = results.stream().filter(r -> r.state() == ActionState.SUCCEEDED).count();
          long failed = results.stream().filter(r -> r.state() == ActionState.FAILED).count();
          long skipped = results.stream().filter(r -> r.state() == ActionState.SKIPPED).count();
          statusLabel.setText(
              actionLabel
                  + " complete for "
                  + selected.name()
                  + ". ok:"
                  + succeeded
                  + " fail:"
                  + failed
                  + " skip:"
                  + skipped);
        } catch (Exception ex) {
          statusLabel.setText(actionLabel + " failed.");
          JOptionPane.showMessageDialog(
              null,
              actionTitle + " failed: " + ex.getMessage(),
              actionTitle + " Error",
              JOptionPane.ERROR_MESSAGE);
        } finally {
          long elapsedMs = runtime.stopProgressTimer();
          statusLabel.setText(statusLabel.getText() + " | " + elapsedMs + " ms");
          setActionControlsEnabled(
//            groupsButton,
              usersButton,
              devicesButton,
              groupMembersButton,
              syncGroupButton,
              rebootGroupButton,
              groupDropdown,
              true);
        }
      }
    }.execute();
  }

  private static void runQuery(
      GuiRuntime runtime,
      JLabel statusLabel,
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown,
      String loadingMessage,
      String[] columns,
      QueryRunner queryRunner,
      String donePrefix) {
    setActionControlsEnabled(
//      groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown,
        false);
    statusLabel.setText(loadingMessage);
    runtime.startProgressTimer();

    new SwingWorker<List<String[]>, Void>() {
      @Override
      protected List<String[]> doInBackground() throws Exception {
        return queryRunner.run();
      }

      @Override
      protected void done() {
        long elapsedMs = -1L;
        try {
          List<String[]> rows = get();
          setResults(runtime, columns, rows);
          elapsedMs = runtime.stopProgressTimer();
          statusLabel.setText(donePrefix + " Rows: " + rows.size() + " | " + elapsedMs + " ms");
        } catch (Exception ex) {
          elapsedMs = runtime.stopProgressTimer();
          statusLabel.setText("Query failed. | " + elapsedMs + " ms");
          JOptionPane.showMessageDialog(
              null,
              "Request failed: " + ex.getMessage(),
              "Graph Query Error",
              JOptionPane.ERROR_MESSAGE);
        } finally {
          if (elapsedMs < 0) {
            runtime.stopProgressTimer();
          }
          setActionControlsEnabled(
//            groupsButton,
              usersButton,
              devicesButton,
              groupMembersButton,
              syncGroupButton,
              rebootGroupButton,
              groupDropdown,
              true);
        }
      }
    }.execute();
  }

  private static void runGroupDevices(
      GuiRuntime runtime,
      JLabel statusLabel,
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    GroupOption selected = (GroupOption) groupDropdown.getSelectedItem();
    if (selected == null) {
      JOptionPane.showMessageDialog(null, "Select a group first.", "No Group Selected", JOptionPane.WARNING_MESSAGE);
      return;
    }

    runQuery(
        runtime,
        statusLabel,
//      groupsButton,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        groupDropdown,
        "Resolving group devices for " + selected.name() + "...",
        new String[] {"Device Name", "Serial Number", "Device Primary User"},
        () -> {
          List<DeviceRef> devices = runtime.resolveGroupDevices(selected.id());
          List<String[]> rows = new ArrayList<>();
          for (DeviceRef device : devices) {
            if (device.skipped()) {
              rows.add(new String[] {device.displayNameOrId(), "-", "-"});
            } else {
              rows.add(
                  new String[] {
                    device.displayNameOrId(), device.serialNumber(), device.primaryUser()
                  });
            }
          }
          sortRowsByColumn(rows, 0);
          return rows;
        },
        "Loaded devices for group " + selected.name() + ".");
  }

  private static void setResults(GuiRuntime runtime, String[] columns, List<String[]> rows) {
    runtime.resultsModel = createModel(columns);
    for (String[] row : rows) {
      runtime.resultsModel.addRow(row);
    }
    runtime.resultsTable.setModel(runtime.resultsModel);
    runtime.resultsSorter = new TableRowSorter<>(runtime.resultsModel);
    runtime.resultsTable.setRowSorter(runtime.resultsSorter);
    runtime.applyResultsFilter();
  }

  private static void setSyncResults(GuiRuntime runtime, List<ActionResult> results) {
    List<ActionResult> sorted = new ArrayList<>(results);
    sorted.sort(
        Comparator.comparing(
            r -> r.device() == null ? "" : r.device().displayNameOrId(),
            String.CASE_INSENSITIVE_ORDER));

    runtime.resultsModel = createModel(new String[] {"State", "Device", "Managed Device ID", "Message"});
    for (ActionResult result : sorted) {
      runtime.resultsModel.addRow(
          new String[] {
            result.state().name(),
            result.device() == null ? "" : result.device().displayNameOrId(),
            result.device() == null || result.device().managedDeviceId() == null
                ? "-"
                : result.device().managedDeviceId(),
            result.errorMessageOrDash()
          });
    }
    runtime.resultsTable.setModel(runtime.resultsModel);
    runtime.resultsSorter = new TableRowSorter<>(runtime.resultsModel);
    runtime.resultsTable.setRowSorter(runtime.resultsSorter);
    runtime.applyResultsFilter();
  }

  private static void setActionControlsEnabled(
//    JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton groupMembersButton,
      JButton syncGroupButton,
      JButton rebootGroupButton,
      JComboBox<GroupOption> groupDropdown,
      boolean enabled) {
//  groupsButton.setEnabled(enabled);
    usersButton.setEnabled(enabled);
    devicesButton.setEnabled(enabled);
    groupMembersButton.setEnabled(enabled);
    syncGroupButton.setEnabled(enabled);
    rebootGroupButton.setEnabled(enabled);
    groupDropdown.setEnabled(enabled);
  }

  private static void sortRowsByColumn(List<String[]> rows, int columnIndex) {
    rows.sort(
        Comparator.comparing(
            row -> {
              if (row == null || columnIndex >= row.length || row[columnIndex] == null) {
                return "";
              }
              return row[columnIndex];
            },
            String.CASE_INSENSITIVE_ORDER));
  }

  private static void waitUntilWindowsClose() {
    while (true) {
      boolean hasDisplayableWindow = false;
      for (Frame frame : Frame.getFrames()) {
        if (frame != null && frame.isDisplayable()) {
          hasDisplayableWindow = true;
          break;
        }
      }
      if (!hasDisplayableWindow) {
        System.out.println("[GUI] No displayable windows remain. Exiting GUI mode.");
        return;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private static DefaultTableModel createModel(String[] columns) {
    return new DefaultTableModel(columns, 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };
  }

  private static String text(JsonNode node, String field) {
    if (node == null) {
      return "";
    }
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return "";
    }
    return value.asText("");
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  @FunctionalInterface
  private interface QueryRunner {
    List<String[]> run() throws Exception;
  }

  private IntuneBulkGuiApp() {}
}
