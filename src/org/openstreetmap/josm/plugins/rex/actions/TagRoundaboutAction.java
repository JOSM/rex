package org.openstreetmap.josm.plugins.rex.actions;

import javax.swing.JOptionPane;

import java.util.*;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.*;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
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
            List<Node> selectedNodes = OsmPrimitive.getFilteredList(selection, Node.class);
            List<Way> selectedWays = OsmPrimitive.getFilteredList(selection, Way.class);

            //if(one single node selected) 
            if( selection.size() == 1 && selectedNodes.size() == 1) {
                //TODO a possible intermediate step before creating a real circle:
                //Set some tags
                Main.main.undoRedo.add(new ChangePropertyCommand(selection, "highway", "mini_roundabout"));
                return;
            }

            //We have enough nodes selected, make a closed way with them
            if(  selectedNodes.size() >= 2) {

                for (Node n : selectedNodes) {
                    moveWayEndNodeTowardsNextNode(n, -6);
                }
                makeCircle(selectedNodes);

                return;
            }

            //We don't have a suitable selection
            pri( tr("No appropriate selection. Try selecting a single node first."));
        }

    /**
     * Create a roundabout way
     *
     * @param List nordes   Nodes to assemble into circle
     * @param Node original Original, central node. Tagging determines direction.
     */
    public void makeCircle(List<Node> nodes) {
        //Create the way
        Way circle = new Way();

        //sort nodes list on heading from original expanded node
        //takes into account direction of original mini roundabout
        //TODO

        //add the nodes to the way
        circle.setNodes(nodes);

        //and the first again, closing it
        circle.addNode(circle.firstNode());

        //Add it to osm
        Main.main.undoRedo.add(new AddCommand(circle));
        Main.map.mapView.repaint();

        //Copy tagging from the most prominent way
        //TODO

        //Add tagging
        Main.main.undoRedo.add(new ChangePropertyCommand(circle, "junction", "roundabout"));
    }

    /**
     * A selected node, being the first or last node in a way, move it x meter towards the next node
     *
     * @param Node   node  Node to be moved
     * @param double distance Distance to move node in meter
     */
    public void moveWayEndNodeTowardsNextNode( Node node, double distance) {
        //some verification:
        List<Way> referedWays = OsmPrimitive.getFilteredList(node.getReferrers(), Way.class);

        //node is member of one way
        if (referedWays.size() != 1) {
            return;
        }

        //Node is only member of one way
        Way way = referedWays.get(0);
        if (!way.isFirstLastNode(node)) {
            return;
        }

        //Way must be at least two nodes long
        if(way.getNodesCount() < 2){
            return;
        }

        //Find heading to next node
        Node ajacent_node = way.getNeighbours(node).iterator().next();
        double heading = node.getCoor().heading(ajacent_node.getCoor());

        //Move the node towards the next node
        LatLon newpos = moveHeadingDistance(node.getCoor(), heading, distance);
        node.setCoor(newpos);
    }

    public void pri(String str) {
        new Notification( str) .setIcon(JOptionPane.WARNING_MESSAGE) .show();
    }

    /**
     * Return a LatLon moved distance meter in heading from start
     *
     * @param LatLon start point
     * @param double heading in radians
     * @param double distance in Meter
     */
    public LatLon moveHeadingDistance(LatLon start, double heading, double distance)
    {
        double  R = 6378100; //Radius of the Earth in meters

        double lat1 = Math.toRadians(start.lat());
        double lon1 = Math.toRadians(start.lon());

        double lat2 = Math.asin( Math.sin(lat1) * Math.cos(distance/R) +
                Math.cos(lat1) * Math.sin(distance/R) * Math.cos(heading));

        double lon2 = lon1 + Math.atan2(Math.sin(heading) * Math.sin(distance/R) * Math.cos(lat1),
                Math.cos(distance/R) - Math.sin(lat1) * Math.sin(lat2));

        lat2 = Math.toDegrees(lat2);
        lon2 = Math.toDegrees(lon2);

        return new LatLon(lat2, lon2);
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

//EOF
