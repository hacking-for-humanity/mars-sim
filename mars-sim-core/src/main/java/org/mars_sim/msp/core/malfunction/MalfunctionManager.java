/**
 * Mars Simulation Project
 * MalfunctionManager.java
 * @version 3.1.2 2020-09-02
 * @author Scott Davis
 */
package org.mars_sim.msp.core.malfunction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mars_sim.msp.core.Inventory;
import org.mars_sim.msp.core.LogConsolidated;
import org.mars_sim.msp.core.Simulation;
import org.mars_sim.msp.core.SimulationConfig;
import org.mars_sim.msp.core.Unit;
import org.mars_sim.msp.core.UnitEventType;
import org.mars_sim.msp.core.events.HistoricalEvent;
import org.mars_sim.msp.core.events.HistoricalEventManager;
import org.mars_sim.msp.core.person.EventType;
import org.mars_sim.msp.core.person.Person;
import org.mars_sim.msp.core.person.PhysicalCondition;
import org.mars_sim.msp.core.person.ai.PersonalityTraitType;
import org.mars_sim.msp.core.person.ai.mission.MissionMember;
import org.mars_sim.msp.core.person.health.Complaint;
import org.mars_sim.msp.core.person.health.ComplaintType;
import org.mars_sim.msp.core.person.health.MedicalManager;
import org.mars_sim.msp.core.resource.ItemResourceUtil;
import org.mars_sim.msp.core.resource.MaintenanceScope;
import org.mars_sim.msp.core.resource.Part;
import org.mars_sim.msp.core.resource.ResourceUtil;
import org.mars_sim.msp.core.robot.Robot;
import org.mars_sim.msp.core.structure.Settlement;
import org.mars_sim.msp.core.structure.building.Building;
import org.mars_sim.msp.core.time.ClockPulse;
import org.mars_sim.msp.core.time.MarsClock;
import org.mars_sim.msp.core.time.MasterClock;
import org.mars_sim.msp.core.time.Temporal;
import org.mars_sim.msp.core.tool.RandomUtil;

/**
 * The MalfunctionManager class manages the current malfunctions in each of the
 * 6 types of units (namely, Building, BuildingKit, EVASuit, Robot,
 * MockBuilding, or Vehicle). Each building has its own MalfunctionManager
 */
public class MalfunctionManager implements Serializable, Temporal {

	/** default serial id. */
	private static final long serialVersionUID = 1L;

	/** default logger. */
	private static Logger logger = Logger.getLogger(MalfunctionManager.class.getName());
	private static String loggerName = logger.getName();
	private static String sourceName = loggerName.substring(loggerName.lastIndexOf(".") + 1, loggerName.length());
	
	/** Modifier for number of parts needed for a trip. */
	public static final double PARTS_NUMBER_MODIFIER = 7.5;
	/** Estimate number of broken parts per malfunctions */
	public static final double AVERAGE_NUM_MALFUNCTION = 2.5;
	/** Estimate number of broken parts per malfunctions for EVA suits. */
	public static final double AVERAGE_EVA_MALFUNCTION = 2.0;
	
	/** Initial estimate for malfunctions per orbit for an entity. */
	private static final double ESTIMATED_MALFUNCTIONS_PER_ORBIT = 10D;
	/** Initial estimate for maintenances per orbit for an entity. */
	private static final double ESTIMATED_MAINTENANCES_PER_ORBIT = 10D;
	/** Factor for chance of malfunction by time since last maintenance. */
	private static final double MAINTENANCE_MALFUNCTION_FACTOR = .000_000_001D;
	/** Factor for chance of malfunction due to wear condition. */
	private static final double WEAR_MALFUNCTION_FACTOR = 9D;
	/** Factor for chance of accident due to wear condition. */
	private static final double WEAR_ACCIDENT_FACTOR = 1D;

	private static final String OXYGEN = "Oxygen";
//	private static final String WATER = "Water";
//	private static final String PRESSURE = "Air Pressure";
//	private static final String TEMPERATURE = "Temperature";

	private static final int SCORE_DEFAULT = 50;

	// Data members

	/** The number of malfunctions the entity has had so far. */
	private int numberMalfunctions;
	/** The number of times the entity has been maintained so far. */
	private int numberMaintenances;
	/** The number of orbits. */
	private int orbitCache = 0;
	/** Time passing (in millisols) since last maintenance on entity. */
	private double timeSinceLastMaintenance;
	/**
	 * Time (millisols) that entity has been actively used since last maintenance.
	 */
	private double effectiveTimeSinceLastMaintenance;
	/** The required work time for maintenance on entity. */
	private double maintenanceWorkTime;
	/** The completed. */
	private double maintenanceTimeCompleted;
	/** The percentage of the malfunctionable's condition from wear and tear. 0% = worn out -> 100% = new condition. */
	private double wearCondition;
	
	/**
	 * The expected life time [in millisols] of active use before the malfunctionable
	 * is worn out.
	 */
	private final double wearLifeTime;

	// Life support modifiers.
	private double oxygenFlowModifier = 100D;

	/** The owning entity. */
	private Malfunctionable entity;
	
	/** The collection of affected scopes. */
	private Set<String> scopes;
	/** The current malfunctions in the unit. */
	private Collection<Malfunction> malfunctions;
	/** The parts currently identified to be retrofitted. */
	private Map<Integer, Integer> partsNeededForMaintenance;
	
