package kernitus.plugin.Hotels;

import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import kernitus.plugin.Hotels.events.HotelCreateEvent;
import kernitus.plugin.Hotels.events.HotelDeleteEvent;
import kernitus.plugin.Hotels.events.HotelRenameEvent;
import kernitus.plugin.Hotels.exceptions.*;
import kernitus.plugin.Hotels.handlers.HTConfigHandler;
import kernitus.plugin.Hotels.managers.HTFileFinder;
import kernitus.plugin.Hotels.managers.HTWorldGuardManager;
import kernitus.plugin.Hotels.managers.Mes;
import kernitus.plugin.Hotels.signs.ReceptionSign;
import kernitus.plugin.Hotels.trade.HotelBuyer;
import kernitus.plugin.Hotels.trade.TradesHolder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Hotel {

	private World world;
	private String name;
	private YamlConfiguration hconfig;

	public Hotel(World world, String name){
		if(world == null)
			this.world = getWorldFromHotelName(name);
		else this.world = world;
		this.name = ChatColor.stripColor(name);
		hconfig = getHotelConfig();
	}
	public Hotel(String name, Player p){
		this(p.getWorld(), name);
	}
	public Hotel(String name, CommandSender sender){
		this.name = name;
		if(sender instanceof Player) world = ((Player) sender).getWorld();//todo check if necessary with player overloaded method available
		else world = getWorldFromHotelName(name);
		hconfig = getHotelConfig();
	}
	private World getWorldFromHotelName(String hotelName){
		World world = null;
		for(Hotel hotel : HotelsAPI.getAllHotels())
			if(hotel.getName().equalsIgnoreCase(hotelName))
				world = hotel.getWorld();

		return world;
	}
	///////////////////////
	////////Getters////////
	///////////////////////
	public boolean exists(){
		return world != null && name != null && HTWorldGuardManager.hasRegion(world, "hotel-" + name);
	}
	public World getWorld(){
		return world;
	}
	public String getName(){
		return name;
	}
	public ProtectedRegion getRegion(){
		return HTWorldGuardManager.getHotelRegion(world, name);
	}
	public ArrayList<Room> getRooms(){
		ArrayList<Room> rooms = new ArrayList<Room>();
		for(ProtectedRegion r : HTWorldGuardManager.getRegions(world)){
			String id = r.getId();
			if(id.matches("hotel-" + name.toLowerCase() + "-\\d+")){
				String num = id.replaceFirst("hotel-" + name.toLowerCase() + "-", "");
				Room room = new Room(this, num);
				rooms.add(room);
			}
		}
		return rooms;
	}
	public int getTotalRoomCount(){
		//Finds total amount of rooms in given hotel
		return getRooms().size();
	}
	public ArrayList<Room> getFreeRooms(){
		ArrayList<Room> rooms = getRooms();
		ArrayList<Room> freeRooms = new ArrayList<Room>();
		for(Room room : rooms){
			if(room.isFree())
				freeRooms.add(room);
		}
		return freeRooms;
	}
	public int getFreeRoomCount(){
		//Finds amount of free rooms in given hotel
		return getFreeRooms().size();
	}
	public boolean hasRentedRooms(){
		if(exists()){
			ArrayList<Room> rooms = getRooms();
			for(Room room : rooms){
				if(room.isRented())
					return true;
			}
		}
		return false;
	}
	public ArrayList<ReceptionSign> getAllReceptionSigns(){
		ArrayList<String> fileList = HTFileFinder.listFiles(HTConfigHandler.getReceptionSignsFolder(name).getAbsolutePath());
		ArrayList<ReceptionSign> signs = new ArrayList<ReceptionSign>();
		for(String x : fileList)
			signs.add(new ReceptionSign(this, x.replace(".yml", "")));
		return signs;
	}
	public File getHotelFile(){
		return HTConfigHandler.getHotelFile(name.toLowerCase());
	}
	public YamlConfiguration getHotelConfig(){
		return HTConfigHandler.getHotelConfig(name.toLowerCase());
	}
	public DefaultDomain getOwners(){
		return getRegion().getOwners();
	}
	public Location getHome(){
		if(world == null ||
				!hconfig.contains("Hotel.home.x", true) ||
				!hconfig.contains("Hotel.home.y", true) ||
				!hconfig.contains("Hotel.home.z", true)
				) return null;

		return new Location(world,
				hconfig.getDouble("Hotel.home.x"),
				hconfig.getDouble("Hotel.home.y"),
				hconfig.getDouble("Hotel.home.z"),
				(float) hconfig.getDouble("Hotel.home.yaw"),
				(float) hconfig.getDouble("Hotel.home.pitch"));
	}
	public HotelBuyer getBuyer(){
		return TradesHolder.getBuyerFromHotel(this);
	}
	public int getNextNewRoom(){
		for(Room room : getRooms()){
			if(!room.exists())
				return Integer.parseInt(room.getNum());
		}
		return 0;
	}
	public boolean isBlockWithinHotelRegion(Block b){
		return getRegion().contains(b.getX(),b.getY(),b.getZ());
	}
	public void setName(String name){
		this.name = name;
	}
	public void rename(String newName) throws EventCancelledException, HotelNonExistentException {
		HotelRenameEvent hre = new HotelRenameEvent(this, newName);
		Bukkit.getPluginManager().callEvent(hre);
		newName = hre.getNewName(); //In case it was modified by the event

		if(hre.isCancelled()) throw new EventCancelledException();
		if(!exists()) throw new HotelNonExistentException();

		//Change hotel name inside reception sign files
		ArrayList<ReceptionSign> rss = getAllReceptionSigns();
		for(ReceptionSign rs : rss)
			rs.setHotelNameInConfig(newName);

		//Renaming reception signs folder to new name
		HTConfigHandler.getReceptionSignsFolder(name).renameTo(HTConfigHandler.getReceptionSignsFolder(newName));

		//Rename rooms
		ArrayList<Room> rooms = getRooms();

		for(Room room : rooms){
			room.renameRoom(newName);
			File hotelsFile = getHotelFile();
			File newHotelsfile = HTConfigHandler.getHotelFile(newName);
			hotelsFile.renameTo(newHotelsfile);
		}
		HTWorldGuardManager.renameRegion("hotel-" + name, "hotel-" + newName, world);
		name = newName;
		ProtectedRegion r = getRegion();

		if(Mes.flagValue("hotel.map-making.GREETING").equalsIgnoreCase("true"))
			r.setFlag(DefaultFlag.GREET_MESSAGE, (Mes.getStringNoPrefix("message.hotel.enter").replaceAll("%hotel%", name)));
		if(Mes.flagValue("hotel.map-making.FAREWELL") != null)
			r.setFlag(DefaultFlag.FAREWELL_MESSAGE, (Mes.getStringNoPrefix("message.hotel.exit").replaceAll("%hotel%", name)));

		updateReceptionSigns();
	}
	public void removeAllSigns(){
		deleteAllReceptionSigns();

		for(Room room : getRooms())
			room.deleteSignAndFile();
	}
	public void deleteAllReceptionSigns(){
		Mes.debug("Deleting all reception signs...");
		for(ReceptionSign rs : getAllReceptionSigns())
			rs.deleteSignAndConfig();
		HTConfigHandler.getReceptionSignsFolder(name).delete();
	}
	public void deleteHotelFile(){
		getHotelFile().delete();
	}
	public boolean createHotelFile(){
		try {
			getHotelFile().createNewFile();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	public boolean saveHotelConfig(){
		try {
			hconfig.save(getHotelFile());
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	public void delete() throws EventCancelledException, HotelNonExistentException {
		HotelDeleteEvent hde = new HotelDeleteEvent(this);
		Bukkit.getPluginManager().callEvent(hde);
		if(hde.isCancelled()) throw new EventCancelledException();
		if(!exists()) throw new HotelNonExistentException();

		//Remove all reception signs and files
		deleteAllReceptionSigns();
		//Remove all rooms including regions, signs and files
		for(Room room : getRooms())
			room.delete();
		//Remove Hotel file if existent
		deleteHotelFile();
		//Delete hotel region
		HTWorldGuardManager.removeRegion(world, getRegion());
	}
	public boolean isOwner(UUID uuid){
		return getOwners().contains(uuid);
	}
	@Deprecated
	public boolean isOwner(String name){
		return getOwners().contains(name);
	}
	public void create(ProtectedRegion region) throws EventCancelledException, HotelAlreadyPresentException {
		HotelCreateEvent hce = new HotelCreateEvent(this, region);
		Bukkit.getPluginManager().callEvent(hce); //Call HotelCreateEvent
		if(hce.isCancelled()) throw new EventCancelledException();

		//In case a listener modified this stuff
		world = hce.getWorld();
		name = hce.getName();
		region = hce.getRegion();
		if(HTWorldGuardManager.doHotelRegionsOverlap(region, world)) throw new HotelAlreadyPresentException();

		HTWorldGuardManager.addRegion(world, region);
		HTWorldGuardManager.hotelFlags(region, name, world);
		HTWorldGuardManager.saveRegions(world);
	}
	public void updateReceptionSigns(){
		ArrayList<ReceptionSign> rss = getAllReceptionSigns();
		Mes.debug("Updating reception signs for hotel " + name);
		for(ReceptionSign rs : rss)
			rs.update();
	}

	//////////////////////////
	////////Setters///////////
	//////////////////////////
	public void setNewOwner(UUID uuid){
		ArrayList<UUID> uuids = new ArrayList<UUID>();
		uuids.add(uuid);
		HTWorldGuardManager.setOwners(uuids, getRegion());
		HTWorldGuardManager.saveRegions(world);
	}
	public void setBuyer(UUID uuid, double price){
		TradesHolder.addHotelBuyer(Bukkit.getPlayer(uuid), this, price);
	}
	public void removeBuyer(){
		TradesHolder.removeHotelBuyer(TradesHolder.getBuyerFromHotel(this).getPlayer());
	}
	public void setHome(Location loc){
		hconfig.set("Hotel.home.x", loc.getX());
		hconfig.set("Hotel.home.y", loc.getY());
		hconfig.set("Hotel.home.z", loc.getZ());
		hconfig.set("Hotel.home.pitch", loc.getPitch());
		hconfig.set("Hotel.home.yaw", loc.getYaw());
	}
	public void addOwner(OfflinePlayer p){
		HTWorldGuardManager.addOwner(p, getRegion());
	}
	public void addOwner(UUID uuid){
		addOwner(Bukkit.getOfflinePlayer(uuid));
	}
	public void removeOwner(OfflinePlayer p){
		HTWorldGuardManager.removeOwner(p, getRegion());
	}
	public void removeOwner(UUID uuid){
		removeOwner(Bukkit.getOfflinePlayer(uuid));
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public List<UUID> getHelpers(){
		List<String> helpers = hconfig.getStringList("Hotel.helpers");
		List<UUID> ids = new ArrayList<>();
		for (String helper : helpers) {
			if(helper != null)
				ids.add(UUID.fromString(helper));
		}
		return ids;
	}
	public boolean isHelper(UUID id){
		for(UUID helper : getHelpers())
			if(id.equals(helper))
				return true;
		return false;
	}
	public void addHelper(OfflinePlayer helper) throws UserNonExistentException, IOException, UserAlreadyThereException, HotelNonExistentException {

		if(!exists()) throw new HotelNonExistentException();

		if(!helper.hasPlayedBefore()) throw new UserNonExistentException();

		if(isHelper(helper.getUniqueId())) throw new UserAlreadyThereException();

		//Adding player as region member

		HTWorldGuardManager.addMember(helper, getRegion());
		//Adding player to config under helpers list
		List<UUID> ids = getHelpers();
		ids.add(helper.getUniqueId());

		List<String> strings = new ArrayList<String>();
		ids.forEach(id -> strings.add(id.toString()));

		hconfig.set("Hotel.helpers", strings);

		saveHotelConfig();
	}

	public void removeHelper(UUID helper) throws FriendNotFoundException, IOException {
		if(!exists()) throw new FileNotFoundException();

		if(!getHelpers().contains(helper))
			throw new FriendNotFoundException();

		//Removing player as region member
		HTWorldGuardManager.removeMember(helper, getRegion());

		//Removing player from config under helpers list
		List<UUID> ids = getHelpers();
		ids.remove(helper);

		List<String> strings = new ArrayList<String>();
		ids.forEach(id -> strings.add(id.toString()));

		hconfig.set("Hotel.helpers", strings);

		saveHotelConfig();
	}
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}