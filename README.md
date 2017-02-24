# *Portal 2D*

2D game based in the amazing _Valve Corporation_'s [__*Portal*__](http://www.valvesoftware.com/games/portal.html) & [__*Portal 2*__](http://www.valvesoftware.com/games/portal2.html) games, which I highly recommend. It has been developed for the **IGGI Game AI module** at *Goldsmiths University* on February 2017 using the [GVGAI Framework](https://github.com/EssexUniversityMCTS/gvgai). This is an Open Source framework used for the [General Video Game Competition](http://www.gvgai.net/).

-------

## Motivation

As part of the Game AI module it was needed to design, implement and playtest an AI-based game. There was no technichal constraints or limitations. The idea was making something that could be useful later for [my research](http://www.iggi.org.uk/students/2016/cristina-guerrero-romero/) and, as I need to use the GVGAI Framework to run some experiments, I opted to use it in order to get familiar with it. _Portal_ series are in the top of my favourite games list so I decided to make my game based on them.

-------

## Architecture

The [GVGAI Framework](https://github.com/EssexUniversityMCTS/gvgai) is a JAVA Open Source framework used for the [General Video Game Competition](http://www.gvgai.net/). For detailed information, please refer to [its wiki page](https://github.com/EssexUniversityMCTS/gvgai/wiki). An overview:

+ It is built in JAVA.
+ Supports arcade-style 2D games written using [Video Game Definition Language (VGDL)](https://github.com/EssexUniversityMCTS/gvgai/wiki/VGDL-Language).
+ Suppports single-player or 2-player games; controlled by a human or automatically played by a provided controller.
+ Supports creating [level generators](https://github.com/EssexUniversityMCTS/gvgai/wiki/Creating-Level-Generators).

### VGDL language

Allows creating new games easily just providing two files:

+ Game description file. Containing the definition, rules, interactions, etc that conforms the game.
+ Level description file. Defines the map of the game using a 2D matrix of symbols that will be mapped using the information provided in the game description file.

-------

## Game

The game is a very small version of Valve's _Portal_.

### Players & gameplay

It is a 2-player game with two different roles: the subject and the turret:

#### Human (subject)

Her goal is collecting the cake being able to move UP, DOWN, LEFT and RIGHT. It is possible to create two portals that would allow the player to move around the map avoiding the turret and some traps as she is teleported from one portal to another when entering one of them. The portals (blue and orange) to be shot are alternated every time one of them hits a wall.

#### Turret

Its goal is killing the human, being able to move UP, DOWN, LEFT and RIGHT and shooting a laser that kills the human when it hits her. The AI has been created to control this character and it is explained in detail below.

### Game elements

The game is formed by

+ Floor. Walkable tale.
+ Wall. Immovable object the avatars can't go through and provides the shape of the map. Portals are created on it when being hit.
+ Cake. Object that should be collected by the human player. It is an obstacle for the turret as it can't walk through it.
+ Portals (Blue & Orange). Created when the human shoots to the walls and allows her teleportation between the positions on the map where they are created.
+ Trap. Tale that looks like the floor (with small modification in order to be spotted by the player). Kills the human when she steps on it but does not affect the turret.

### Running the game

To run the game, it is needed to download the framework following the [instructions provided](https://github.com/EssexUniversityMCTS/gvgai/wiki/GVG-Framework). The files created for the game are:

+ _src/ModuleGame_ Main that runs the game.
+ _aimodule/cake.txt_ Game description file.
+ _aimodule/cake_lvl0.txt_ Level description file.
+ _src/controllers/aimodule/KillSubject/Agent.java_ Code of the controller for the turret.

-------

## AI behaviour

The AI has been developed as controller for the turrect player so it is focused on killing the human player before she collects the cake.

### Pathfinding & actions planning

It has been implemented the _A-Star_ as pathfinding algorithm to be used to find an efficient way to move around the map. It is provided a start position (the agent location) and a goal position (explained below) and returned the path to be followed. As heuristic it has been considered the distance between the position and the goal, being the closer the better.

Once the path has been calculated, it is transformed in a list of actions that will allow the agent to reach the goal.

### Logic

When the game is started, it is defined and calculated some information that would be helpful for the agent when deciding the actions to carry out next:

+ Agent navigation matrix. Defines the positions on the grid where the agent can walk and shoot freely. Note that, although the turret is not allowed to step on the cake, this is not taken into consideration in this case but will take into consideration in other parts of the algorithm.

+ Cake positions. List of Vector2D indicating where the cakes are located in the map.

+ Areas. It is calculated the different 'areas' that conforms the map. These areas are the ones where the human avatar can't go through because of the presence of traps. As it is explained below, storing this information was helpful to improve the logic of the decision algorithm.

+ Area where the human avatar was located the previous tick.

The AI decision logic goes as follows:

1. Checks if the player in sight of the agent. To achieve this checking, it is obtained the spots in the map from where the human is 'visible' (taken from the agent navigation matrix) and it is checked if the current position of the turret falls in one of those positions.
  * If TRUE -> Shoot the avatar. The plan of actions corresponding to target and shoot the avatar are listed to be executed by the agent in the following ticks.
  * If FALSE -> keep deciding how to proceed

2. If the list of actions is non-existance or empty (because there is no actions planned to reach a certain position) -> It is run the pathfinding algorithm and retrieved the plan of actions to reach an optimal position of the agent. This optimal position is obtained taking into consideration the line of vision of the human, taking the closest spot for the agent that belongs to this line of vision. If there is an already list of actions planned, it is executed the next action UNLESS the human has moved between areas, as there is no point on going to an area where the agent is not anymore.

3. The next action planned is carried out.

### Playtesting

Playtesting sessions have been very helpful to improve the game and the AI. 

#### 1st playtesting session

In this session, a first very simple version of the game had been developed with not AI at all. It was focused on the controllers and general feeling of the game. The feedback provided was:

- The size of the map looked ok, no need to make it smaller or bigger.
- It would be interesting to force the player to use the portals as it was possible to just walk to the cake
- The colour of the portal to be shot next was not clear enough causing confusion
- Weird movement (Pulse keyhandler provided by the framework)

Thanks to this feedback the following modification were done:

1. Traps added so the human player was not able to step on them and, thereforce, being forced to use portals to reach the cake.
2. Updated human sprite to make the colour to be shot clearer. It was added the coloured line in the bottom of the sprite.

It was tried to make modifications in the movement of the player but it was not possible as it's part of the framework and not using the pulse keyhandler broke the game and its logic completely.

#### 2nd playtesting session

The modifications in the game were introduced and approved. The AI logic had not been developed yet so it was not possible to test it further.

#### 3rd playtesting session

In this session the AI logic had been finished and it was helpful to tweak it in order to make the game 'less difficult' and more enjoyable. The feedback provided was:

- THE GAME IS TOO DIFFICULT because the AI goes too fast!
- The amount of time the portal were disabled was not enough which was confusing

The following changes were implemented:

1. Added slowDown actions to AI logic. Basically, a certain number of do-nothing actions were added until the gameplay was smoother so the agent moves and shoots slower.
2. Portal delay slowed down to provide more time to leave it.

These changes were implemented and playtested until they were satisfactory.
It was suggested to create easiest levels in order to get familiar with the controllers (movement and portal creation) before being chased by the turret.

Some bugs were fixed thanks to this playtesting as well:

+ Cakes positions were taken into consideration as the best spot to shoot the avatar but they shouldn't because the avatar cant not go through them or step on them
+ Cakes positions were taken into consideration in the pathfinding algorithm and they shouldn't as the agent is not allowed to walk through them. Therefore, the A-Star algorithm should be updated to ignore these positions.

-------

## Improvements

There are some improvements that would be great to make and I'm thinking to include in the future:

+ _A-Star heuristic improvement_: Currently, all moves are considered with cost 1 (apart from the heuristic based on the distance) but when the agent is facing one direction, if a certain movement implies a new orientation, it takes two movements (change direction + actual move). The algorithm should be updated to consider these cases with cost 2 and not 1.

+ _Game improvements_: Add more elements to increase complexity and make the gameplay more enjoyable: movable boxes (companion cubes!), doors and buttons to be used to create puzzles.

+ _More levels_: The development has focused in the creation of the game and the AI and the level used for the playtesting was design as prototype to show everything works as expected so it's very basic. The idea is creating more levels including different puzzles and new elements.

-------

> PS: _The cake is a lie_