	// The static instances
	private static SimulationConfig simconfig = SimulationConfig.instance();
	private static Simulation sim = Simulation.instance();
	
	private static MasterClock masterClock;
	private static MarsClock currentTime;
	private static MedicalManager medic;
	private static MalfunctionFactory factory;
	private static HistoricalEventManager eventManager;
	
	// NOTE : each building has its own MalfunctionManager

	/**
	 * Constructor.
	 * 
	 * @param entity              the malfunctionable entity.
	 * @param wearLifeTime        the expected life time (millisols) of active use
	 *                            before the entity is worn out.
	 * @param maintenanceWorkTime the amount of work time (millisols) required for
	 *                            maintenance.
	 */
	public MalfunctionManager(Malfunctionable entity, double wearLifeTime, double maintenanceWorkTime) {

		// Initialize data members
		this.entity = entity;

		timeSinceLastMaintenance = 0D;
		effectiveTimeSinceLastMaintenance = 0D;
		scopes = new HashSet<>();
		malfunctions = new CopyOnWriteArrayList<Malfunction>();
		this.maintenanceWorkTime = maintenanceWorkTime;
		this.wearLifeTime = wearLifeTime;
		wearCondition = 100D;

		masterClock = sim.getMasterClock();
		if (masterClock != null)
			// Note that this if above is for maven test, or else NullPointerException
			currentTime = masterClock.getMarsClock();
			
		medic = sim.getMedicalManager();
		factory = sim.getMalfunctionFactory();
		eventManager = sim.getEventManager();
	}

	/**
	 * Add a scope string of a system or a function to the manager.
	 * 
	 * @param scopeString
	 */
	public void addScopeString(String scopeString) {
		if ((scopeString != null) && !scopes.contains(scopeString.toLowerCase()))
			scopes.add(scopeString.toLowerCase());

		// Update maintenance parts.
		determineNewMaintenanceParts();
	}
	
	public Set<String> getScopes() {
		return scopes;
	}

	/**
	 * Checks if entity has a malfunction.
	 * 
	 * @return true if malfunction
	 */
	public boolean hasMalfunction() {
		return !malfunctions.isEmpty();
	}

	/**
	 * Checks if entity has any emergency malfunctions.
	 * 
	 * @return true if emergency malfunction
	 */
	public boolean hasEmergencyMalfunction() {
		boolean result = false;

		if (hasMalfunction()) {
			for (Malfunction malfunction : malfunctions) {
				if (!malfunction.isEmergencyRepairDone())
					return true;
			}
		}

		return result;
	}

	/**
	 * Checks if entity has any general malfunctions.
	 * 
	 * @return true if general malfunction
	 */
	public boolean hasGeneralMalfunction() {
		boolean result = false;

		if (hasMalfunction()) {
			for (Malfunction malfunction : malfunctions) {
				if (!malfunction.isGeneralRepairDone())
					return true;
			}
		}

		return result;
	}

	/**
	 * Checks if entity has any EVA malfunctions.
	 * 
	 * @return true if EVA malfunction
	 */
	public boolean hasEVAMalfunction() {
		boolean result = false;

		if (hasMalfunction()) {
			for (Malfunction malfunction : malfunctions) {
				if (!malfunction.isEVARepairDone())
					return true;
			}
		}

		return result;
	}

	/**
	 * Gets a list of the unit's current malfunctions.
	 * 
	 * @return malfunction list
	 */
	public List<Malfunction> getMalfunctions() {
		return new ArrayList<>(malfunctions);
	}

	/**
	 * Gets the most serious malfunction the entity has.
	 * 
	 * @return malfunction
	 */
	public Malfunction getMostSeriousMalfunction() {

		Malfunction result = null;
		double highestSeverity = 0;

		if (hasMalfunction()) {
			for (Malfunction malfunction : malfunctions) {
				if ((malfunction.getSeverity() > highestSeverity) && !malfunction.isFixed()) {
					highestSeverity = malfunction.getSeverity();
					result = malfunction;
				}
			}
		}

		return result;
	}

	/**
	 * Gets the most serious emergency malfunction the entity has.
	 * 
	 * @return malfunction
	 */
	public Malfunction getMostSeriousEmergencyMalfunction() {

		Malfunction result = null;
		double highestSeverity = 0D;

		if (hasMalfunction()) {
			for (Malfunction malfunction : malfunctions) {
				if (!malfunction.isEmergencyRepairDone()
						&& malfunction.getSeverity() > highestSeverity) {
					highestSeverity = malfunction.getSeverity();
					result = malfunction;
				}
			}
		}

		return result;
	}

	/**
	 * Gets the most serious general malfunction the entity has.
	 * 
	 * @return malfunction
	 */
	public Malfunction getMostSeriousGeneralMalfunction() {

		Malfunction result = null;
		double highestSeverity = 0D;

		if (hasMalfunction()) {
			for (Malfunction malfunction : malfunctions) {
				if (!malfunction.isGeneralRepairDone()
						&& malfunction.getSeverity() > highestSeverity) {
					highestSeverity = malfunction.getSeverity();
					result = malfunction;
				}
			}
		}

		return result;
	}

