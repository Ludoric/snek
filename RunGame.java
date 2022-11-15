import java.util.*;

import java.awt.*;
import javax.imageio.*;
import java.awt.image.*;
import java.io.*;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Graphics;




enum State {
    INITIALISE,
    RUN,
    AI_RUN,
    PAUSE,
    GAMEOVER,
    YOUWON,
    RESET
}


enum TileType{
    S_RIGHT(0,  1, 0),
    S_UP   (1,  0,-1),
    S_LEFT (2, -1, 0),
    S_DOWN (3,  0, 1),

    FOOD   (4,  0, 0); //special case

    public final int val;
    public final int x;
    public final int y;

    private static final TileType [] directionList = {S_RIGHT, S_UP, S_LEFT, S_DOWN};
    TileType(int v, int x, int y){
        this.val = v;
        this.x = x;
        this.y = y;
    }

    public TileType left(){
        return directionList[(this.val-1+4)%4];
    }
    public TileType right(){
        return directionList[(this.val+1)%4];
    }
    public TileType front(){
        return this;
    }
    public TileType back(){
        return directionList[(this.val+2)%4];
    }
}


class Tile{
  public TileType t = null;
  public int x, y;
  public int g, h;

  Tile(TileType t, int x, int y){
    this.t = t;
    this.x = x;
    this.y = y;
  }

  Tile(TileType t, int x, int y, int gcount, int goalx, int goaly){
    this.t = t;
    this.x = x;
    this.y = y;

    this.g = gcount;
    this.h = Math.abs(x-goalx) + Math.abs(y-goaly);
  }

  public String toString(){
    return(t.toString() + "\t @ (" + x + ", " + y + ") g=" + g + " h=" + h);
  }

}


public class RunGame extends JPanel implements KeyListener {
    public static final int ARRAY_W = 16;
    public static final int ARRAY_H = 12;
    public static final int TILE_SIZE = 32;

    public static final int WIDTH = TILE_SIZE*ARRAY_W;
    public static final int HEIGHT = TILE_SIZE*ARRAY_H;

    public static final int FRAMES_PER_SECOND = 60;
    public static final int SKIP_TICKS = 1000 / FRAMES_PER_SECOND;
    public static final int FRAMES_PER_STEP = 8;//8
    private int sleep_time;


    private Image H_D;
    private Image H_L;
    private Image H_U;
    private Image H_R;
    private Image T_D;
    private Image T_L;
    private Image T_U;
    private Image T_R;
    private Image B_D;
    private Image B_L;
    private Image B_U;
    private Image B_R;
    private Image B_RU;
    private Image B_RD;
    private Image B_UR;
    private Image B_UL;
    private Image B_DR;
    private Image B_DL;
    private Image B_LU;
    private Image B_LD;


    private static final Color GREYOUT_COLOUR = new Color(0, 0, 0, 90);
    private static final Color FOOD_COLOUR = new Color(255, 0, 0);
    private static final Color BACK_COLOUR = new Color(207, 188, 143);
    private static final Color SNAKE_COLOUR = new Color(0, 255, 0);
    private static final Color TEXT_COLOUR = new Color(255, 255, 255);
    private static final int NUM_COLOURS = 180;
    private int colourIndex = 0;
    private static final Color[] col = new Color[NUM_COLOURS];
    private Font SmallTextFont;
    private Font LargeTextFont;

    private boolean[] keyDown = new boolean[65536];
    private boolean[] keyPressed = new boolean[65536];

    private State state = State.INITIALISE;
    private long framecount;

    private TileType[][] board;
    private Tile oldSegment;
    private ArrayDeque<Tile> body;//head at end
    private TileType direction, oldDirection;
    private int posx, posy;
    private int foodx, foody;


    //AI data
    private ArrayDeque<Tile> moves = new ArrayDeque<Tile>();
    private boolean hasSafePath;

