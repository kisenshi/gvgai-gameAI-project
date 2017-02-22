package controllers.aimodule.Test;

import core.game.Observation;
import core.game.StateObservationMulti;
import core.player.AbstractMultiPlayer;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Agent extends AbstractMultiPlayer {

    int id; //this player's ID
    int opp_id; //opponent player's ID
    int floor_id = 1;

    protected ArrayList<Observation> grid[][];
    protected List<ArrayList<Vector2d>> areas = new ArrayList<ArrayList<Vector2d>>();
    protected int block_size;


    private Vector2d getBlockCoordinate(Vector2d position){
        return new Vector2d(position.x, position.y);
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
        block_size = stateObs.getBlockSize();

        //  Positions belonging to different areas are created
        int id_area = 0;

        // Goes through every floor
        ArrayList<Observation>[] fixedPositions = stateObs.getImmovablePositions();
        int block_size = stateObs.getBlockSize();


        ArrayList<Observation> floor_elements = fixedPositions[floor_id];
        Dimension grid_dimension = stateObs.getWorldDimension();

        boolean floor_matrix[][] = new boolean[grid_dimension.width / block_size][grid_dimension.height / block_size];
        for (int k = 0; k < floor_elements.size(); k++) {
            Vector2d floor_position = floor_elements.get(k).position;
            floor_matrix[(int)floor_position.x / block_size][(int)floor_position.y / block_size] = true;
        }

        //TEST
        /*for (int i = 0; i < grid_dimension.width / block_size; i++){
            for (int j = 0; j < grid_dimension.height / block_size; j++){
                System.out.print(floor_matrix[i][j] + " ");
            }
            System.out.println();
        }*/

        /* Areas calculation */
        Stack area_element_st = new Stack<Vector2d>();
        for (int i = 0; i < grid_dimension.width / block_size; i++) {
            for (int j = 0; j < grid_dimension.height / block_size; j++){
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