	/**
	 * Gets a list of all general malfunctions sorted by highest severity first.
	 * 
	 * @return list of malfunctions.
	 */
	public List<Malfunction> getGeneralMalfunctions() {
		List<Malfunction> result = new CopyOnWriteArrayList<Malfunction>();
		for (Malfunction malfunction : malfunctions) {
			if (!malfunction.isGeneralRepairDone())
				result.add(malfunction);
		}
		Collections.sort(result, new MalfunctionSeverityComparator());
		return result;
	}

	/**
	 * Gets a list of all emergency malfunctions sorted by highest severity first.
	 * 
	 * @return list of malfunctions.
	 */
	public List<Malfunction> getEmergencyMalfunctions() {
		List<Malfunction> result = new CopyOnWriteArrayList<Malfunction>();
		for (Malfunction malfunction : malfunctions) {
			if (!malfunction.isEmergencyRepairDone())
				result.add(malfunction);
		}
		Collections.sort(result, new MalfunctionSeverityComparator());
		return result;
	}
	
	/**
	 * Gets the most serious EVA malfunction the entity has.
	 * 
	 * @return malfunction
	 */
	public Malfunction getMostSeriousEVAMalfunction() {

		Malfunction result = null;
		double highestSeverity = 0D;

		if (hasMalfunction()) {
			for (Malfunction malfunction : malfunctions) {
				if (!malfunction.isEVARepairDone()
						&& malfunction.getSeverity() > highestSeverity) {
					highestSeverity = malfunction.getSeverity();
					result = malfunction;
				}
			}
		}

		return result;
	}

	/**
	 * Gets a list of all EVA malfunctions sorted by highest severity first.
	 * 
	 * @return list of malfunctions.
	 */
	public List<Malfunction> getEVAMalfunctions() {
		List<Malfunction> result = new CopyOnWriteArrayList<Malfunction>();
		for (Malfunction malfunction : malfunctions) {
			if (!malfunction.isEVARepairDone())
				result.add(malfunction);
		}
		Collections.sort(result, new MalfunctionSeverityComparator());
		return result;
	}

	/**
	 * Select a malfunction randomly to the unit, based on the affected scope.
	 * 
	 * @param actor
	 */
	private boolean selectMalfunction(MissionMember actor) {
		boolean result = false;
		// Clones a malfunction and determines repair parts
		Malfunction malfunction = factory.pickAMalfunction(scopes);
		if (malfunction != null) {
			addMalfunction(malfunction, true, actor);
			result = true;
		}

		return result;
	}

	/**
	 * Triggers a particular malfunction (used by VehicleChatUtils)
	 * 
	 * @param {@link Malfunction}
	 * @param value
	 */
	public Malfunction triggerMalfunction(MalfunctionMeta m, boolean registerEvent) {
		Malfunction malfunction = new Malfunction(factory.getNewIncidentNum(), m);

		addMalfunction(malfunction, registerEvent, null);

		return malfunction;
	}
	
	/**
	 * Adds a malfunction to the unit.
	 * 
	 * @param malfunction   the malfunction to add.
	 * @param registerEvent
	 * @param actor
	 */
	private void addMalfunction(Malfunction malfunction, boolean registerEvent, MissionMember actor) {
		malfunctions.add(malfunction);
		numberMalfunctions++;
		
		getUnit().fireUnitUpdate(UnitEventType.MALFUNCTION_EVENT, malfunction);

		if (registerEvent) {
			registerAMalfunction(malfunction, actor);
		} 


		// Register the failure of the Parts involved
		Map<Integer, Integer> parts = malfunction.getRepairParts();
		Set<Integer> partSet = parts.keySet();

		for (Integer p : partSet) {
			int num = parts.get(p);
	
			Inventory inv = entity.getAssociatedSettlement().getInventory();
			// Add tracking demand
			inv.addItemDemandTotalRequest(p, num);
			inv.addItemDemand(p, num);
			
			// Compute the new reliability and failure rate for this malfunction
			Part part = ItemResourceUtil.findItemResource(p);
			String part_name = part.getName();

			if (part_name.equalsIgnoreCase("decontamination kit") || part_name.equalsIgnoreCase("airleak patch")
					|| part_name.equalsIgnoreCase("fire extinguisher")) {
				// NOTE : they do NOT contribute to the malfunctions and are tools to fix the
				// malfunction and therefore do NOT need to change their reliability.
				return;
			}

//			double old_rel = part.getReliability();
//			double old_prob = malfunctionConfig.getRepairPartProbability(malfunctionName, part_name);
//			double old_failure = (100 - old_rel) * old_prob / 100D;
//			double old_mal_probl_failure = malfunction.getProbability();
//			double old_MTBF = part.getMTBF();

			// Increment the number of failure for this Part
			part.setFailure(num);

			// Need to calculate the new probability for the whole MalfunctionMeta object
			// String name = p.getName();
//			double new_rel = part.getReliability();
//			double new_prob = malfunctionConfig.getRepairPartProbability(malfunctionName, part_name);
//			double new_failure = (100 - new_rel) * new_prob / 100D;
//			double new_mal_prob_failure = (old_mal_probl_failure + new_failure) / 2.0;
//			double new_MTBF = part.getMTBF();
//			
//			logger.warning("          *** Part : " + part_name + " ***");
//			
//			logger.warning(" (1).   Reliability : " + addWhiteSpace(Math.round(old_rel * 1000.0) / 1000.0 + " %") 
//							+ "  -->  " + Math.round(new_rel * 1000.0) / 1000.0 + " %");
//
//			logger.warning(" (2).  Failure Rate : " + addWhiteSpace(Math.round(old_failure * 1000.0) / 1000.0 + " %") 
//							+ "  -->  " + Math.round(new_failure * 1000.0) / 1000.0 + " %");
//
//			logger.warning(" (3).          MTBF : " + addWhiteSpace(Math.round(old_MTBF * 1000.0) / 1000.0 + " hr") 
//							+ "  -->  " + Math.round(new_MTBF * 1000.0) / 1000.0 + " hr");
//
//			logger.warning("          *** Malfunction : " + malfunctionName + " ***");
//			
//			logger.warning(" (4).   Probability : " + addWhiteSpace(Math.round(old_mal_probl_failure * 1000.0) / 1000.0 + " %") 
//							+ "  -->  " + Math.round(new_mal_prob_failure * 1000.0) / 1000.0 + " %");
			
			// Should be on MalfunctionMeta ??
			//malfunction.setProbability(new_mal_prob_failure);

		}

		issueMedicalComplaints(malfunction);

	}

