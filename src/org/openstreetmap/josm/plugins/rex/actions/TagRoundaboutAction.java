package org.openstreetmap.josm.plugins.rex.actions;

import javax.swing.JOptionPane;

import java.util.*;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.*;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.Notification;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.SplitWayAction;
import org.openstreetmap.josm.actions.UnGlueAction;

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
        //Make default settings
        int roundabout_size = Integer.parseInt(Main.pref.get("rex.roundabout_size"));
        if (roundabout_size < 1) {
            Main.pref.put("rex.roundabout_size", "12");
        }
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
        if (selection.size() == 1 && selectedNodes.size() == 1) {
            Node node = selectedNodes.get(0);
            if (node.getKeys().get("highway") != "mini_roundabout") {
                tagAsRoundabout(node);
                return;
            } else {
                makeRoundabout(node);
                return;
            }
        }
        if (selectedNodes.size() > 1) {
            test(selectedNodes);

            selection = getCurrentDataSet().getSelected();
            selectedNodes = OsmPrimitive.getFilteredList(selection, Node.class);
            selectedWays = OsmPrimitive.getFilteredList(selection, Way.class);
            //Add tagging
            tagAsRoundabout(selectedWays.get(0));
            return;
        }

        if (selection.size() == 1
             && selectedWays.size() == 1
        ) {
            Way way = selectedWays.get(0);
            if (way.isClosed()) { 
                tagAsRoundabout(way);
            }
        }

        //We don't have a suitable selection
        Main.map.mapView.repaint();
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
    * Tag closed way as roundabout
    *
    * This method is overloaded with (Node node)
    */
    public void tagAsRoundabout(Way circle) {
        //remove oneway=* and highway=mini_roundabout
        Main.main.undoRedo.add(new ChangePropertyCommand(circle, "oneway", ""));
        Main.main.undoRedo.add(new ChangePropertyCommand(circle, "junction", "roundabout"));
    }


    private void splitall(Node node) {
        //Find all ways connected to this node
        List<Way> referedWays = OsmPrimitive.getFilteredList(node.getReferrers(), Way.class);
        //Walk through each and check if we are in the middle
        for (Way from : referedWays) {
            if (from.isFirstLastNode(node)) {
                //do nothing if node is end of way
            } else {
                //split way if node is in the middle
                SplitWayAction.SplitWayResult result = SplitWayAction.split(
                        SplitWayAction.getEditLayer(),
                        from,
                        Collections.singletonList(node),
                        Collections.<OsmPrimitive>emptyList()
                );
                Main.main.undoRedo.add(result.getCommand());
            }
        }
    }

    /**
     * Create a roundabout way
     *
     * @param Node node Node to expand to Roundabout
     */
    public void makeRoundabout(Node node) {
        //  remember direction
        //  analyze referenced ways and find what tags the circle should inherit

       //Remove irrelevant tagging
        node.remove("highway");
        node.remove("junction");
        node.remove("direction");

        splitall(node); 
        unglueWays(node);
    }

     /**
     * dupe a single node into as many nodes as there are ways using it
     */
    private void unglueWays(Node selectedNode) {
        List<Node> newNodes = new LinkedList<>();

        Way wayWithSelectedNode = null;
        LinkedList<Way> parentWays = new LinkedList<>();
        for (OsmPrimitive osm : selectedNode.getReferrers()) {
            if (osm.isUsable() && osm instanceof Way) {
                Way w = (Way) osm;
                if (wayWithSelectedNode == null && !w.isFirstLastNode(selectedNode)) {
                    wayWithSelectedNode = w;
                } else {
                    parentWays.add(w);
                }
            }
        }
        //Why?
        if (wayWithSelectedNode == null) {
            parentWays.removeFirst();
        }
        //Then actually unglue each parent way
        for (Way w : parentWays) {
            Main.main.undoRedo.add(new ChangeCommand(w, modifyWay(selectedNode, w, newNodes)));
        }

        //Add the original node to newNodes to be selected
        newNodes.add(selectedNode);

        //Select the result
        getCurrentDataSet().setSelected(newNodes);
    }
    /**
     * dupe the given node of the given way
     *
     * assume that OrginalNode is in the way
     *
     * -the new node will be put into the parameter newNodes.
     */
    private Way modifyWay(Node originalNode, Way w, List<Node> newNodes) {
        // clone the node for the way
        Node newNode = new Node(originalNode, true /* clear OSM ID */);
        newNodes.add(newNode);
        Main.main.undoRedo.add(new AddCommand(newNode));

        List<Node> nn = new ArrayList<>();
        for (Node pushNode : w.getNodes()) {
            if (originalNode == pushNode) {
                pushNode = newNode;
            }
            nn.add(pushNode);
        }
        Way newWay = new Way(w);
        newWay.setNodes(nn);

        return newWay;
    }

    public void test(List<Node> selectedNodes) {
        List<Node> ungrouped_nodes = selectedNodes;

        //If there are only two nodes, the roundabout will be a bit flat
        //so we add in a new point in the original center to get a triangle
        if (ungrouped_nodes.size() == 2) {
            pri("Sorry! Roundabout will look flat...");
            //ungrouped_nodes.add(ungrouped_nodes.get(0));
        }

        //Move nodes outward
        int roundabout_size = Integer.parseInt(Main.pref.get("rex.roundabout_size"));
        for (Node n : ungrouped_nodes) {
            moveWayEndNodeTowardsNextNode(n, roundabout_size/2);
        }

        //Sort nodes clockwise compared to the original center.
        angularSort(ungrouped_nodes, selectedNodes.get(0));
        
        //Reverse to make it counter clockwise
        Collections.reverse(ungrouped_nodes);

        //Create the new roundabout
        Way circle = makeCircle(ungrouped_nodes);

        //Add it to osm
        Main.main.undoRedo.add(new AddCommand(circle));

        //Copy tagging from the most prominent way

        //Select it
        getCurrentDataSet().setSelected(circle);

    }

    /**
     * To hold internal settings for AngComp
     * @TODO set as a method instead
     */
    private Node sort_center;
    /**
     * A comparator that a is clockwise to b relative to sort_center
     */
    class AngComp implements Comparator<Node> {
        @Override
        public int compare(Node a, Node b) {
            double ax = a.getCoor().lat();
            double ay = a.getCoor().lon();

            double bx = b.getCoor().lat();
            double by = b.getCoor().lon();

            double cx = sort_center.getCoor().lat();
            double cy = sort_center.getCoor().lon();

            if (ax >= 0 && bx < 0) {
                return 1;
            } else if ( ax == 0 && bx == 0) { 
                return (ay > by) ? 1 : 0;
            }

            double det = (ax - cx) * (by - cy) - (bx - cx) * (ay - cy);
            if (det < 0) {
                return 1;
            } else if (det > 0) {
                return 0;
            }

            double d1 = (ax - cx) * (ax - cx) + (ay - cy) * (ay - cy);
            double d2 = (bx - cx) * (bx - cx) + (by - cy) * (by - cy);
            return (d1 > d2) ? 1 : 0;

        }
    }
    /**
     * Sort nodes angular in relation to center
     */
    private void angularSort(List<Node> nodes, Node center) {
        sort_center = center;
        Collections.sort(nodes, new AngComp());
    }

    /**
     * Create a circular way connecting the given nodes
     * avoiding crossing ways and in a given direction
     *
     * @param List nodes        Nodes to assemble into circle
     * @param boolean clockwise If way should be made clockwise
     *
     * @return Way
     */
    public Way makeCircle(List<Node> nodes) {
        //Create the way
        Way circle = new Way();

        //add the nodes to the way
        circle.setNodes(nodes);

        //and the first again, closing it
        circle.addNode(circle.firstNode());

        return circle;
    }

    /**
     * A selected node, being the first or last node in a way,
     * move it x meter towards the next node in the way.
     *
     * @param Node   node  Node to be moved
     * @param double distance Distance to move node in meter
     */
    public void moveWayEndNodeTowardsNextNode( Node node, double distance) {
        //some verification:
        List<Way> referedWays = OsmPrimitive.getFilteredList(node.getReferrers(), Way.class);

        //node must be member of exactly one way
        if (referedWays.size() != 1) {
            //pri("node is not member of exactly one way");
            return;
        }

        //Node must be first or last node in way
        Way way = referedWays.get(0);
        if (!way.isFirstLastNode(node)) {
            //pri("not first or last node in way");
            return;
        }

        //Way must be at least two nodes long
        if(way.getNodesCount() < 2){
            //pri("fewer than two nodes");
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

        double lon2 = lon1 + Math.atan2(Math.sin(heading) * Math.sin((distance*-1)/R) * Math.cos(lat1),
                Math.cos(distance/R) - Math.sin(lat1) * Math.sin(lat2));

        return new LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }

} //end TagRoundaboutAction

//EOF
