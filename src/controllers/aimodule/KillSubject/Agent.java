package controllers.aimodule.KillSubject;

import com.sun.deploy.util.SyncAccess;
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

    private Queue<ACTIONS> actions_list = null;
    private boolean deployed = false;

    private int lastAvatarArea;

    private List<Vector2d> cake_positions = new ArrayList<>();

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

        // Gets floor and traps + floor
        Dimension grid_dimension = stateObs.getWorldDimension();
        grid_width = grid_dimension.width / block_size;
        grid_height = grid_dimension.height / block_size;

        ArrayList<Observation>[] fixedPositions = stateObs.getImmovablePositions();
        ArrayList<Observation> floor_elements = fixedPositions[FLOOR_ID];
        ArrayList<Observation> traps_elements = fixedPositions[TRAP_ID];
        ArrayList<Observation> cake_elements = fixedPositions[CAKE_ID];

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

        for (int k = 0; k < cake_elements.size(); k++){
            Vector2d cake_position = cake_elements.get(k).position;
            cake_positions.add(cake_position);
        }

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

        // It is initialise the area where the avatar is located
        Vector2d avatarpos = stateObs.getAvatarPosition(opp_id);
        lastAvatarArea = getAreaLocated(avatarpos);
    }

    /**
     * return ACTION_NIL on every call to simulate doNothing player
     * @param stateObs Observation of the current state.
     * @param elapsedTimer Timer when the action returned is due.
     * @return 	ACTION_NIL all the time
     */
    @Override
    public ACTIONS act(StateObservationMulti stateObs, ElapsedCpuTimer elapsedTimer) {

        ArrayList<Observation>[] fixedPositions = stateObs.getImmovablePositions();

        // It is obtained current positions for players and the cake
        Vector2d avatarpos = stateObs.getAvatarPosition(opp_id);
        Vector2d agentpos = stateObs.getAvatarPosition(id);

        Vector2d agentorientation = stateObs.getAvatarOrientation(id);

        List<Vector2d> vision_path_to_avatar = getVisionPathToAvatar(avatarpos);

        // If the avatar is in sight it is high priority and we should shoot
        // It does not matter if there were something else planned
        if (isAvatarInSight(vision_path_to_avatar, agentpos)){
            // DEPLOYING!!!!
            if (actions_list == null || actions_list.isEmpty()) {
                deployed = false;
            }
            if (!deployed){
                // Delay added to every shoot as people was becoming crazy...
                actions_list.clear();
                actions_list.addAll(slowDown(4));
                actions_list.addAll(shootAvatar(agentpos, avatarpos, agentorientation));
                deployed = true;
            }
        } else {
            // IS ANYONE THERE?
            deployed = false;
            // It there are actions planned to carry out, no search is made unless the avatar has changed their area
            if (actions_list == null || actions_list.isEmpty() || hasAvatarChangedArea(avatarpos)) {
                // Looks for the best closest spot to shoot the subject 0.0
                Vector2d best_spot = getBestSpotToShootAvatar(agentpos, vision_path_to_avatar);
                actions_list = getActionsToReachPosition(agentpos, best_spot, agentorientation);
            }
        }

        lastAvatarArea = getAreaLocated(avatarpos);

        // Returns the next action planned
        return carryOutNextAction();
    }

    /**
     * WORLD STATE CHECK
     * Functions that allow the agent to check the world status and decide how to act depending on it
     */

    /*
    * Checks if the coordinates belongs to a position out of the grid
    * */
    private boolean isOutOfBounds(int x, int y){
        if ((x < 0) || (x >= grid_width)){
            return true;
        }
        if ((y < 0) || (y >= grid_height)){
            return true;
        }
        return false;
    }

    /*
    * Returns the id of the area the provided location belongs to
    * */
    private int getAreaLocated(Vector2d location){
        int area = -1;

        for (int k=0; k < areas.size(); k++){
            ArrayList c_area = areas.get(k);

            if (c_area.contains(location)){
                area = k;
                break;
            }
        }

        return area;
    }

    /*
    * Checks if the avatar has changed its area comparing her current position
    * with the one stored last
    * */
    private boolean hasAvatarChangedArea(Vector2d avatarpos){
        // Checks if the avatar has changed the area since the last tick
        int avatararea = getAreaLocated(avatarpos);

        // Has the avatar changed the area since last tick?
        return (lastAvatarArea != avatararea);
    }

    /*
    * Checks if the agent and the avatar are in line
    * It is provided the positions that are in sight from the avatar and checks if
    * the agent is in one of them
    * */
    private boolean isAvatarInSight(List<Vector2d> avatar_in_sight_positions, Vector2d position){
        return avatar_in_sight_positions.contains(position);
    }

    /*
    * Returns the best position the agent should move to
    * Goes through every avatar in sight positions and chooses the closest one to the agent
    * */
    private Vector2d getBestSpotToShootAvatar(Vector2d agentpos, List<Vector2d> avatar_in_sight_positions){
        Vector2d best_position = new Vector2d(0,0);
        double best_dist = 1000000000;
        double spot_dist;
        for (int i=0; i < avatar_in_sight_positions.size(); i++){
            Vector2d position = avatar_in_sight_positions.get(i);
            if (!cake_positions.contains(position)) {
                // The cake position is not a reachable position for the agent so remove it from the available positions
                spot_dist = agentpos.dist(position);
                if (spot_dist < best_dist) {
                    best_dist = spot_dist;
                    best_position = position;
                }
            }
        }

        return best_position;
    }

    /*
    * Returns the position that are in sight from the avatar
    * Starting with distance 1, checks if the spot is in the agent space
    * Distance is increased until there are no more in sight spots and the algorithm finished
    * */
    private List<Vector2d> getVisionPathToAvatar(Vector2d avatarpos){
        // In every direction, it is checked if the tales are in the agent space and include them in the vision path
        // to avatar positions
        List<Vector2d> vision_to_avatar_list = new ArrayList<>();

        int x = (int)avatarpos.x / block_size;
        int y = (int)avatarpos.y / block_size;

        int d = 1; // The distance from the avatar to check
        int coord = 0;
        boolean checkR = true, checkL = true, checkU = true, checkD = true;
        while (checkR || checkL || checkU || checkD){
            // Right vision positions
            if (checkR){
                coord = x + d;

                if (!isOutOfBounds(coord, y) && (agent_nav_matrix[coord][y])){
                    vision_to_avatar_list.add(new Vector2d(coord*block_size, y*block_size));
                } else {
                    // End of vision to the right reached
                    checkR = false;
                }
            }
            // Left vision positions
            if (checkL){
                coord = x - d;

                if (!isOutOfBounds(coord, y) && (agent_nav_matrix[coord][y])){
                    vision_to_avatar_list.add(new Vector2d(coord*block_size, y*block_size));
                } else {
                    // End of vision to the left reached
                    checkL = false;
                }
            }
            // Up vision position
            if (checkU){
                coord = y - d;

                if (!isOutOfBounds(x, coord) && (agent_nav_matrix[x][coord])){
                    vision_to_avatar_list.add(new Vector2d(x*block_size, coord*block_size));
                } else {
                    // End of vision up reached
                    checkU = false;
                }
            }
            // Down vision position
            if (checkD){
                coord = y + d;

                if (!isOutOfBounds(x, coord) && (agent_nav_matrix[x][coord])){
                    vision_to_avatar_list.add(new Vector2d(x*block_size, coord*block_size));
                } else {
                    // End of vision down reached
                    checkD = false;
                }
            }
            d++;
        }

        return vision_to_avatar_list;
    }

    /**
     * AGENT POSSIBLE ACTIONS
     * It si defined the list of different actions the agent would be able to carry out combining the actions available
     */

    /**
     * SINGLE ACTIONS
     * */

    /*
    * Returns next action in the actions_list or NIL if it is empty
    * */
    private ACTIONS carryOutNextAction() {
        if (actions_list.isEmpty()) {
            return ACTIONS.ACTION_NIL;
        }

        return actions_list.poll();
    }

    /*
    * Just shoots, which corresponds with ACTION_USE but defined for mental sanity when coding
    * */
    private ACTIONS shoot(){
        return ACTIONS.ACTION_USE;
    }

    /*
    * Return the action that corresponds moving in the desired orientation
    * Returns ACTION_NIL of the orientation provided is not a valid one
    * */
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

    /*
    * Returns the action to change the orientation to be able to perform actions in that direction
    * Basically is the same as moving but defined for mental sanity when coding
    * */
    private ACTIONS changeOrientation(Vector2d needed_orientation){
        return move(needed_orientation);
    }

    /**
     * SERIES OF ACTIONS
     * */

    /*
    * Agent is too fast and it needs to alternate actions with do-nothing actions
    * It is provided the number of ticks the agent should just do-nothing
    * */
    private List<ACTIONS> slowDown(int ticks){
        List<ACTIONS> slow_down_actions = new ArrayList<>();
        for (int i=0; i < ticks; i++){
            slow_down_actions.add(ACTIONS.ACTION_NIL);
        }

        return slow_down_actions;
    }

    /*
    * Shoots the avatar
    * Checks if the turret is pointing to the avatar first and, if not, changes agent orientation first
    * */
    private Queue<ACTIONS> shootAvatar(Vector2d agentpos, Vector2d avatarpos, Vector2d agentorientation){
        Queue<ACTIONS> actions_queue =  new LinkedList<>();

        // It is needed to shoot in the direction of the avatar if not what point on this?
        Vector2d needed_orientation = new Vector2d(avatarpos.x - agentpos.x, avatarpos.y - agentpos.y);
        needed_orientation.normalise();

        if (!agentorientation.equals(needed_orientation)){
            // If the agent is not facing the avatar, needs to change the orientation
            actions_queue.add(changeOrientation(needed_orientation));
        }

        // SHOOT!
        actions_queue.add(shoot());

        return actions_queue;
    }

    /*
    * Returns the actions enqueued to be able to reach the goal position
    * Runs A* algorithm to obtain optimal positions path
    * Transforms the calculated path into valid actions for the agent and returns it
    * It is needed to alternate the agent movement with do-nothing actions to slow it down
    * as it was too difficult to run away from it
    * */
    private Queue<ACTIONS> getActionsToReachPosition(Vector2d start, Vector2d goal, Vector2d start_orientation){
        Queue<ACTIONS> actions_queue = new LinkedList<>();

        // If goal is not in the agent space, the queue is returned empty
        if (!agent_nav_matrix[(int)goal.x/block_size][(int)goal.y/block_size]){
            return actions_queue;
        }

        List<Vector2d> path = aStarPath(start, goal);

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
            actions_queue.addAll(slowDown(9));
            current_pos = next_pos;
        }

        return actions_queue;
    }

    /**
     * PATHFINDING
     * AStar implementation & needed helper classes and functions
     * */

    /*
    * Fills the neighbours list with the reachable positions from the current one
    * It is checked TOP, RIGHT, DOWN and LEFT as it is not possible to move in diagonal
    * */
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

    /*
    * Object to be used in the PriorityQueue containing the vector2d position and the cost assigned to it
    * It has been implemented the Comparable interface (compareTo function) to be able to be used in the
    * priority Queue directly
    * */
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

    /*
    * Solution for being able to map two Keys to be able to use vector coordinates as index
    * Found in StackOverflow: http://stackoverflow.com/a/14678042 and adapted to my needs
    * */
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

    /*
    * Heuristic to be used for the aStar algorithm
    * It is used the distance to the goal
    * */
    private double aStarHeuristic(Vector2d position, Vector2d goal){
        // The distance to the goal is returned as the heuristic
        return position.dist(goal);
    }

    /*
    * A* algorithm
    * Returns a list of Vector2D which corresponds the spots that should be followed
    * */
    private List<Vector2d> aStarPath(Vector2d start, Vector2d goal){
        //System.out.println("Looking path from "+start+" to "+goal);

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

                // If there is a cake in that position, it must be ignored as the agent cant go through them
                if (!cake_positions.contains(next)) {
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
     * FOR DEBUG
     */
}
