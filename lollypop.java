
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
                    Collection<OsmPrimitive> select = Collections.<OsmPrimitive>emptyList();
                    select.add(way);
                    select.add(moveToNode);
                    getCurrentDataSet().setSelected(select);

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


