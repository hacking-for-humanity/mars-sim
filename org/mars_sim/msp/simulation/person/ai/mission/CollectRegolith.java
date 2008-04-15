/**
 * Mars Simulation Project
 * CollectRegolith.java
 * @version 2.84 2008-04-14
 * @author Sebastien Venot
 */
package org.mars_sim.msp.simulation.person.ai.mission;

import java.util.Collection;
import java.util.List;

import org.mars_sim.msp.simulation.equipment.Bag;
import org.mars_sim.msp.simulation.person.Person;
import org.mars_sim.msp.simulation.resource.AmountResource;
import org.mars_sim.msp.simulation.structure.Settlement;
import org.mars_sim.msp.simulation.structure.goods.GoodsManager;
import org.mars_sim.msp.simulation.structure.goods.GoodsUtil;
import org.mars_sim.msp.simulation.vehicle.Rover;

/** 
 * The Exploration class is a mission to travel in a rover to several
 * random locations around a settlement and collect Regolith.
 */
public class CollectRegolith  extends CollectResourcesMission {

	// Default description.
	public static final String DEFAULT_DESCRIPTION = "Regolith Prospecting";
	
	// Amount of regolith to be gathered at a given site (kg). 
	private static final double SITE_GOAL = 500D;
	
	// Number of bags required for the mission. 
	public static final int REQUIRED_BAGS = 10;
	
	// Collection rate of regolith during EVA (kg/millisol).
	private static final double COLLECTION_RATE = 20D;
	
	// Number of collection sites.
	private static final int NUM_SITES = 1;
	
	// Minimum number of people to do mission.
	private final static int MIN_PEOPLE = 2;

	/**
	 * Constructor
	 * @param startingPerson the person starting the mission.
	 * @throws MissionException if problem constructing mission.
	 */
	public CollectRegolith (Person startingPerson) throws MissionException {
		
		// Use CollectResourcesMission constructor.
		super(DEFAULT_DESCRIPTION, startingPerson, 
			AmountResource.REGOLITH, SITE_GOAL, 
			COLLECTION_RATE, 
			Bag.class, REQUIRED_BAGS, 
			NUM_SITES, MIN_PEOPLE);
	}
	
    /**
     * Constructor with explicit data.
     * @param members collection of mission members.
     * @param startingSettlement the starting settlement.
     * @param regolithCollectionSites the sites to collect regolith.
     * @param rover the rover to use.
     * @param description the mission's description.
     * @throws MissionException if error constructing mission.
     */
    public CollectRegolith (Collection<Person> members, Settlement startingSettlement, 
    		List regolithCollectionSites, Rover rover, String description) 
    		throws MissionException {
    	
       	// Use CollectResourcesMission constructor.
    	super(description, members, startingSettlement, AmountResource.REGOLITH, 
    	      SITE_GOAL, COLLECTION_RATE, 
    	      Bag.class, REQUIRED_BAGS, regolithCollectionSites.size(), 
    	      1, rover, regolithCollectionSites);
    }
	
	/** 
	 * Gets the weighted probability that a given person would start this mission.
	 * @param person the given person
	 * @return the weighted probability
	 */
	public static double getNewMissionProbability(Person person) {

		double result = CollectResourcesMission.getNewMissionProbability(person, Bag.class, 
				REQUIRED_BAGS, MIN_PEOPLE, CollectRegolith.class);
		
		if (result > 0D) {
			// Factor the value of regolith at the settlement.
			GoodsManager manager = person.getSettlement().getGoodsManager();
			double value = manager.getGoodValuePerMass(GoodsUtil.getResourceGood(AmountResource.REGOLITH));
			result *= value * 10D;
			
			// Check if min number of EVA suits at settlement.
			if (VehicleMission.getNumberAvailableEVASuitsAtSettlement(person.getSettlement()) < MIN_PEOPLE) result = 0D;
		}
		
		return result;
	}
	
    /**
     * Gets the description of a collection site.
     * @param siteNum the number of the site.
     * @return description
     */
    protected String getCollectionSiteDescription(int siteNum) {
    	return "prospecting site";
    }
}