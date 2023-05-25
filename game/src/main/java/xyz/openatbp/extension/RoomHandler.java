package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.SFSUser;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.entities.variables.RoomVariable;
import com.smartfoxserver.v2.entities.variables.SFSRoomVariable;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.champions.FlamePrincess;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class RoomHandler implements Runnable{
    private ATBPExtension parentExt;
    private Room room;
    private ArrayList<Minion> minions;
    private ArrayList<Tower> towers;
    private ArrayList<UserActor> players;
    private List<Projectile> activeProjectiles;
    private List<Monster> campMonsters;
    private Base[] bases = new Base[2];
    private int mSecondsRan = 0;
    private int secondsRan = 0;
    private int[] altarStatus = {0,0,0};
    private HashMap<String,Integer> cooldowns = new HashMap<>();
    public RoomHandler(ATBPExtension parentExt, Room room){
        this.parentExt = parentExt;
        this.room = room;
        this.minions = new ArrayList<>();
        this.towers = new ArrayList<>();
        this.players = new ArrayList<>();
        this.campMonsters = new ArrayList<>();
        HashMap<String, Point2D> towers0 = MapData.getTowerData(room.getGroupId(),0);
        HashMap<String, Point2D> towers1 = MapData.getTowerData(room.getGroupId(),1);
        for(String key : towers0.keySet()){
            towers.add(new Tower(parentExt,room,key,0,towers0.get(key)));
        }
        for(String key : towers1.keySet()){
            towers.add(new Tower(parentExt,room,key,1,towers1.get(key)));
        }
        bases[0] = new Base(parentExt, room,0);
        bases[1] = new Base(parentExt, room, 1);
        for(User u : room.getUserList()){
            players.add(Champion.getCharacterClass(u,parentExt));
            //ExtensionCommands.createActor(this.parentExt,u,"testMonster","bot_finn",new Point2D.Float(0f,0f),0f,2);
            //ExtensionCommands.createActor(this.parentExt,u,"testMonster2","bot_jake",new Point2D.Float(0f,0f),0f,2);
            //ExtensionCommands.createActor(this.parentExt,u,"testMonster3","bot_iceking",new Point2D.Float(0f,0f),0f,2);

        }
        this.activeProjectiles = new ArrayList<>();
        this.campMonsters = new ArrayList<>();
        //this.campMonsters = GameManager.initializeCamps(parentExt,room);

    }
    @Override
    public void run() {
        mSecondsRan+=100;
        if(mSecondsRan % 1000 == 0){ // Handle every second
            try{
                if(room.getUserList().size() == 0) parentExt.stopScript(room.getId()); //If no one is in the room, stop running.
                else{
                    handleAltars();
                    handleHealthRegen();
                }
                ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
                for(String s : GameManager.SPAWNS){ //Check all mob/health spawns for how long it's been since dead
                    if(s.length()>3){
                        int spawnRate = 10; //Mob spawn rate
                        if(s.equalsIgnoreCase("keeoth")) spawnRate = 120;
                        else if(s.equalsIgnoreCase("ooze")) spawnRate = 90;
                        if(spawns.getInt(s) == spawnRate){ //Mob timers will be set to 0 when killed or health when taken
                            spawnMonster(s);
                            spawns.putInt(s,spawns.getInt(s)+1);
                        }else{
                            spawns.putInt(s,spawns.getInt(s)+1);
                        }
                    }else{
                        int time = spawns.getInt(s);
                        if(time == 10){
                            spawnHealth(s);
                        }
                        else if(time < 91){
                            time++;
                            spawns.putInt(s,time);
                        }
                    }
                }
                handleCooldowns();
                secondsRan++;
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        try{
            for(UserActor u : players){ //Tracks player location
                u.update(mSecondsRan);
            }
            for(Projectile p : activeProjectiles){ //Handles skill shots
                p.updateTimeTraveled();
                if(p.getDestination().distance(p.getLocation()) <= 0.01){
                    System.out.println("Removing projectile");
                    p.destroy();
                    activeProjectiles.remove(p);
                    break;
                }
                UserActor hitActor = p.checkPlayerCollision(this);
                if(hitActor != null){
                    System.out.println("Hit w/ projectile: " + hitActor.getAvatar());
                    activeProjectiles.remove(p);
                    break;
                }
            }
            handleHealth();
            for(Minion m : minions){ //Handles minion behavior
                m.update(mSecondsRan);
            }
            minions.removeIf(m -> (m.getHealth()<=0));
            for(Monster m : campMonsters){
                m.update(mSecondsRan);
            }
            //campMonsters.removeIf(m -> (m.getHealth()<=0));
            for(Tower t : towers){
                if(t.getHealth() <= 0 && (t.getTowerNum() == 0 || t.getTowerNum() == 3)) bases[t.getTeam()].unlock();
            }
            towers.removeIf(t -> (t.getHealth()<=0));
            //TODO: Add minion waves
            if(mSecondsRan == 5000){
                //this.addMinion(1,1,0,1);
                //this.addMinion(0,0,0,1);
            }else if(mSecondsRan == 7000){
                //this.addMinion(1,1,0,1);
                //this.addMinion(0,1,0,1);
            }else if(mSecondsRan == 9000){
                //this.addMinion(0,2,0);
            }
            if(this.room.getUserList().size() == 0) parentExt.stopScript(this.room.getId());
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    public Tower findTower(String id){
        for(Tower t : towers){
            if(t.getId().equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public Minion findMinion(String id){
        for(Minion m : minions){
            if(m.getId().equalsIgnoreCase(id)) return m;
        }
        return null;
    }

    public void addMinion(int team, int type, int wave, int lane){
        Minion m = new Minion(parentExt,room, team, type, wave,lane);
        minions.add(m);
        for(User u : room.getUserList()){
            ExtensionCommands.createActor(parentExt,u,m.creationObject());
        }
        m.move(parentExt);
    }

    public Base getOpposingTeamBase(int team){
        if(team == 0) return bases[1];
        else return  bases[0];
    }

    private void handleHealth(){
        for(String s : GameManager.SPAWNS){
            if(s.length() == 3){
                ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
                if(spawns.getInt(s) == 91){
                    for(UserActor u : players){
                        Point2D currentPoint = u.getLocation();
                        if(insideHealth(currentPoint,getHealthNum(s))){
                            int team = u.getTeam();
                            Point2D healthLoc = getHealthLocation(getHealthNum(s));
                            ExtensionCommands.removeFx(parentExt,room,s+"_fx");
                            ExtensionCommands.createActorFX(parentExt,room,String.valueOf(u.getId()),"picked_up_health_cyclops",2000,s+"_fx2",true,"",false,false,team);
                            ExtensionCommands.playSound(parentExt,u.getUser(),"sfx_health_picked_up",healthLoc);
                            Champion.updateHealth(parentExt,u.getUser(),100);
                            Champion.giveBuff(parentExt,u.getUser(), Buff.HEALTH_PACK);
                            spawns.putInt(s,0);
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean insideHealth(Point2D pLoc, int health){
        Point2D healthLocation = getHealthLocation(health);
        double hx = healthLocation.getX();
        double hy = healthLocation.getY();
        double px = pLoc.getX();
        double pz = pLoc.getY();
        double dist = Math.sqrt(Math.pow(px-hx,2) + Math.pow(pz-hy,2));
        return dist<=0.5;
    }

    private Point2D getHealthLocation(int num){
        float x = MapData.L2_BOT_BLUE_HEALTH[0];
        float z = MapData.L2_BOT_BLUE_HEALTH[1];
        // num = 1
        switch(num){
            case 0:
                z*=-1;
                break;
            case 2:
                x = MapData.L2_LEFT_HEALTH[0];
                z = MapData.L2_LEFT_HEALTH[1];
                break;
            case 3:
                x*=-1;
                z*=-1;
                break;
            case 4:
                x*=-1;
                break;
            case 5:
                x = MapData.L2_LEFT_HEALTH[0]*-1;
                z = MapData.L2_LEFT_HEALTH[1];
                break;
        }
        return new Point2D.Float(x,z);
    }

    private int getHealthNum(String id){
        switch(id){
            case "ph2": //Purple team bot
                return 4;
            case "ph1": //Purple team top
                return 3;
            case "ph3": // Purple team mid
                return 5;
            case "bh2": // Blue team bot
                return 1;
            case "bh1": // Blue team top
                return 0;
            case "bh3": //Blue team mid
                return 2;
        }
        return -1;
    }

    private void spawnMonster(String monster){
        System.out.println("Spawning: " + monster);
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        String map = room.getGroupId();
        for(User u : users){
            ISFSObject monsterObject = new SFSObject();
            ISFSObject monsterSpawn = new SFSObject();
            float x = 0;
            float z = 0;
            String actor = monster;
            if(monster.equalsIgnoreCase("gnomes") || monster.equalsIgnoreCase("owls")){
                char[] abc = {'a','b','c'};
                for(int i = 0; i < 3; i++){ //Gnomes and owls have three different mobs so need to be spawned in triplets
                    if(monster.equalsIgnoreCase("gnomes")){
                        actor="gnome_"+abc[i];
                        x = (float)MapData.GNOMES[i].getX();
                        z = (float)MapData.GNOMES[i].getY();
                        campMonsters.add(new Monster(parentExt,room,MapData.GNOMES[i],actor));
                    }else{
                        actor="ironowl_"+abc[i];
                        x = (float)MapData.OWLS[i].getX();
                        z = (float)MapData.OWLS[i].getY();
                        campMonsters.add(new Monster(parentExt,room,MapData.OWLS[i],actor));
                    }
                    monsterObject.putUtfString("id",actor);
                    monsterObject.putUtfString("actor",actor);
                    monsterObject.putFloat("rotation",0);
                    monsterSpawn.putFloat("x",x);
                    monsterSpawn.putFloat("y",0);
                    monsterSpawn.putFloat("z",z);
                    monsterObject.putSFSObject("spawn_point",monsterSpawn);
                    monsterObject.putInt("team",2);
                    parentExt.send("cmd_create_actor",monsterObject,u);
                }
            }else if(monster.length()>3){
                switch(monster){
                    case "hugwolf":
                        x = MapData.HUGWOLF[0];
                        z = MapData.HUGWOLF[1];
                        campMonsters.add(new Monster(parentExt,room,MapData.HUGWOLF,actor));
                        break;
                    case "grassbear":
                        x = MapData.GRASS[0];
                        z = MapData.GRASS[1];
                        campMonsters.add(new Monster(parentExt,room,MapData.GRASS,actor));
                        break;
                    case "keeoth":
                        x = MapData.L2_KEEOTH[0];
                        z = MapData.L2_KEEOTH[1];
                        campMonsters.add(new Monster(parentExt,room,MapData.L2_KEEOTH,actor));
                        break;
                    case "ooze":
                        x = MapData.L2_OOZE[0];
                        z = MapData.L2_OOZE[1];
                        actor = "ooze_monster";
                        campMonsters.add(new Monster(parentExt,room,MapData.L2_OOZE,actor));
                        break;
                }
                monsterObject.putUtfString("id",actor);
                monsterObject.putUtfString("actor",actor);
                monsterObject.putFloat("rotation",0);
                monsterSpawn.putFloat("x",x);
                monsterSpawn.putFloat("y",0);
                monsterSpawn.putFloat("z",z);
                monsterObject.putSFSObject("spawn_point",monsterSpawn);
                monsterObject.putInt("team",2);
                parentExt.send("cmd_create_actor",monsterObject,u);
            }
        }
    }
    private void spawnHealth(String id){
        int healthNum = getHealthNum(id);
        Point2D healthLocation = getHealthLocation(healthNum);
        for(User u : room.getUserList()){
            int effectTime = (15*60-secondsRan)*1000;
            ExtensionCommands.createWorldFX(parentExt,u, String.valueOf(u.getId()),"pickup_health_cyclops",id+"_fx",effectTime,(float)healthLocation.getX(),(float)healthLocation.getY(),false,2,0f);
        }
        room.getVariable("spawns").getSFSObjectValue().putInt(id,91);
    }

    private void handleAltars(){
        int[] altarChange = {0,0,0};
        boolean[] playerInside = {false,false,false};
        for(UserActor u : players){

            int team = u.getTeam();
            Point2D currentPoint = u.getLocation();
            for(int i = 0; i < 3; i++){ // 0 is top, 1 is mid, 2 is bot
                if(insideAltar(currentPoint,i)){
                    playerInside[i] = true;
                    if(team == 1) altarChange[i]--;
                    else altarChange[i]++;
                }
            }
        }
        for(int i = 0; i < 3; i++){
            if(altarChange[i] > 0) altarChange[i] = 1;
            else if(altarChange[i] < 0) altarChange[i] = -1;
            else if(altarChange[i] == 0 && !playerInside[i]){
                if(altarStatus[i]>0) altarChange[i]=-1;
                else if(altarStatus[i]<0) altarChange[i]=1;
            }
            if(Math.abs(altarStatus[i]) <= 5) altarStatus[i]+=altarChange[i];
            for(UserActor u : players){
                int team = 2;
                if(altarStatus[i]>0) team = 0;
                else team = 1;
                String altarId = "altar_"+i;
                if(Math.abs(altarStatus[i]) == 6){ //Lock altar
                    altarStatus[i]=10; //Locks altar
                    if(i == 1) addScore(team,15);
                    else addScore(team,10);
                    cooldowns.put(altarId+"__"+"altar",180);
                    ISFSObject data2 = new SFSObject();
                    data2.putUtfString("id",altarId);
                    data2.putUtfString("bundle","fx_altar_lock");
                    data2.putInt("duration",1000*60*3);
                    data2.putUtfString("fx_id","fx_altar_lock"+i);
                    data2.putBool("parent",false);
                    data2.putUtfString("emit",altarId);
                    data2.putBool("orient",false);
                    data2.putBool("highlight",true);
                    data2.putInt("team",team);
                    parentExt.send("cmd_create_actor_fx",data2,u.getUser());
                    ISFSObject data = new SFSObject();
                    int altarNum = -1;
                    if(i == 0) altarNum = 1;
                    else if(i == 1) altarNum = 0;
                    else if(i == 2) altarNum = i;
                    data.putInt("altar",altarNum);
                    data.putInt("team",team);
                    data.putBool("locked",true);
                    parentExt.send("cmd_altar_update",data,u.getUser());
                    if(u.getTeam()==team){
                        ISFSObject data3 = new SFSObject();
                        String buffName;
                        String buffDescription;
                        String icon;
                        String bundle;
                        if(i == 1){
                            buffName = "Attack Altar" +i + " Buff";
                            buffDescription = "Gives you a burst of attack damage!";
                            icon = "icon_altar_attack";
                            bundle = "altar_buff_offense";
                        }else{
                            buffName = "Defense Altar" + i + " Buff";
                            buffDescription = "Gives you defense!";
                            icon = "icon_altar_armor";
                            bundle = "altar_buff_defense";
                        }
                        data3.putUtfString("name",buffName);
                        data3.putUtfString("desc",buffDescription);
                        data3.putUtfString("icon",icon);
                        data3.putFloat("duration",1000*60);
                        parentExt.send("cmd_add_status_icon",data3,u.getUser());
                        cooldowns.put(u.getId()+"__buff__"+buffName,60);
                        ISFSObject data4 = new SFSObject();
                        data4.putUtfString("id",String.valueOf(u.getId()));
                        data4.putUtfString("bundle",bundle);
                        data4.putInt("duration",1000*60);
                        data4.putUtfString("fx_id",bundle+u.getId());
                        data4.putBool("parent",true);
                        data4.putUtfString("emit",String.valueOf(u.getId()));
                        data4.putBool("orient",true);
                        data4.putBool("highlight",true);
                        data4.putInt("team",team);
                        parentExt.send("cmd_create_actor_fx",data4,u.getUser());
                        ISFSObject data5 = new SFSObject();
                        data5.putUtfString("id",altarId);
                        data5.putUtfString("attackerId",String.valueOf(u.getId()));
                        data5.putInt("deathTime",180);
                        parentExt.send("cmd_knockout_actor",data5,u.getUser());
                        handleXPShare(u,101);
                        //ExtensionCommands.updateActorData(parentExt,u.getUser(),ChampionData.addXP(u,101,parentExt));
                    }
                }else if(Math.abs(altarStatus[i])<=5 && altarStatus[i]!=0){ //Update altar
                    int stage = Math.abs(altarStatus[i]);
                    ISFSObject data = new SFSObject();
                    data.putUtfString("id",altarId);
                    data.putUtfString("bundle","fx_altar_"+stage);
                    data.putInt("duration",1000);
                    data.putUtfString("fx_id","fx_altar_"+stage+i);
                    data.putBool("parent",false);
                    data.putUtfString("emit",altarId);
                    data.putBool("orient",false);
                    data.putBool("highlight",true);
                    data.putInt("team",team);
                    parentExt.send("cmd_create_actor_fx",data,u.getUser());
                }
            }
        }
    }

    private boolean insideAltar(Point2D pLoc, int altar){
        double altar2_x = 0;
        double altar2_y = 0;
        if(altar == 0){
            altar2_x = MapData.L2_TOP_ALTAR[0];
            altar2_y = MapData.L2_TOP_ALTAR[1];
        }else if(altar == 2){
            altar2_x = MapData.L2_BOT_ALTAR[0];
            altar2_y = MapData.L2_BOT_ALTAR[1];
        }
        double px = pLoc.getX();
        double pz = pLoc.getY();
        double dist = Math.sqrt(Math.pow(px-altar2_x,2) + Math.pow(pz-altar2_y,2));
        return dist<=2;
    }
    public void addScore(int team, int points){
        ISFSObject scoreObject = room.getVariable("score").getSFSObjectValue();
        int blueScore = scoreObject.getInt("blue");
        int purpleScore = scoreObject.getInt("purple");
        if(team == 0) blueScore+=points;
        else purpleScore+=points;
        scoreObject.putInt("blue",blueScore);
        scoreObject.putInt("purple",purpleScore);
        ISFSObject pointData = new SFSObject();
        pointData.putInt("teamA",blueScore);
        pointData.putInt("teamB",purpleScore);
        for(User u : room.getUserList()){
            parentExt.send("cmd_update_score",pointData,u);
        }

    }

    private void handleCooldowns(){ //Cooldown keys structure is id__cooldownType__value. Example for a buff cooldown could be lich__buff__attackDamage
        for(String key : cooldowns.keySet()){
            String[] keyVal = key.split("__");
            String id = keyVal[0];
            String cooldown = keyVal[1];
            String value = "";
            if(keyVal.length > 2) value = keyVal[2];
            int time = cooldowns.get(key)-1;
            if(time<=0){
                switch(cooldown){
                    case "altar":
                        for(User u : room.getUserList()){
                            int altarIndex = Integer.parseInt(id.split("_")[1]);
                            ISFSObject data = new SFSObject();
                            int altarNum = -1;
                            if(id.equalsIgnoreCase("altar_0")) altarNum = 1;
                            else if(id.equalsIgnoreCase("altar_1")) altarNum = 0;
                            else if(id.equalsIgnoreCase("altar_2")) altarNum = 2;
                            data.putInt("altar",altarNum);
                            data.putInt("team",2);
                            data.putBool("locked",false);
                            parentExt.send("cmd_altar_update",data,u);
                            altarStatus[altarIndex] = 0;
                        }
                        break;
                    case "buff":
                        ISFSObject data = new SFSObject();
                        data.putUtfString("name",value);
                        parentExt.send("cmd_remove_status_icon",data,room.getUserById(Integer.parseInt(id)));
                        break;
                }
                cooldowns.remove(key);
            }else{
                cooldowns.put(key,time);
            }
        }
    }

    private void handleHealthRegen(){ // TODO: Fully heals player for some reason
        for(User u : room.getUserList()){
            ISFSObject stats = u.getVariable("stats").getSFSObjectValue();
            if(stats.getInt("currentHealth") > 0 && stats.getInt("currentHealth") < stats.getInt("maxHealth")){
                double healthRegen = stats.getDouble("healthRegen");
                this.getPlayer(String.valueOf(u.getId())).setHealth(stats.getInt("currentHealth"));
                Champion.updateHealth(parentExt,u,(int)healthRegen);
            }

        }
    }

    public ArrayList<UserActor> getPlayers(){
        return this.players;
    }

    public UserActor getPlayer(String id){
        for(UserActor p : players){
            if(p.getId().equalsIgnoreCase(id)) return p;
        }
        return null;
    }

    public void addProjectile(Projectile p){
        this.activeProjectiles.add(p);
    }

    public Minion getMinion(String id){
        for(Minion m : minions){
            if(m.getId().equalsIgnoreCase(id)) return m;
        }
        return  null;
    }

    public Tower getTower(String id){
        for(Tower t : towers){
            if(t.getId().equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public List<Actor> getActors(){
        List<Actor> actors = new ArrayList<>();
        actors.addAll(towers);
        actors.addAll(minions);
        Collections.addAll(actors, bases);
        actors.addAll(players);
        actors.addAll(campMonsters);
        return actors;
    }

    public Actor getActor(String id){
        for(Actor a : this.getActors()){
            if(a.getId().equalsIgnoreCase(id)) return a;
        }
        return null;
    }

    public List<Minion> getMinions(){
        return this.minions;
    }

    public List<Monster> getCampMonsters(){
        return this.campMonsters;
    }

    public List<Monster> getCampMonsters(String id){
        List<Monster> returnMonsters = new ArrayList<>(3);
        String type = id.split("_")[0];
        for(Monster m : this.campMonsters){
            if(!m.getId().equalsIgnoreCase(id) && m.getId().contains(type)){
                returnMonsters.add(m);
            }
        }
        return returnMonsters;
    }

    public void handleSpawnDeath(Actor a){
        System.out.println("The room has killed " + a.getId());
        String mons = a.getId().split("_")[0];
        if(a.getActorType() == ActorType.MONSTER){
            campMonsters.remove((Monster) a);
            System.out.println("New monster list: \n");
            for(Monster m : campMonsters){
                System.out.println(m.getId() + "\n");
            }
        }

        for(String s : GameManager.SPAWNS){
            if(s.contains(mons)){
                if(s.contains("keeoth")){
                    room.getVariable("spawns").getSFSObjectValue().putInt(s,0);
                    return;
                }
                else if(s.contains("ooze")){
                    room.getVariable("spawns").getSFSObjectValue().putInt(s,0);
                    return;
                }
                else if(!s.contains("gnomes") && !s.contains("owls")){
                    room.getVariable("spawns").getSFSObjectValue().putInt(s,0);
                    return;
                }
                else {
                    for(Monster m : campMonsters){
                        if(!m.getId().equalsIgnoreCase(a.getId()) && m.getId().contains(mons)) return;
                    }
                    room.getVariable("spawns").getSFSObjectValue().putInt(s,0);
                    return;
                }
            }
        }
    }

    public void handleXPShare (UserActor a, int xp){
        a.addXP(xp);
        for(UserActor p : players){
            if(!p.getId().equalsIgnoreCase(a.getId()) && p.getTeam() == a.getTeam()){
                int newXP = (int)Math.floor(xp*0.2);
                p.addXP(newXP);
            }
        }
    }

    public int getAveragePlayerLevel(){
        int combinedPlayerLevel = 0;
        for(UserActor a : this.players){
            combinedPlayerLevel+=a.getLevel();
        }
        return combinedPlayerLevel/this.players.size();
    }

    public List<Tower> getTowers(){
        return this.towers;
    }
}