	/**
	 * Sets up a malfunction event
	 * 
	 * @param malfunction
	 * @param actor
	 */
	private void registerAMalfunction(Malfunction malfunction, MissionMember actor) {
		String malfunctionName = malfunction.getName();

		Settlement settlement = entity.getAssociatedSettlement();
		String loc0 = entity.getNickName();
		String loc1 = entity.getImmediateLocation();
		EventType eventType = EventType.MALFUNCTION_PARTS_FAILURE;
		
		String offender = "None";
		String task = "N/A";
		if (actor != null) {
			task = actor.getTaskDescription();
			offender = actor.getName();
			
			if (actor instanceof Person) {
				eventType = EventType.MALFUNCTION_HUMAN_FACTORS;
			}
			else {
				eventType = EventType.MALFUNCTION_PROGRAMMING_ERROR;				
			}
		}
		
		// Meteorite override the event type
		if (malfunction.getName().equals(MalfunctionFactory.METEORITE_IMPACT_DAMAGE)) {
			eventType = EventType.MALFUNCTION_ACT_OF_GOD;
		}
		
		HistoricalEvent newEvent = new MalfunctionEvent(eventType,
								malfunction, malfunctionName, task, offender, loc0, loc1, settlement.getName());
		eventManager.registerNewEvent(newEvent);

		LogConsolidated.log(logger, Level.WARNING, 0, sourceName,
									"[" + loc1 + "] " + loc0 + " had '" + malfunction.getName() 
									+ "'. Probable Cause : " + eventType.getName()
									+ (actor != null ? "caused by " + offender : ""));
	}

	/**
	 * Time passing for tracking the wear and tear condition while the unit is being actively used.
	 * 
	 * @param time amount of time passing (in millisols)
	 */
	public void activeTimePassing(double time) {

		effectiveTimeSinceLastMaintenance += time;

		// Add time to wear condition.
		wearCondition = wearCondition - (time / wearLifeTime) * 100D;
		if (wearCondition < 0D)
			wearCondition = 0D;

		double maintFactor = effectiveTimeSinceLastMaintenance * MAINTENANCE_MALFUNCTION_FACTOR;
		double wearFactor = (100D - wearCondition) / 100D * WEAR_MALFUNCTION_FACTOR + 1D;
		double chance = time * maintFactor * wearFactor;

		// Check for malfunction due to lack of maintenance and wear condition.
		if (RandomUtil.lessThanRandPercent(chance)) {
			int solsLastMaint = (int) (effectiveTimeSinceLastMaintenance / 1000D);
			// Reduce the max possible health condition
//			maxCondition = (wearCondition + 400D)/500D; 
			LogConsolidated.log(logger, Level.WARNING, 1000, sourceName,
					"[" + entity.getImmediateLocation() + "] " + entity.getNickName() 
					+ " experienced a malfunction due to wear-and-tear.  "
					+ "# of sols since last check-up: " + solsLastMaint + ".   Condition: " + Math.round(wearCondition*10.0)/10.0
					+ " %.");

			// TODO: how to connect maintenance to field reliability statistics when selecting a malfunction ?
			selectMalfunction(null);
		}
	}

	/**
	 * Time passing for unit.
	 * 
	 * @param time amount of time passing (in millisols)
	 */
	@Override
	public boolean timePassing(ClockPulse pulse) {
		double time = pulse.getElapsed();
		
		// Check if life support modifiers are still in effect.
//		setLifeSupportModifiers(time);

		// Check if resources is still draining
		depleteResources(time);
		
		checkFixedMalfunction();

		// Add time passing.
		timeSinceLastMaintenance += time;
		
		return true;
	}