    private RunGame(){
        JFrame f = new JFrame("Snek AI");
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setBackground(BACK_COLOUR);
        f.setBackground(BACK_COLOUR);
        f.getContentPane().add(this);
        //f.setSize(WIDTH+30, HEIGHT+30);

        f.pack();
        f.setVisible(true);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setResizable(false);
        f.addKeyListener(this);


        SmallTextFont = f.getGraphics().getFont();
        LargeTextFont = SmallTextFont.deriveFont(SmallTextFont.getSize() * 3.0f);

        int num = 180;
        for (int i=0; i<num;i++){
            col[i] = Color.getHSBColor((float)(1.0*i/num),1,0.9f);
        }

        try{
            H_D = ImageIO.read(RunGame.class.getResourceAsStream("sprites/head_d.png"));
            H_L = ImageIO.read(RunGame.class.getResourceAsStream("sprites/head_l.png"));
            H_U = ImageIO.read(RunGame.class.getResourceAsStream("sprites/head_u.png"));
            H_R = ImageIO.read(RunGame.class.getResourceAsStream("sprites/head_r.png"));

            T_D = ImageIO.read(RunGame.class.getResourceAsStream("sprites/tail_d.png"));
            T_L = ImageIO.read(RunGame.class.getResourceAsStream("sprites/tail_l.png"));
            T_U = ImageIO.read(RunGame.class.getResourceAsStream("sprites/tail_u.png"));
            T_R = ImageIO.read(RunGame.class.getResourceAsStream("sprites/tail_r.png"));

            B_D = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_d.png"));
            B_L = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_l.png"));
            B_U = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_u.png"));
            B_R = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_r.png"));

            B_DL = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_dl.png"));
            B_DR = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_dr.png"));
            B_LD = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_ld.png"));
            B_LU = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_lu.png"));
            B_RD = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_rd.png"));
            B_RU = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_ru.png"));
            B_UL = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_ul.png"));
            B_UR = ImageIO.read(RunGame.class.getResourceAsStream("sprites/body_ur.png"));


        } catch (IOException e) {
            System.err.println("Image Loading Failed");
        }
    }

    private void reset(){
        // RESET!!!!
        board = new TileType[ARRAY_W][ARRAY_H];
        body = new ArrayDeque<Tile>();
        direction = TileType.S_LEFT;
        oldDirection = direction;
        posx = ARRAY_W*3/4;
        posy = ARRAY_H/2;

        body.offer(new Tile(direction, posx, posy));
        body.offer(new Tile(direction, posx, posy));
        board[posx-1][posy] = TileType.FOOD;
        //body.offer(new Tile(TileType.FOOD, posx-2, posy));
        update();
        update();
        update();//a stupid way to initialise the board

        //board[ARRAY_W*3/4][ARRAY_H/2] = direction;
        this.repaint();
        state = State.RUN;
    }

    private void update(){

        if(state == State.AI_RUN ){
            if(!hasSafePath){// moves.isEmpty() || framecount%(FRAMES_PER_STEP*5)==0){
                //generateAIMoves(new Tile(TileType.FOOD, foodx, foody));
                board[foodx][foody] = null;
                generateSafeAIMoves(new Tile(TileType.FOOD, foodx, foody), oldSegment);
                board[foodx][foody] = TileType.FOOD;
            }
            // if(moves.isEmpty()){
            //   generateAIMoves(oldSegment);
            // }
            if(!moves.isEmpty()){// auto move the snake
                direction = moves.poll().t;
            }else{
                System.err.println("ALL PATHING FAILED");
            }
        }


        if(oldDirection == TileType.S_DOWN && direction == TileType.S_UP
        || oldDirection == TileType.S_UP && direction == TileType.S_DOWN
        || oldDirection == TileType.S_LEFT && direction == TileType.S_RIGHT
        || oldDirection == TileType.S_RIGHT && direction == TileType.S_LEFT
        ){direction = oldDirection;}

        switch(direction){
            case S_UP:
            posy--;
            if(posy < 0){state = State.GAMEOVER; return;}
            break;

            case S_DOWN:
            posy++;
            if(posy >= ARRAY_H){state = State.GAMEOVER; return;}
            break;

            case S_LEFT:
            posx--;
            if(posx < 0){state = State.GAMEOVER; return;}
            break;

            case S_RIGHT:
            posx++;
            if(posx >= ARRAY_W){state = State.GAMEOVER; return;}
            break;
        }
        //System.out.println(posx+", "+ posy);
        boolean hasJustEaten = false;
        if(board[posx][posy] == TileType.FOOD){
            //add new food tile
            int x, y;
            do{
                x = (int)(Math.random()*ARRAY_W);
                y = (int)(Math.random()*ARRAY_H);
            }while(board[x][y] != null);
            board[x][y] = TileType.FOOD;
            foodx = x;
            foody = y;
            hasJustEaten = true;
            hasSafePath = false;
        }else{
            //remove the last tile in the snake from both places
            board[body.peek().x][body.peek().y] = null;
            oldSegment = body.pop();
            if(board[posx][posy] != null){
                state = State.GAMEOVER;
                return;
            }
        }
        board[posx][posy] = direction;
        body.offer(new Tile(direction, posx, posy));
        oldDirection = direction;


        // //check if its appropriate to generate AI
        if(hasJustEaten && state == State.AI_RUN){
            moves.clear();
        }
    }

