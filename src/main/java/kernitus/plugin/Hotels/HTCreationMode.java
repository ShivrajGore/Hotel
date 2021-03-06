package kernitus.plugin.Hotels;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import com.sk89q.worldedit.bukkit.selections.Polygonal2DSelection;
import com.sk89q.worldedit.bukkit.selections.Selection;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import kernitus.plugin.Hotels.events.RoomCreateEvent;
import kernitus.plugin.Hotels.exceptions.EventCancelledException;
import kernitus.plugin.Hotels.exceptions.HotelAlreadyPresentException;
import kernitus.plugin.Hotels.handlers.HTConfigHandler;
import kernitus.plugin.Hotels.managers.HTWorldGuardManager;
import kernitus.plugin.Hotels.managers.Mes;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HTCreationMode {

	public static boolean isInCreationMode(UUID uuid){
		return HTConfigHandler.getInventoryFile(uuid).exists();
	}

	public static void hotelSetup(String hotelName, CommandSender s){
		Player p = (Player) s;

		if(hotelName.contains("-")){ Mes.mes(p, "chat.creationMode.invalidChar"); return; }

		Selection sel = getWorldEdit().getSelection(p);
		Hotel hotel = new Hotel(p.getWorld(), hotelName);

		if(hotel.exists()){	Mes.mes(p, "chat.creationMode.hotelCreationFailed"); return; }

		if(sel==null){ Mes.mes(p, "chat.creationMode.noSelection"); return; }

		int ownedHotels = HotelsAPI.getHotelsOwnedBy(p.getUniqueId()).size();
		int maxHotels = HTConfigHandler.getconfigYML().getInt("maxHotelsOwned", 3);
		if(ownedHotels > maxHotels && !Mes.hasPerm(p, "hotels.create.admin")){
			Mes.mes(p, "chat.commands.create.maxHotelsReached", "%max%", String.valueOf(maxHotels));
		}
		//Creating hotel region

		ProtectedRegion r;

		if(sel instanceof CuboidSelection){
			r = new ProtectedCuboidRegion(
					"Hotel-" + hotelName,
					new BlockVector(sel.getNativeMinimumPoint()),
					new BlockVector(sel.getNativeMaximumPoint())
			);
		}
		else if(sel instanceof Polygonal2DSelection){
			int minY = sel.getMinimumPoint().getBlockY();
			int maxY = sel.getMaximumPoint().getBlockY();
			List<BlockVector2D> points = ((Polygonal2DSelection) sel).getNativePoints();
			r = new ProtectedPolygonalRegion("Hotel-"+hotelName, points, minY, maxY);
		}
		else{ Mes.mes(p, "chat.creationMode.selectionInvalid"); return; }

		try{
			hotel.create(r); //Exception could be thrown here

			r = hotel.getRegion(); //In case it was modified by the event
			HTWorldGuardManager.addOwner(p, r);

			Mes.mes(p, "chat.creationMode.hotelCreationSuccessful",
					"%hotel%", hotel.getName());
			ownedHotels = HotelsAPI.getHotelsOwnedBy(p.getUniqueId()).size();

			String hotelsLeft = String.valueOf(maxHotels-ownedHotels);

			if(!Mes.hasPerm(p, "hotels.create.admin"))//If the player has hotel limit display message
				Mes.mes(p, "chat.commands.create.creationSuccess",
						"%tot%", String.valueOf(ownedHotels),
						"%left%", String.valueOf(hotelsLeft));
		}
		catch (HotelAlreadyPresentException e){
			Mes.mes(p, "chat.commands.create.hotelAlreadyPresent");
		}
		catch (EventCancelledException e){}
	}

	public static void roomSetup(String hotelName, String roomNum, Player p){
		Selection sel = getWorldEdit().getSelection(p);
		World world = p.getWorld();
		Hotel hotel = new Hotel(world, hotelName);
		if(!hotel.exists()){ Mes.mes(p, "chat.commands.hotelNonExistent"); return; }
		Room room = new Room(hotel, roomNum);
		if(room.exists()){ Mes.mes(p, "chat.creationMode.rooms.alreadyExists"); return; }

		RoomCreateEvent rce = new RoomCreateEvent(room);
		Bukkit.getPluginManager().callEvent(rce);// Call RoomCreateEvent
		if(rce.isCancelled()) return;

		ProtectedRegion pr = hotel.getRegion();
		if(sel==null){ Mes.mes(p, "chat.creationMode.noSelection"); return; }

		ProtectedRegion r;

		if(sel instanceof CuboidSelection){
			if(!pr.contains(sel.getNativeMinimumPoint()) || !pr.contains(sel.getNativeMaximumPoint())){
				Mes.mes(p, "chat.creationMode.rooms.notInHotel"); return;	}

			r = new ProtectedCuboidRegion(
					"Hotel-" + hotelName + "-" + room.getNum(),
					new BlockVector(sel.getNativeMinimumPoint()),
					new BlockVector(sel.getNativeMaximumPoint())
			);
		}
		else if (sel instanceof Polygonal2DSelection){
			Polygonal2DSelection poly = (Polygonal2DSelection) sel;
			List<BlockVector2D> points = poly.getNativePoints();

			if(!pr.containsAny(points)){
				Mes.mes(p, "chat.creationMode.rooms.notInHotel"); return;	}

			int minY = sel.getMinimumPoint().getBlockY();
			int maxY = sel.getMaximumPoint().getBlockY();

			r = new ProtectedPolygonalRegion("Hotel-" + hotelName + "-" + room.getNum(), points, minY, maxY);
		} else {
			Mes.mes(p, "chat.creationMode.selectionInvalid"); return;
		}

		room.createRegion(r, p);
	}

	public static void resetInventoryFiles(UUID id){
		HTConfigHandler.getInventoryFile(id).delete();
	}

	public static void saveInventory(CommandSender s){
		Player p = ((Player) s);
		UUID playerUUID = p.getUniqueId();
		PlayerInventory pinv = p.getInventory();
		File file = HTConfigHandler.getInventoryFile(playerUUID);

		if(file.exists()){ Mes.mes(p, "chat.commands.creationMode.alreadyIn"); return; }

		try {
			file.getParentFile().mkdirs();
			file.createNewFile();
		} catch (IOException e){
			Mes.mes(p, "chat.creationMode.inventory.storeFail");
			e.printStackTrace();
		}

		YamlConfiguration inv = HTConfigHandler.getInventoryConfig(playerUUID);

		inv.set("inventory", pinv.getContents());
		inv.set("armour", pinv.getArmorContents());

		try{//Only if in 1.9 try getting this
			inv.set("extra", pinv.getExtraContents());
		}
		catch(NoSuchMethodError er){ }//We must be in a pre-1.9 version

		try {
			inv.save(file);
		} catch (IOException e) {
			Mes.mes(p, "chat.creationMode.inventory.storeFail");
			e.printStackTrace();
		}

		pinv.clear();
		pinv.setArmorContents(new ItemStack[4]);
		Mes.mes(p, "chat.creationMode.inventory.storeSuccess");
	}

	@SuppressWarnings("unchecked")
	public static void loadInventory(Player p){
		UUID id = p.getUniqueId();
		PlayerInventory inv = p.getInventory();

		File invFile = HTConfigHandler.getInventoryFile(id);
		if(!invFile.exists()){ Mes.mes(p, "chat.creationMode.inventory.restoreFail"); return; }

		YamlConfiguration invConfig = YamlConfiguration.loadConfiguration(invFile);

		List<ItemStack> inventoryItems = (List<ItemStack>) invConfig.getList("inventory");
		inv.setContents(inventoryItems.toArray(new ItemStack[inventoryItems.size()]));

		List<ItemStack> armourItems = (List<ItemStack>) invConfig.getList("armour");
		inv.setArmorContents(armourItems.toArray(new ItemStack[armourItems.size()]));

		try{
			List<ItemStack> extraItems = (List<ItemStack>) invConfig.getList("extra");
			inv.setExtraContents(extraItems.toArray(new ItemStack[extraItems.size()]));
		}
		catch(Exception et){ } //Must be in a pre-1.9 version

		Mes.mes(p, "chat.creationMode.inventory.restoreSuccess");
		invFile.delete();
	}

	public static WorldEditPlugin getWorldEdit(){
		Plugin p = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
		return p instanceof WorldEditPlugin ? (WorldEditPlugin) p : null;
	}

	public static Selection getSelection(Player p){
		return getWorldEdit().getSelection(p);
	}

	public static void giveItems(CommandSender s){
		Player p = (Player) s;
		File file = new File("plugins" + File.separator + "WorldEdit" + File.separator + "config.yml");
		PlayerInventory pi = p.getInventory();

		//Wand
		if(file.exists()){
			YamlConfiguration weconfig = YamlConfiguration.loadConfiguration(file);
			if(!weconfig.contains("wand-item") || weconfig.get("wand-item") == null) return;
			int wanditem = weconfig.getInt("wand-item");
			@SuppressWarnings("deprecation")
			ItemStack wand = new ItemStack(wanditem, 1);
			ItemMeta im = wand.getItemMeta();
			im.setDisplayName(Mes.getStringNoPrefix("chat.creationMode.items.wand.name"));

			List<String> loreList = new ArrayList<String>();
			loreList.add(Mes.getStringNoPrefix("chat.creationMode.items.wand.lore1"));
			loreList.add(Mes.getStringNoPrefix("chat.creationMode.items.wand.lore2"));
			im.setLore(loreList);
			wand.setItemMeta(im);
			pi.setItem(0, wand);
		}

		//Sign
		if(p.getGameMode().equals(GameMode.CREATIVE)){
			ItemStack sign = new ItemStack(Material.SIGN, 1);
			ItemMeta sim = sign.getItemMeta();
			sim.setDisplayName(Mes.getStringNoPrefix("chat.creationMode.items.sign.name"));
			List<String> signLoreList = new ArrayList<String>();
			signLoreList.add(Mes.getStringNoPrefix("chat.creationMode.items.sign.lore1"));
			signLoreList.add(Mes.getStringNoPrefix("chat.creationMode.items.sign.lore2"));
			sim.setLore(signLoreList);
			sign.setItemMeta(sim);
			pi.setItem(1, sign);
		}

		//Compass
		if(Mes.hasPerm(p,"worldedit.navigation")){
			ItemStack compass = new ItemStack(Material.COMPASS, 1);
			ItemMeta cim = compass.getItemMeta();
			cim.setDisplayName(Mes.getStringNoPrefix("chat.creationMode.items.compass.name"));
			List<String> compassLoreList = new ArrayList<String>();
			compassLoreList.add(Mes.getStringNoPrefix("chat.creationMode.items.compass.lore1"));
			compassLoreList.add(Mes.getStringNoPrefix("chat.creationMode.items.compass.lore2"));
			cim.setLore(compassLoreList);
			compass.setItemMeta(cim);
			pi.setItem(2, compass);
		}

		//Leather
		if(Mes.hasPerm(p,"worldguard.region.wand")){
			ItemStack leather = new ItemStack(Material.LEATHER, 1);
			ItemMeta lim = leather.getItemMeta();
			lim.setDisplayName(Mes.getStringNoPrefix("chat.creationMode.items.leather.name"));
			List<String> leatherLoreList = new ArrayList<String>();
			leatherLoreList.add(Mes.getStringNoPrefix("chat.creationMode.items.leather.lore1"));
			leatherLoreList.add(Mes.getStringNoPrefix("chat.creationMode.items.leather.lore2"));
			lim.setLore(leatherLoreList);
			leather.setItemMeta(lim);
			pi.setItem(3, leather);
		}
	}
}