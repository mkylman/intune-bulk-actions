package com.mkylm.intunebulk.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class GuiActionPanel {
  static final String DEVICE_ACTION_GROUP_MEMBERS = "Group Members";
  static final String DEVICE_ACTION_SYNC = "Sync Group";
  static final String DEVICE_ACTION_REBOOT = "Reboot Group";
  static final String DEVICE_ACTION_REMOVE_PRIMARY_USER = "Remove Primary User Group";
  static final String DEVICE_ACTION_SHOW_ADVANCED = "Show advanced...";
  static final String DEVICE_ACTION_HIDE_ADVANCED = "Hide advanced...";

  private static final String[] BASIC_DEVICE_ACTIONS = {
    DEVICE_ACTION_GROUP_MEMBERS, DEVICE_ACTION_SYNC, DEVICE_ACTION_SHOW_ADVANCED
  };

  private static final String[] ADVANCED_DEVICE_ACTIONS = {
    DEVICE_ACTION_GROUP_MEMBERS,
    DEVICE_ACTION_SYNC,
    DEVICE_ACTION_REBOOT,
    DEVICE_ACTION_REMOVE_PRIMARY_USER,
    DEVICE_ACTION_HIDE_ADVANCED
  };

  static Controls build(ReportRegistry reportRegistry) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Run Queries and Actions"));

    JButton runReportButton = new JButton("Run Report");
    JComboBox<String> reportsDropdown = new JComboBox<>();
    String[] reportLabels = reportRegistry.labels().toArray(String[]::new);
    reportsDropdown.setModel(new DefaultComboBoxModel<>(reportLabels));
    String prototype = "Expired Passwords";
    for (String label : reportLabels) {
      if (label != null && label.length() > prototype.length()) {
        prototype = label;
      }
    }
    reportsDropdown.setPrototypeDisplayValue(prototype);

    JButton userGroupMembersButton = new JButton("Group Members");
    JComboBox<GroupOption> userGroupDropdown = new JComboBox<>();
    userGroupDropdown.setPrototypeDisplayValue(new GroupOption("WWWWWWWWWWWWWWWWWWWWWWWWWWWW", "id"));
    userGroupDropdown.setEnabled(false);

    JComboBox<GroupOption> deviceGroupDropdown = new JComboBox<>();
    deviceGroupDropdown.setPrototypeDisplayValue(new GroupOption("WWWWWWWWWWWWWWWWWWWWWWWWWWWW", "id"));
    deviceGroupDropdown.setEnabled(false);

    JComboBox<String> deviceGroupActionsDropdown = new JComboBox<>();
    deviceGroupActionsDropdown.setModel(new DefaultComboBoxModel<>(BASIC_DEVICE_ACTIONS));
    deviceGroupActionsDropdown.setPrototypeDisplayValue(DEVICE_ACTION_REMOVE_PRIMARY_USER);
    deviceGroupActionsDropdown.setEnabled(false);
    deviceGroupActionsDropdown.addActionListener(
        event -> handleDeviceActionSelectionChange(deviceGroupActionsDropdown));
    JButton runDeviceGroupActionButton = new JButton("Run");
    runDeviceGroupActionButton.setEnabled(false);

    JLabel statusLabel = new JLabel("Ready.");

    JPanel queryButtonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    queryButtonsRow.add(new JLabel("Reports:"));
    queryButtonsRow.add(reportsDropdown);
    queryButtonsRow.add(runReportButton);

    JPanel userGroupSelectionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    userGroupSelectionRow.add(new JLabel("User Groups:"));
    userGroupSelectionRow.add(userGroupDropdown);
    userGroupSelectionRow.add(userGroupMembersButton);

    JPanel deviceGroupSelectionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    deviceGroupSelectionRow.add(new JLabel("Device Groups:"));
    deviceGroupSelectionRow.add(deviceGroupDropdown);
    deviceGroupSelectionRow.add(deviceGroupActionsDropdown);
    deviceGroupSelectionRow.add(runDeviceGroupActionButton);

    JPanel rows = new JPanel(new GridLayout(3, 1, 0, 4));
    rows.add(queryButtonsRow);
    rows.add(userGroupSelectionRow);
    rows.add(deviceGroupSelectionRow);
    panel.add(rows, BorderLayout.CENTER);
    panel.add(statusLabel, BorderLayout.SOUTH);

    return new Controls(
        panel,
        runReportButton,
        reportsDropdown,
        userGroupMembersButton,
        deviceGroupActionsDropdown,
        runDeviceGroupActionButton,
        userGroupDropdown,
        deviceGroupDropdown,
        statusLabel);
  }

  static boolean isDeviceActionMeta(String action) {
    return DEVICE_ACTION_SHOW_ADVANCED.equals(action) || DEVICE_ACTION_HIDE_ADVANCED.equals(action);
  }

  private static void handleDeviceActionSelectionChange(JComboBox<String> dropdown) {
    String selected = (String) dropdown.getSelectedItem();
    if (DEVICE_ACTION_SHOW_ADVANCED.equals(selected)) {
      dropdown.setModel(new DefaultComboBoxModel<>(ADVANCED_DEVICE_ACTIONS));
      dropdown.setSelectedItem(DEVICE_ACTION_REBOOT);
      dropdown.showPopup();
      return;
    }
    if (DEVICE_ACTION_HIDE_ADVANCED.equals(selected)) {
      dropdown.setModel(new DefaultComboBoxModel<>(BASIC_DEVICE_ACTIONS));
      dropdown.setSelectedItem(DEVICE_ACTION_GROUP_MEMBERS);
    }
  }

  static final class Controls {
    final JPanel panel;
    final JButton runReportButton;
    final JComboBox<String> reportsDropdown;
    final JButton userGroupMembersButton;
    final JComboBox<String> deviceGroupActionsDropdown;
    final JButton runDeviceGroupActionButton;
    final JComboBox<GroupOption> userGroupDropdown;
    final JComboBox<GroupOption> deviceGroupDropdown;
    final JLabel statusLabel;

    Controls(
        JPanel panel,
        JButton runReportButton,
        JComboBox<String> reportsDropdown,
        JButton userGroupMembersButton,
        JComboBox<String> deviceGroupActionsDropdown,
        JButton runDeviceGroupActionButton,
        JComboBox<GroupOption> userGroupDropdown,
        JComboBox<GroupOption> deviceGroupDropdown,
        JLabel statusLabel) {
      this.panel = panel;
      this.runReportButton = runReportButton;
      this.reportsDropdown = reportsDropdown;
      this.userGroupMembersButton = userGroupMembersButton;
      this.deviceGroupActionsDropdown = deviceGroupActionsDropdown;
      this.runDeviceGroupActionButton = runDeviceGroupActionButton;
      this.userGroupDropdown = userGroupDropdown;
      this.deviceGroupDropdown = deviceGroupDropdown;
      this.statusLabel = statusLabel;
    }
  }

  private GuiActionPanel() {}
}
