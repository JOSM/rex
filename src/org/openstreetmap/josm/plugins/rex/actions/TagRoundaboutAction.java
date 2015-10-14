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

        //TODO: BUG: If selected node only refers to one way and that
        //way has the node as its end, dont do anything
        //if () return;

        //If we have exactly one single node selected
        if (selection.size() == 1
            && selectedNodes.size() == 1
        ) {
            Node node = selectedNodes.get(0);
            if (node.getKeys().get("highway") != "mini_roundabout") {
                tagAsRoundabout(node);
            } else {
                makeRoundabout(node);
            }
        }

        //We have exactly one way selected
        if (selection.size() == 1
            && selectedWays.size() == 1
        ) {
            Way way = selectedWays.get(0);
            //And the way is closed (looks like roundabout)
            if (way.isClosed()) { 
                tagAsRoundabout(way);
            }
        }

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
        //Main tag to make a way a roundabout
        Main.main.undoRedo.add(new ChangePropertyCommand(circle, "junction", "roundabout"));

        //oneway is implicit from junction=roundabout, so not needed
        //TODO If oneway=-1 then reverse direction of circle before
        Main.main.undoRedo.add(new ChangePropertyCommand(circle, "oneway", ""));

        //If mistagged as a mini_roundabout, remove it
        if (circle.getKeys().get("highway") == "mini_roundabout") {
            Main.main.undoRedo.add(new ChangePropertyCommand(circle, "highway", ""));
        }

        //If not already tagged as highway, tag as road
        if (! circle.getKeys().containsKey("highway")) {
            Main.main.undoRedo.add(new ChangePropertyCommand(circle, "highway", "road"));
        }
    }


    /**
     * Create a roundabout way
     *
     * @param Node node Node to expand to Roundabout
     */
    public void makeRoundabout(Node node) {
        //get default direction
        boolean lefthandtraffic = (Main.pref.get("mappaint.lefthandtraffic") == "true" ? true : false);

        //See if user want another direction
        if (node.getKeys().get("direction") == "clockwise") {
            lefthandtraffic = true;
        }

        //Copy tags from most prominent way. That is the 'beefiest' highway
        //of the ways passing through node, or the 'beefiest of any way
        //ending at the node.
        //
        //if referenced ways.size()>1 
        //foreach referenced way
        //if node is first or last
        //    put way in endways
        //else
        //    put in middleways
        //if middleways.size() > 0
        //  copy tags of the highest highway in middleways
        //else
        //  copy tags of the highest highway in endways

        //Remove irrelevant tagging from the node
        node.remove("highway");
        node.remove("junction");
        node.remove("direction");
        node.remove("oneway");

        //Split all ways using the node
        splitAll(node);

        //Unglue so the ways at node connected anymore
        //We'll continue working with the resulting nodes.
        List<Node> ungrouped_nodes = unglueWays(node);

        //If there are only two nodes, the roundabout would be a bit flat.
        //So we add in a new point in the original center to get a triangle
        if (ungrouped_nodes.size() == 2) {
            Node extra = new Node(node.getCoor());
            Main.main.undoRedo.add(new AddCommand(extra));
            ungrouped_nodes.add(extra);
        }

        //Move nodes outward, towards the next node in way
        int roundabout_size = Integer.parseInt(Main.pref.get("rex.roundabout_size"));
        for (Node n : ungrouped_nodes) {
            moveWayEndNodeTowardsNextNode(n, roundabout_size/2);
        }

        //Sort nodes around the the original node. Clockwise if desired.
        //We do this to avoid funny figure of eight roundabouts.
        angularSort(ungrouped_nodes, node, lefthandtraffic);
        
        //TODO here we may add some extra nodes to 
        //make the resulting roundabout pretty.

        //Create the roundabout way
        Way circle = new Way();

        //add the nodes to the way
        circle.setNodes(ungrouped_nodes);

        //and the first again, closing it
        circle.addNode(circle.firstNode());

        //Paste tagging from the most prominent way
        //TODO
        //circle.setKeys(keys_copied_from_prominent_way);

        //Add tagging
        tagAsRoundabout(circle);

        //Add it to osm
        Main.main.undoRedo.add(new AddCommand(circle));

        //Select it
        getCurrentDataSet().setSelected(circle);

    }

    /**
     * Split all ways connected to node
     * TODO BUG Crashes on circular ways. ALSO MAKE A Circular Way Splitter!
     */
    private void splitAll(Node node) {
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
     * Unglue all ways using the selectedNode and return the set of new nodes
     *
     * @param Node selectedNode The original node
     * @return List<Node> The set of new nodes
     */
    private List<Node> unglueWays(Node selectedNode) {
        List<Node> newNodes = new LinkedList<>();

        Way wayWithSelectedNode = null;
        LinkedList<Way> parentWays = new LinkedList<>();
        for (OsmPrimitive osm : selectedNode.getReferrers()) {
            if (osm.isUsable() && osm instanceof Way) {
                Way w = (Way) osm;
                if (wayWithSelectedNode == null && !w.isFirstLastNode(selectedNode)) {
                    pri("wayWithSelected");
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

        return newNodes;
    }

    /**
     * Sub method of unglueWays.
     *
     * Creates a new version of originalWay,
     * with originalNode replaced with a duplicate of it.
     *
     * We assume that OrginalNode is in the way.
     *
     * We also put the new node into newNodes.
     */
    private Way modifyWay(Node originalNode, Way originalWay, List<Node> newNodes) {
        // clone the node for the way
        Node newNode = new Node(originalNode, true /* clear OSM ID */);
        newNodes.add(newNode);
        Main.main.undoRedo.add(new AddCommand(newNode));

        List<Node> nn = new ArrayList<>();
        for (Node pushNode : originalWay.getNodes()) {
            if (originalNode == pushNode) {
                pushNode = newNode;
            }
            nn.add(pushNode);
        }
        Way newWay = new Way(originalWay);
        newWay.setNodes(nn);

        return newWay;
    }

    /**
     * Sort nodes angular in relation to center
     *
     * @param List<Node> nodes
     * @param Node       center
     * @param boolean    clockwise
     */
    private void angularSort(List<Node> nodes, Node center, boolean clockwise) {
        Collections.sort(nodes, new AngComp(center));
        //Reverse if we dont want it clockwise
        if (!clockwise) {
            Collections.reverse(nodes);
        }
    }

    /**
     * A comparator that may be used to sort Nodes by angle
     * relative to center.
     * The comparator returnes true if Node a is
     * clockwise to b relative to center
     */
    class AngComp implements Comparator<Node> {

        /**
         * To hold center Node
         */
        private Node center;

        /**
         * Constructor with center specified
         */
        public AngComp(Node center)
        {
            this.center = center;
        }

        @Override
        public int compare(Node a, Node b) {
            double ax = a.getCoor().lat();
            double ay = a.getCoor().lon();

            double bx = b.getCoor().lat();
            double by = b.getCoor().lon();

            double cx = center.getCoor().lat();
            double cy = center.getCoor().lon();

            //Get some simple cases out of the way
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

            //Both are on the same angle, so we sort by distance
            double ad = (ax - cx) * (ax - cx) + (ay - cy) * (ay - cy);
            double bd = (bx - cx) * (bx - cx) + (by - cy) * (by - cy);
            return (ad > bd) ? 1 : 0;

        }
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
