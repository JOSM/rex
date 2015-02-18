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
    private static final String TITLE = tr("Create Roundabout");

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

    public TagRoundaboutAction() {
        super(
            tr("REX"),
            "images/dialogs/logo-rex.png",
            tr("Roundabout Expander"),
            Shortcut.registerShortcut(
                "menu:rex",
                tr("Menu: {0}", tr("Roundabout Expander")),
                KeyEvent.VK_R, Shortcut.ALT_CTRL
            ),
            false
        );
    }

/**
 * Called when the action is executed, typically with keyboard shortcut.
 *
 * This method looks at what is selected and performs one
 * step of the gradual process of making a roundabout.
 * After each step, we stop. This is to allow adjustments to be made
 * by the user.
 * So, to make a full roundabout with flares, one may repeatedly press
 * the keyboard shortcut until the roundabout is made.
 */
    @Override
    public void actionPerformed( ActionEvent e ) {
        //Figure out what we have to work with:
        Collection<OsmPrimitive> selection = getCurrentDataSet().getSelected();
        List<Node> selectedNodes = OsmPrimitive.getFilteredList(selection, Node.class);
        List<Way> selectedWays = OsmPrimitive.getFilteredList(selection, Way.class);

        //If we have one single node selected
        if( selection.size() == 1 && selectedNodes.size() == 1) {
            //Node is not tagged with highway=mini_roundabout {
                tagAsRoundabout(selectedNodes.get(0));
                return;
            //} else {
            //  untag highway=mini_roundabout and remember direction
            //  analyze referenced ways and find what the circle should inherit
            //  split all attached ways
            //  ungroup the node
            //for (Node n : selectedNodes) { //the nodes that was just ungrouped
            //     moveWayEndNodeTowardsNextNode(n, -6);
            // }
            //  makeCircle(selectedNodes, center, direction)
            //  tagAsRoundabout();
            //  paste tags from major way
            //  return;
            //}
        }

        //If we have a closed way selected
        //  Way is not tagged with junction=roundabout {
        //      tagAsRoundabout();
        //      return;
        //  } else {
        //      tagAsRoundabout(); //Becasuse this might clean up other issues.
        //      selectPreFlareNodes(circle);
        //      return;
        //  }

        //If one or more node is selected and common referenced way is roundabout {
        //      makeFlares();
        //      return;
        //  }

        //We don't have a suitable selection
        pri( tr("No appropriate selection. Try selecting a single node in an intersection"));
    }

    /**
    * Tag node as roundabout
    *
    * This method is overloaded with (Way circle)
    */
    public void tagAsRoundabout(Node node) {
        Main.main.undoRedo.add(new ChangePropertyCommand(node, "junction", "roundabout"));
        Main.main.undoRedo.add(new ChangePropertyCommand(node, "highway", "mini_roundabout"));
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
        pri(tr("Copying tags from the most prominent way"));
        //TODO

        //Add tagging
        tagAsRoundabout(circle);
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

    /**
    * Tag closed way as roundabout
    *
    * This method is overloaded with (Node node)
    */
    public void tagAsRoundabout(Way circle) {
        //remove oneway=* and highway=mini_roundabout
        Main.main.undoRedo.add(new ChangePropertyCommand(circle, "junction", "roundabout"));
    }

    /**
    * Select nodes that can be made to flares
    */
    public void selectPreFlareNodes(Way circle) {
        //TODO    
        pri(tr("Selecting flare nodes"));
        //Set<Node> pre_flares = new Set();
        //for(circle.getNodes() : node) {
        //   if(referenced roads=2 and one is closed and roundabout) {
        //      pre_flares.add(node);
        //   }
        //}
        //select(pre_flares);
    }

    /**
    * Make flares of incoming ways
    */
    public void makeFlares(Set<Node> nodes) {
        //TODO    
        pri(tr("Making flares"));
        //
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
} //

//EOF
