/**
 * Mars Simulation Project
 * GoodsUtil.java
 * @version 3.1.2 2020-09-02
 * @author Scott Davis
 */
package org.mars_sim.msp.core.structure.goods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.mars_sim.msp.core.SimulationConfig;
import org.mars_sim.msp.core.equipment.EquipmentFactory;
import org.mars_sim.msp.core.equipment.EquipmentType;
import org.mars_sim.msp.core.resource.AmountResource;
import org.mars_sim.msp.core.resource.ItemResource;
import org.mars_sim.msp.core.resource.ItemResourceUtil;
import org.mars_sim.msp.core.resource.Part;
import org.mars_sim.msp.core.resource.Resource;
import org.mars_sim.msp.core.resource.ResourceUtil;
import org.mars_sim.msp.core.vehicle.VehicleConfig;
import org.mars_sim.msp.core.vehicle.VehicleType;

/**
 * Utility class for goods information.
 */
public class GoodsUtil {

	private static Logger logger = Logger.getLogger(GoodsUtil.class.getName());
	
	// Data members
	private static List<Good> goodsList = null;
	private static Map<Integer, Good> goodsMap = null;
	
	private static VehicleConfig vehicleConfig = SimulationConfig.instance().getVehicleConfiguration();
	
	
	/**
	 * Private constructor for utility class.
	 */
	private GoodsUtil() {

	}

	/**
	 * Gets a list of all goods in the simulation.
	 * 
	 * @return list of goods
	 */
	public static List<Good> getGoodsList() {
		if (goodsList == null || goodsMap == null) {

			populateGoods();
		}

		return Collections.unmodifiableList(goodsList);
	}

	/**
	 * Gets a list of all goods in the simulation.
	 * 
	 * @return list of goods
	 */
	private static Map<Integer, Good> getGoodsMap() {
		if (goodsList == null || goodsMap == null) {
			populateGoods();
		}
		
		return goodsMap;
	}
	
	/**
	 * Destroys the current goods list and maps.
	 */
	public static void destroyGoods() {
		if (goodsList != null) {
			goodsList.clear();
		}

		goodsList = null;
		
		if (goodsMap != null) {
			goodsMap.clear();
		}

		goodsList = null;
	}
	
	public static Good createResourceGood(Resource resource) {
		if (resource == null) {
			logger.severe("resource is NOT supposed to be null.");
		}
		GoodType category = null;
		if (resource instanceof AmountResource)
			category = GoodType.AMOUNT_RESOURCE;
		else if (resource instanceof ItemResource)
			category = GoodType.ITEM_RESOURCE;
		return new Good(resource.getName(), resource.getID(), category);
	}

	/**
	 * Gets a good object for a given resource.
	 * 
	 * @param resource the resource.
	 * @return good for the resource.
	 */
	public static Good getResourceGood(Resource resource) {
		if (resource == null) { 
			throw new IllegalArgumentException("resource is NOT supposed to be null.");
		}
		
		int id = resource.getID();
		Map<Integer, Good> goods = getGoodsMap();
		Good result = goods.get(id);
		if (result == null) {
			throw new IllegalArgumentException("Resource " + resource + " cannot be mapped to a Good");
		}
		return result;
	}

	/**
	 * Gets a good object for a given resource.
	 * 
	 * @param id the resource id.
	 * @return good for the resource.
	 */
	public static Good getResourceGood(int id) {
		return getGoodsMap().get(id);
	}
	
	
	public static Good getResourceGood(String name) {
		for (Good g: getGoodsList()) {
			if (name.equalsIgnoreCase(g.getName()))
				return g;
		}
		return null;
	}
	
	/**
	 * Creates a good object for a given equipment class.
	 * 
	 * @param equipmentClass the equipment class.
	 * @return good for the resource class or null if none.
	 */
	public static Good createEquipmentGood(Class<?> equipmentClass) {
		if (equipmentClass == null) {
			logger.severe("goodClass cannot be null");
		}
		int id = EquipmentType.convertClass2ID(equipmentClass);
		return new Good(EquipmentType.convertID2Enum(id).getName(), id, GoodType.EQUIPMENT);
	}

	/**
	 * Gets a good object for a given equipment class.
	 * 
	 * @param equipmentClass the equipment class.
	 * @return good for the resource class or null if none.
	 */
	public static Good getEquipmentGood(Class<?> equipmentClass) {
		if (equipmentClass == null) {
			logger.severe("equipmentClass is NOT supposed to be null.");
		}
		int id = EquipmentType.convertClass2ID(equipmentClass);
		if (id > 0) {
			return getEquipmentGood(id);
		}
		
		return null;
	}

	
	/**
	 * Gets a good object for a given equipment id
	 * 
	 * @param id
	 * @return
	 */
	public static Good getEquipmentGood(int id) {
		return getGoodsMap().get(id);
	}

	/**
	 * Creates a good object for the given vehicle type.
	 * 
	 * @param vehicleType the vehicle type string.
	 * @return good for the vehicle type.
	 */
	public static Good createVehicleGood(String vehicleType) {
		if ((vehicleType == null) || vehicleType.trim().length() == 0) {
			logger.severe("vehicleType is NOT supposed to be blank or null.");
		}
		return new Good(vehicleType, VehicleType.convertName2ID(vehicleType), GoodType.VEHICLE);
	}

