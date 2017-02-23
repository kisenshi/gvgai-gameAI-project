package controllers.aimodule.KillSubject;

import core.game.Observation;
import core.game.StateObservationMulti;
import core.player.AbstractMultiPlayer;
import jdk.internal.cmm.SystemResourcePressureImpl;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Agent extends AbstractMultiPlayer {

    int id; //this player's ID
    int opp_id; //opponent player's ID

    // 0: walls, 1: floors, 2: traps, 3: cake
    int FLOOR_ID = 1;
    int TRAP_ID = 2;
    int CAKE_ID = 3;

    protected ArrayList<Observation> grid[][];
    protected List<ArrayList<Vector2d>> areas = new ArrayList<>();

    protected boolean agent_nav_matrix[][];
    protected int grid_width;
    protected int grid_height;
    protected int block_size;

    //TEST
    protected List<Vector2d> path_to_hell = null;
    private Queue<ACTIONS> actions_list = null;
    //

    protected CurrentPlan plan;

    private class CurrentPlan{
        private Queue<ACTIONS> actions;
        private double utility;
        private ACTIONS last_action;

        public CurrentPlan(Queue<ACTIONS> actions, double utility){
            this.actions = actions;
            this.utility = utility;
            this.last_action = null;
        }

        public void updatePlan(Queue<ACTIONS> new_actions, double new_utility){
            this.actions = new_actions;
            this.utility = new_utility;
        }

        public boolean isNewPlanBetter(double new_utility){
            if(new_utility > utility){
                return true;
            }
            return false;
        }

        public boolean isThereCurrentPlan(){
            return !actions.isEmpty();
        }

        public ACTIONS executeNextAction(){
            ACTIONS next_action = actions.poll();
            last_action = next_action;
            return next_action;
        }
    }

    /**
     * initialize all variables for the agent
     * @param stateObs Observation of the current state.
     * @param elapsedTimer Timer when the action returned is due.
     * @param playerID ID if this agent
     */
    public Agent(StateObservationMulti stateObs, ElapsedCpuTimer elapsedTimer, int playerID){
        id = playerID; //player ID of this agent
        opp_id = (playerID + 1) % 2; // player ID of the opponent. We know that there are only 2 players in the game
        block_size = stateObs.getBlockSize(); // useful to consider the map as coordinates for different calculations

        plan = new CurrentPlan(null,-1);

        // Gets floor and traps + floor
        Dimension grid_dimension = stateObs.getWorldDimension();
        grid_width = grid_dimension.width / block_size;
        grid_height = grid_dimension.height / block_size;

        ArrayList<Observation>[] fixedPositions = stateObs.getImmovablePositions();
        ArrayList<Observation> floor_elements = fixedPositions[FLOOR_ID];
        ArrayList<Observation> traps_elements = fixedPositions[TRAP_ID];

        // It is initialise a floor matrix to be able to distinguish between different areas
        // And the agent_nav_matrix that will be used to create the navigation graph on the flow when using the pathfinding algorithm
        boolean floor_matrix[][] = new boolean[grid_width][grid_height];
        agent_nav_matrix = new boolean[grid_width][grid_height];

        for (int k = 0; k < floor_elements.size(); k++) {
            Vector2d floor_position = floor_elements.get(k).position;
            int floor_coord_x = (int)floor_position.x / block_size;
            int floor_coord_y = (int)floor_position.y / block_size;

            floor_matrix[floor_coord_x][floor_coord_y] = true;
            agent_nav_matrix[floor_coord_x][floor_coord_y] = true;
        }

        for (int k = 0; k < traps_elements.size(); k++) {
            Vector2d trap_position = traps_elements.get(k).position;
            agent_nav_matrix[(int)trap_position.x / block_size][(int)trap_position.y / block_size] = true;
        }

        //TEST
        /*for (int i = 0; i < grid_dimension.width / block_size; i++){
            for (int j = 0; j < grid_dimension.height / block_size; j++){
                System.out.print(floor_matrix[i][j] + " ");
            }
            System.out.println();
        }*/


        // To obtain the different areas a stack will be used in the logic to push every tale that belongs to each area
        // for being connected
        Stack area_element_st = new Stack<Vector2d>();

        // NOTE: It is assumed that every map is surrounded by walls so there is no chance of going 'out of bounds' and therefore no need to check if i-1, i+1, j-1 or j+1 is out of bounds
        for (int i = 0; i < grid_width; i++) {
            for (int j = 0; j < grid_height; j++){

                /* -------------------------------------------- AREAS CALCULATION ------------------------------------------------------------ */

                if (floor_matrix[i][j]) {
                    ArrayList<Vector2d> current_area_positions = new ArrayList<>();
                    current_area_positions.add(new Vector2d(i * block_size, j * block_size));

                    // Check neighbours and add them to the stack to be visited and they are marked as false to avoid check them again
                    if (floor_matrix[i-1][j]){
                        area_element_st.push(new Vector2d(i-1, j));
                        floor_matrix[i-1][j] = false;
                    }
                    if (floor_matrix[i+1][j]){
                        area_element_st.push(new Vector2d(i+1, j));
                        floor_matrix[i+1][j] = false;
                    }
                    if (floor_matrix[i][j-1]){
                        area_element_st.push(new Vector2d(i, j-1));
                        floor_matrix[i][j-1] = false;
                    }
                    if (floor_matrix[i][j+1]){
                        area_element_st.push(new Vector2d(i, j+1));
                        floor_matrix[i][j+1] = false;
                    }

                    while(!area_element_st.empty()){
                        // While there are elements in the stack we are considering floor belonging to the same area,
                        // so the algorithm should continue
                        Vector2d current_area_position = (Vector2d)area_element_st.pop();

                        int current_i = (int)current_area_position.x;
                        int current_j = (int)current_area_position.y;

                        if (floor_matrix[current_i-1][current_j]){
                            area_element_st.push(new Vector2d(current_i-1, current_j));
                            floor_matrix[current_i-1][current_j] = false;
                        }
                        if (floor_matrix[current_i+1][current_j]){
                            area_element_st.push(new Vector2d(current_i+1, current_j));
                            floor_matrix[current_i+1][current_j] = false;
                        }
                        if (floor_matrix[current_i][current_j-1]){
                            area_element_st.push(new Vector2d(current_i, current_j-1));
                            floor_matrix[current_i][current_j-1] = false;
                        }
                        if (floor_matrix[current_i][current_j+1]){
                            area_element_st.push(new Vector2d(current_i, current_j+1));
                            floor_matrix[current_i][current_j+1] = false;
                        }

                        current_area_position.x *= block_size;
                        current_area_position.y *= block_size;

                        current_area_positions.add(current_area_position);
                    }
                    areas.add(current_area_positions);
                }

                /* ------------------------------------------------------------------------------------------------------------------------- */

            }
        }

        /*System.out.println(areas.get(0));
        System.out.println(areas.get(1));
        System.out.println(areas.get(2));
        System.out.println(areas.get(3));*/
    }

    /**
     * return ACTION_NIL on every call to simulate doNothing player
     * @param stateObs Observation of the current state.
     * @param elapsedTimer Timer when the action returned is due.
     * @return 	ACTION_NIL all the time
     */
    @Override
    public ACTIONS act(StateObservationMulti stateObs, ElapsedCpuTimer elapsedTimer) {
        // DEBUGING TO NOW WHAT WE HAVE AVAILABLE

        /*ArrayList<Observation>[] fixedPositions = stateObs.getImmovablePositions();
        ArrayList<Observation>[] movingPositions = stateObs.getMovablePositions();
        ArrayList<Observation>[] resourcesPositions = stateObs.getResourcesPositions();
        ArrayList<Observation>[] portalPositions = stateObs.getPortalsPositions();

        grid = stateObs.getObservationGrid();

        printDebug(fixedPositions,"fix");
        printDebug(movingPositions,"mov");
        printDebug(resourcesPositions,"res");
        printDebug(portalPositions,"por");
        System.out.println();*/

        ArrayList<Observation>[] fixedPositions = stateObs.getImmovablePositions();
        ArrayList<Observation> cake_pieces = fixedPositions[CAKE_ID];

        // It is obtained current positions for players and the cake
        Vector2d cakepos = cake_pieces.get(0).position; // Just 1 cake at the moment
        Vector2d avatarpos = stateObs.getAvatarPosition(opp_id);
        Vector2d agentpos = stateObs.getAvatarPosition(id);

        Vector2d agentorientation = stateObs.getAvatarOrientation(id);
        Vector2d avatarOrientation = stateObs.getAvatarOrientation(opp_id);

        /*System.out.println(avatarpos);
        System.out.println(avatarOrientation);
        System.out.println();*/


        // It is retrieved the actions needed to reach the path
        if (actions_list == null || actions_list.isEmpty()) {
            actions_list = getActionsToReachPosition(agentpos, avatarpos, agentorientation);
            // When the position is reached, SHOOT
            actions_list.addAll(shoot());
        }

        int cakearea = -1;
        int avatararea = -1;
        int agentarea = -1;

        /* In which area is each element? */
        for (int k=0; k < areas.size(); k++){
            ArrayList c_area = areas.get(k);
            if ((cakearea == -1)&&(c_area.contains(cakepos))){
                cakearea = k;
            }
            if ((avatararea == -1)&&(c_area.contains(avatarpos))){
                avatararea = k;
            }
            if ((agentarea == -1)&&(c_area.contains(agentpos))){
                agentarea = k;
            }
        }

        /*System.out.println();
        System.out.print("DISTRIBUTION: cake: "+cakearea+"avatar: "+avatararea+"agent: "+agentarea);

        System.out.println();
        for (int i = 0; i < grid_width; i++){
            for (int j = 0; j < grid_height; j++){
                if (agentpos.x / block_size == i && agentpos.y / block_size == j){
                    System.out.print(" T ");
                }else if (avatarpos.x / block_size == i && avatarpos.y / block_size == j) {
                    System.out.print(" A ");
                }else if (cakepos.x / block_size == i && cakepos.y / block_size == j) {
                    System.out.print(" C ");
                } else if (agent_nav_matrix[i][j]){
                    System.out.print(" - ");
                }else {
                    System.out.print(" X ");
                }

            }
            System.out.println();
        }
        System.out.println();*/

        /*ArrayList<ACTIONS> a = stateObs.getAvailableActions(id);
        System.out.println(a);

        if((previous_action == ACTIONS.ACTION_NIL)||(ACTIONS.isMoving(previous_action))){
            previous_action = ACTIONS.ACTION_USE;
            return ACTIONS.ACTION_USE;
        }*/

        return carryOutNextAction(actions_list);
    }

    /**
     * AGENT POSSIBLE ACTIONS
     * It si defined the list of different actions the agent would be able to carry out combining the actions available
     */

    private Queue<ACTIONS> shoot(){
        Queue<ACTIONS> actions_queue =  new LinkedList<>();
        actions_queue.add(ACTIONS.ACTION_USE);
        return actions_queue;
    }

    private Queue<ACTIONS> shootInCertainDirection(){
        Queue<ACTIONS> actions_queue =  new LinkedList<>();
        actions_queue.add(ACTIONS.ACTION_NIL);
        return actions_queue;
    }

    private ACTIONS carryOutNextAction(Queue<ACTIONS> actions_queue){
        if (actions_queue.isEmpty()){
            return ACTIONS.ACTION_NIL;
        }

        return actions_queue.poll();
    }

    private ACTIONS move(Vector2d orientation){
        Vector2d VECTOR_UP = new Vector2d(0, -1);
        Vector2d VECTOR_DOWN = new Vector2d(0, 1);
        Vector2d VECTOR_RIGHT = new Vector2d(1, 0);
        Vector2d VECTOR_LEFT = new Vector2d(-1, 0);

        if (orientation.equals(VECTOR_UP)){
            return ACTIONS.ACTION_UP;
        }
        if (orientation.equals(VECTOR_DOWN)){
            return ACTIONS.ACTION_DOWN;
        }
        if (orientation.equals(VECTOR_RIGHT)){
            return ACTIONS.ACTION_RIGHT;
        }
        if (orientation.equals(VECTOR_LEFT)){
            return ACTIONS.ACTION_LEFT;
        }

        return ACTIONS.ACTION_NIL;
    }

    private ACTIONS changeOrientation(Vector2d needed_orientation){
        return move(needed_orientation);
    }

    private Vector2d getAdjacentAgentSpace(int x, int y){
        if (agent_nav_matrix[x-1][y]){
            return new Vector2d((x-1)*block_size, y*block_size);
        }

        if (agent_nav_matrix[x+1][y]){
            return new Vector2d((x+1)*block_size, y*block_size);
        }

        if (agent_nav_matrix[x][y-1]){
            return new Vector2d(x*block_size, (y-1)*block_size);
        }

        if (agent_nav_matrix[x][y+1]){
            return new Vector2d(x*block_size, (y+1)*block_size);
        }

        return null;
    }

    private Queue<ACTIONS> getActionsToReachPosition(Vector2d start, Vector2d goal, Vector2d start_orientation){
        Queue<ACTIONS> actions_queue = new LinkedList<>();

        // If goal is not in the agent space, the queue is returned empty
        if (!agent_nav_matrix[(int)goal.x/block_size][(int)goal.y/block_size]){
            return actions_queue;
        }

        List<Vector2d> path = aStarPath(start, goal);
        System.out.println(path);

        if (path.isEmpty()){
            actions_queue.add(ACTIONS.ACTION_NIL);
        }

        Vector2d current_pos = start;
        Vector2d current_orientation = new Vector2d(start_orientation.x, start_orientation.y);
        Vector2d needed_orientation = new Vector2d(0,0);
        for (int k=0; k < path.size(); k++){
            Vector2d next_pos = path.get(k);

            // It is obtained the difference between the vectors to figure out the orientation that should be applied
            // It is needed to normalise the orientation vector to work in the same conditions
            needed_orientation.set(next_pos.x - current_pos.x, next_pos.y - current_pos.y);
            needed_orientation.normalise();

            if (!current_orientation.equals(needed_orientation)){
                actions_queue.add(changeOrientation(needed_orientation));
                current_orientation.set(needed_orientation.x, needed_orientation.y);
            }

            // Move
            actions_queue.add(move(current_orientation));
            current_pos = next_pos;
        }

        return actions_queue;
    }

    /* Fills the neighbours list with the reachable positions from the current one
     * It is checked TOP, RIGHT, DOWN and LEFT as it is not possible to move in diagonal */
    private void getCurrentPositionNeighbours(List<Vector2d> neighbours, Vector2d c_pos){
        int x = (int)c_pos.x / block_size;
        int y = (int)c_pos.y / block_size;

        // NOTE: It is assumed that every map is surrounded by walls so there is no chance of going 'out of bounds' and
        // therefore no need to check if i-1, i+1, j-1 or j+1 is out of bounds

        if (agent_nav_matrix[x-1][y]){
            neighbours.add(new Vector2d((x-1)*block_size, y*block_size));
        }

        if (agent_nav_matrix[x+1][y]){
            neighbours.add(new Vector2d((x+1)*block_size, y*block_size));
        }

        if (agent_nav_matrix[x][y-1]){
            neighbours.add(new Vector2d(x*block_size, (y-1)*block_size));
        }

        if (agent_nav_matrix[x][y+1]){
            neighbours.add(new Vector2d(x*block_size, (y+1)*block_size));
        }
        return;
    }

    /* Object to be used in the PriorityQueue containing the vector2d position and the cost assigned to it
    *  It has been implemented the Comparable interface (compareTo function) to be able to be used in the
    *  priority Queue directly */
    private class NodePosition implements Comparable<NodePosition>{
        public Vector2d position;
        public double cost;

        public NodePosition(Vector2d position, double cost){
            this.position = position;
            this.cost = cost;
        }

        @Override
        public int compareTo(NodePosition o) {
            if (this.cost < o.cost){
                return -1;
            }
            if (this.cost > o.cost){
                return 1;
            }
            return 0;
        }
    }

    /* Solution for being able to map two Keys to be able to use vector coordinates as index
    *  Found in StackOverflow: http://stackoverflow.com/a/14678042 and adapted to my needs */
    public class VectorKeys {

        private int x;
        private int y;

        public VectorKeys(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public void updateKeys(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VectorKeys)) return false;
            VectorKeys key = (VectorKeys) o;
            return x == key.x && y == key.y;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return result;
        }

    }

    private double aStarHeuristic(Vector2d position, Vector2d goal){
        // The distance to the goal is returned as the heuristic
        return position.dist(goal);
    }

    private List<Vector2d> aStarPath(Vector2d start, Vector2d goal){
        System.out.println("Looking path from "+start+" to "+goal);

        PriorityQueue<NodePosition> frontier = new PriorityQueue<>();
        Map<VectorKeys, Vector2d> visited_from = new HashMap<>();
        Map<VectorKeys, Double> cost_so_far = new HashMap<>();
        List<Vector2d> neighbours = new ArrayList<>();

        NodePosition startNode = new NodePosition(start, 0);
        frontier.add(startNode);

        VectorKeys start_keys = new VectorKeys((int)start.x,(int)start.y);
        visited_from.put(start_keys, null);
        cost_so_far.put(start_keys, 0.0);

        boolean goal_found = false;

        while(!frontier.isEmpty()){
            NodePosition currentNode = frontier.poll();
            if (currentNode.position.equals(goal)){
                // GOAL HAS BEEN FOUND YAYYYYYYY
                goal_found = true;
                break;
            }

            neighbours.clear();
            getCurrentPositionNeighbours(neighbours, currentNode.position);

            VectorKeys currentNode_keys = new VectorKeys((int)currentNode.position.x, (int)currentNode.position.y);

            for(int k=0; k < neighbours.size(); k++){
                Vector2d next = neighbours.get(k);
                VectorKeys next_keys = new VectorKeys((int)next.x, (int)next.y);

                double next_cost = cost_so_far.get(currentNode_keys) + 1; // all costs are 1

                if (!cost_so_far.containsKey(next_keys) || next_cost < cost_so_far.get(next_keys)){
                    NodePosition next_node_pos = new NodePosition(next, next_cost+aStarHeuristic(next, goal));

                    frontier.add(next_node_pos);
                    visited_from.put(next_keys, currentNode.position);
                    cost_so_far.put(next_keys, next_cost);
                }
            }
        }

        // Reconstruct the path
        List<Vector2d> path = null;
        if (goal_found) {
            path = new ArrayList<>();

            Vector2d current_pos = goal;
            VectorKeys current_pos_keys = new VectorKeys((int)current_pos.x, (int)current_pos.y);

            while (!current_pos.equals(start)) {

                path.add(current_pos);

                current_pos = visited_from.get(current_pos_keys);
                current_pos_keys.updateKeys((int)current_pos.x, (int)current_pos.y);

                if (path.contains(current_pos)) {
                    System.out.println ("Error: path contains a loop");
                    return new ArrayList<>();
                }
            }
            //path.add(start);
            Collections.reverse(path);
        }

        return path;
    }

    /**
     * Prints the number of different types of sprites available in the "positions" array.
     * Between brackets, the number of observations of each type.
     * @param positions array with observations.
     * @param str identifier to print
     */
    private void printDebug(ArrayList<Observation>[] positions, String str)
    {
        if(positions != null){
            System.out.print(str + ":" + positions.length + "(");
            for (int i = 0; i < positions.length; i++) {
                System.out.print(positions[i].size() + ",");
                for (int j=0; j < positions[i].size(); j++) {
                    Vector2d obs = positions[i].get(j).position;
                    /*System.out.print(obs.x + " " + obs.y);
                    System.out.println();*/
                }
            }
            System.out.print("); ");
        }else System.out.print(str + ": 0; ");
    }
}