    /*
    *  will return the path from the start to the second endpoint if it can find it, otherwise it will return null
    */
    public ArrayDeque<Tile> generateLongAIMoves(Tile endpoint1, Tile endpoint2){
        Tile pPos = new Tile(direction, posx, posy);
        ArrayDeque<Tile> newMoves = generateAIMoves(pPos, endpoint1, board);
        if(newMoves.isEmpty()){
            return null; //  there is no path to endpoint1
        }
        // else: (there IS a path to the food)
        //System.out.println("Attempting Food Pathing");
        //generate the board at the time the snake reaches the food
        TileType[][] boardTemp = new TileType[ARRAY_W][];
        for(int i=0; i<ARRAY_W; i++){
            boardTemp[i] = new TileType[ARRAY_H];
        } // create a new board
        int i = 0;
        for(Tile t:newMoves){
            boardTemp[t.x][t.y] = t.t;
            i++;
        }
        for(Tile t:body){
            boardTemp[t.x][t.y] = t.t;
            // i--;
            // if(i<0){
            //   break;
            // }
        } // populate the board bits of snake, but only up to where they would be


        ArrayDeque<Tile> movesOld = newMoves;
        newMoves = generateAIMoves(endpoint1, endpoint2, boardTemp);
        if(newMoves.isEmpty()){
            return null; // there is no SAFE path to the food
        }else{
            movesOld.addAll(newMoves);
            return movesOld;
        }
    }



    public void generateSafeAIMoves(Tile endpoint1, Tile endpoint2){
        hasSafePath=false;

        ArrayDeque<Tile> temp = null;
        // if(body.size() < (ARRAY_W*ARRAY_H-1)-50){// if there are only a few tiles left
            // don't go straight for the food; you might get stuck
            temp = generateLongAIMoves(endpoint1, endpoint2);
        // }

        if(temp == null){
            //pathing failed, try something else
            ArrayDeque<Tile> suggestedEndpoints = new ArrayDeque<Tile>();
            // int px = posx;//+direction.x;
            // int py = posy;//+direction.y;
            //
            // TileType next = direction.left();
            // suggestedEndpoints.add(new Tile(TileType.FOOD, px+next.x, py+next.y));
            // next = direction;
            // suggestedEndpoints.add(new Tile(TileType.FOOD, px+next.x, py+next.y));
            // next = direction.right();
            // suggestedEndpoints.add(new Tile(TileType.FOOD, px+next.x, py+next.y));
            // System.out.printf("(%d, %d)\r\n", next.x, next.y);

            suggestedEndpoints.add(new Tile(TileType.FOOD, posx, posy+1));
            suggestedEndpoints.add(new Tile(TileType.FOOD, posx, posy-1));
            suggestedEndpoints.add(new Tile(TileType.FOOD, posx+1, posy));
            suggestedEndpoints.add(new Tile(TileType.FOOD, posx-1, posy));



            while(suggestedEndpoints.size()<0){
                int x = (int)(Math.random()*ARRAY_W);
                int y = (int)(Math.random()*ARRAY_H);
                suggestedEndpoints.add(new Tile(TileType.FOOD, x, y));// fake food
            }

            while(temp==null && suggestedEndpoints.size()>0){// attempt to find a path
                Tile newPoint = suggestedEndpoints.poll();
                if(newPoint.x<0 || newPoint.x>=ARRAY_W ||
                newPoint.y<0 || newPoint.y>=ARRAY_H ||
                board[newPoint.x][newPoint.y] != null){
                    continue;
                }
                temp = generateLongAIMoves(newPoint, endpoint2); // try the random tile
            }
            if(temp == null){// if there is still no path
                // System.out.println("Here");//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                temp = generateAIMoves(new Tile(direction, posx, posy), endpoint2, board); //  head straight to the tail
                hasSafePath=false;
            }
        }else{
            hasSafePath=true;
        }
        moves = temp;
    }