	/**
	 * Gets a good object for the given vehicle type.
	 * 
	 * @param vehicleType the vehicle type string.
	 * @return good for the vehicle type.
	 */
	public static Good getVehicleGood(String vehicleType) {
		if ((vehicleType == null) || vehicleType.trim().length() == 0) {
			logger.severe("vehicleType is NOT supposed to be blank or null.");
		}
		
		int id = VehicleType.convertName2ID(vehicleType);		
		return getGoodsMap().get(id);
	}
	
	/**
	 * Checks if a good is valid in the simulation.
	 * 
	 * @param good the good to check.
	 * @return true if good is valid.
	 */
	public static boolean containsGood(Good good) {
		if (good == null) {
			logger.severe("good is NOT supposed to be null.");
		}
		return getGoodsList().contains(good);
	}

	/**
	 * Checks if a good is valid in the simulation.
	 * 
	 * @param good the good to check.
	 * @return true if good is valid.
	 */
	public static boolean containsGood(int id) {
		if (id > 0) {
			logger.severe("good id is NOT supposed to be less than zero.");
		}
		return getGoodsMap().containsKey(id);
	}
	
	/**
	 * Populates the goods list with all goods.
	 */
	private static synchronized void populateGoods() {
		if (goodsList != null) {
			// Another thread has created the lists
			return;
		}
		
		// Only updated here so don't need to be thread safe
		List<Good> newList = new ArrayList<>();
		Map<Integer,Good> newMap = new HashMap<>();
		
		// Populate amount resources.
		populateAmountResources(newList, newMap);

		// Populate item resources.
		populateItemResources(newList, newMap);

		// Populate equipment.
		populateEquipment(newList, newMap);

		// Populate vehicles.
		populateVehicles(newList, newMap);

		// Sort goods by name.
		Collections.sort(newList);
		
		// Do now assign to the static until fully populated to avoid race condition ith other Threads accessing
		// the values as they are populated
		goodsList = newList;
		goodsMap = newMap;
		
	}

	
	/**
	 * Populates the goods list with all amount resources.
	 * @param newMap 
	 * @param newList 
	 */
	private static void populateAmountResources(List<Good> newList, Map<Integer, Good> newMap) {
//		Iterator<Integer> i = ResourceUtil.getInstance().getARIDs().iterator();
		Iterator<AmountResource> i = ResourceUtil.getAmountResources().iterator();
		while (i.hasNext()) {
			AmountResource ar = i.next();
			Good g = createResourceGood(ar);
			newList.add(g);
			newMap.put(ar.getID(), g);
		}		
	}
	
	/**
	 * Populates the goods list with all item resources.
	 */
	private static void populateItemResources(List<Good> newList, Map<Integer, Good> newMap) {
		Iterator<Part> i = ItemResourceUtil.getItemResources().iterator();
		while (i.hasNext()) {
			Part p = i.next();
			Good g = createResourceGood(p);
			newList.add(g);
			newMap.put(p.getID(), g);
		}		
	}
	
	/**
	 * Populates the goods list with all equipment.
	 * @param newMap 
	 * @param newList 
	 */
	private static void populateEquipment(List<Good> newList, Map<Integer, Good> newMap) {
		List<String> equipmentNames = new ArrayList<>(EquipmentFactory.getEquipmentNames());
		Iterator<String> i = equipmentNames.iterator();
		while (i.hasNext()) {
			String name = i.next();
			int id = EquipmentType.convertName2ID(name);
			Good g = new Good(name, id, GoodType.EQUIPMENT);
			newList.add(g);
			newMap.put(id, g);
		}
	}
	
	/**
	 * Populates the goods list with all vehicles.
	 * @param newMap 
	 * @param newList 
	 */
	private static void populateVehicles(List<Good> newList, Map<Integer, Good> newMap) {
		Iterator<String> i = vehicleConfig.getVehicleTypes().iterator();
		while (i.hasNext()) {
			String name = i.next();
			int id = VehicleType.convertName2ID(name);
			Good g = new Good(name, id, GoodType.VEHICLE);
			newList.add(g);
			newMap.put(id, g);
		}
	}

	/**
	 * Gets the mass per item for a good.
	 * 
	 * @param good the good to check.
	 * @return mass (kg) per item (or 1kg for amount resources).
	 * @throws Exception if error getting mass per item.
	 */
	public static double getGoodMassPerItem(Good good) {
		double result = 0D;

		if (GoodType.AMOUNT_RESOURCE == good.getCategory())
			result = 1D;
		else if (GoodType.ITEM_RESOURCE == good.getCategory())
			result = ItemResourceUtil.findItemResource(good.getID()).getMassPerItem();
		else if (GoodType.EQUIPMENT == good.getCategory())
			result = EquipmentFactory.getEquipmentMass(good.getName());
		else if (GoodType.VEHICLE == good.getCategory()) {
			result = vehicleConfig.getEmptyMass(good.getName());
		}

		return result;
	}
	
}
