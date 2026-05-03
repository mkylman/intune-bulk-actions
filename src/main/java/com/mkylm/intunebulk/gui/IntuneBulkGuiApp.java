package com.mkylm.intunebulk.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mkylm.intunebulk.core.ActionOptions;
import com.mkylm.intunebulk.core.ActionRequest;
import com.mkylm.intunebulk.core.ActionResult;
import com.mkylm.intunebulk.core.ActionState;
import com.mkylm.intunebulk.core.ActionType;
import com.mkylm.intunebulk.core.DeviceActionService;
import com.mkylm.intunebulk.core.GroupDeviceResolver;
import com.mkylm.intunebulk.graph.GraphClient;
import com.mkylm.intunebulk.graph.TokenProvider;
import com.mkylm.intunebulk.graph.TokenProviderFactory;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

/** Phase-1 desktop GUI shell to complement the existing CLI workflow. */
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

    JPanel top = new JPanel(new BorderLayout());
    top.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
    JLabel title = new JLabel("Intune Bulk Actions - GUI (Phase 1)");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
    top.add(title, BorderLayout.NORTH);

    JTextArea intro = new JTextArea();
    intro.setEditable(false);
    intro.setLineWrap(true);
    intro.setWrapStyleWord(true);
    intro.setOpaque(false);
    intro.setText(
        "CLI remains fully supported. These buttons run live Graph reads using your existing auth config.");
    top.add(intro, BorderLayout.CENTER);
    frame.add(top, BorderLayout.NORTH);

    GuiRuntime runtime = new GuiRuntime();

    JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
    centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
    centerPanel.add(buildActionPanel(runtime), BorderLayout.NORTH);

    JSplitPane split =
        new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            buildStatusTable(),
            buildResultsPanel(runtime));
    split.setResizeWeight(0.35);
    centerPanel.add(split, BorderLayout.CENTER);

    frame.add(centerPanel, BorderLayout.CENTER);
    frame.add(buildCommandPanel(), BorderLayout.SOUTH);

    frame.pack();
    frame.setVisible(true);
  }

  private static JPanel buildActionPanel(GuiRuntime runtime) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Run Queries and Actions"));

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JButton groupsButton = new JButton("Groups");
    JButton usersButton = new JButton("Users");
    JButton devicesButton = new JButton("Devices");
    JButton syncGroupButton = new JButton("Sync Group");
    JComboBox<GroupOption> groupDropdown = new JComboBox<>();
    groupDropdown.setPrototypeDisplayValue(new GroupOption("WWWWWWWWWWWWWWWWWWWWWWWWWWWW", "id"));
    groupDropdown.setEnabled(false);
    JLabel statusLabel = new JLabel("Ready.");

    groupsButton.addActionListener(
        event -> runGroups(runtime, statusLabel, groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown));
    usersButton.addActionListener(
        event -> runUsers(runtime, statusLabel, groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown));
    devicesButton.addActionListener(
        event -> runDevices(runtime, statusLabel, groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown));
    syncGroupButton.addActionListener(
        event -> runSyncGroup(runtime, statusLabel, groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown));

    buttons.add(groupsButton);
    buttons.add(usersButton);
    buttons.add(devicesButton);
    buttons.add(syncGroupButton);
    panel.add(buttons, BorderLayout.WEST);

    JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    selectorPanel.add(new JLabel("Group:"));
    selectorPanel.add(groupDropdown);
    panel.add(selectorPanel, BorderLayout.CENTER);
    panel.add(statusLabel, BorderLayout.SOUTH);

    loadGroupsIntoDropdown(
        runtime, statusLabel, groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown);
    return panel;
  }

  private static JScrollPane buildStatusTable() {
    String[] columns = {"Setting", "Value", "Status"};
    DefaultTableModel model =
        new DefaultTableModel(columns, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false;
          }
        };

    Map<String, String> checks = new LinkedHashMap<>();
    checks.put("INTUNE_AUTH_MODE", envOrDefault("INTUNE_AUTH_MODE", "interactive"));
    checks.put("INTUNE_TENANT_ID", envOrDefault("INTUNE_TENANT_ID", "(default: organizations)"));
    checks.put("INTUNE_CLIENT_ID", envOrDefault("INTUNE_CLIENT_ID", "(not set)"));
    checks.put("INTUNE_REDIRECT_URI", envOrDefault("INTUNE_REDIRECT_URI", "(default: http://localhost)"));
    checks.put("INTUNE_SCOPES", envOrDefault("INTUNE_SCOPES", "(default delegated scopes)"));

    for (Map.Entry<String, String> entry : checks.entrySet()) {
      String value = entry.getValue();
      String status = value.contains("not set") ? "needs review" : "ok";
      model.addRow(new Object[] {entry.getKey(), value, status});
    }

    JTable table = new JTable(model);
    table.setRowHeight(24);
    JScrollPane pane = new JScrollPane(table);
    pane.setBorder(
        BorderFactory.createTitledBorder("Configuration Snapshot (Environment / Defaults)"));
    pane.setPreferredSize(new Dimension(860, 300));
    return pane;
  }

  private static JScrollPane buildResultsPanel(GuiRuntime runtime) {
    runtime.resultsModel = createModel(new String[] {"Name", "Value 1", "Value 2"});
    runtime.resultsTable = new JTable(runtime.resultsModel);
    runtime.resultsTable.setRowHeight(24);
    JScrollPane pane = new JScrollPane(runtime.resultsTable);
    pane.setBorder(BorderFactory.createTitledBorder("Query Results"));
    pane.setPreferredSize(new Dimension(860, 320));
    return pane;
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
            ".\\mvnw.cmd -q package",
            "java -jar target/intune-bulk-actions-0.1.0.jar shell",
            "java -jar target/intune-bulk-actions-0.1.0.jar bulk sync --groupId <GUID> --dryRun",
            "java -jar target/intune-bulk-actions-0.1.0.jar gui"));

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
      JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton syncGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    runQuery(
        runtime,
        statusLabel,
        groupsButton,
        usersButton,
        devicesButton,
        syncGroupButton,
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
      JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton syncGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    runQuery(
        runtime,
        statusLabel,
        groupsButton,
        usersButton,
        devicesButton,
        syncGroupButton,
        groupDropdown,
        "Loading users...",
        new String[] {"Display Name", "UPN", "User ID"},
        () -> {
          String path = "/users?$select=" + urlEncode("id,displayName,userPrincipalName");
          List<JsonNode> rows = runtime.graph().getV1PagedValues(path);
          List<String[]> tableRows = new ArrayList<>();
          for (JsonNode row : rows) {
            tableRows.add(
                new String[] {text(row, "displayName"), text(row, "userPrincipalName"), text(row, "id")});
          }
          sortRowsByColumn(tableRows, 0);
          return tableRows;
        },
        "Loaded users.");
  }

  private static void runDevices(
      GuiRuntime runtime,
      JLabel statusLabel,
      JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton syncGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    runQuery(
        runtime,
        statusLabel,
        groupsButton,
        usersButton,
        devicesButton,
        syncGroupButton,
        groupDropdown,
        "Loading devices...",
        new String[] {"Device Name", "Serial", "Managed Device ID"},
        () -> {
          String path =
              "/deviceManagement/managedDevices?$select="
                  + urlEncode("id,deviceName,serialNumber");
          List<JsonNode> rows = runtime.graph().getV1PagedValues(path);
          List<String[]> tableRows = new ArrayList<>();
          for (JsonNode row : rows) {
            tableRows.add(new String[] {text(row, "deviceName"), text(row, "serialNumber"), text(row, "id")});
          }
          sortRowsByColumn(tableRows, 0);
          return tableRows;
        },
        "Loaded devices.");
  }

  private static void loadGroupsIntoDropdown(
      GuiRuntime runtime,
      JLabel statusLabel,
      JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton syncGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    setActionControlsEnabled(
        groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown, false);
    statusLabel.setText("Loading group names...");

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
          setActionControlsEnabled(
              groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown, true);
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
      JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton syncGroupButton,
      JComboBox<GroupOption> groupDropdown) {
    GroupOption selected = (GroupOption) groupDropdown.getSelectedItem();
    if (selected == null) {
      JOptionPane.showMessageDialog(null, "Select a group first.", "No Group Selected", JOptionPane.WARNING_MESSAGE);
      return;
    }

    int choice =
        JOptionPane.showConfirmDialog(
            null,
            "Run SYNC on all resolvable devices in group:\n" + selected.name() + "?",
            "Confirm Sync Group",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice != JOptionPane.YES_OPTION) {
      return;
    }

    setActionControlsEnabled(
        groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown, false);
    statusLabel.setText("Resolving devices for " + selected.name() + "...");

    new SwingWorker<List<ActionResult>, Void>() {
      @Override
      protected List<ActionResult> doInBackground() {
        List<com.mkylm.intunebulk.core.DeviceRef> targets =
            runtime.groupResolver().resolveFromAadGroup(selected.id());
        ActionOptions options =
            ActionOptions.builder()
                .dryRun(false)
                .maxConcurrency(6)
                .maxRetries(6)
                .baseBackoff(java.time.Duration.ofSeconds(2))
                .continueOnError(true)
                .build();
        ActionRequest request = new ActionRequest(ActionType.SYNC, targets, options);
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
              "SYNC complete for "
                  + selected.name()
                  + ". ok:"
                  + succeeded
                  + " fail:"
                  + failed
                  + " skip:"
                  + skipped);
        } catch (Exception ex) {
          statusLabel.setText("SYNC failed.");
          JOptionPane.showMessageDialog(
              null,
              "Sync-group failed: " + ex.getMessage(),
              "Sync Error",
              JOptionPane.ERROR_MESSAGE);
        } finally {
          setActionControlsEnabled(
              groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown, true);
        }
      }
    }.execute();
  }

  private static void runQuery(
      GuiRuntime runtime,
      JLabel statusLabel,
      JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton syncGroupButton,
      JComboBox<GroupOption> groupDropdown,
      String loadingMessage,
      String[] columns,
      QueryRunner queryRunner,
      String donePrefix) {
    setActionControlsEnabled(
        groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown, false);
    statusLabel.setText(loadingMessage);

    new SwingWorker<List<String[]>, Void>() {
      @Override
      protected List<String[]> doInBackground() throws Exception {
        return queryRunner.run();
      }

      @Override
      protected void done() {
        try {
          List<String[]> rows = get();
          setResults(runtime, columns, rows);
          statusLabel.setText(donePrefix + " Rows: " + rows.size());
        } catch (Exception ex) {
          statusLabel.setText("Query failed.");
          JOptionPane.showMessageDialog(
              null,
              "Request failed: " + ex.getMessage(),
              "Graph Query Error",
              JOptionPane.ERROR_MESSAGE);
        } finally {
          setActionControlsEnabled(
              groupsButton, usersButton, devicesButton, syncGroupButton, groupDropdown, true);
        }
      }
    }.execute();
  }

  private static void setResults(GuiRuntime runtime, String[] columns, List<String[]> rows) {
    runtime.resultsModel = createModel(columns);
    for (String[] row : rows) {
      runtime.resultsModel.addRow(row);
    }
    runtime.resultsTable.setModel(runtime.resultsModel);
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
  }

  private static void setActionControlsEnabled(
      JButton groupsButton,
      JButton usersButton,
      JButton devicesButton,
      JButton syncGroupButton,
      JComboBox<GroupOption> groupDropdown,
      boolean enabled) {
    groupsButton.setEnabled(enabled);
    usersButton.setEnabled(enabled);
    devicesButton.setEnabled(enabled);
    syncGroupButton.setEnabled(enabled);
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

  private static String envOrDefault(String name, String fallback) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  @FunctionalInterface
  private interface QueryRunner {
    List<String[]> run() throws Exception;
  }

  private static final class GuiRuntime {
    private GraphClient graph;
    private GroupDeviceResolver groupResolver;
    private DeviceActionService actionService;
    private JTable resultsTable;
    private DefaultTableModel resultsModel;

    private GraphClient graph() {
      if (graph == null) {
        TokenProvider tokenProvider = TokenProviderFactory.fromEnvironment();
        graph = GraphClient.createDefault(tokenProvider);
      }
      return graph;
    }

    private GroupDeviceResolver groupResolver() {
      if (groupResolver == null) {
        groupResolver = new GroupDeviceResolver(graph());
      }
      return groupResolver;
    }

    private DeviceActionService actionService() {
      if (actionService == null) {
        actionService = new DeviceActionService(graph());
      }
      return actionService;
    }
  }

  private record GroupOption(String name, String id) {
    @Override
    public String toString() {
      return name;
    }
  }

  private IntuneBulkGuiApp() {}
}
