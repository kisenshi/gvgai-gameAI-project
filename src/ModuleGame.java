/**
 * Created by Cris
 */

import java.util.Random;
import core.ArcadeMachine;

public class ModuleGame {

    public static void main(String[] args) {

        // Controllers
        String humanController = "controllers.multiPlayer.human.Agent";
        String testController = "controllers.aimodule.Test.Agent";

        // Tests
        String randomController = "controllers.multiPlayer.sampleRandom.Agent";
        String sampleGAController = "controllers.multiPlayer.sampleGA.Agent";

        String controllers = humanController + " " + testController;

        // Available games:
        String gamePath = "aimodule/";
        String gameName = "cake";

        // Other settings
        boolean visuals = true;
        int seed = new Random().nextInt();

        // Game and level to play
        String game = gamePath + gameName + ".txt";
        String level1 = gamePath + gameName + "_lvl0.txt";

        String recordActionsFile = null;// "actions_" + games[gameIdx] + "_lvl"
        // + levelIdx + "_" + seed + ".txt";
        // //where to record the actions
        // executed. null if not to save.

        // 1. This starts a game, in a level, played by two humans.
        //ArcadeMachine.playOneGameMulti(game, level1, recordActionsFile, seed);

        // 2. This plays a game in a level by the controllers. If one of the
        // players is human, change the playerID passed
        // to the runOneGame method to be that of the human player (0 or 1).
        ArcadeMachine.runOneGame(game, level1, visuals, controllers, recordActionsFile, seed, 0);
    }
}
