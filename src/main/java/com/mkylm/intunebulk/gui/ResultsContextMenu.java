package com.mkylm.intunebulk.gui;

import com.mkylm.intunebulk.core.ActionOptions;
import com.mkylm.intunebulk.core.ActionRequest;
import com.mkylm.intunebulk.core.ActionResult;
import com.mkylm.intunebulk.core.ActionType;
import com.mkylm.intunebulk.core.DeviceRef;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;

final class ResultsContextMenu {
  static void install(GuiRuntime runtime) {
    if (runtime.resultsTable == null) {
      return;
    }
    runtime.resultsTable.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            maybeShow(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShow(e);
          }

          private void maybeShow(MouseEvent e) {
            if (!e.isPopupTrigger()) {
              return;
            }
            int viewRow = runtime.resultsTable.rowAtPoint(e.getPoint());
            if (viewRow < 0) {
              return;
            }
            runtime.resultsTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            JPopupMenu menu = buildMenu(runtime, viewRow, runtime.resultsTable);
            if (menu.getComponentCount() > 0) {
              menu.show(e.getComponent(), e.getX(), e.getY());
            }
          }
        });
  }

  private static JPopupMenu buildMenu(GuiRuntime runtime, int viewRow, Component parent) {
    JPopupMenu menu = new JPopupMenu();
    ResultsEntityType type = runtime.resultsEntityType();
    if (type == ResultsEntityType.DEVICE) {
      JMenuItem reboot = new JMenuItem("Reboot");
      reboot.addActionListener(event -> rebootDevice(runtime, viewRow, parent));
      menu.add(reboot);
    } else if (type == ResultsEntityType.USER) {
      JMenuItem resetPassword = new JMenuItem("Reset password");
      resetPassword.addActionListener(event -> resetPasswordStub(parent));
      menu.add(resetPassword);
    }
    return menu;
  }

  private static void rebootDevice(GuiRuntime runtime, int viewRow, Component parent) {
    String managedDeviceId = runtime.resultsEntityIdAtViewRow(viewRow);
    String displayName = runtime.resultsEntityNameAtViewRow(viewRow);
    if (managedDeviceId == null
        || managedDeviceId.isBlank()
        || "-".equals(managedDeviceId.trim())) {
      JOptionPane.showMessageDialog(
          parent,
          "This row does not have a managed device ID to reboot.",
          "Reboot",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    String label = displayName == null || displayName.isBlank() ? managedDeviceId : displayName;
    int choice =
        JOptionPane.showConfirmDialog(
            parent,
            "Reboot device:\n" + label + "?",
            "Confirm Reboot",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice != JOptionPane.YES_OPTION) {
      return;
    }

    DeviceRef target =
        DeviceRef.ofManagedDevice(managedDeviceId, null, label, null, null);
    ActionOptions options = ActionOptions.builder().maxConcurrency(1).build();
    ActionRequest request = new ActionRequest(ActionType.REBOOT, List.of(target), options);

    new SwingWorker<List<ActionResult>, Void>() {
      @Override
      protected List<ActionResult> doInBackground() {
        return runtime.actionService().execute(request);
      }

      @Override
      protected void done() {
        try {
          List<ActionResult> results = get();
          ActionResult result = results.isEmpty() ? null : results.get(0);
          if (result == null) {
            JOptionPane.showMessageDialog(
                parent, "No reboot result returned.", "Reboot", JOptionPane.WARNING_MESSAGE);
            return;
          }
          switch (result.state()) {
            case SUCCEEDED ->
                JOptionPane.showMessageDialog(
                    parent,
                    "Reboot requested for " + label + ".",
                    "Reboot",
                    JOptionPane.INFORMATION_MESSAGE);
            case SKIPPED ->
                JOptionPane.showMessageDialog(
                    parent,
                    "Reboot skipped: " + result.errorMessageOrDash(),
                    "Reboot",
                    JOptionPane.WARNING_MESSAGE);
            default ->
                JOptionPane.showMessageDialog(
                    parent,
                    "Reboot failed: " + result.errorMessageOrDash(),
                    "Reboot",
                    JOptionPane.ERROR_MESSAGE);
          }
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(
              parent,
              "Reboot failed: " + ex.getMessage(),
              "Reboot",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private static void resetPasswordStub(Component parent) {
    JOptionPane.showMessageDialog(
        parent,
        "Password reset is not implemented yet.",
        "Reset password",
        JOptionPane.INFORMATION_MESSAGE);
  }

  private ResultsContextMenu() {}
}
