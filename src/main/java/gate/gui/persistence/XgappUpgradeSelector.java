/*
 *  Copyright (c) 1995-2013, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan 23/01/2001
 *
 *  $Id: XgappUpgradeSelector.java 1 2018-05-04 14:36:21Z ian_roberts $
 *
 */
package gate.gui.persistence;

import gate.gui.AlternatingTableCellEditor;
import gate.persist.PersistenceException;
import gate.resources.img.svg.GreenBallIcon;
import gate.resources.img.svg.InvalidIcon;
import gate.resources.img.svg.RedBallIcon;
import gate.resources.img.svg.YellowBallIcon;
import gate.swing.XJTable;
import gate.util.persistence.PersistenceManager;
import gate.util.persistence.UpgradeXGAPP;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.EnumMap;
import java.util.EventObject;
import java.util.List;

public class XgappUpgradeSelector {

  private XJTable pluginTable;

  private JScrollPane scroller;

  private List<UpgradeXGAPP.UpgradePath> upgrades;

  private UpgradePathTableModel model;

  private URI gappUri;

  private EnumMap<UpgradeXGAPP.UpgradePath.UpgradeStrategy, Icon> strategyIcons;

  private Icon disabledStrategyIcon;

  private JLabel statusLabel;

  private JPanel mainPanel;

  private Icon warningIcon = new InvalidIcon(16, 16);


