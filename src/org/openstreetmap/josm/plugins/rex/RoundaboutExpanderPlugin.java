package org.openstreetmap.josm.plugins.rex;

import java.util.ArrayList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

import org.openstreetmap.josm.plugins.rex.actions.TagRoundaboutAction;

/**
 * This is the main class for the rex plugin.
 * 
 */
public class RoundaboutExpanderPlugin extends Plugin {

    private final ArrayList<Relation> turnrestrictions = new ArrayList<Relation>();

    JMenu toolsMenu = Main.main.menu.moreToolsMenu;
    JMenuItem roundaboutTag;

    public RoundaboutExpanderPlugin(PluginInformation info) {
        super(info);
        System.out.println(getPluginDir());
        roundaboutTag = MainMenu.add(toolsMenu, new TagRoundaboutAction());
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
