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
            tr("Roundabout Expander"),
            "images/dialogs/logo-rex.png",
            tr("Roundabout Expander"),
            Shortcut.registerShortcut(
                "menu:rex",
                tr("Menu: {0}", tr("Roundabout Expander")),
                KeyEvent.VK_R, Shortcut.CTRL_SHIFT
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
    public void actionPerformed(ActionEvent e) {

        //Figure out what we have to work with:
        Collection<OsmPrimitive> selection = getCurrentDataSet().getSelected();
        List<Node> selectedNodes = OsmPrimitive.getFilteredList(selection, Node.class);
        List<Way> selectedWays = OsmPrimitive.getFilteredList(selection, Way.class);

        //If we have exactly one single node selected
        if (selection.size() == 1
                && selectedNodes.size() == 1
           ) {
            Node node = selectedNodes.get(0);
            if (node.getKeys().get("highway") != "mini_roundabout") {
                //Make it a mini roundabout
                tagAsRoundabout(node);
            } else {
                //Get defaults
                double radi = Main.pref.getInteger("rex.diameter_meter", 12)/2;
                double max_gap = Math.toRadians(Main.pref.getInteger("rex.max_gap_degrees", 30));
                boolean lefthandtraffic = Main.pref.getBoolean("mappaint.lefthandtraffic", false);

                //See if user want another direction
                if (node.getKeys().get("direction") == "clockwise") {
                    lefthandtraffic = true;
                }

                //See if user want another size
                if (node.getKeys().containsKey("diameter")) {
                    radi = Double.parseDouble(node.getKeys().get("diameter"))/2;
                }

                makeRoundabout(node, radi, lefthandtraffic, max_gap);
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
                selectFlareCandidates();
            }
           }

        //We have some nodes selected
        //TODO check that they are all member of one and the same roundabout, then
        //reduce 1 to 0 in the if
        if (1 < selectedNodes.size()
                && selection.size() == selectedNodes.size()
           ) {
            makeFlares();
           }


        //Lollypop-mover
        if (2 == selection.size()
                && 1 == selectedNodes.size()
                && 1 == selectedWays.size()
           ) {
            Way way = selectedWays.get(0);
            Node node = selectedNodes.get(0);
            if (way.isFirstLastNode(node)) {
                List<Way> referedWays = OsmPrimitive.getFilteredList(node.getReferrers(), Way.class);
                if (2 == referedWays.size()) {
                    Way alongway = null;
                    Node moveToNode = null;
                    for (Way alongway_candidate : referedWays) {
                        if (alongway_candidate != way) {
                            alongway = alongway_candidate;
                        }
                    }
                    //Select a random neighbour
                    //for (Node mtnc : alongway.getNeighbours(node)) {
                    //    moveToNode = mtnc;
                    //    break;
                    //}

                    //Select the next node in alongway
                    int direction = 1; //-1
                    int new_pos = (alongway.getNodes().indexOf(node) + direction)
                        % alongway.getNodes().size();
                    moveToNode = alongway.getNodes().get(new_pos);

                    //Maintain selection TODO also select the way
                    getCurrentDataSet().setSelected(moveToNode);

                    //Create a new version
                    List<Node> nn = new ArrayList<>();
                    for (Node pushNode : way.getNodes()) {
                        if (node == pushNode) {
                            pushNode = moveToNode;
                        }
                        nn.add(pushNode);
                    }
                    Way newWay = new Way(way);
                    newWay.setNodes(nn);

                    //Plopp it in
                    Main.main.undoRedo.add(new ChangeCommand(way, newWay));
                }  else {
                    //The node refers to more than one way other than the
                    //one we selected, so we don't know witch one we want.
                }
            }
        }

        Main.map.mapView.repaint();
    }


    /**
    * Tag node as roundabout
    *
    * @TODO direction as well?
    *
    * This method is overloaded with (Way circle)
    */
    public void tagAsRoundabout(Node node) {
        Main.main.undoRedo.add(new ChangePropertyCommand(node, "junction", "roundabout"));
        Main.main.undoRedo.add(new ChangePropertyCommand(node, "highway", "mini_roundabout"));
        Main.main.undoRedo.add(new ChangePropertyCommand(node, "diameter", "12"));
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
     * @param Node    node            Node to expand to Roundabout
     * @param double  radi            Radius of roundabout in meter
     * @param boolean lefthandtraffic Direction of roundabout
     * @param double  max_gap         Max gap in radians between nodes to make it pretty
     */
    public void makeRoundabout(Node node, double radi, boolean lefthandtraffic, double max_gap) {
        //Store center for later use
        LatLon center = node.getCoor();

        //Copy tags from most prominent way.
        //TODO Prioritize through ways over single ways.
        List<Way> refWays = OsmPrimitive.getFilteredList(node.getReferrers(), Way.class);
        Collections.sort(refWays, new HighComp(node));
        Map<String,String> tagsToCopy = refWays.get(0).getKeys();

        //Remove irrelevant tagging from the node
        node.remove("highway");
        node.remove("junction");
        node.remove("direction");
        node.remove("diameter");
        node.remove("oneway");

        //Split all ways using the node
        splitAll(node);

        //Unglue so the ways at node connected anymore
        //We'll continue working with the resulting nodes.
        List<Node> ungrouped_nodes = unglueWays(node);

        //Move nodes towards the next node in each way
        for (Node n : ungrouped_nodes) {
            moveWayEndNodeTowardsNextNode(n, radi);
        }

        angularSort(ungrouped_nodes, center, lefthandtraffic);

        //Construct some nodes to make it pretty.
        Node filler_node = null; 
        double heading1, heading2;
        int s = ungrouped_nodes.size();
        for (Node q : ungrouped_nodes) { System.out.println(center.heading(q.getCoor())+" "+q);}
        for (int i = 0, next_i = 0; i < s; i++) {
            next_i = i+1;
            //Reference back to start
            if (next_i == s) next_i = 0;

            heading1 = center.heading(ungrouped_nodes.get(i).getCoor());
            heading2 = center.heading(ungrouped_nodes.get(next_i).getCoor());

            //Add full circle (2PI) to heading 2 to "come around" the circle.
            if (heading1>heading2 || i == next_i) {heading2 += Math.PI*2;}

            double gap = heading2 - heading1;
            int fillers_to_make = ((int)(gap/max_gap))-1;
            System.out.println("pair: "+i+" "+next_i+" "+heading1+ " "+ heading2 + " gap "+gap+ " fillers "+fillers_to_make);
            if (fillers_to_make > 0) {
                double to_next = gap / (fillers_to_make+1);
                     System.out.println("to next: " +to_next);
                double next;
                for (int j = 1; j <= fillers_to_make; j++) {
                    next = heading1 + to_next * j;
                     System.out.println("adding filler: "+j+ " heading: " +next);
                    filler_node = new Node(moveHeadingDistance(center, next, radi));
                    Main.main.undoRedo.add(new AddCommand(filler_node));
                    ungrouped_nodes.add(filler_node);
                }
            } else {
                //We don't need any fillers
            }
        }

        //Sort nodes around the the original node. Clockwise if desired.
        //We do this to avoid funny figure of eight roundabouts.
        angularSort(ungrouped_nodes, center, lefthandtraffic);
        
        //Create the roundabout way
        Way newRoundaboutWay = new Way();

        //add the nodes to the way
        newRoundaboutWay.setNodes(ungrouped_nodes);

        //and the first again, closing it
        newRoundaboutWay.addNode(newRoundaboutWay.firstNode());

        //Paste tagging from the most prominent way
        newRoundaboutWay.setKeys(tagsToCopy);

        //Add tagging
        tagAsRoundabout(newRoundaboutWay);

        //Add it to osm
        Main.main.undoRedo.add(new AddCommand(newRoundaboutWay));

        //Select it
        getCurrentDataSet().setSelected(newRoundaboutWay);

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
            if (from.isClosed()) {
                pri("something funky is going to happen. we have a circular way");
                continue; //to avoid bug
            }
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
     *
     * @return List<Node> The set of new nodes
     */
    private List<Node> unglueWays(Node selectedNode) {
        List<Node> newNodes = new LinkedList<>();

        Way wayWithSelectedNode = null;
        LinkedList<Way> parentWays = new LinkedList<>();
        //if (selectedNode.getReferrers().size() > 1)
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
    private void angularSort(List<Node> nodes, LatLon center, boolean clockwise) {
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
        private LatLon center;

        /**
         * Constructor with center specified
         */
        public AngComp(LatLon center)
        {
            this.center = center;
        }

        @Override
        public int compare(Node a, Node b) {
            double ah = center.heading(a.getCoor());
            double bh = center.heading(b.getCoor());
            if (ah == bh) return 0;
            return (ah < bh) ? 1 : -1;
        }

        public int compare_quick(Node a, Node b) {
            double ax = a.getCoor().lat();
            double ay = a.getCoor().lon();

            double bx = b.getCoor().lat();
            double by = b.getCoor().lon();

            double cx = center.lat();
            double cy = center.lon();

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
    } //END AngComp

    /**
     * A comparator that may be used to sort Ways by beefyness
     * relative to node.
     * The comparator returns 1, 0 or -1
     */
    class HighComp implements Comparator<Way> {

        /**
         * To hold reference Node
         */
        private Node reference;

        /**
         * Constructor with center specified
         */
        public HighComp(Node reference)
        {
            this.reference = reference;
        }

        @Override
        public int compare(Way a, Way b) {
            List<String> rankList = new ArrayList<String>();
            rankList.add("motorway");
            rankList.add("trunk");
            rankList.add("primary");
            rankList.add("secondary");
            rankList.add("tertiary");
            rankList.add("unclassified");
            rankList.add("residential");
            rankList.add("service");
            rankList.add("track");
            rankList.add("cycleway");
            rankList.add("footway");
            rankList.add("path");
            rankList.add("road");
            //TODO add _link roads too.

            //TODO don't crash if missing highway tag
            String ahigh = a.getKeys().get("highway");
            String bhigh = b.getKeys().get("highway");

            if (rankList.indexOf(ahigh) == rankList.indexOf(bhigh)) {
                return 0;
            }
            if (rankList.indexOf(ahigh) > rankList.indexOf(bhigh)) {
                return 1;
            } else {
                return -1;
            }
        }
    }

    /**
     * Move a node it distance meter in the heading of 
     * the next node in the way it is the last node in.
     *
     * @param Node   node     Node to be moved
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
     * Return a LatLon moved distance meter in heading from start
     *
     * @param LatLon start point
     * @param double heading in radians
     * @param double distance in Meter
     *
     * @return LatLon New position
     */
    private LatLon moveHeadingDistance(LatLon start, double heading, double distance)
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

    /**
     * Output a message
     *
     * @param String Message
     */
    public void pri(String str)
    {
        Notification t = new Notification(str);
        t.setIcon(JOptionPane.WARNING_MESSAGE);
        t.setDuration(Notification.TIME_SHORT);
        t.show();
        System.out.println(str);
    }

    public boolean selectFlareCandidates()
    {
        Collection<OsmPrimitive> selection = getCurrentDataSet().getSelected();
        List<Node> selectedNodes = OsmPrimitive.getFilteredList(selection, Node.class);
        List<Way> selectedWays = OsmPrimitive.getFilteredList(selection, Way.class);
        //pri("Selecting flare candidates");

        //We have exactly one way selected
        if (selection.size() == 1
            && selectedWays.size() == 1
        ) {
            Way way = selectedWays.get(0);
            //And the way is closed (looks like roundabout)
            if (way.isClosed()) {
                if (way.getKeys().get("junction") == "roundabout") {
                    List<Node> nodes = way.getNodes();
                    List<Node> nodes2 = new ArrayList<Node>();
                    List<Way> refWays;
                    for (Node node : nodes) {
                        refWays = OsmPrimitive.getFilteredList(node.getReferrers(), Way.class);
                        for (Way hmmway : refWays) {
                            if (hmmway.isFirstLastNode(node)
                                && hmmway!=way
                                && hmmway.getKeys().get("oneway") != "yes"
                            ) {
                                nodes2.add(node);
                            }
                        }
                    }
                    getCurrentDataSet().setSelected(nodes2);
                } else {
                    //System.out.println("not tagged");
                    return false;
                }
            } else {
                //System.out.println("not closed");
                return false;
            }
        } else {
            //System.out.println("not one way selected");
            return false;
        }
        return false;
    }

    /**
     * Make flares.
     *
    *       split way at the next node
    *       determine direction of the connected roundabout way
    *       along the roundabout, create a new node half the distance to the next node in both directions
    *       those two nodes become the end nodes of the two node way according to direction
    *       tag the flare(oneway=yes)
    *       split the flare at the outer node
    */
    public void makeFlares()
    {
        Collection<OsmPrimitive> selection = getCurrentDataSet().getSelected();
        List<Node> selectedNodes = OsmPrimitive.getFilteredList(selection, Node.class);
        List<Way> selectedWays = OsmPrimitive.getFilteredList(selection, Way.class);
        //pri("Selecting flare candidates");

        //We have a reasonable amount of nodes selected
        //TODO also check that they are all of the same roundabout
        if (0 < selectedNodes.size()
            && 10 > selectedNodes.size()
            && selection.size() == selectedNodes.size()
        ) {
            //pri("Making "+selection.size()+" flares");
            for (Node node : selectedNodes) {
                makeFlare(node);
            }
        }
    }

    public void makeFlare(Node node)
    {
        System.out.println("starting node");
        int flare_length = 6; //meter
        int direction = -1; //One arm of the flare will be connected to the next node

        //TODO some more sanity checks
        //two ways, one closed roundabout, one non oneway highway
        List<Way> referedWays = OsmPrimitive.getFilteredList(node.getReferrers(), Way.class);
        if (referedWays.size() == 2) {
            Way rw; //roundabout way
            Way iw; //incoming way

            //We take a chance:
            rw = referedWays.get(0);
            if (rw.isClosed() && rw.getKeys().get("junction") == "roundabout") {
                //We guessed right
                iw = referedWays.get(1);
            } else {
                rw = referedWays.get(1);
                iw = referedWays.get(0);
            }
            //TODO some more sanity checking!

            //Unglue the node from roundabout
            List<Node> ungrouped_nodes = unglueWays(node);

            if (ungrouped_nodes.size() != 2) pri("Unglue error");

            //Move towards next node in way
            moveWayEndNodeTowardsNextNode(ungrouped_nodes.get(0), flare_length);

            //Find relevant nodes for flare
            Node fs = ungrouped_nodes.get(0);
            Node fn1 = ungrouped_nodes.get(1);

            //Find the next node in the roundabout
            int new_pos = (rw.getNodes().indexOf(node) + direction)
                % rw.getNodes().size();
            Node fn2 = rw.getNodes().get(new_pos);

            //Create flare ways
            Way fw1 = new Way();
            Way fw2 = new Way();

            //add the nodes to the way
            fw1.addNode(fs);
            fw1.addNode(fn1);
            fw2.addNode(fn2);
            fw2.addNode(fs);

            //Copy tagging from iw
            Map<String,String> tagsToCopy = iw.getKeys();
            fw1.setKeys(tagsToCopy);
            fw2.setKeys(tagsToCopy);

            fw1.put("oneway", "yes");
            fw2.put("oneway", "yes");

            fw1.put("oneway_type", "roundabout_flare");
            fw2.put("oneway_type", "roundabout_flare");

            //Add them to osm
            Main.main.undoRedo.add(new AddCommand(fw1));
            Main.main.undoRedo.add(new AddCommand(fw2));
        } else {
            //The node had too many referrers, dunno with one to flare
        }
    }

} //end TagRoundaboutAction

//EOF
