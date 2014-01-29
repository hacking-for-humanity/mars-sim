/**
 * Mars Simulation Project
 * RoverMission.java
 * @version 3.06 2014-01-29
 * @author Scott Davis
 */
package org.mars_sim.msp.core.person.ai.mission;

import org.mars_sim.msp.core.Coordinates;
import org.mars_sim.msp.core.structure.Settlement;

import java.io.Serializable;

/**
 * A navigation point for travel missions.
 */
public class NavPoint implements Serializable {
	
	// Data members.
	private Coordinates location; // The location of the navpoint.
	private Settlement settlement; // The settlement at this navpoint.
	private String description; // The description of the navpoint.

	/**
	 * Constructor with location.
	 * @param location the location of the navpoint.
	 * @param description the navpoint description.
	 */
	public NavPoint(Coordinates location, String description) {
		if (location == null) throw new IllegalArgumentException("location is null");
		this.location = new Coordinates(location);
		this.description = description;
	}

	/**
	 * Constructor with location and settlement.
	 * @param location the location of the navpoint.
	 * @param settlement the settlement at the navpoint.
	 * @param description the navpoint description.
	 */
	public NavPoint(Coordinates location, Settlement settlement, String description) {
		this(location, description);
		if (settlement == null) throw new IllegalArgumentException("settlement is null");
		this.settlement = settlement;
	}
	
	/**
	 * Gets the location of this navpoint.
	 * @return the coordinate location.
	 */
	public Coordinates getLocation() {
		return new Coordinates(location);
	}
	
	/**
	 * Gets the description of the navpoint.
	 * @return description
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * Gets the settlement at the navpoint.
	 * @return the settlement or null if none.
	 */
	public Settlement getSettlement() {
		return settlement;
	}
	
	/**
	 * Checks if there is a settlement at this navpoint.
	 * @return true if settlement.
	 */
	public boolean isSettlementAtNavpoint() {
		return (settlement != null);
	}
	
	/**
	 * Checks if this NavPoint is the same as another object.
	 * @return true if the same navpoint.
	 */
	public boolean equals(Object object) {
		boolean result = false;
		if (object instanceof NavPoint) {
			NavPoint otherNavpoint = (NavPoint) object;
			if (getLocation().equals(otherNavpoint.getLocation())) {
				if (isSettlementAtNavpoint()) {
					if (settlement.equals(otherNavpoint.settlement)) result = true;
				}
				else result = true;
			}
		}
		return result;
	}
	
	/**
	 * Gets the hash code for this object.
	 * @return hash code.
	 */
	public int hashCode() {
		return getLocation().hashCode() * settlement.hashCode();
	}
}