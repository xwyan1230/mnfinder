///////////////////////////////////////////////////////////////////////////////
//PROJECT:       MicroNuclei detection project
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.micronuclei.internal.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.table.TableColumnModel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.micronuclei.internal.data.ChannelInfo;

/**
 * Generates a panel with a view on a channel table, and + and - buttons 
 * that let the user add and remove channels
 * 
 * @author Nico
 */
public final class ChannelPanel extends JPanel {
   private static final String COL0WIDTH = "Col0Width";  
   private static final String COL1WIDTH = "Col1Width";
   private static final String COL2WIDTH = "Col2Width";
   private static final String COL3WIDTH = "Col3Width";
   private static final String CHANNELDATA = "ChannelData";
   
   private final UserProfile userProfile_;
   private final Class profileClass_;
   private final ChannelTableModel channelTableModel_;
   
   public ChannelPanel(final Studio studio, final Class profileClass) {
      
      userProfile_ = studio.getUserProfile();
      profileClass_ = profileClass;
      final ChannelTable table = new ChannelTable(studio, this);
      channelTableModel_ = new ChannelTableModel();
      restoreChannelsFromProfile();

      studio.events().registerForEvents(channelTableModel_);
      table.setModel(channelTableModel_);
      
      super.setLayout(new MigLayout("insets 0", "[][]", "[][]"));
      
      JScrollPane tableScrollPane = new JScrollPane();      
      tableScrollPane.setHorizontalScrollBarPolicy(
              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      tableScrollPane.setVerticalScrollBarPolicy(
              ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
      
      TableColumnModel cm = table.getColumnModel();
      cm.getColumn(0).setPreferredWidth(userProfile_.getInt(profileClass_, COL0WIDTH, 20));
      cm.getColumn(1).setPreferredWidth(userProfile_.getInt(profileClass_, COL1WIDTH, 100));
      cm.getColumn(2).setPreferredWidth(userProfile_.getInt(profileClass_, COL2WIDTH, 50));
      cm.getColumn(3).setPreferredWidth(userProfile_.getInt(profileClass_, COL3WIDTH, 50));
      tableScrollPane.setViewportView(table);
      super.add(tableScrollPane, "span 1 2, hmax 75, wmax 320 ");
      
      final JButton plusButton = new JButton("");
      plusButton.setIcon(new ImageIcon(getClass().getResource(
              "/org/micromanager/icons/plus.png")));
      plusButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            channelTableModel_.addChannel(new ChannelInfo());
            storeChannelsInProfile();
         }
      });
      super.add(plusButton, "hmin 25, wrap");
      
      final JButton minusButton = new JButton("");
      minusButton.setIcon(new ImageIcon(getClass().getResource(
               "/org/micromanager/icons/minus.png")));
      minusButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            for (int row : table.getSelectedRows()) {
               channelTableModel_.removeChannel(row);
               storeChannelsInProfile();
            }
         }
      });
      super.add(minusButton, "hmin 25, top, wrap");
      
   }
   
   
   public void storeChannelsInProfile() {
      try {
         userProfile_.setObject(profileClass_, CHANNELDATA,
                 channelTableModel_.getChannels());
      } catch (IOException ex) {
         // TODO report exception
      }
   }
   
   
   public void restoreChannelsFromProfile() {
      try {
         channelTableModel_.setChannels( (List<ChannelInfo>) 
                 userProfile_.getObject(profileClass_, CHANNELDATA, null));
      } catch (IOException ex) {
         // TODO Report exception
      }
   }
   
}