  public XgappUpgradeSelector(URI gappUri, List<UpgradeXGAPP.UpgradePath> upgrades) {
    this.gappUri = gappUri;
    this.upgrades = upgrades;
    this.model = new UpgradePathTableModel(upgrades);

    strategyIcons = new EnumMap<UpgradeXGAPP.UpgradePath.UpgradeStrategy, Icon>(UpgradeXGAPP.UpgradePath.UpgradeStrategy.class);
    strategyIcons.put(UpgradeXGAPP.UpgradePath.UpgradeStrategy.UPGRADE, new GreenBallIcon(16, 16));
    strategyIcons.put(UpgradeXGAPP.UpgradePath.UpgradeStrategy.PLUGIN_ONLY, new YellowBallIcon(16, 16));
    strategyIcons.put(UpgradeXGAPP.UpgradePath.UpgradeStrategy.SKIP, new RedBallIcon(16, 16));
    disabledStrategyIcon = new RedBallIcon(16, 16, true);

    pluginTable = new XJTable(model);
    pluginTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    scroller = new JScrollPane(pluginTable);
    statusLabel = new JLabel("Select plugin versions to upgrade to");
    mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(statusLabel, BorderLayout.NORTH);
    mainPanel.add(scroller, BorderLayout.CENTER);

    DefaultTableCellRenderer newPluginRenderer = new DefaultTableCellRenderer();
    newPluginRenderer.setToolTipText("Double-click or press the space key to change");
    pluginTable.getColumnModel().getColumn(1).setCellRenderer(newPluginRenderer);
    pluginTable.getColumnModel().getColumn(2).setCellRenderer(new UpgradeStrategyRenderer());
    pluginTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        label.setEnabled(((UpgradeXGAPP.UpgradePath.UpgradeStrategy)table.getValueAt(row, 2)).upgradePlugin);
        return label;
      }
    });
    pluginTable.getColumnModel().getColumn(1).setCellEditor(new PluginCoordinatesEditor());
    // Alternate between two different cell editor components to avoid combo box rendering weirdness
    pluginTable.getColumnModel().getColumn(2).setCellEditor(
            new AlternatingTableCellEditor(new UpgradeStrategyEditor(), new UpgradeStrategyEditor()));
    pluginTable.getColumnModel().getColumn(3).setCellEditor(
            new AlternatingTableCellEditor(new UpgradeVersionEditor(), new UpgradeVersionEditor()));
  }

  public boolean showDialog(Window parent) {
    JOptionPane optionPane = new JOptionPane(mainPanel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
    JDialog dialog = optionPane.createDialog(parent, "Select plugin versions");
    dialog.setResizable(true);
    dialog.setVisible(true);
    return (((Integer)optionPane.getValue()).intValue() == JOptionPane.OK_OPTION);
  }

  protected class UpgradePathTableModel extends AbstractTableModel {
    private List<UpgradeXGAPP.UpgradePath> upgrades;

    UpgradePathTableModel(List<UpgradeXGAPP.UpgradePath> upgrades) {
      this.upgrades = upgrades;
    }

    @Override
    public int getRowCount() {
      return upgrades.size();
    }

    @Override
    public int getColumnCount() {
      return 4;
    }

    @Override
    public String getColumnName(int column) {
      switch(column) {
        case 0:
          return "Old plugin";
        case 1:
          return "New plugin";
        case 2:
          return "Upgrade?";
        case 3:
          return "Target version";
        default:
          return "UNKNOWN";
      }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      switch(columnIndex) {
        case 0:
          return String.class;
        case 1:
          return PluginCoordinates.class;
        case 2:
          return UpgradeXGAPP.UpgradePath.UpgradeStrategy.class;
        case 3:
          return Version.class;
        default:
          return null;
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      UpgradeXGAPP.UpgradePath path = upgrades.get(rowIndex);
      return (columnIndex == 1) ||
              (columnIndex == 2 && path.getGroupID() != null) ||
              (columnIndex == 3 && path.getUpgradeStrategy().upgradePlugin);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      UpgradeXGAPP.UpgradePath path = upgrades.get(rowIndex);
      if(columnIndex == 1) {
        PluginCoordinates coords = (PluginCoordinates)aValue;
        VersionRangeResult vrr = UpgradeXGAPP.getPluginVersions(coords.groupId, coords.artifactId);
        List<Version> versions = (vrr == null ? null : vrr.getVersions());
        if(versions != null && !versions.isEmpty()) {
          path.setGroupID(coords.groupId);
          path.setArtifactID(coords.artifactId);
          path.setVersionRangeResult(vrr);
          path.setUpgradeStrategy(UpgradeXGAPP.UpgradePath.UpgradeStrategy.UPGRADE);
          fireTableCellUpdated(rowIndex, 2);
          if(!versions.contains(path.getSelectedVersion())) {
            path.setSelectedVersion(UpgradeXGAPP.getDefaultSelection(vrr));
            fireTableCellUpdated(rowIndex, 3);
          }
        } else {
          statusLabel.setIcon(warningIcon);
          statusLabel.setText(coords + " is not a valid GATE plugin");
        }
      } else if(columnIndex == 2) {
        path.setUpgradeStrategy((UpgradeXGAPP.UpgradePath.UpgradeStrategy) aValue);
        // may need to re-render the version column
        fireTableCellUpdated(rowIndex, 3);
      } else if(columnIndex == 3) {
        path.setSelectedVersion((Version) aValue);
      }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      UpgradeXGAPP.UpgradePath path = upgrades.get(rowIndex);
      if(columnIndex == 0) {
        if(path.getOldPath().startsWith("$gatehome$plugins/")) {
          // pre-8.5 GATE_HOME/plugins/Something
          return "Pre-8.5 " + path.getOldPath().substring(18, path.getOldPath().length() - 1) + " (built-in)";
        } else if(path.getOldPath().startsWith("creole:")) {
          // an existing Maven plugin
          String[] gav = path.getOldPath().substring(9).split(";", 3);
          if("uk.ac.gate.plugins".equals(gav[0])) {
            return gav[1];
          } else {
            return gav[0] + ":" + gav[1];
          }
        } else {
          // a path to something that isn't gatehome
          try {
            URI oldURI = PersistenceManager.URLHolder.unpackPersistentRepresentation(gappUri, path.getOldPath());
            String[] parts = oldURI.toString().split("/");
            return "<html><body>Directory plugin \"" + parts[parts.length - 1]
                    + "\"<br><span style='font-size:75%'>" + oldURI + "</span></body></html>";
          } catch(PersistenceException e) {
            return path.getOldPath();
          }
        }
      } else if(columnIndex == 1) {
        return new PluginCoordinates(path.getGroupID(), path.getArtifactID());
      } else if(columnIndex == 2) {
        return path.getUpgradeStrategy();
      } else {
        // column 3
        return path.getSelectedVersion();
      }
    }
  }

  protected class UpgradeVersionEditor extends DefaultCellEditor {

    @SuppressWarnings("unchecked")
    protected UpgradeVersionEditor() {
      super(new JComboBox<Version>());
      JComboBox<Version> combo = (JComboBox<Version>)getComponent();
      combo.setModel(new DefaultComboBoxModel<>());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      JComboBox<Version> combo = (JComboBox<Version>)super.getTableCellEditorComponent(table, value, isSelected, row, column);
      DefaultComboBoxModel<Version> model = (DefaultComboBoxModel<Version>)combo.getModel();
      model.removeAllElements();
      for(Version v : upgrades.get(row).getVersions()) {
        model.addElement(v);
      }
      combo.setSelectedItem(value); // which must be one of the available ones
      return combo;
    }
  }

  protected class UpgradeStrategyRenderer extends DefaultTableCellRenderer {
    private JLabel prototype = new DefaultTableCellRenderer();

    {
      prototype.setText(UpgradeXGAPP.UpgradePath.UpgradeStrategy.PLUGIN_ONLY.label);
      prototype.setIcon(strategyIcons.get(UpgradeXGAPP.UpgradePath.UpgradeStrategy.PLUGIN_ONLY));
    }

    @Override
    public Dimension getMinimumSize() {
      return prototype.getMinimumSize();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel renderer = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      renderer.setText(((UpgradeXGAPP.UpgradePath.UpgradeStrategy)value).label);
      renderer.setToolTipText(((UpgradeXGAPP.UpgradePath.UpgradeStrategy)value).tooltip);
      renderer.setIcon(strategyIcons.get(value));
      renderer.setDisabledIcon(disabledStrategyIcon);
      renderer.setEnabled(((PluginCoordinates)table.getValueAt(row, 1)).groupId != null);
      return renderer;
    }
  }

  protected class UpgradeStrategyEditor extends DefaultCellEditor {

    @SuppressWarnings("unchecked")
    protected UpgradeStrategyEditor() {
      super(new JComboBox<UpgradeXGAPP.UpgradePath.UpgradeStrategy>());
      JComboBox<UpgradeXGAPP.UpgradePath.UpgradeStrategy> combo = (JComboBox)getComponent();
      combo.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          JLabel renderer = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          renderer.setText(((UpgradeXGAPP.UpgradePath.UpgradeStrategy)value).label);
          renderer.setToolTipText(((UpgradeXGAPP.UpgradePath.UpgradeStrategy)value).tooltip);
          renderer.setIcon(strategyIcons.get(value));
          renderer.setDisabledIcon(disabledStrategyIcon);
          return renderer;
        }
      });
      combo.setModel(new DefaultComboBoxModel<>());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      JComboBox<UpgradeXGAPP.UpgradePath.UpgradeStrategy> combo = (JComboBox)super.getTableCellEditorComponent(table, value, isSelected, row, column);
      DefaultComboBoxModel<UpgradeXGAPP.UpgradePath.UpgradeStrategy> model = (DefaultComboBoxModel)combo.getModel();
      model.removeAllElements();
      if(((String)table.getValueAt(row, 0)).startsWith("<html>")) {
        // directory plugin
        model.addElement(UpgradeXGAPP.UpgradePath.UpgradeStrategy.UPGRADE);
        model.addElement(UpgradeXGAPP.UpgradePath.UpgradeStrategy.PLUGIN_ONLY);
        model.addElement(UpgradeXGAPP.UpgradePath.UpgradeStrategy.SKIP);
      } else {
        // old $gatehome$ or existing Maven
        model.addElement(UpgradeXGAPP.UpgradePath.UpgradeStrategy.UPGRADE);
        model.addElement(UpgradeXGAPP.UpgradePath.UpgradeStrategy.SKIP);
      }
      combo.setSelectedItem(value); // which must be one of the available ones
      return combo;
    }
  }

  protected class PluginCoordinatesEditor extends AbstractCellEditor implements TableCellEditor {
    TableCellRenderer renderer = new DefaultTableCellRenderer();

    JPanel mainPanel;
    JTextField groupIdField;
    JTextField artifactIdField;

    PluginCoordinatesEditor() {
      GridBagLayout layout = new GridBagLayout();
      mainPanel = new JPanel(layout);
      GridBagConstraints c = new GridBagConstraints();
      c.insets = new Insets(5, 5, 5, 5);

      JLabel label = new JLabel("Please enter Maven co-ordinates of new plugin");
      c.gridx = 0;
      c.gridy = 0;
      c.anchor = GridBagConstraints.CENTER;
      c.weightx = 0;
      c.gridwidth = 2;
      mainPanel.add(label);
      layout.setConstraints(label, c);

      c.anchor = GridBagConstraints.LINE_START;
      c.gridx = 0;
      c.gridy = 1;
      c.gridwidth = 1;
      label = new JLabel("Group ID");
      mainPanel.add(label);
      layout.setConstraints(label, c);

      c.gridx = 1;
      c.weightx = 1;
      groupIdField = new JTextField(30);
      mainPanel.add(groupIdField);
      layout.setConstraints(groupIdField, c);

      c.gridx = 0;
      c.gridy = 2;
      c.weightx = 0;
      label = new JLabel("Artifact ID");
      mainPanel.add(label);
      layout.setConstraints(label, c);

      c.gridx = 1;
      c.weightx = 1;
      artifactIdField = new JTextField(30);
      mainPanel.add(artifactIdField);
      layout.setConstraints(artifactIdField, c);
    }

    PluginCoordinates selected = null;
    @Override
    public Object getCellEditorValue() {
      return selected;
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
      if (anEvent instanceof MouseEvent) {
        return ((MouseEvent)anEvent).getClickCount() >= 2;
      } else {
        return true;
      }
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      PluginCoordinates coords = (PluginCoordinates)value;
      if(coords.groupId != null) {
        groupIdField.setText(coords.groupId);
      } else {
        groupIdField.setText("");
      }
      artifactIdField.setText(coords.artifactId);
      SwingUtilities.invokeLater(() -> {
        int retVal = JOptionPane.showConfirmDialog(table, mainPanel, "Select new plugin",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if(retVal == JOptionPane.CANCEL_OPTION) {
          selected = null;
          fireEditingCanceled();
        } else {
          selected = new PluginCoordinates(groupIdField.getText(), artifactIdField.getText());
          fireEditingStopped();
        }
      });
      return renderer.getTableCellRendererComponent(table,
              value, isSelected, true, row, column);
    }
  }

  protected static class PluginCoordinates {
    String groupId;
    String artifactId;

    public PluginCoordinates(String groupId, String artifactId) {
      this.groupId = groupId;
      this.artifactId = artifactId;
    }

    public String toString() {
      if(groupId == null) {
        return "<html><body><em>&lt;unknown&gt;</em></body></html>";
      } else if("uk.ac.gate.plugins".equals(groupId)) {
        return artifactId;
      } else {
        return groupId + ":" + artifactId;
      }
    }
  }
}