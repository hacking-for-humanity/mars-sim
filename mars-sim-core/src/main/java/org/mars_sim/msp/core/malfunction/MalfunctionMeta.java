/**
 * Mars Simulation Project
 * Malfunction.java
 * @version 3.1.2 2020-09-02
 * @author Scott Davis
 */

package org.mars_sim.msp.core.malfunction;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mars_sim.msp.core.person.health.ComplaintType;

/**
 * The MalfunctionMeta class represents a defintion of a malfunction in a vehicle, structure or
 * equipment.
 */
public class MalfunctionMeta implements Serializable {

	private static final long serialVersionUID = 1L;

	// Data members
	private int severity;

	private double probability;
	
	private String name;

	private Set<String> systems;
	private List<RepairPart> parts;
	private Map<Integer, Double> resourceEffects;
	private Map<String, Double> lifeSupportEffects;
	private Map<ComplaintType, Double> medicalComplaints;
	private Map<MalfunctionRepairWork, Double> repairEffort;

	/**
	 * Constructs a Malfunction object
	 * 
	 * @param name name of the malfunction
	 */
	public MalfunctionMeta(String name,int severity, double probability, Map<MalfunctionRepairWork,Double> repairEffort,
			Set<String> entities, Map<Integer, Double> resourceEffects,
			Map<String, Double> lifeSupportEffects, Map<ComplaintType, Double> medicalComplaints,
			List<RepairPart> parts) {

		// Initialize data members
		this.name = name;
		this.severity = severity;
		this.probability = probability;
		this.repairEffort = repairEffort;
		this.systems = entities;
		this.resourceEffects = resourceEffects;
		this.lifeSupportEffects = lifeSupportEffects;
		this.medicalComplaints = medicalComplaints;
		this.parts = parts;
	}


	
	/**
	 * Returns the name of the malfunction.
	 * 
	 * @return name of the malfunction
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Returns the severity level of the malfunction.
	 * 
	 * @return severity of malfunction (1 - 100)
	 */
	public int getSeverity() {
		return severity;
	}

	/**
	 * Returns the probability of failure of the malfunction
	 * 
	 * @return probability in %
	 */
	public double getProbability() {
		return probability;
	}

	public void setProbability(double p) {
		probability = p;
	}

	/**
	 * Returns the work time required to repair the malfunction.
	 * 
	 * @return work time (in millisols)
	 */
	public Map<MalfunctionRepairWork, Double> getRepairEffort() {
		return repairEffort;
	}

	/**
	 * Checks if a unit's scope strings have any matches with the malfunction's
	 * scope strings.
	 * 
	 * @return true if any matches
	 */
	public boolean isMatched(Collection<String> scopes) {

		if ((systems.size() > 0) && (scopes.size() > 0)) {
			for (String s : systems) {
				for (String u : scopes) {
					if (s.equalsIgnoreCase(u))
						return true;//result = true;
				}
			}
		}

		return false;
	}

	/**
	 * Gets the resource effects of the malfunction.
	 * 
	 * @return resource effects as name-value pairs in Map
	 */
	public Map<Integer, Double> getResourceEffects() {
		return resourceEffects;
	}

	/**
	 * Gets the life support effects of the malfunction.
	 * 
	 * @return life support effects as name-value pairs in Map
	 */
	public Map<String, Double> getLifeSupportEffects() {
		return lifeSupportEffects;
	}

	/**
	 * Gets the medical complaints produced by this malfunction and their
	 * probability of occurrence.
	 * 
	 * @return medical complaints as name-value pairs in Map
	 */
	public Map<ComplaintType, Double> getMedicalComplaints() {
		return medicalComplaints;
	}

	/**
	 * Get the parts required to fix this malfunction
	 * @return
	 */
	public List<RepairPart> getParts() {
		return parts;
	}
	
	/**
	 * Gets the string value for the object.
	 */
	public String toString() {
		return name;
	}

	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (this.getClass() != obj.getClass()) return false;
		MalfunctionMeta m = (MalfunctionMeta) obj;
		return this.name.equalsIgnoreCase(m.getName());
	}

	/**
	 * Gets the hash code for this object.
	 * 
	 * @return hash code.
	 */
	public int hashCode() {
		int hashCode = (int)(1 + severity);
		hashCode *= (1 + name.toLowerCase().hashCode());
		return hashCode;
	}
}