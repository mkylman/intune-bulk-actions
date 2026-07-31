package com.mkylm.intunebulk.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
    JButton groupMembersButton = new JButton("Group Members");
    JButton syncGroupButton = new JButton("Sync Group");
    JButton rebootGroupButton = new JButton("Reboot Group");
    JButton removePrimaryUserGroupButton = new JButton("Remove Primary User Group");
    JComboBox<GroupOption> groupDropdown = new JComboBox<>();
    groupDropdown.setPrototypeDisplayValue(new GroupOption("WWWWWWWWWWWWWWWWWWWWWWWWWWWW", "id"));
    groupDropdown.setEnabled(false);
    JLabel statusLabel = new JLabel("Ready.");
    JLabel elapsedLabel = new JLabel("Elapsed: -- ms");
    elapsedLabel.setPreferredSize(new Dimension(140, elapsedLabel.getPreferredSize().height));

    JPanel queryButtonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    queryButtonsRow.add(usersButton);
    queryButtonsRow.add(devicesButton);

    JPanel groupSelectionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    groupSelectionRow.add(groupDropdown);
    groupSelectionRow.add(groupMembersButton);

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

    JPanel rows = new JPanel(new GridLayout(4, 1, 0, 4));
    rows.add(queryButtonsRow);
    rows.add(groupSelectionRow);
    rows.add(syncGroupRow);
    rows.add(destructiveActionsRow);
    panel.add(rows, BorderLayout.CENTER);
    JPanel statusRow = new JPanel(new BorderLayout(8, 0));
    statusRow.add(statusLabel, BorderLayout.CENTER);
    statusRow.add(elapsedLabel, BorderLayout.EAST);
    panel.add(statusRow, BorderLayout.SOUTH);

    return new Controls(
        panel,
        usersButton,
        devicesButton,
        groupMembersButton,
        syncGroupButton,
        rebootGroupButton,
        removePrimaryUserGroupButton,
        groupDropdown,
        statusLabel,
        elapsedLabel);
  }

  static final class Controls {
    final JPanel panel;
    final JButton usersButton;
    final JButton devicesButton;
    final JButton groupMembersButton;
    final JButton syncGroupButton;
    final JButton rebootGroupButton;
    final JButton removePrimaryUserGroupButton;
    final JComboBox<GroupOption> groupDropdown;
    final JLabel statusLabel;
    final JLabel elapsedLabel;

    Controls(
        JPanel panel,
        JButton usersButton,
        JButton devicesButton,
        JButton groupMembersButton,
        JButton syncGroupButton,
        JButton rebootGroupButton,
        JButton removePrimaryUserGroupButton,
        JComboBox<GroupOption> groupDropdown,
        JLabel statusLabel,
        JLabel elapsedLabel) {
      this.panel = panel;
      this.usersButton = usersButton;
      this.devicesButton = devicesButton;
      this.groupMembersButton = groupMembersButton;
      this.syncGroupButton = syncGroupButton;
      this.rebootGroupButton = rebootGroupButton;
      this.removePrimaryUserGroupButton = removePrimaryUserGroupButton;
      this.groupDropdown = groupDropdown;
      this.statusLabel = statusLabel;
      this.elapsedLabel = elapsedLabel;
    }
  }

  private GuiActionPanel() {}
}
