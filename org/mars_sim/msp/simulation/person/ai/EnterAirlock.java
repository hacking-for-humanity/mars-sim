/**
 * Mars Simulation Project
 * EnterAirlock.java
 * @version 2.75 2003-02-07
 * @author Scott Davis
 */

package org.mars_sim.msp.simulation.person.ai;

import org.mars_sim.msp.simulation.*;
import org.mars_sim.msp.simulation.person.*;
import org.mars_sim.msp.simulation.structure.*;
import org.mars_sim.msp.simulation.vehicle.*;
import org.mars_sim.msp.simulation.equipment.*;
import java.io.Serializable;

/** 
 * The EnterAirlock class is a task for entering a airlock from an EVA operation. 
 */
class EnterAirlock extends Task implements Serializable {

    // Data members
    private Airlockable entity; // The entity to be entered.

    /** 
     * Constructs a EnterAirlock object
     * @param person the person to perform the task
     * @param mars the virtual Mars
     * @param entity the entity to be entered
     */
    public EnterAirlock(Person person, Mars mars, Airlockable entity) {
        super("Entering airlock from EVA", person, false, mars);

        // Initialize data members
        description = "Entering " + entity.getName() + " from EVA";
        this.entity = entity;

        // System.out.println(person.getName() + " is starting to enter " + entity.getName());
    }

    /**
     * Constructs a EnterAirlock object without an airlockable entity.
     * @param person the person to perform the task.
     * @param mars the virtual Mars
     */
    public EnterAirlock(Person person, Mars mars) {
        super("Entering airlock from EVA", person, false, mars);

        // System.out.println("Enter Airlock due to strange situation.");
        // System.out.println("Illness: " + person.getPhysicalCondition().getHealthSituation());
        // System.out.println("Performance Rating: " + person.getPerformanceRating());
	
        // Determine airlockable entity from other people on mission.
        if (person.getMind().getMission() != null) {
            PersonIterator i = person.getMind().getMission().getPeople().iterator();
            while (i.hasNext() && (entity == null)) {
                Person p = i.next();
                if (p != person) {
                    if (p.getSettlement() != null) entity = p.getSettlement();
                    else if (p.getVehicle() != null) entity = (Rover) p.getVehicle();
                }
            }
        }

        // If not look for any settlements at person's location.
        if (entity == null) {
            SettlementIterator i = mars.getUnitManager().getSettlements().iterator();
            while (i.hasNext() && (entity == null)) {
                Settlement settlement = i.next();
                if (person.getCoordinates().equals(settlement.getCoordinates())) 
                entity = settlement;
            }
        }

        // If not look for any rovers at person's location.
        if (entity == null) {
            VehicleIterator i = mars.getUnitManager().getVehicles().iterator();
            while (i.hasNext() && (entity == null)) {
                Vehicle vehicle = i.next();
            	if (vehicle instanceof Rover) {
                    Rover rover = (Rover) vehicle;
            	    if (person.getCoordinates().equals(rover.getCoordinates())) 
                    entity = rover;
                }
            }
        }

        // If still no airlockable entity, end task.
        if (entity == null) done = true;
        else description = "Entering " + entity.getName() + " from EVA";
    }
   
    /** Returns the weighted probability that a person might perform this task.
     *  It should return a 0 if there is no chance to perform this task given the person and his/her situation.
     *  @param person the person to perform the task
     *  @param mars the virtual Mars
     *  @return the weighted probability that a person might perform this task
     */
    public static double getProbability(Person person, Mars mars) {
        double result = 0D;

        if (person.getLocationSituation().equals(Person.OUTSIDE)) result = 500D;

        return result;
    }
    
    /** 
     * Performs this task for the given amount of time.
     * @param time the amount of time to perform this task (in millisols)
     * @return amount of time remaining after finishing with task (in millisols)
     */
    double performTask(double time) {
        double timeLeft = super.performTask(time);
        if (subTask != null) return timeLeft;

        Airlock airlock = entity.getAirlock();
	
        // If person is in airlock, wait around.
        if (airlock.inAirlock(person)) {
            // Make sure airlock is activated.
            airlock.activateAirlock();
        }
        else {
            // If person is outside, try to enter airlock.
            if (person.getLocationSituation().equals(Person.OUTSIDE)) {
                if (airlock.isOuterDoorOpen()) airlock.enterAirlock(person, false);
            	else airlock.requestOpenDoor();
            }
            else {
                // If person is inside, put stuff away and end task.
                putAwayEVASuit();
            	done = true;
            }
        }

        return 0D;
    }

    /**
     * Puts the person's EVA suite back into the entity's inventory.
     * EVA Suit is refilled with oxygen and water from the entity's inventory.
     */
    private void putAwayEVASuit() {
       
        EVASuit suit = (EVASuit) person.getInventory().findUnit(EVASuit.class);
        Inventory suitInv = suit.getInventory();
        Inventory personInv = person.getInventory();
        Inventory entityInv = ((Unit) entity).getInventory();

        // Refill oxygen in suit from entity's inventory. 
        double neededOxygen = suitInv.getResourceRemainingCapacity(Resource.OXYGEN);
        double takenOxygen = entityInv.removeResource(Resource.OXYGEN, neededOxygen);
        // System.out.println(person.getName() + " refilling EVA suit with " + takenOxygen + " oxygen.");
        suitInv.addResource(Resource.OXYGEN, takenOxygen);

        // Refill water in suit from entity's inventory.
        double neededWater = suitInv.getResourceRemainingCapacity(Resource.WATER);
        double takenWater = entityInv.removeResource(Resource.WATER, neededWater);
        // System.out.println(person.getName() + " refilling EVA suit with " + takenWater + " water.");
        suitInv.addResource(Resource.WATER, takenWater);

        // Return suit to entity's inventory.
        // System.out.println(person.getName() + " putting away EVA suit into " + entity.getName());
        personInv.takeUnit(suit, (Unit) entity);
    }

    /**
     * Checks if a person can enter an airlock from an EVA.
     * @param person the person trying to enter
     * @param entity the entity to be entered 
     * @return true if person can enter the entity 
     */
    public static boolean canEnterAirlock(Person person, Airlockable entity) {
        return true;
    }
}