    public ArrayDeque<Tile> generateAIMoves(Tile start, Tile endpoint, TileType[][] knownBoard){
        ///// CHECK FOR PATH TO TAIL AFTER EATING FOOD -  and default to it after normal pathfinding fails


        //uses the ArrayDeque<TileType> body, the Tile[][] board, posx,posy, foodx,foody

        //do A* pathfinding
        Queue<Tile> openList = new PriorityQueue<Tile>((Tile a, Tile b) -> {return(a.h+a.g-b.h-b.g);});//openList keeps track of the tiles waiting for pathfinding

        Tile[][] paths = new Tile[ARRAY_W][];
        for(int i=0; i<ARRAY_W; i++){
            paths[i] = new Tile[ARRAY_H];
        }

        openList.add(new Tile(direction, start.x, start.y));
        Tile pathFound = null;
        while(!openList.isEmpty()){
            //1 find the path with the lowest score
            // openList.poll()
            //2 remove from open and add to closed list(paths)
            Tile currentPath = openList.poll();
            paths[currentPath.x][currentPath.y]=currentPath;

            if(currentPath.x == endpoint.x && currentPath.y == endpoint.y){//check if we are on the endsquare
                pathFound=currentPath;
                break;
            }

            //3 for each square around it:

            addToList(knownBoard, TileType.S_UP,   paths,openList,endpoint,currentPath);
            addToList(knownBoard, TileType.S_DOWN, paths,openList,endpoint,currentPath);
            addToList(knownBoard, TileType.S_LEFT, paths,openList,endpoint,currentPath);
            addToList(knownBoard, TileType.S_RIGHT,paths,openList,endpoint,currentPath);
            // System.out.println();
            // for(Tile t : openList){
            //   System.out.println(t);
            // }

        }
        //System.out.println(pathFound + "    " + endpoint.x + "," + endpoint.y);

        ArrayDeque<Tile> moveSet = new ArrayDeque<Tile>();
        if(pathFound!=null){//test if pathfinding was successful
            //moves = new ArrayDeque<Tile>();
            moveSet.push(pathFound);


            while(!(moveSet.peek().x == start.x && moveSet.peek().y == start.y)){//fill the route object up with the path objects to get to the final location
                int x = moveSet.peek().x;
                int y = moveSet.peek().y;

                switch(moveSet.peek().t){
                    case S_UP:
                    y++;  break;
                    case S_DOWN:
                    y--;  break;
                    case S_LEFT:
                    x++;  break;
                    case S_RIGHT:
                    x--;  break;
                }

                moveSet.push(paths[x][y]);
            }
            moveSet.pop();//as the last tile on the stack is invalid (just marks the startpoint)

        }else{
            // System.err.println("PATHFINDING FAILED");
            //There is a fatal error!!!!!
        }
        //now to actually move the player:
        // (happens elsewhere)
        return moveSet;
    }

    private void addToList(TileType[][] knownBoard, TileType dir, Tile[][] paths, Queue<Tile> openList, Tile endpoint, Tile current){
        int x = current.x;
        int y = current.y;

        switch(dir){
            case S_UP:
            y--;  break;
            case S_DOWN:
            y++;  break;
            case S_LEFT:
            x--;  break;
            case S_RIGHT:
            x++;  break;
        }

        Tile n = new Tile(dir, x, y, current.g+1, endpoint.x, endpoint.y);

        if(n.x<0 || n.x>=ARRAY_W || n.y<0 || n.y>=ARRAY_H || !(knownBoard[n.x][n.y]==null || knownBoard[n.x][n.y] == TileType.FOOD) || paths[n.x][n.y]!=null){
            return;
        }// check if tile is valid

        Tile pathInList = null;
        for(Tile p :openList){
            if(p.x == n.x && p.y == n.y){
                pathInList = p;
                break;
            }
        }
        if(pathInList==null){
            openList.offer(n);
        }else if(pathInList.g>n.g){//if this cell is in the list then update the score
            pathInList.g = n.g;
        }

    }






