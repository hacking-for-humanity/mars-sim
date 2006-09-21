package org.mars_sim.msp.simulation;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.swingui.TestRunner;

/**
 * JUnit test suite
 */
public class AllJUnitTests extends TestCase {

	private static final Class thisClass = AllJUnitTests.class;

	/**
	 * Run all JUnit tests.
	 */
	public static void main(String[] args) {
		TestRunner.run(thisClass);
	}
	
	/**
	 * Collection of external test suites to be included in current testing.
	 */
	public static Test suite() {
		TestSuite suite = new TestSuite(thisClass);

		suite.addTestSuite(org.mars_sim.msp.simulation.TestInventory.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.equipment.JUnitTests.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.events.JUnitTests.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.malfunction.JUnitTests.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.person.JUnitTests.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.person.ai.JUnitTests.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.person.ai.mission.JUnitTests.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.person.ai.task.JUnitTests.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.person.medical.JUnitTests.class);
		suite.addTestSuite(org.mars_sim.msp.simulation.resource.TestAmountResourceStorage.class);
		suite.addTestSuite(org.mars_sim.msp.simulation.resource.TestAmountResourcePhaseStorage.class);
		suite.addTestSuite(org.mars_sim.msp.simulation.resource.TestAmountResourceTypeStorage.class);
		suite.addTestSuite(org.mars_sim.msp.simulation.resource.TestItemResource.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.structure.JUnitTests.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.structure.building.JUnitTests.class);
		//suite.addTestSuite(org.mars_sim.msp.simulation.vehicle.JUnitTests.class);

		return suite;
	}
	
	/**
	 * Every JUnit test suite needs at least one test.This one obviously does nothing.
	 * Any others begining with "test..." will be automatically included as well.
	 */
	public void testNothing() {
	}
}
