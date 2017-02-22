package controllers.aimodule.Test;

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
    protected List<ArrayList<Vector2d>> areas = new ArrayList<ArrayList<Vector2d>>();

    protected boolean agent_nav_matrix[][];
    protected int grid_width;
    protected int grid_height;
    protected int block_size;

    /**
     * initialize all variables for the agent
     * @param stateObs Observation of the current state.
     * @param elapsedTimer Timer when the action returned is due.
     * @param playerID ID if this agent
     */
    public Agent(StateObservationMulti stateObs, ElapsedCpuTimer elapsedTimer, int playerID){
        id = playerID; //player ID of this agent
        opp_id = (playerID + 1) % 2; // player ID of the opponent. We know that there are only 2 players in the game
        block_size = stateObs.getBlockSize();

        // Gets floor and traps + floor
        Dimension grid_dimension = stateObs.getWorldDimension();
        grid_width = grid_dimension.width / block_size;
        grid_height = grid_dimension.height / block_size;

        ArrayList<Observation>[] fixedPositions = stateObs.getImmovablePositions();
        ArrayList<Observation> floor_elements = fixedPositions[FLOOR_ID];
        ArrayList<Observation> traps_elements = fixedPositions[TRAP_ID];

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


        Stack area_element_st = new Stack<Vector2d>();

        // Go over the grid to initialise different elements that will be useful for the algorithm
        // NOTE: It is assumed that every map is surrounded by walls so there is no chance of going 'out of bounds' and therefore no need to check if i-1, i+1, j-1 or j+1 is out of bounds
        for (int i = 0; i < grid_width; i++) {
            for (int j = 0; j < grid_height; j++){

                /* -------------------------------------------- AREAS CALCULATION ------------------------------------------------------------ */

                if (floor_matrix[i][j]) {
                    ArrayList<Vector2d> current_area_positions = new ArrayList<Vector2d>();
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
                        //System.out.print("("+current_area_position.x/block_size+","+current_area_position.y/block_size+")");
                        current_area_positions.add(current_area_position);


                    }
                    //System.out.println();
                    areas.add(current_area_positions);
                }

                /* ------------------------------------------------------------------------------------------------------------------------- */

            }
        }

        System.out.println(areas.get(0));
        System.out.println(areas.get(1));
        System.out.println(areas.get(2));
        System.out.println(areas.get(3));
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

        // JUST 1 CAKE ATM
        Vector2d cakepos = cake_pieces.get(0).position;

        Vector2d avatarpos = stateObs.getAvatarPosition(opp_id);
        Vector2d agentpos = stateObs.getAvatarPosition(id);

        System.out.println();

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
        System.out.println();

        ArrayList<ACTIONS> a = stateObs.getAvailableActions(id);
        return ACTIONS.ACTION_NIL;
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