	/**
	 * Resets one or more flow modifier
	 * 
	 * @param type
	 */
	private void resetModifiers(int type) {
		// compare from previous modifier
//		logger.info("Reseting modifiers type " + type );
		if (type == 0) {
			oxygenFlowModifier = 100D;
			LogConsolidated.log(logger, Level.WARNING, 5_000, sourceName,
					"[" + entity.getLocale() + "] The oxygen flow retrictor had been fixed in "
					+ entity.getNickName());
		}
//		
//		else if (type == 1) {
//			waterFlowModifier = 100D;
//			LogConsolidated.log(Level.WARNING, 0, sourceName,
//					"[" + entity.getLocale() + "] The water flow retrictor has been fixed in "
//					+ entity.getImmediateLocation(), null);
//		}
//		
//		else if (type == 2) {
//			airPressureModifier = 100D;
//			LogConsolidated.log(Level.WARNING, 0, sourceName,
//				"[" + entity.getLocale() + "] The air pressure regulator has been fixed in "
//				+ entity.getImmediateLocation(), null);
//		}
//		
//		else if (type == 3) {
//			temperatureModifier = 100D;
//			LogConsolidated.log(Level.WARNING, 0, sourceName,
//					"[" + entity.getLocale() + "] The temperature regulator has been fixed in "
//					+ entity.getImmediateLocation(), null);
//			
//		}
	}
	
	/**
	 * Checks if any malfunctions have been fixed
	 * 
	 * @param time
	 */
	private void checkFixedMalfunction() { 
		Collection<Malfunction> fixedMalfunctions = new ArrayList<>();

		// Check if any malfunctions are fixed.
		if (hasMalfunction()) {
			for (Malfunction m : malfunctions) {

				if (m.isFixed()) {
					fixedMalfunctions.add(m);
				}
			}
		}

		for (Malfunction m : fixedMalfunctions) {
			// Reset the modifiers
			Map<String, Double> effects = m.getLifeSupportEffects();
			if (!effects.isEmpty()) {
				if (effects.containsKey(OXYGEN))
					resetModifiers(0);
//					if (effects.containsKey(WATER))
//						resetModifiers(1);
//					if (effects.containsKey(PRESSURE))
//						resetModifiers(2);
//					if (effects.containsKey(TEMPERATURE))
//						resetModifiers(3);
			}
				
			getUnit().fireUnitUpdate(UnitEventType.MALFUNCTION_EVENT, m);

			String chiefRepairer = m.getMostProductiveRepairer();
				
			HistoricalEvent newEvent = new MalfunctionEvent(EventType.MALFUNCTION_FIXED, m,
					m.getName(), "Repairing", chiefRepairer, entity.getImmediateLocation(),
					entity.getNickName(), entity.getLocale());

			eventManager.registerNewEvent(newEvent);
				
			String loc1 = "";
			if (entity.getImmediateLocation().toLowerCase().contains("outside"))
				loc1 = "outside.";
			else
				loc1 = "in " + entity.getImmediateLocation() + ".";
			
			LogConsolidated.log(logger, Level.WARNING, 0, sourceName,
					"[" + entity.getLocale() + "] The malfunction '" + m.getName() + "' had been dealt with "
					+ loc1);
		
			// Remove the malfunction
			malfunctions.remove(m);				
		}
	}
	
	/**
	 * Determine life support modifiers for given time.
	 * 
	 * @param time amount of time passing (in millisols)
	 */
	public void setLifeSupportModifiers(double time) {

		double tempOxygenFlowModifier = 0D;
//		double tempWaterFlowModifier = 0D;
//		double tempAirPressureModifier = 0D;
//		double tempTemperatureModifier = 0D;

		// Make any life support modifications.
		if (hasMalfunction()) {
			for (Malfunction malfunction : malfunctions) {
				if (!malfunction.isFixed()) {
					Map<String, Double> effects = malfunction.getLifeSupportEffects();
					if (effects.get(OXYGEN) != null)
						tempOxygenFlowModifier += effects.get(OXYGEN) * (100D - malfunction.getPercentageFixed())/100D;
//					if (effects.get(WATER) != null)
//						tempWaterFlowModifier += effects.get(WATER) * (100D - malfunction.getPercentageFixed())/100D;
//					if (effects.get(PRESSURE) != null)
//						tempAirPressureModifier += effects.get(PRESSURE) * (100D - malfunction.getPercentageFixed())/100D;
//					if (effects.get(TEMPERATURE) != null)
//						tempTemperatureModifier += effects.get(TEMPERATURE) * (100D - malfunction.getPercentageFixed())/100D;
				}
			}

			if (tempOxygenFlowModifier < 0D) {
				oxygenFlowModifier += tempOxygenFlowModifier * time ;
				if (oxygenFlowModifier < 0)
					oxygenFlowModifier = 0;
				LogConsolidated.log(logger, Level.WARNING, 20_000, sourceName,
						"[" + getUnit().getLocationTag().getLocale() + "] Oxygen flow restricted to "
								+ Math.round(oxygenFlowModifier*10.0)/10.0 + "% capacity in " 
								+ getUnit().getLocationTag().getImmediateLocation()+ ".", null);
			} 
//
//			if (tempWaterFlowModifier < 0D) {
//				waterFlowModifier += tempWaterFlowModifier * time;
//				if (waterFlowModifier < 0)
//					waterFlowModifier = 0;
//				LogConsolidated.log(Level.WARNING, 20_000, sourceName,
//						"[" + getUnit().getLocationTag().getLocale() + "] Water flow restricted to "
//								+ Math.round(waterFlowModifier*10.0)/10.0 + "% capacity in " + getUnit().getLocationTag().getImmediateLocation() + ".", null);
//			} 
//
//			if (tempAirPressureModifier < 0D) {
//				airPressureModifier += tempAirPressureModifier * time;
//				if (airPressureModifier < 0)
//					airPressureModifier = 0;
//				LogConsolidated.log(Level.WARNING, 20_000, sourceName,
//						"[" + getUnit().getLocationTag().getLocale() + "] Air pressure regulator malfunctioned at "
//								+ Math.round(airPressureModifier*10.0)/10.0 + "% capacity in " + getUnit().getLocationTag().getImmediateLocation() + ".", null);
//			} 
//
//			// temp mod can be above 0 or below zero
//			if (tempTemperatureModifier != 0D) {
//				temperatureModifier += tempTemperatureModifier * time;
//				if (temperatureModifier < 0)
//					temperatureModifier = 0;
//				LogConsolidated.log(Level.WARNING, 20_000, sourceName,
//						"[" + getUnit().getLocationTag().getLocale() + "] Temperature regulator malfunctioned at "
//								+ Math.round(temperatureModifier*10.0)/10.0 + "% capacity in " + getUnit().getLocationTag().getImmediateLocation() + ".", null);
//			}
		}
	}

