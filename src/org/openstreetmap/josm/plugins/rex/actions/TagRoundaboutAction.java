// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rex.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.SplitWayCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

/**
 * Expands to a roundabout
 *
 * @author Gorm
 */
public class TagRoundaboutAction extends JosmAction {

    private static final long serialVersionUID = 1L;

    @Override
    protected void updateEnabledState() {
        if (getLayerManager().getEditDataSet() == null) {
            setEnabled(false);
        } else
            updateEnabledState(getLayerManager().getEditDataSet().getSelected());
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        if (selection == null || selection.isEmpty()) {
            setEnabled(false);
            return;
        }
    }

    public TagRoundaboutAction() {
        super(
                tr("Roundabout Expander"),
                "dialogs/logo-rex.png",
                tr("Roundabout Expander"),
                Shortcut.registerShortcut("menu:rex",
                        tr("Menu: {0}", tr("Roundabout Expander")),
                        KeyEvent.VK_R, Shortcut.CTRL_SHIFT),
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
        Collection<OsmPrimitive> selection = getLayerManager().getEditDataSet().getSelected();
        List<Node> selectedNodes = new ArrayList<>(Utils.filteredCollection(selection, Node.class));
        List<Way> selectedWays = new ArrayList<>(Utils.filteredCollection(selection, Way.class));

        //If we have exactly one single node selected
        if (selection.size() == 1
                && selectedNodes.size() == 1
                ) {
            Node node = selectedNodes.get(0);
            if (!"mini_roundabout".equals(node.getKeys().get("highway"))) {
                //Make it a mini roundabout
                tagAsRoundabout(node);
            } else {
                //Get defaults
                double radi = Config.getPref().getInt("rex.diameter_meter", 12) /2;
                double max_gap = Math.toRadians(Config.getPref().getInt("rex.max_gap_degrees", 30));
                boolean lefthandtraffic = Config.getPref().getBoolean("mappaint.lefthandtraffic", false);

                //See if user want another direction
                if (node.getKeys().containsKey("direction") &&
                        node.getKeys().get("direction").equals("clockwise")
                        ) {
                    lefthandtraffic = true;
                    Logging.debug("REX: direction overridden by direction=clockwise");
                }

                //See if user want another size
                if (node.getKeys().containsKey("diameter")) {
                    try {
                        int d = Integer.parseInt(node.getKeys().get("diameter"));
                        radi = d/2;
                        Logging.debug("REX: diameter overridden by tag diameter={0}", d);
                    } catch (NumberFormatException ex) {
                        Logging.warn("REX: failed getting diameter from node tag diameter");
                        Logging.warn(ex);
                    }
                }
                makeRoundabout(node, radi, lefthandtraffic, max_gap);
                selectFlareCandidates();
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
        //reduce 1 to 0 in the if
        if (1 < selectedNodes.size()
                && selection.size() == selectedNodes.size()
                ) {
            makeFlares();
        }

        MainApplication.getMap().mapView.repaint();
    }

    /**
     * Tag node as roundabout
     *
     * This method is overloaded with (Way circle)
     * @param node node
     */
    public void tagAsRoundabout(Node node) {
        // TODO direction as well?
        UndoRedoHandler.getInstance().add(new ChangePropertyCommand(node, "junction", "roundabout"));
        UndoRedoHandler.getInstance().add(new ChangePropertyCommand(node, "highway", "mini_roundabout"));
        int d = Config.getPref().getInt("rex.diameter_meter", 12);
        UndoRedoHandler.getInstance().add(new ChangePropertyCommand(node, "diameter", Integer.toString(d)));
    }

    /**
     * Tag closed way as roundabout
     *
     * This method is overloaded with (Node node)
     * @param circle way
     */
    public void tagAsRoundabout(Way circle) {
        DataSet ds = circle.getDataSet();
        if (ds == null) {
            ds = OsmDataManager.getInstance().getEditDataSet();
        }
        Map<String, String> tags = new HashMap<>();
        //Main tag to make a way a roundabout
        tags.put("junction", "roundabout");
        //oneway is implicit from junction=roundabout, so not needed
        //TODO If oneway=-1 then reverse direction of circle before
        tags.put("oneway", "");

        //If mistagged as a mini_roundabout, remove it
        if ("mini_roundabout".equals(circle.getKeys().get("highway"))) {
            tags.put("highway", "");
        }

        //If not already tagged as highway, tag as road
        if (!circle.getKeys().containsKey("highway")) {
            tags.put("highway", "road");
        }
        UndoRedoHandler.getInstance().add(new ChangePropertyCommand(ds, Collections.singleton(circle), tags));
    }

    /**
     * Create a roundabout way
     *
     * @param node            Node to expand to Roundabout
     * @param radi            Radius of roundabout in meter
     * @param lefthandtraffic Direction of roundabout
     * @param max_gap         Max gap in radians between nodes to make it pretty
     */
    public void makeRoundabout(Node node, double radi, boolean lefthandtraffic, double max_gap) {
        //Store center for later use
        LatLon center = node.getCoor();

        //Copy tags from most prominent way.
        //TODO Prioritize through ways over single ways.
        List<Way> refWays = new ArrayList<>(Utils.filteredCollection(node.getReferrers(), Way.class));
        Collections.sort(refWays, new HighComp(node));
        Map<String, String> tagsToCopy = new HashMap<>();
        if (!refWays.isEmpty()) {
            tagsToCopy.putAll(refWays.get(0).getKeys());
        }

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

        //Always as righthand for the maths below to be correct
        angularSort(ungrouped_nodes, center, false);

        //Construct some nodes to make it pretty.
        Node filler_node = null;
        double heading1, heading2;
        int s = ungrouped_nodes.size();
        DataSet ds = OsmDataManager.getInstance().getEditDataSet();
        for (int i = 0, next_i = 0; i < s; i++) {
            next_i = i+1;
            //Reference back to start
            if (next_i == s) next_i = 0;

            heading1 = -center.bearing(ungrouped_nodes.get(i).getCoor());
            heading2 = -center.bearing(ungrouped_nodes.get(next_i).getCoor());

            //Add full circle (2PI) to heading2 to "come around" the circle.
            if (heading1 > heading2 || i == next_i) {
                heading2 += Math.PI*2;
            }

            double gap = heading2 - heading1;
            int fillers_to_make = ((int) (gap/max_gap))-1;
            if (fillers_to_make > 0) {
                double to_next = gap / (fillers_to_make+1);
                double next;
                for (int j = 1; j <= fillers_to_make; j++) {
                    next = heading1 + to_next * j;
                    filler_node = new Node(moveHeadingDistance(center, next, radi));
                    UndoRedoHandler.getInstance().add(new AddCommand(ds, filler_node));
                    ungrouped_nodes.add(filler_node);
                }
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
        UndoRedoHandler.getInstance().add(new AddCommand(ds, newRoundaboutWay));

        //Select it
        getLayerManager().getEditDataSet().setSelected(newRoundaboutWay);

    }

    /**
     * Split all ways connected to node
     * TODO BUG Crashes on circular ways. ALSO MAKE A Circular Way Splitter!
     */
    private void splitAll(Node node) {
        //Find all ways connected to this node
        List<Way> referedWays = new ArrayList<>(Utils.filteredCollection(node.getReferrers(), Way.class));
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
                UndoRedoHandler.getInstance().add(SplitWayCommand.split(
                        from,
                        Collections.singletonList(node),
                        Collections.<OsmPrimitive>emptyList()
                        ));
            }
        }
    }

    /**
     * Unglue all ways using the selectedNode and return the set of new nodes
     *
     * @param Node selectedNode The original node
     *
     * @return The list of new nodes
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
        if (wayWithSelectedNode == null && !parentWays.isEmpty()) {
            parentWays.removeFirst();
        }
        //Then actually unglue each parent way
        for (Way w : parentWays) {
            UndoRedoHandler.getInstance().add(new ChangeCommand(w, modifyWay(selectedNode, w, newNodes)));
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
        UndoRedoHandler.getInstance().add(new AddCommand(OsmDataManager.getInstance().getEditDataSet(), newNode));

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
     * @param nodes nodes
     * @param center center
     * @param clockwise clockwise?
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
    static class AngComp implements Comparator<Node> {

        /**
         * To hold center Node
         */
        private final LatLon center;

        /**
         * Constructor with center specified
         */
        AngComp(LatLon center) {
            this.center = center;
        }

        @Override
        public int compare(Node a, Node b) {
            double ah = center.bearing(a.getCoor());
            double bh = center.bearing(b.getCoor());
            if (ah == bh) return 0;
            return (ah > bh) ? 1 : -1;
        }

    } //END AngComp

    /**
     * A comparator that may be used to sort Ways by beefyness
     * relative to node.
     * The comparator returns 1, 0 or -1
     */
    static class HighComp implements Comparator<Way> {

        /**
         * Constructor with center specified
         */
        HighComp(Node reference) {
        }

        @Override
        public int compare(Way a, Way b) {
            List<String> rankList = new ArrayList<>();
            rankList.add("motorway");
            rankList.add("motorway_link");
            rankList.add("trunk");
            rankList.add("trunk_link");
            rankList.add("primary");
            rankList.add("primary_link");
            rankList.add("secondary");
            rankList.add("secondary_link");
            rankList.add("tertiary");
            rankList.add("unclassified");
            rankList.add("residential");
            rankList.add("service");
            rankList.add("track");
            rankList.add("cycleway");
            rankList.add("footway");
            rankList.add("path");
            rankList.add("road");
            rankList.add("construction");

            //reverse so that 'not found' (-1) comes last
            Collections.reverse(rankList);

            //TODO don't crash if missing highway tag
            String ahigh = a.getKeys().get("highway");
            String bhigh = b.getKeys().get("highway");

            if (rankList.indexOf(ahigh) == rankList.indexOf(bhigh)) {
                return 0;
            }
            if (rankList.indexOf(ahigh) < rankList.indexOf(bhigh)) {
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
     * @param node     Node to be moved
     * @param distance Distance to move node in meter
     */
    public boolean moveWayEndNodeTowardsNextNode(Node node, double distance) {
        //some verification:
        List<Way> referedWays = new ArrayList<>(Utils.filteredCollection(node.getReferrers(), Way.class));

        //node must be member of exactly one way
        if (referedWays.size() != 1) {
            //pri("node is not member of exactly one way");
            return false;
        } else {
            return moveWayEndNodeTowardsNextNode(node, distance, referedWays.get(0));
        }
    }

    /**
     * Move a node it distance meter in the heading of
     * the next node in the way it is the last node in.
     *
     * @param node     Node to be moved
     * @param distance Distance to move node in meter
     * @param way      Way
     */
    public boolean moveWayEndNodeTowardsNextNode(Node node, double distance, Way way) {
        //Node must be first or last node in way
        if (!way.isFirstLastNode(node)) {
            //pri("not first or last node in way");
            return false;
        }

        //Way must be at least two nodes long
        if (way.getNodesCount() < 2) {
            //pri("fewer than two nodes");
            return false;
        }

        //Find heading to next node
        Node ajacent_node = way.getNeighbours(node).iterator().next();
        double heading = -node.getCoor().bearing(ajacent_node.getCoor());

        //Move the node towards the next node
        LatLon newpos = moveHeadingDistance(node.getCoor(), heading, distance);
        node.setCoor(newpos);

        return true;
    }

    /**
     * Return a LatLon moved distance meter in heading from start
     *
     * @param start point
     * @param heading in radians
     * @param distance in Meter
     *
     * @return LatLon New position
     */
    private LatLon moveHeadingDistance(LatLon start, double heading, double distance) {
        double R = 6378100; //Radius of the Earth in meters

        double lat1 = Math.toRadians(start.lat());
        double lon1 = Math.toRadians(start.lon());

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(distance/R) +
                Math.cos(lat1) * Math.sin(distance/R) * Math.cos(heading));

        double lon2 = lon1 + Math.atan2(Math.sin(heading) * Math.sin((distance*-1)/R) * Math.cos(lat1),
                Math.cos(distance/R) - Math.sin(lat1) * Math.sin(lat2));

        return new LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }

    /**
     * Output a warning message
     *
     * @param str Message
     */
    public void pri(String str) {
        Notification t = new Notification(str);
        t.setIcon(JOptionPane.WARNING_MESSAGE);
        t.setDuration(Notification.TIME_SHORT);
        t.show();
        Logging.warn(str);
    }

    public boolean selectFlareCandidates() {
        Collection<OsmPrimitive> selection = getLayerManager().getEditDataSet().getSelected();
        List<Way> selectedWays = new ArrayList<>(Utils.filteredCollection(selection, Way.class));
        //pri("Selecting flare candidates");

        //We have exactly one way selected
        if (selection.size() == 1
                && selectedWays.size() == 1
                ) {
            Way way = selectedWays.get(0);
            //And the way is closed (looks like roundabout)
            if (way.isClosed()) {
                if ("roundabout".equals(way.getKeys().get("junction"))) {
                    List<Node> nodes = way.getNodes();
                    List<Node> nodes2 = new ArrayList<>();
                    List<Way> refWays;
                    for (Node node : nodes) {
                        refWays = new ArrayList<>(Utils.filteredCollection(node.getReferrers(), Way.class));
                        for (Way hmmway : refWays) {
                            if (hmmway.isFirstLastNode(node)
                                    && hmmway != way
                                    && !"yes".equals(hmmway.getKeys().get("oneway"))
                                    ) {
                                nodes2.add(node);
                            }
                        }
                    }
                    getLayerManager().getEditDataSet().setSelected(nodes2);
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
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
    public boolean makeFlares() {
        Collection<OsmPrimitive> selection = getLayerManager().getEditDataSet().getSelected();
        List<Node> selectedNodes = new ArrayList<>(Utils.filteredCollection(selection, Node.class));

        //We have a reasonable amount of nodes selected
        if (0 < selectedNodes.size()
                && 10 > selectedNodes.size()
                && selection.size() == selectedNodes.size()
                ) {
            Set<Way> commonWays = findCommonWays(selectedNodes);
            if (commonWays.size() == 1) {
                Way tWay = commonWays.iterator().next();
                for (Node cNode : selectedNodes) {
                    List<Way> iWayCandidates = new ArrayList<>(Utils.filteredCollection(cNode.getReferrers(), Way.class));
                    if (iWayCandidates.size() == 2) {
                        for (Way iWay : iWayCandidates) {
                            if (iWay != tWay) {
                                makeFlare(iWay, tWay, cNode);
                            }
                        }
                    }
                }
                getLayerManager().getEditDataSet().setSelected(tWay);
                return true;
            } //else {
            // There was no one common way
            // TODO perhaps look in the set for a closed or roundabout?
            //if (w.isClosed() && w.getKeys().get("junction").equals("roundabout")) {
            //}
            /*
            for (Way w : OsmPrimitive.getFilteredList(selectedNodes.get(0).getReferrers(), Node.class)) {
             */
        }
        return false;
    }

    /**
     * Find a set of ways that all nodes are a member of
     */
    private Set<Way> findCommonWays(List<Node> nodes) {
        Set<Way> ret = new HashSet<>();

        //We examine the referring ways of one of nodes
        Node n = nodes.get(0);
        List<Way> nodeReferringWays = new ArrayList<>(Utils.filteredCollection(n.getReferrers(), Way.class));
        for (Way referredWay : nodeReferringWays) {
            //if all nodes are a member
            if (wayContainsAllNodes(referredWay, nodes)) {
                ret.add(referredWay);
            }
        }

        return ret;
    }

    private boolean wayContainsAllNodes(Way way, List<Node> nodes) {
        for (Node node : nodes) {
            if (!way.containsNode(node)) return false;
        }
        return true;
    }

    /**
     * @param iWay  incoming way
     * @param tWay  across way
     * @param cNode common node
     *
     * @return boolean Success
     */
    public boolean makeFlare(Way iWay, Way tWay, Node cNode) {
        //pri("making flare on "+cNode);
        int flare_length = 6; //meter
        int direction = -1; //One arm of the flare will be connected to the next node

        if (iWay.isFirstLastNode(cNode) && tWay.containsNode(cNode)
                //iWay must be >1 tWay > 2
                ) {
            //Carry on
        } else {
            //cNode is not common for iWay and tWay
            return false;
        }

        //Unglue cNode from tWay
        List<Node> a = new LinkedList<>();
        UndoRedoHandler.getInstance().add(new ChangeCommand(iWay, modifyWay(cNode, iWay, a)));
        Node iWayNewNode = a.get(0);

        //Move iWayNewNode towards ajacent node in iWay
        if (!moveWayEndNodeTowardsNextNode(iWayNewNode, flare_length, iWay)) return false;

        //Find relevant nodes for flare
        Node fs = iWayNewNode;
        Node fn1 = cNode;

        //Find the next node in tWay
        int new_pos = tWay.getNodes().indexOf(cNode) + direction;
        if (tWay.isClosed()) {
            //Closed
            //  0 1 2 3 4  0=4
            if (new_pos < 0) new_pos += tWay.getRealNodesCount();
            if (new_pos >= tWay.getNodesCount()) new_pos = 0;
        } else {
            //Open
            // 0 1 2 3
            if (new_pos < 0) new_pos += tWay.getRealNodesCount();
            if (new_pos >= tWay.getNodesCount()) new_pos = 0;
        }
        Node fn2 = tWay.getNodes().get(new_pos);

        //Create flare ways
        Way flareWay1 = new Way();
        Way flareWay2 = new Way();

        //add the nodes to the way
        flareWay1.addNode(fs);
        flareWay1.addNode(fn1);

        flareWay2.addNode(fn2);
        flareWay2.addNode(fs);

        //Copy tagging from iWay
        Map<String, String> tagsToCopy = iWay.getKeys();
        flareWay1.setKeys(tagsToCopy);
        flareWay2.setKeys(tagsToCopy);

        flareWay1.put("oneway", "yes");
        flareWay2.put("oneway", "yes");

        flareWay1.put("oneway_type", "roundabout_flare");
        flareWay2.put("oneway_type", "roundabout_flare");

        //Add them to osm
        DataSet ds = OsmDataManager.getInstance().getEditDataSet();
        UndoRedoHandler.getInstance().add(new AddCommand(ds, flareWay1));
        UndoRedoHandler.getInstance().add(new AddCommand(ds, flareWay2));

        return true;
    } //end method makeFlare
} //end class TagRoundaboutAction

//EOF
