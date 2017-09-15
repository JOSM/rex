// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rex;

import javax.swing.JMenuItem;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.rex.actions.TagRoundaboutAction;

/**
 * This is the main class for the rex plugin.
 *
 */
public class RoundaboutExpanderPlugin extends Plugin {

    JMenuItem roundaboutTag;

    public RoundaboutExpanderPlugin(PluginInformation info) {
        super(info);
        roundaboutTag = MainMenu.add(MainApplication.getMenu().moreToolsMenu, new TagRoundaboutAction());
    }

    /**
     * Called when the JOSM map frame is created or destroyed.
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        boolean enabled = newFrame != null;
        roundaboutTag.setEnabled(enabled);
    }
}