	/**
	 * Depletes resources due to malfunctions.
	 * 
	 * @param time amount of time passing (in millisols)
	 * @throws Exception if error depleting resources.
	 */
	private void depleteResources(double time) {

		if (hasMalfunction()) {
			for (Malfunction malfunction : malfunctions) {
				if (!malfunction.isFixed() && !malfunction.isEmergencyRepairDone()) {
					Map<Integer, Double> effects = malfunction.getResourceEffects();
					Iterator<Integer> i2 = effects.keySet().iterator();
					while (i2.hasNext()) {
						Integer resource = i2.next();
						double amount = effects.get(resource);
						double amountDepleted = amount * time;
						Inventory inv = entity.getInventory();
						double amountStored = inv.getAmountResourceStored(resource, false);

						if (amountStored < amountDepleted) {
							amountDepleted = amountStored;
						}
						if (amountDepleted >= 0) {
							inv.retrieveAmountResource(resource, amountDepleted);
							LogConsolidated.log(logger, Level.WARNING, 15_000, sourceName,
									"[" + getUnit().getLocationTag().getLocale() + "] Leaking "
											+ Math.round(amountDepleted*100.0)/100.0 + " of  " 
											+ ResourceUtil.findAmountResource(resource) 
											+ " in " + getUnit().getLocationTag().getImmediateLocation()+ ".");
						}
					}
				}
			}
		}
	}

	/**
	 * Creates a series of related malfunctions
	 * 
	 * @param r the robot who triggers the malfunction
	 */
	public void createASeriesOfMalfunctions(Robot r) {
		createASeriesOfMalfunctions(null, r);
	}
	
	/**
	 * Creates a series of related malfunctions
	 * @param location the place of accident
	 * @param r the robot who triggers the malfunction
	 */
	public void createASeriesOfMalfunctions(String location, Robot r) {
		determineNumOfMalfunctions(location, SCORE_DEFAULT, r);
	}

	/**
	 * Creates a series of related malfunctions
	 * 
	 * @param p the person who triggers the malfunction
	 */
	public void createASeriesOfMalfunctions(Person p) {
		createASeriesOfMalfunctions(null, p);
	}
	
	/**
	 * Creates a series of related malfunctions
	 * 
	 * @param location the place of accident
	 * @param p the person who triggers the malfunction
	 */
	public void createASeriesOfMalfunctions(String location, Person p) {
		int nervousness = p.getMind().getTraitManager().getPersonalityTrait(PersonalityTraitType.NEUROTICISM);
		determineNumOfMalfunctions(location, nervousness, p);
	}

	/**
	 * Determines the numbers of malfunctions.
	 * 
	 * @param type  the type of malfunction
	 * @param s     the place of accident
	 * @param actor the person/robot who triggers the malfunction
	 */
	private void determineNumOfMalfunctions(String location, int score, MissionMember actor) {
		// Multiple malfunctions may have occurred.
		// 50% one malfunction, 25% two etc.
		boolean hasMal = false;
		boolean done = false;
		double chance = 100D;
		double mod = (double)score / SCORE_DEFAULT;
		while (!done) {
			if (RandomUtil.lessThanRandPercent(chance)) {
				hasMal = selectMalfunction(actor);
				chance = chance / 3D * mod;
			} else {
				done = true;
			}
		}

		if (hasMal) {
			logger.warning("[" + entity.getLocale() + "] " + actor.getName() + " had reported an incident.");

			String aType;
			String in = "";
			if (location != null) {
				aType = "Type-I";
				in = " in " + location;
			}
			else {
				aType = "Type-II";
			}

			// More generic simplifed log message
			LogConsolidated.log(logger, Level.WARNING, 3000, sourceName,
				"[" + entity.getLocale() + "] A " + aType + " accident occurred with " 
						+ entity.getNickName() + " caused by " + actor.getName() + in + ".");
			
			// Add stress to people affected by the accident.
			Collection<Person> people = entity.getAffectedPeople();
			Iterator<Person> i = people.iterator();
			while (i.hasNext()) {
				PhysicalCondition condition = i.next().getPhysicalCondition();
				condition.setStress(condition.getStress() + PhysicalCondition.ACCIDENT_STRESS);
			}
		}
	}

