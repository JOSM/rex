package org.openstreetmap.josm.plugins.rex.actions;

import javax.swing.JOptionPane;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.*;
import java.util.*;
import java.awt.event.KeyEvent;
import org.openstreetmap.josm.tools.Shortcut;
import java.awt.event.ActionEvent;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.Notification;
import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Expands to a roundabout
 *
 * @author Gorm
 */
public class TagRoundaboutAction extends JosmAction {
    private static final String TITLE = tr("Add Roundabout Tag");

    public TagRoundaboutAction() {

        super(tr("REX"), "images/dialogs/logo-rex.png",
            tr("Roundabout Expander"),
            Shortcut.registerShortcut("menu:rex", tr("Menu: {0}", tr("Roundabout Expander")),
                KeyEvent.VK_R, Shortcut.ALT_CTRL), false);

    }

    @Override
        public void actionPerformed( ActionEvent e ) {
            //Figure out what we have to work with
            Collection<OsmPrimitive> selection = getCurrentDataSet().getSelected();

            //Nothing selected
            if( selection.isEmpty()) {
                //Do nothing 
                //TODO some user education?

                new Notification(
                        tr("Nothing selected. Try selecting a single node first."))
                    .setIcon(JOptionPane.WARNING_MESSAGE)
                    .show();

                return;
            }

            //if(one single node selected) 
            {
                //Set some tags
                Main.main.undoRedo.add(new ChangePropertyCommand(selection, "highway", "mini_roundabout"));
            }

            /**
             * 
             *
             */

        }

    @Override
        protected void updateEnabledState() {
            if( getCurrentDataSet() == null ) {
                setEnabled(false);
            }  else
                updateEnabledState(getCurrentDataSet().getSelected());
        }

    @Override
        protected void updateEnabledState( Collection<? extends OsmPrimitive> selection ) {
            if( selection == null || selection.isEmpty() ) {
                setEnabled(false);
                return;
            }

        }
}
