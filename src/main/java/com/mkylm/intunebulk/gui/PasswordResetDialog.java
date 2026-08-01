package com.mkylm.intunebulk.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;

/** Splash-style dialog to collect a new password for a selected user. */
final class PasswordResetDialog {
  static void show(Component parent, String displayName, String userId) {
    String label =
        displayName == null || displayName.isBlank()
            ? (userId == null || userId.isBlank() ? "User" : userId)
            : displayName;

    Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
    JDialog dialog =
        new JDialog(owner, "Reset password — " + label, JDialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setResizable(false);
    dialog.setAlwaysOnTop(true);

    JLabel userLabel = new JLabel(label);
    JPasswordField passwordField = new JPasswordField(28);
    JPasswordField confirmField = new JPasswordField(28);
    JLabel statusLabel = new JLabel(" ");

    JPanel form = new JPanel(new GridBagLayout());
    form.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    GridBagConstraints labels = new GridBagConstraints();
    labels.gridx = 0;
    labels.anchor = GridBagConstraints.WEST;
    labels.insets = new Insets(4, 4, 4, 8);
    GridBagConstraints fields = new GridBagConstraints();
    fields.gridx = 1;
    fields.fill = GridBagConstraints.HORIZONTAL;
    fields.weightx = 1;
    fields.insets = new Insets(4, 4, 4, 4);

    labels.gridy = 0;
    fields.gridy = 0;
    form.add(new JLabel("User"), labels);
    form.add(userLabel, fields);

    labels.gridy = 1;
    fields.gridy = 1;
    form.add(new JLabel("New password"), labels);
    form.add(passwordField, fields);

    labels.gridy = 2;
    fields.gridy = 2;
    form.add(new JLabel("Confirm password"), labels);
    form.add(confirmField, fields);

    JButton cancelButton = new JButton("Cancel");
    JButton resetButton = new JButton("Reset Password");
    cancelButton.addActionListener(event -> dialog.dispose());
    resetButton.addActionListener(
        event -> {
          char[] password = passwordField.getPassword();
          char[] confirm = confirmField.getPassword();
          try {
            if (password.length == 0) {
              statusLabel.setText("Password is required.");
              return;
            }
            if (!Arrays.equals(password, confirm)) {
              statusLabel.setText("Passwords do not match.");
              return;
            }
            // Graph password reset is not wired yet; keep the splash UX ready.
            JOptionPane.showMessageDialog(
                dialog,
                "Password reset is not implemented yet.",
                "Reset password",
                JOptionPane.INFORMATION_MESSAGE);
            dialog.dispose();
          } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
          }
        });

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(cancelButton);
    buttons.add(resetButton);

    JPanel content = new JPanel(new BorderLayout(8, 8));
    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    content.add(form, BorderLayout.CENTER);
    JPanel south = new JPanel(new BorderLayout());
    south.add(statusLabel, BorderLayout.CENTER);
    south.add(buttons, BorderLayout.EAST);
    content.add(south, BorderLayout.SOUTH);
    dialog.setContentPane(content);

    dialog.pack();
    dialog.setLocationRelativeTo(owner);
    dialog.toFront();
    dialog.setVisible(true);
  }

  private PasswordResetDialog() {}
}