	/**
	 * Gets the time since last maintenance on entity.
	 * 
	 * @return time (in millisols)
	 */
	public double getTimeSinceLastMaintenance() {
		return timeSinceLastMaintenance;
	}

	/**
	 * Gets the time the entity has been actively used since its last maintenance.
	 * 
	 * @return time (in millisols)
	 */
	public double getEffectiveTimeSinceLastMaintenance() {
		return effectiveTimeSinceLastMaintenance;
	}

	/**
	 * Gets the required work time for maintenance for the entity.
	 * 
	 * @return time (in millisols)
	 */
	public double getMaintenanceWorkTime() {
		return maintenanceWorkTime;
	}

	/**
	 * Gets the work time completed on maintenance.
	 * 
	 * @return time (in millisols)
	 */
	public double getMaintenanceWorkTimeCompleted() {
		return maintenanceTimeCompleted;
	}

	/**
	 * Add work time to maintenance.
	 * 
	 * @param time (in millisols)
	 */
	public void addMaintenanceWorkTime(double time) {
		maintenanceTimeCompleted += time;
		if (maintenanceTimeCompleted >= maintenanceWorkTime) {
			maintenanceTimeCompleted = 0D;
			timeSinceLastMaintenance = 0D;
			effectiveTimeSinceLastMaintenance = 0D;
			determineNewMaintenanceParts();
			numberMaintenances++;
		}
	}

	/**
	 * Issues any necessary medical complaints.
	 * 
	 * @param malfunction the new malfunction
	 */
	private void issueMedicalComplaints(Malfunction malfunction) {

		// Determine medical complaints for each malfunction.
		for (Entry<ComplaintType, Double> impact : malfunction.getMedicalComplaints().entrySet()) {
			// Replace the use of String name with ComplaintType
			Complaint complaint = medic.getComplaintByName(impact.getKey());
			if (complaint != null) {
				double probability = impact.getValue();
				
				// Get people who can be affected by this malfunction.
				Iterator<Person> i2 = entity.getAffectedPeople().iterator();
				while (i2.hasNext()) {
					Person person = i2.next();
					if (RandomUtil.lessThanRandPercent(probability)) {
						person.getPhysicalCondition().addMedicalComplaint(complaint);
						person.fireUnitUpdate(UnitEventType.ILLNESS_EVENT);
					}
				}
			}
		}
	}

	/**
	 * Gets the oxygen flow modifier.
	 * 
	 * @return modifier
	 */
	public double getOxygenFlowModifier() {
		return oxygenFlowModifier;
	}
//
//	/**
//	 * Gets the water flow modifier.
//	 * 
//	 * @return modifier
//	 */
//	public double getWaterFlowModifier() {
//		return waterFlowModifier;
//	}
//
//	/**
//	 * Gets the air flow modifier.
//	 * 
//	 * @return modifier
//	 */
//	public double getAirPressureModifier() {
//		return airPressureModifier;
//	}
//
//	/**
//	 * Gets the temperature modifier.
//	 * 
//	 * @return modifier
//	 */
//	public double getTemperatureModifier() {
//		return temperatureModifier;
//	}

	/**
	 * Gets the unit associated with this malfunctionable.
	 * 
	 * @return associated unit.
	 * @throws Exception if error finding associated unit.
	 */
	public Unit getUnit() {
		if (entity instanceof Unit)
			return (Unit) entity;
		else if (entity instanceof Building)
			return ((Building) entity).getSettlement();
		else
			throw new IllegalStateException("Could not find unit associated with malfunctionable.");
	}

	/**
	 * Determines a new set of required maintenance parts.
	 */
	private void determineNewMaintenanceParts() {
		if (partsNeededForMaintenance == null)
			partsNeededForMaintenance = new ConcurrentHashMap<>();
		partsNeededForMaintenance.clear();

		for(MaintenanceScope maintenance : simconfig.getPartConfiguration().getMaintenance(scopes)) {
			if (RandomUtil.lessThanRandPercent(maintenance.getProbability())) {
				int number = RandomUtil.getRandomRegressionInteger(maintenance.getMaxNumber());
				int id = maintenance.getPart().getID();
				if (partsNeededForMaintenance.containsKey(maintenance.getPart().getID())) {
						number += partsNeededForMaintenance.get(id);
				}
				partsNeededForMaintenance.put(id, number);
			}	
		}
		

	}

	/**
	 * Gets the parts needed for maintenance on this entity.
	 * 
	 * @return map of parts and their number.
	 */
	public Map<Integer, Integer> getMaintenanceParts() {
		if (partsNeededForMaintenance == null)
			partsNeededForMaintenance = new ConcurrentHashMap<>();
		return new ConcurrentHashMap<>(partsNeededForMaintenance);
	}

