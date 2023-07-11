package xyz.openatbp.extension.game.actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.entities.Room;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.ActorType;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NewMinion extends Actor{

    private Actor target;
    private final double[] blueBotX = {36.90,26.00,21.69,16.70,3.44,-9.56,-21.20,-28.02,-33.11,-36.85}; //Path points from blue base to purple base
    private final double[] blueBotY = {2.31,8.64,12.24,17.25,17.81,18.76,14.78,7.19,5.46,2.33};
    private final double[] blueTopX = {36.68, 30.10, 21.46, 18.20, -5.26, -12.05, -24.69, -28.99, -35.67};
    private final double[] blueTopY = {-2.56, -7.81, -12.09, -16.31, -17.11, -17.96, -13.19, -7.50, -2.70};
    public enum MinionType{RANGED, MELEE, SUPER} //Type of minion
    private MinionType type;
    private boolean dead = false;
    private Map<Actor, Integer> aggressors;
    private int lane;
    private float travelTime;
    private int pathIndex = 0;
    private Line2D movementLine;

    public NewMinion(ATBPExtension parentExt, Room room, int team, int minionNum, int wave, int lane){
        this.avatar = "creep"+team;
        String typeString = "super";
        if(minionNum <= 2){
            typeString = "melee"+minionNum;
            this.type = MinionType.MELEE;
            this.maxHealth = 450;
        }else if(minionNum <= 4){
            typeString = "ranged"+minionNum;
            this.avatar+="_ranged";
            this.type = MinionType.RANGED;
            this.maxHealth = 350;
        }
        else{
            this.type = MinionType.SUPER;
            this.avatar+="_super";
            this.maxHealth = 500;
        }
        this.displayName = parentExt.getDisplayName(this.getAvatar());
        this.currentHealth = this.maxHealth;
        float x = (float) blueBotX[0]; //Bot Lane
        float y = (float) blueBotY[0];
        if(team == 0) x = (float) blueBotX[blueBotX.length-1];
        if(lane == 0){ //Top Lane
            x = (float) blueTopX[0];
            y = (float) blueTopY[0];
            if(team == 0){
                x = (float) blueTopX[blueTopX.length-1];
                y = (float) blueTopY[blueTopY.length-1];
            }
        }
        this.speed = 1.75f;
        this.location = new Point2D.Float(x,y);
        this.id = team+"creep_"+lane+typeString+wave;
        this.room = room;
        this.team = team;
        this.parentExt = parentExt;
        this.lane = lane;
        this.actorType = ActorType.MINION;
        if(team == 0){
            if(lane == 0) pathIndex = blueTopX.length-1;
            else pathIndex = blueBotX.length-1;
        }
        System.out.println(id + " spawning at " + x + "," + y);
        this.movementLine = new Line2D.Float(this.location,this.location);
        aggressors = new HashMap<>(3);
        this.stats = this.initializeStats();
        ExtensionCommands.createActor(parentExt,room,this.id,this.getAvatar(),this.location,0f,this.team);
        this.attackCooldown = this.getPlayerStat("attackSpeed");
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {

    }

    @Override
    public void attack(Actor a) {

    }

    @Override
    public void die(Actor a) {

    }

    @Override
    public void update(int msRan) {
        this.handleDamageQueue();
        if(this.dead) return;
        this.location = this.getRelativePoint();
        if(this.attackCooldown > 0) this.attackCooldown-=100;
        if(this.target == null){
            Actor potentialTarget = this.searchForTarget();
            if(potentialTarget != null){
                this.setTarget(potentialTarget);
            }else{
                if(this.hasArrived()){
                    this.moveAlongPath();
                }else{
                    this.travelTime+=0.1f;
                }
            }
        }else{
            if(this.withinAggroRange(this.target.getLocation())){
                if(this.withinRange(this.target)){
                    if(!this.isStopped()) this.stopMoving();
                    if(this.canAttack()) this.attack(this.target);
                }else{
                    if(this.isStopped()) this.moveTowardsTarget();
                    this.travelTime+=0.1f;
                }
            }else{
                this.resetTarget();
            }
        }
    }

    @Override
    public void stopMoving(){
        super.stopMoving();
        this.movementLine = new Line2D.Float(this.location,this.location);
        this.travelTime = 0f;
    }

    private void moveTowardsTarget(){
        this.travelTime = 0f;
        this.movementLine = new Line2D.Float(this.location,this.target.getLocation());
        ExtensionCommands.moveActor(parentExt,room,id, movementLine.getP1(), movementLine.getP2(), (float) speed, true);
    }

    private boolean isStopped(){
        return this.movementLine.getX1() == this.movementLine.getX2() && this.movementLine.getY1() == this.movementLine.getY2();
    }

    @Override
    public void setTarget(Actor a) {
        this.target = a;
        this.movementLine = new Line2D.Float(this.location,a.getLocation());
        this.travelTime = 0.1f;
        ExtensionCommands.moveActor(parentExt,room,id, movementLine.getP1(), movementLine.getP2(), (float) this.speed, true);
    }

    @Override
    public String getAvatar(){
        return this.avatar.replace("0","");
    }

    private boolean hasArrived(){
        return this.movementLine == null || this.location.distance(this.movementLine.getP2()) <= 0.01f;
    }

    private Actor searchForTarget(){
        Actor closestActor = null;
        double distance = 1000f;
        for(Actor a : this.parentExt.getRoomHandler(this.room.getId()).getActors()){
            if(a.getTeam() != this.team && this.withinAggroRange(a.getLocation()) && this.facingEntity(a.getLocation())){
                if(a.getLocation().distance(this.location) < distance){
                    distance = a.getLocation().distance(this.location);
                    closestActor = a;
                }
            }
        }
        return closestActor;
    }

    private boolean facingEntity(Point2D p){ // Returns true if the point is in the same direction as the minion is heading
        //TODO: Some minions don't attack others attacking the base when they spawn
        double deltaX = movementLine.getX2()-location.getX();
        //Negative = left Positive = right
        if(Double.isNaN(deltaX)) return false;
        if(deltaX>0 && p.getX()>this.location.getX()) return true;
        else return deltaX < 0 && p.getX() < this.location.getX();
    }

    private boolean withinAggroRange(Point2D p){
        return p.distance(this.location) <= 5;
    }

    private void resetTarget(){
        this.target = null;
        this.pathIndex = this.findPathIndex();
        this.travelTime = 0f;
        this.movementLine = new Line2D.Double(this.location,this.getPathPoint());
        ExtensionCommands.moveActor(parentExt,room,id,this.movementLine.getP1(),this.movementLine.getP2(),(float)this.speed,true);
    }

    private void moveAlongPath(){
        this.travelTime = 0.1f;
        if(this.team == 1) this.pathIndex++;
        else this.pathIndex--;
        if(this.pathIndex < 0) this.pathIndex = 0;
        else{
            if(this.lane == 0 && this.pathIndex == blueTopX.length) this.pathIndex--;
            else if(this.lane == 1 && this.pathIndex == blueBotX.length) this.pathIndex--;
        }
        this.movementLine = new Line2D.Double(this.location,this.getPathPoint());
        ExtensionCommands.moveActor(parentExt,room,id,this.movementLine.getP1(),this.movementLine.getP2(), (float) this.speed,true);
    }

    private Point2D getRelativePoint(){ //Gets player's current location based on time
        Point2D rPoint = new Point2D.Float();
        if(this.movementLine == null) return this.location;
        float x2 = (float) this.movementLine.getX2();
        float y2 = (float) this.movementLine.getY2();
        float x1 = (float) movementLine.getX1();
        float y1 = (float) movementLine.getY1();
        double dist = movementLine.getP1().distance(movementLine.getP2());
        double time = dist/(float)this.getPlayerStat("speed");
        double currentTime = this.travelTime;
        if(currentTime>time) currentTime=time;
        double currentDist = (float)this.getPlayerStat("speed")*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
        if(dist != 0) return rPoint;
        else return location;
    }

    private int findPathIndex(){ //Finds the nearest point along the defined path for the minion to travel to
        double[] pathX;
        double[] pathY;
        if(this.lane != 0){
            pathX = blueBotX;
            pathY = blueBotY;
        }else{
            pathX = blueTopX;
            pathY = blueTopY;
        }
        double shortestDistance = 100;
        int index = -1;
        if(movementLine == null) index = this.pathIndex;
        else{
            for(int i = 0; i < pathX.length; i++){
                Point2D pathPoint = new Point2D.Double(pathX[i],pathY[i]);
                if(this.facingEntity(pathPoint)){
                    if(Math.abs(this.location.distance(pathPoint)) < shortestDistance){
                        shortestDistance = Math.abs(this.location.distance(pathPoint));
                        index = i;
                    }
                }
            }
            if(Math.abs(shortestDistance) < 0.01 && ((this.team == 0 && index+1 != pathX.length) || (this.team == 1 && index-1 != 0))){
                if(this.team == 1) index++;
                else index--;
            }
        }
        return index;
    }

    private Point2D getPathPoint(){
        double x;
        double y;
        if(this.lane == 0){
            x = blueTopX[this.pathIndex];
            y = blueTopY[this.pathIndex];
        }else{
            x = blueBotX[this.pathIndex];
            y = blueBotY[this.pathIndex];
        }
        return new Point2D.Double(x,y);
    }

    public int getLane(){
        return this.lane;
    }

    public MinionType getType(){
        return this.type;
    }

    @Override
    protected HashMap<String, Double> initializeStats(){
        HashMap<String, Double> stats = new HashMap<>();
        JsonNode actorStats = this.parentExt.getActorStats(this.avatar.replace("0",""));
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k,actorStats.get(k).asDouble());
        }
        return stats;
    }
}