    public void drawBody(Graphics g, Tile t1, TileType dir2){

        switch(t1.t){
            case S_UP:
            switch(dir2){
                case S_UP:
                g.drawImage(B_U, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
                case S_LEFT:
                g.drawImage(B_UL, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
                case S_RIGHT:
                g.drawImage(B_UR, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
            }
            break;
            case S_DOWN:
            switch(dir2){
                case S_DOWN:
                g.drawImage(B_D, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
                case S_LEFT:
                g.drawImage(B_DL, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
                case S_RIGHT:
                g.drawImage(B_DR, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
            }
            break;
            case S_LEFT:
            switch(dir2){
                case S_DOWN:
                g.drawImage(B_LD, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
                case S_LEFT:
                g.drawImage(B_L, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
                case S_UP:
                g.drawImage(B_LU, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
            }
            break;
            case S_RIGHT:
            switch(dir2){
                case S_DOWN:
                g.drawImage(B_RD, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
                case S_UP:
                g.drawImage(B_RU, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
                case S_RIGHT:
                g.drawImage(B_R, t1.x*TILE_SIZE, t1.y*TILE_SIZE, null);
                break;
            }
            break;
        }

    }




    public void paint(Graphics g){
        g.setColor(BACK_COLOUR);
        g.fillRect(0,0,WIDTH,HEIGHT);


        if(body==null){
            return;
        }
        // g.setColor(BACK_COLOUR);
        // g.fillRect(oldSegment.x*TILE_SIZE, oldSegment.y*TILE_SIZE,  TILE_SIZE, TILE_SIZE);
        // g.setColor(SNAKE_COLOUR);
        // g.fillRect(body.peekLast().x*TILE_SIZE+1, body.peekLast().y*TILE_SIZE+1,  TILE_SIZE-2, TILE_SIZE-2);
        g.setColor(FOOD_COLOUR);
        g.fillRect((foodx*TILE_SIZE)+1, (foody*TILE_SIZE)+1,  TILE_SIZE-2, TILE_SIZE-2);


        Iterator<Tile> it = body.iterator();
        Tile previous = it.next();
        Tile current = it.next();
        Image im = T_D;
        switch(current.t){
            case S_UP:
            im = T_U;  break;
            case S_DOWN:
            im = T_D;  break;
            case S_LEFT:
            im = T_L;  break;
            case S_RIGHT:
            im = T_R;  break;
        }
        g.drawImage(im, previous.x*TILE_SIZE, previous.y*TILE_SIZE, null);

        while(it.hasNext()){

            previous = current;
            current = it.next();
            drawBody(g, previous, current.t);
        }
        switch(current.t){
            case S_UP:
            im = H_U;  break;
            case S_DOWN:
            im = H_D;  break;
            case S_LEFT:
            im = H_L;  break;
            case S_RIGHT:
            im = H_R;  break;
        }
        g.drawImage(im, current.x*TILE_SIZE, current.y*TILE_SIZE, null);


        // g.setColor(SNAKE_COLOUR);
        // for(int i=0; i<ARRAY_W; i++){
        //   for(int j=0; j<ARRAY_H; j++){
        //     if(board[i][j] !=null){
        //       if(board[i][j] == TileType.FOOD){
        //         g.setColor(FOOD_COLOUR);
        //         g.fillRect((i*TILE_SIZE)+1, (j*TILE_SIZE)+1,  TILE_SIZE-2, TILE_SIZE-2);
        //         g.setColor(SNAKE_COLOUR);
        //       }else{
        //         g.fillRect((i*TILE_SIZE)+1, (j*TILE_SIZE)+1,  TILE_SIZE-2, TILE_SIZE-2);
        //       }
        //     }
        //   }
        // }

        switch(state){
            case RESET:
            g.setColor(BACK_COLOUR);
            g.fillRect(0,0,WIDTH,HEIGHT);
            break;
            case AI_RUN:
            //no break;
            case RUN:
            //moved outside for pause menu

            // for debugging purposes
            // g.setColor(Color.BLUE);
            // for(int i=0; i<ARRAY_W; i++){
            //   for(int j=0; j<ARRAY_H; j++){
            //     if(board[i][j] !=null){
            //       g.fillRect((i*TILE_SIZE)+8, (j*TILE_SIZE)+8,  TILE_SIZE-16, TILE_SIZE-16);
            //     }
            //   }
            // }

            break;
            case PAUSE:
            g.setColor(GREYOUT_COLOUR);
            g.fillRect(0,0,WIDTH,HEIGHT);
            g.setColor(TEXT_COLOUR);
            g.drawString("Press R to restart",100,100);
            g.drawString("Press P to unpause",100,150);
            g.drawString("Press A to autoplay",100,200);
            break;
            case YOUWON:
            g.setColor(col[colourIndex]);
            g.fillRect(0,0,WIDTH,HEIGHT);
            colourIndex++;
            colourIndex= colourIndex<NUM_COLOURS ? colourIndex: 0;
            g.setColor(TEXT_COLOUR);
            g.setFont(LargeTextFont);
            g.drawString("YOU WON",WIDTH/2-105, HEIGHT/2-25);
            g.setFont(SmallTextFont);
            g.drawString("Press R to restart", 100, 100);
            break;
            case GAMEOVER:

            g.setColor(GREYOUT_COLOUR);
            g.fillRect(0,0,WIDTH,HEIGHT);
            g.setColor(TEXT_COLOUR);
            g.setFont(LargeTextFont);
            g.drawString("GAME OVER",WIDTH/2-105,WIDTH/2-25);
            g.setFont(SmallTextFont);
            g.drawString("Press R to restart",100,100);
            g.drawString("Press A to autoplay",100,150);
            break;
        }
    }

    public void keyTyped(KeyEvent e){
    } //Unused

    public void keyPressed(KeyEvent e){
        keyPressed[e.getKeyCode()] = true;
        keyDown[e.getKeyCode()] = true;
    }
    public void keyReleased(KeyEvent e){
        keyDown[e.getKeyCode()] = false;
    }

    public void loop(){
        long next_game_tick = System.currentTimeMillis();

        while(true){
            switch(state){


                case RUN:
                if(keyDown[KeyEvent.VK_UP]){
                    direction = TileType.S_UP;
                }else
                if(keyDown[KeyEvent.VK_DOWN]){
                    direction = TileType.S_DOWN;
                }else
                if(keyDown[KeyEvent.VK_LEFT]){
                    direction = TileType.S_LEFT;
                }else
                if(keyDown[KeyEvent.VK_RIGHT]){
                    direction = TileType.S_RIGHT;

                }

                //no break;
                case AI_RUN:

                if(state==State.AI_RUN || framecount%FRAMES_PER_STEP == 0){
                    this.update();
                }
                // if(player is dead){
                //   this.state = State.GAMEOVER;
                // }

                if(body.size() >= ARRAY_W*ARRAY_H-1){
                    state = State.YOUWON;
                }

                if(keyPressed[KeyEvent.VK_P]){
                    keyPressed[KeyEvent.VK_P] = false;
                    state = State.PAUSE;
                }
                keyPressed[KeyEvent.VK_R] = false;
                keyPressed[KeyEvent.VK_A] = false;
                break;

                case PAUSE:
                if(keyPressed[KeyEvent.VK_P]){
                    keyPressed[KeyEvent.VK_P] = false;
                    state = State.RUN;
                }else if(keyPressed[KeyEvent.VK_R]){
                    keyPressed[KeyEvent.VK_R] = false;
                    state = State.RESET;
                }
                if(keyPressed[KeyEvent.VK_A]){
                    keyPressed[KeyEvent.VK_A] = false;
                    state = State.AI_RUN;
                }
                break;

                case YOUWON:
                if(keyPressed[KeyEvent.VK_R]){
                    keyPressed[KeyEvent.VK_R] = false;
                    keyPressed[KeyEvent.VK_P] = false;
                    state = State.RESET;
                }
                break;

                case GAMEOVER:
                if(keyPressed[KeyEvent.VK_R]){
                    keyPressed[KeyEvent.VK_R] = false;
                    keyPressed[KeyEvent.VK_P] = false;
                    state = State.RESET;
                }
                if(keyPressed[KeyEvent.VK_A]){
                    keyPressed[KeyEvent.VK_A] = false;
                    reset();
                    state = State.AI_RUN;
                }
                break;

                case INITIALISE:
                state = State.RESET;
                //break;

                case RESET:
                this.reset();
                break;
            }
            this.repaint();


            next_game_tick += SKIP_TICKS;
            sleep_time = (int)(next_game_tick - System.currentTimeMillis());
            if( sleep_time>=0 ) {// this is a terrible way to make the game loop have a set length
                try{
                    Thread.sleep(sleep_time);
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();
                }
            }//else we are running behind
            framecount++;

        }
    }

    public static void main(String[] args) {
        RunGame run = new RunGame();
        run.loop();
    }
}
