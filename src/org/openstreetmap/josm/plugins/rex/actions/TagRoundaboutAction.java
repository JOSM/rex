package org.openstreetmap.josm.plugins.rex.actions;

import javax.swing.JOptionPane;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.*;
import java.util.*;
import java.util.LinkedList;
import java.awt.event.KeyEvent;
import org.openstreetmap.josm.tools.Shortcut;
import java.awt.event.ActionEvent;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
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
            List<Node> selectedNodes = OsmPrimitive.getFilteredList(selection, Node.class);
            List<Way> selectedWays = OsmPrimitive.getFilteredList(selection, Way.class);

            //if(one single node selected) 
            if( selection.size() == 1 && selectedNodes.size() == 1) {
                //TODO a possible intermediate step before creating a real circle:
                //Set some tags
                Main.main.undoRedo.add(new ChangePropertyCommand(selection, "highway", "mini_roundabout"));

                //Move a bit
                Node n = (Node) selectedNodes.toArray()[0];
                LatLon coor2 = moveHeadingDistance(n.getCoor(), 1, 6);
                Main.main.undoRedo.add(new MoveCommand(n, coor2));
                Main.map.mapView.repaint();

               return;
            }

            //We have 3 nodes selected, make a closed way with them
            if(  selectedNodes.size() >= 3) {
                new Notification(
                        tr("Making a way of the nodes"))
                    .setIcon(JOptionPane.WARNING_MESSAGE)
                    .show();
                
                for (Node n : selectedNodes) {
                     moveWayEndNodeTowardsNextNode(n);
                }
                makeCircle(selectedNodes);

                return;
            }

            //We don't have a suitable selection
            new Notification(
                    tr("No appropriate selection. Try selecting a single node first."))
                .setIcon(JOptionPane.WARNING_MESSAGE)
                .show();
        }

    /**
     * Create a roundabout way
     */
    public void makeCircle(List<Node> nodes) {
        //Create the way
        Way circle = new Way();

        //sort nodes list on heading from original expanded node and counter_clockwise
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
    * @param Node   endnode  Node to be moved
    * @param double distance Distance to move node
    */
    public void moveWayEndNodeTowardsNextNode( Node endnode) {
        //some verification:
        List<Way> referedWays = OsmPrimitive.getFilteredList(endnode.getReferrers(), Way.class);
        //node is member of one way
        if (referedWays.size() == 1) {
    
            new Notification(
                    tr("Yay! The node is in just one way!"))
                .setIcon(JOptionPane.WARNING_MESSAGE)
                .show();
        } else {
            return;
        }

        Way way = referedWays.get(0);
        if (way.isFirstLastNode(endnode)) {
    
            new Notification(
                    tr("Yay! The node is a end node!"))
                .setIcon(JOptionPane.WARNING_MESSAGE)
                .show();
        } else {
            return;
        }

        if(way.getNodes().size() >= 2){
    
            new Notification(
                    tr("Yay! The way has min two nodes!"))
                .setIcon(JOptionPane.WARNING_MESSAGE)
                .show();
        } else {
            return;
        }

        /*
           Node nextnode = 1;
           if (endnode.position_in_way(way) != 0) {
           nextnode = way.number_of_nodes()-1;
           }
           double heading = endnode.heading(nextnode.getCoor());
           double distance = 6;
           LatLon newpos = moveHeadingDistance(node, heading, distance);
           endnode.setLatLon(newpos);
           */
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
        double lat2 = start.lat() + 0.00001 * distance;
        double lon2 = start.lon() + 0.00001 * distance; 
        return new LatLon(lat2, lon2);
 /*
 * import math
 *
 * R = 6378.1 #Radius of the Earth
 * brng = 1.57 #Bearing is 90 degrees converted to radians.
 * d = 15 #Distance in km
 *
 * #lat2  52.20444 - the lat result I'm hoping for
 * #lon2  0.36056 - the long result I'm hoping for.
 *
 * lat1 = math.radians(52.20472) #Current lat point converted to radians
 * lon1 = math.radians(0.14056) #Current long point converted to radians
 *
 * lat2 = math.asin( math.sin(lat1)*math.cos(d/R) +
 *      math.cos(lat1)*math.sin(d/R)*math.cos(brng))
 *
 *      lon2 = lon1 + math.atan2(math.sin(brng)*math.sin(d/R)*math.cos(lat1),
 *                   math.cos(d/R)-math.sin(lat1)*math.sin(lat2))
 *
 *                   lat2 = math.degrees(lat2)
 *                   lon2 = math.degrees(lon2)
 *
 *                   print(lat2)
 *                   print(lon2)
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
