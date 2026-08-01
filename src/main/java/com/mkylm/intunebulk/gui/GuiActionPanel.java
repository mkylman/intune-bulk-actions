package com.mkylm.intunebulk.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class GuiActionPanel {
  static Controls build() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Run Queries and Actions"));

    JButton usersButton = new JButton("Users");
    JButton devicesButton = new JButton("Devices");
    JButton userGroupMembersButton = new JButton("User Group Members");
    JButton deviceGroupMembersButton = new JButton("Device Group Members");
    JButton syncGroupButton = new JButton("Sync Group");
    JButton rebootGroupButton = new JButton("Reboot Group");
    JButton removePrimaryUserGroupButton = new JButton("Remove Primary User Group");
    JComboBox<GroupOption> userGroupDropdown = new JComboBox<>();
    userGroupDropdown.setPrototypeDisplayValue(new GroupOption("WWWWWWWWWWWWWWWWWWWWWWWWWWWW", "id"));
    userGroupDropdown.setEnabled(false);
    JComboBox<GroupOption> deviceGroupDropdown = new JComboBox<>();
    deviceGroupDropdown.setPrototypeDisplayValue(new GroupOption("WWWWWWWWWWWWWWWWWWWWWWWWWWWW", "id"));
    deviceGroupDropdown.setEnabled(false);
    JLabel statusLabel = new JLabel("Ready.");

    JPanel queryButtonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    queryButtonsRow.add(usersButton);
    queryButtonsRow.add(devicesButton);

    JPanel userGroupSelectionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    userGroupSelectionRow.add(new JLabel("User Groups:"));
    userGroupSelectionRow.add(userGroupDropdown);
    userGroupSelectionRow.add(userGroupMembersButton);

    JPanel deviceGroupSelectionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    deviceGroupSelectionRow.add(new JLabel("Device Groups:"));
    deviceGroupSelectionRow.add(deviceGroupDropdown);
    deviceGroupSelectionRow.add(deviceGroupMembersButton);

    JPanel syncGroupRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    syncGroupRow.add(syncGroupButton);

    JPanel destructiveActionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    rebootGroupButton.setBackground(new Color(255, 204, 0));
    rebootGroupButton.setForeground(Color.BLACK);
    rebootGroupButton.setOpaque(true);
    rebootGroupButton.setBorderPainted(false);
    removePrimaryUserGroupButton.setBackground(new Color(192, 0, 0));
    removePrimaryUserGroupButton.setForeground(Color.WHITE);
    removePrimaryUserGroupButton.setOpaque(true);
    removePrimaryUserGroupButton.setBorderPainted(false);
    destructiveActionsRow.add(rebootGroupButton);
    destructiveActionsRow.add(removePrimaryUserGroupButton);

    JPanel rows = new JPanel(new GridLayout(5, 1, 0, 4));
    rows.add(queryButtonsRow);
    rows.add(userGroupSelectionRow);
    rows.add(deviceGroupSelectionRow);
    rows.add(syncGroupRow);
    rows.add(destructiveActionsRow);
    panel.add(rows, BorderLayout.CENTER);
    panel.add(statusLabel, BorderLayout.SOUTH);

    return new Controls(
        panel,
        usersButton,
        devicesButton,
        userGroupMembersButton,
        deviceGroupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        removePrimaryUserGroupButton,
        userGroupDropdown,
        deviceGroupDropdown,
        statusLabel);
  }

  static final class Controls {
    final JPanel panel;
    final JButton usersButton;
    final JButton devicesButton;
    final JButton userGroupMembersButton;
    final JButton deviceGroupMembersButton;
    final JButton syncGroupButton;
    final JButton rebootGroupButton;
    final JButton removePrimaryUserGroupButton;
    final JComboBox<GroupOption> userGroupDropdown;
    final JComboBox<GroupOption> deviceGroupDropdown;
    final JLabel statusLabel;

    Controls(
        JPanel panel,
        JButton usersButton,
        JButton devicesButton,
        JButton userGroupMembersButton,
        JButton deviceGroupMembersButton,
        JButton syncGroupButton,
        JButton rebootGroupButton,
        JButton removePrimaryUserGroupButton,
        JComboBox<GroupOption> userGroupDropdown,
        JComboBox<GroupOption> deviceGroupDropdown,
        JLabel statusLabel) {
      this.panel = panel;
      this.usersButton = usersButton;
      this.devicesButton = devicesButton;
      this.userGroupMembersButton = userGroupMembersButton;
      this.deviceGroupMembersButton = deviceGroupMembersButton;
      this.syncGroupButton = syncGroupButton;
      this.rebootGroupButton = rebootGroupButton;
      this.removePrimaryUserGroupButton = removePrimaryUserGroupButton;
      this.userGroupDropdown = userGroupDropdown;
      this.deviceGroupDropdown = deviceGroupDropdown;
      this.statusLabel = statusLabel;
    }
  }

  private GuiActionPanel() {}
}