	/**
	 * Adds a number of a part to the entity for maintenance.
	 * 
	 * @param part   the part.
	 * @param number the number used.
	 */
	public void maintainWithParts(Integer part, int number) {
		if (part == null)
			throw new IllegalArgumentException("part is null");
		if (partsNeededForMaintenance.containsKey(part)) {
			int numberNeeded = partsNeededForMaintenance.get(part);
			if (number > numberNeeded)
				throw new IllegalArgumentException(
						"number " + number + " is greater that number of parts needed: " + numberNeeded);
			else {
				numberNeeded -= number;
				if (numberNeeded > 0)
					partsNeededForMaintenance.put(part, numberNeeded);
				else
					partsNeededForMaintenance.remove(part);
			}
		} else
			throw new IllegalArgumentException("Part " + part + " is not needed for maintenance.");
	}

	/**
	 * Gets the repair part probabilities for the malfunctionable.
	 * 
	 * @return maps of parts and probable number of parts needed per malfunction.
	 * @throws Exception if error finding probabilities.
	 */
	public Map<Integer, Double> getRepairPartProbabilities() {
		return factory.getRepairPartProbabilities(scopes);
	}

	/**
	 * Gets the repair part probabilities for maintenance.
	 * 
	 * @return maps of parts and probable number of parts in case of maintenance.
	 * @throws Exception if error finding probabilities.
	 */
	public Map<Integer, Double> getMaintenancePartProbabilities() {
		return factory.getMaintenancePartProbabilities(scopes);
	}
		

	/**
	 * Gets the estimated number of malfunctions this entity will have in one
	 * Martian orbit.
	 * 
	 * @return number of malfunctions.
	 */
	public double getEstimatedNumberOfMalfunctionsPerOrbit() {
		double avgMalfunctionsPerOrbit = 0D;

		double totalTimeMillisols = MarsClock.getTimeDiff(currentTime, masterClock.getInitialMarsTime());
		double totalTimeOrbits = totalTimeMillisols / 1000D / MarsClock.SOLS_PER_ORBIT_NON_LEAPYEAR;

		if (totalTimeOrbits < 1D) {
			avgMalfunctionsPerOrbit = (numberMalfunctions + ESTIMATED_MALFUNCTIONS_PER_ORBIT) / 2D;
		} else {
			avgMalfunctionsPerOrbit = numberMalfunctions / totalTimeOrbits;
		}

		int orbit = currentTime.getOrbit();
		if (orbitCache != orbit) {
			orbitCache = orbit;
			numberMalfunctions = 0;
		}

		return avgMalfunctionsPerOrbit;
	}

	/**
	 * Gets the estimated number of periodic maintenances this entity will have in
	 * one Martian orbit.
	 * 
	 * @return number of maintenances.
	 */
	public double getEstimatedNumberOfMaintenancesPerOrbit() {
		double avgMaintenancesPerOrbit = 0D;


			double totalTimeMillisols = MarsClock.getTimeDiff(currentTime, masterClock.getInitialMarsTime());
			double totalTimeOrbits = totalTimeMillisols / 1000D / MarsClock.SOLS_PER_ORBIT_NON_LEAPYEAR;

			if (totalTimeOrbits < 1D) {
				avgMaintenancesPerOrbit = (numberMaintenances + ESTIMATED_MAINTENANCES_PER_ORBIT) / 2D;
			} else {
				avgMaintenancesPerOrbit = numberMaintenances / totalTimeOrbits;
			}
//		}

		return avgMaintenancesPerOrbit;
	}

	/**
	 * Inner class comparator for sorting malfunctions my highest severity to
	 * lowest.
	 */
	private static class MalfunctionSeverityComparator implements Comparator<Malfunction>, Serializable {
		/** default serial id. */
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(Malfunction malfunction1, Malfunction malfunction2) {
			int severity1 = malfunction1.getSeverity();
			int severity2 = malfunction2.getSeverity();
			if (severity1 > severity2)
				return -1;
			else if (severity1 == severity2)
				return 0;
			else
				return 1;
		}
	}

	/**
	 * Get the percentage representing the malfunctionable's condition from wear &
	 * tear. 100% = new condition 0% = worn out condition.
	 * 
	 * @return wear condition.
	 */
	public double getWearCondition() {
		return wearCondition;
	}

	/**
	 * Gets the multiplying modifier for the chance of an accident due to the
	 * malfunctionable entity's wear condition.
	 * 
	 * @return accident modifier.
	 */
	public double getWearConditionAccidentModifier() {
		return (100D - wearCondition) / 100D * WEAR_ACCIDENT_FACTOR + 1D;
	}

	/**
	 * initializes instances after loading from a saved sim
	 * 
	 * @param c0 {@link MasterClock}
	 * @param c1 {@link MarsClock}
	 * @param mf {@link MalfunctionFactory}
	 * @param m {@link MedicalManager}	 
	 * @param e {@link HistoricalEventManager}
	 */
	public static void initializeInstances(MasterClock c0, MarsClock c1, MalfunctionFactory mf, MedicalManager m, HistoricalEventManager e) {
		masterClock = c0;
		currentTime = c1;
		sim = Simulation.instance();
		simconfig = SimulationConfig.instance();
		factory = mf;
		medic = m;
		eventManager = e;
	}
	
	/**
	 * Prepare object for garbage collection.
	 */
	public void destroy() {
		entity = null;
		scopes.clear();
		scopes = null;
		malfunctions.clear();
		malfunctions = null;
		if (partsNeededForMaintenance != null) {
			partsNeededForMaintenance.clear();
		}
		partsNeededForMaintenance = null;
	}

}
