/**
 * Mars Simulation Project
 * MasterClock.java
 * @version 3.1.0 2017-01-12
 * @author Scott Davis
 */

package org.mars_sim.msp.core.time;

import org.mars_sim.msp.core.Simulation;
import org.mars_sim.msp.core.SimulationConfig;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The MasterClock represents the simulated time clock on virtual
 * Mars. Virtual Mars has only one master clock. The master clock
 * delivers a clock pulse the virtual Mars every second or so, which
 * represents a pulse of simulated time.  All actions taken with
 * virtual Mars and its units are synchronized with this clock pulse.
 * <p/>
 * Update: The pulse is now tied to the system clock. This means that each time
 * a timePulse is generated, it is the following length:
 * <p/>
 * (realworldseconds since last call ) * timeRatio
 * <p/>
 * update: with regard to pauses..
 * <p/>
 * they work. the sim will completely pause when setPause(true) is called, and will
 * resume with setPause(false);
 * However ! Do not make any calls to System.currenttimemillis(), instead use
 * uptimer.getuptimemillis(), as this is "shielded" from showing any passed time
 * while the game is paused. Thank you.
 */
public class MasterClock implements Serializable { // Runnable,

	/** default serial id. */
	static final long serialVersionUID = -1688463735489226493L;

	/** Initialized logger. */
	private static Logger logger = Logger.getLogger(MasterClock.class.getName());

	/** Clock thread sleep time (milliseconds) 40 milli secs --> 25Hz should be sufficient. */
	//private static final long TIME_BETWEEN_UPDATES = 40_000_000L; //in nanoseconds (25 FPS)
	//private static final int NO_DELAYS_PER_YIELD = 16;
	//private static final int MAX_FRAME_SKIPS = 10;

	// Data members
	/** Runnable flag. */
	private transient volatile boolean keepRunning;
	/** Pausing clock. */
	private transient volatile boolean isPaused = false;
	/** Simulation/real-time ratio. */
	private volatile double timeRatio = 0D;
	/** Simulation/real-time ratio. */
	private volatile double defaultTimeRatio = 0D;
	//private int count = 0;
	private long timeBetweenUpdates = 0L;
	private int noDelaysPerYield = 0;
	private int maxFrameSkips = 0;

	/** Flag for loading a new simulation. */
	private transient volatile boolean loadSimulation;
	/** Mode for saving a simulation. */
	private transient volatile int saveType;
	/** Flag for auto-saving a simulation. */
	//private transient volatile boolean autosaveSimulation;
	/** Flag for ending the simulation program. */
	private transient volatile boolean exitProgram;

	private long totalPulses = 1;
	private transient long elapsedlast;
	private transient long elapsedMilliseconds;

	/** Clock listeners. */
	private transient List<ClockListener> listeners;
	private transient List<ClockListenerTask> clockListenerTaskList =  new CopyOnWriteArrayList<>();

	//private double time_ratio;

	/** Martian Clock. */
	private MarsClock marsTime;
	/** Initial Martian time. */
	private MarsClock initialMarsTime;
	/** Earth Clock. */
	private EarthClock earthTime;
	/** Uptime Timer. */
	private UpTimer uptimer;
	/** The file to save or load the simulation. */
	private transient volatile File file;

	private ClockThreadTask clockThreadTask;

	private transient ThreadPoolExecutor clockListenerExecutor;

	private Simulation sim;
	private SimulationConfig config;

    /**
     * Constructor
     *
     * @throws Exception if clock could not be constructed.
     */
    public MasterClock() {
        //logger.info("MasterClock's constructor is on " + Thread.currentThread().getName() + " Thread");

        long t0 = System.nanoTime();

    	sim = Simulation.instance();
        // Initialize data members
        config = SimulationConfig.instance();

        // Create a Martian clock
        marsTime = new MarsClock(config.getMarsStartDateTime());
        initialMarsTime = (MarsClock) marsTime.clone();

        // Create an Earth clock
        earthTime = new EarthClock(config.getEarthStartDateTime());

        // Create an Uptime Timer
        uptimer = new UpTimer(this);

        // Create listener list.
        listeners = Collections.synchronizedList(new CopyOnWriteArrayList<ClockListener>());
        elapsedlast = uptimer.getUptimeMillis();
        elapsedMilliseconds = 0L;

        //setupClockListenerTask();
        clockThreadTask = new ClockThreadTask();

        // Setting the initial time ratio.
        double ratio = config.getSimulationTimeRatio();
        double ms = config.getTimeBetweenUpdates();

        //TODO: should also test the CPU speed to determine the TPS

        long t1 = System.nanoTime();
        long diff = (long) ((t1 - t0) / 1_000D);
        logger.info("CPU Benchmark 1 : " + diff + " ms");

        if (Simulation.NUM_THREADS == 1) {
        	defaultTimeRatio = ratio/8D;
        	setTimeRatio(defaultTimeRatio);
            setTimeBetweenUpdates(ms*16D);
        }
        else if (Simulation.NUM_THREADS == 2) {
        	defaultTimeRatio = ratio/8D;
        	setTimeRatio(defaultTimeRatio);
            setTimeBetweenUpdates(ms*12D);
        }
        else if (Simulation.NUM_THREADS <= 3) {
        	defaultTimeRatio = ratio/4D;
        	setTimeRatio(defaultTimeRatio);
            setTimeBetweenUpdates(ms*8D);
        }
        else if (Simulation.NUM_THREADS <= 4) {
           	defaultTimeRatio = ratio/4D;
        	setTimeRatio(defaultTimeRatio);
            setTimeBetweenUpdates(ms*4D);
        }
        else if (Simulation.NUM_THREADS <= 6) {
        	defaultTimeRatio = ratio/2D;
        	setTimeRatio(defaultTimeRatio);
            setTimeBetweenUpdates(ms*2D);
        }
        else if (Simulation.NUM_THREADS <= 8) {
        	defaultTimeRatio = ratio/2D;
        	setTimeRatio(defaultTimeRatio);
            setTimeBetweenUpdates(ms);
        }
        else if (Simulation.NUM_THREADS <= 12) {
        	defaultTimeRatio = ratio;
        	setTimeRatio(defaultTimeRatio);
            setTimeBetweenUpdates(ms);
        }
        else {
        	defaultTimeRatio = ratio;
        	setTimeRatio(defaultTimeRatio);
            setTimeBetweenUpdates(ms);
        }

        // 2015-10-31 Added loading the values below from SimulationConfig
        setNoDelaysPerYield(config.getNoDelaysPerYield());
        setMaxFrameSkips(config.getMaxFrameSkips());
    }

    /**
     * Returns the Martian clock
     *
     * @return Martian clock instance
     */
    public MarsClock getMarsClock() {
        return marsTime;
    }

    /**
     * Gets the initial Mars time at the start of the simulation.
     *
     * @return initial Mars time.
     */
    public MarsClock getInitialMarsTime() {
        return initialMarsTime;
    }

    /**
     * Returns the Earth clock
     *
     * @return Earth clock instance
     */
    public EarthClock getEarthClock() {
        return earthTime;
    }

    /**
     * Returns uptime timer
     *
     * @return uptimer instance
     */
    public UpTimer getUpTimer() {
        return uptimer;
    }

    /**
     * Adds a clock listener
     * @param newListener the listener to add.
     */
    // 2015-04-02 Modified addClockListener()
    public final void addClockListener(ClockListener newListener) {
        // if listeners list does not exist, create one
    	if (listeners == null) listeners = Collections.synchronizedList(new CopyOnWriteArrayList<ClockListener>());
        // if the listeners list does not contain newListener, add it to the list
    	if (!listeners.contains(newListener)) listeners.add(newListener);
    	// will check if clockListenerTaskList already contain the newListener's task, if it doesn't, create one
    	addClockListenerTask(newListener);
      }


    /**
     * Removes a clock listener
     * @param oldListener the listener to remove.
     */
    // 2015-04-02 Modified removeClockListener()
    public final void removeClockListener(ClockListener oldListener) {
    	//System.out.println("calling removeClockListener()");
        if (listeners == null) listeners = Collections.synchronizedList(new CopyOnWriteArrayList<ClockListener>());
        if (listeners.contains(oldListener)) listeners.remove(oldListener);
       	// Check if clockListenerTaskList contain the newListener's task, if it does, delete it
        ClockListenerTask task = retrieveClockListenerTask(oldListener);
        if (task != null) clockListenerTaskList.remove(task);
    }

    /**
     * Adds a clock listener task
     *
     * @param newListener the clock listener task to add.
     */
    // 2015-04-02 addClockListenerTask()
    public void addClockListenerTask(ClockListener listener) {
    	boolean hasIt = false;
    	//startClockListenerExecutor();
    	if (clockListenerTaskList == null)
    		clockListenerTaskList =  new CopyOnWriteArrayList<ClockListenerTask>();
    	Iterator<ClockListenerTask> i = clockListenerTaskList.iterator();
    	while (i.hasNext()) {
    		ClockListenerTask c = i.next();
    		if (c.getClockListener().equals(listener))
    			hasIt = true;
    	}
    	if (!hasIt) {
	    	clockListenerTaskList.add(new ClockListenerTask(listener));
    	}
    }

    /**
     * Retrieve a clock listener task
     * @param oldListener the clock listener task to remove.
     */
    // 2015-04-02 retrieveClockListenerTask()
    public ClockListenerTask retrieveClockListenerTask(ClockListener oldListener) {
/*     	ClockListenerTask c = null;
    	clockListenerTaskList.forEach(t -> {
    		ClockListenerTask l = c;
    		if (t.getClockListener().equals(oldListener))
    			l = t;
    	});
*/
    	ClockListenerTask t = null;
    	Iterator<ClockListenerTask> i = clockListenerTaskList.iterator();
    	while (i.hasNext()) {
    		ClockListenerTask c = i.next();
    		if (c.getClockListener().equals(oldListener))
    		 t = c;
    	}
		return t;
    }

    /**
     * Sets the load simulation flag and the file to load from.
     *
     * @param file the file to load from.
     */
    public void loadSimulation(File file) {
        this.setPaused(false);
        loadSimulation = true;
        this.file = file;
    }

    /**
     * Checks if in the process of loading a simulation.
     *
     * @return true if loading simulation.

    public boolean isLoadingSimulation() {
        return loadSimulation;
    }
*/

    /**
     * Sets the save simulation flag and the file to save to.
     * @param file save to file or null if default file.
     */
    public void saveSimulation(int type, File file) {
        saveType = type;
        //System.out.println("file is "+ file);
        this.file = file;
    }

    /**
     * Sets the autosave simulation flag and the file to save to.
     * @param file autosave to file or null if default file.
     */
    // 2015-01-08 Added autosaveSimulation
    //public void autosaveSimulation() {
    //    autosaveSimulation = true;
    //    this.file = null;
    //}

    /**
     * Sets the autosave simulation flag and the file to save to.
     * @param file autosave to file or null if default file.
     */
    // 2016-08-01 Added autosaveSimulation
    //public void autosaveSimulation(File file) {
    //    autosaveSimulation = true;
    //    this.file = file;
    //}
    /**
     * Checks if in the process of saving a simulation.
     * @return true if saving simulation.
     */
    public boolean isSavingSimulation() {
    	if (saveType != 0)
    		return true;
    	else
    		return false;
        //return saveSimulation || autosaveSimulation;
    }

    /**
     * Checks if in the process of autosaving a simulation.
     * @return true if autosaving simulation.
     */
    // 2015-01-08 Added isAutosavingSimulation
    //public boolean isAutosavingSimulation() {
    //    return autosaveSimulation;
    //}

    /**
     * Sets the exit program flag.
     */
    public void exitProgram() {
        this.setPaused(true);
        exitProgram = true;
    }

    /**
     * Gets the time pulse length
     * in other words, the number of realworld seconds that have elapsed since it was last called
     * @return time pulse length in millisols
     * @throws Exception if time pulse length could not be determined.
     */
    public double getTimePulse() {

        double timePulse;
        if (timeRatio > 0D) {
            double timePulseSeconds = ((double) getElapsedmillis() * (timeRatio / 1000D));
            timePulse = MarsClock.convertSecondsToMillisols(timePulseSeconds);
            //System.out.print(" timePulseSeconds : " + timePulseSeconds);
        }
        else {
            timePulse = 1D;
        }
        //System.out.println("  timePulse : " + timePulse );
        return timePulse;
    }

    public long getTotalPulses() {
        return totalPulses;
    }

    // 2017-01-09 Add resetTotalPulses()
	public void resetTotalPulses() {
		totalPulses = (totalPulses*3)/5;
	}

    /**
     * setTimeRatio is for setting the Masterclock's time ratio directly. It is a double
     * indicating the simetime:realtime ratio. 1000 means 1000 sim time minutes elapse for
     * each real-world minute.
     */
    public void setTimeRatio(double ratio) {
        if (ratio >= 16D && ratio <= 4096D) {
            timeRatio = Math.round(ratio*100D)/100D;
            //System.out.println("timeRatio : " + timeRatio + " ");
        }
        else throw new IllegalArgumentException("Time ratio out of bounds ");
    }


    /**
     * Gets the real-time/simulation ratio.
     *
     * @return ratio
     */
    public double getTimeRatio() {
        return timeRatio;
    }

    /**
     * Gets the default simulation time ratio.
     *
     * @return ratio
     */
    public double getDefaultTimeRatio() {
        return defaultTimeRatio;
    }

    /**
     * Sets the sleep time between two successive game updates
     * @param value in milliseconds
     */
    public void setTimeBetweenUpdates(double value) {
        if (value >= 1D && value <= 10800D) {
            timeBetweenUpdates = (long)value * 1_000_000L; // convert milli to nano
        }
        else throw new IllegalArgumentException("time between updates is out of bounds. Must be between 1 and 1000 ");
    }

    /**
     * Gets the time between updates
     * @return value in milliseconds
     */
    public double getTimeBetweenUpdates() {
        return timeBetweenUpdates;
    }


    /**
     * Sets the value of no-delay-per-yield
     * @param value in number
     */
    public void setNoDelaysPerYield(int value) {
        if (value >= 1D && value <= 200D) {
        	noDelaysPerYield = value;
        }
        else throw new IllegalArgumentException("No-Delays-Per-Yield is out of bounds. Must be between 1 and 200");
    }

    /**
     * Gets the number of no-delay-per-yield
     * @return value in milliseconds
     */
    public int getNoDelaysPerYield() {
        return noDelaysPerYield;
    }


    /**
     * Sets the maximum number of skipped frames allowed
     * @param number of frames
     */
    public void setMaxFrameSkips(int value) {
        if (value >= 1 && value <= 200) {
        	maxFrameSkips = value;
        }
        else throw new IllegalArgumentException("max-frame-skips is out of bounds. Must be between 1 and 200");
    }

    /**
     * Gets the maximum number of skipped frames allowed
     * @return number of frames
     */
    public int getMaxFrameSkips() {
        return maxFrameSkips;
    }
    /**
     * Returns the instance of ClockThreadTask
     * @return ClockThreadTask
     */
    public ClockThreadTask getClockThreadTask() {
    	return clockThreadTask;
    }

    /**
     * Runs master clock's thread using ThreadPoolExecutor
     */
    class ClockThreadTask implements Runnable, Serializable {

		private static final long serialVersionUID = 1L;

		private ClockThreadTask() {
			//logger.info("MasterClock's ClockThreadTask's constructor is on " + Thread.currentThread().getName() + " Thread");
		}

		@Override
		public void run() {
	        elapsedlast = uptimer.getUptimeMillis();

	        // 2015-06-26 For variable sleepTime
			long t1, t2, sleepTime, overSleepTime = 0L, excess = 0L;
	        int noDelays = 0;
	        t1 = System.nanoTime();

	        // Keep running until told not to by calling stop()
	        keepRunning = true;

	        if (!keepRunning)
	        	System.out.println("keepRunning is false");

	        while (keepRunning) {

	        	statusUpdate();

		        // 2015-06-26 Refactored codes for variable sleepTime
	            t2 = System.nanoTime();
	            //dt = t2 - t1;
	            sleepTime = timeBetweenUpdates - t2 + t1 - overSleepTime;
	            //System.out.print ("sleep : " + sleepTime/1_000_000 + "ms\t");

	            if (sleepTime > 0) {
	            	if (keepRunning) {
			            // Pause simulation to allow other threads to complete.
			            try {
			                //Thread.yield();
			                //Thread.sleep(SLEEP_TIME);
							TimeUnit.NANOSECONDS.sleep(sleepTime);
			            }
			            //catch (Exception e) {
			            //    logger.log(Level.WARNING, "program terminated while running sleep() in MasterClock.run() ", e);
			            //}
			            catch (InterruptedException e) {
			            	Thread.currentThread().interrupt();
			            }

			            overSleepTime = (System.nanoTime() - t2) - sleepTime;

		            }
	            }

	            else { // last frame went beyond the PERIOD
	            	excess -= sleepTime;
	            	overSleepTime = 0L;

	            	if (++noDelays >= noDelaysPerYield) {
	            		Thread.yield();
	            		noDelays = 0;
	            	}
	            }

	            t1 = System.nanoTime();

	         // 2015-07-03 Added skipping the sleep time if the statusUpdate() or other processes take too long
	            int skips = 0;
	            while ((excess > timeBetweenUpdates) && (skips < maxFrameSkips)) {
	            	excess -= timeBetweenUpdates;
	            	//logger.warning("Making up a lost frame by calling statusUpdate() again. skips :" + skips);
	            	// Make up a lost frame by calling statusUpdate() again
	            	statusUpdate();
	            	skips++;
	            }
	            // 2017-01-19 set excess to zero to prevent getting stuck in the above while loop after waking up from power saving
	            excess = 0;

	        } // end of while
	    } // end of run
    }

    // 2015-07-03  Relocated codes into statusUpdate()
    private void statusUpdate() {
        //logger.info("MasterClock's statusUpdate() is on " + Thread.currentThread().getName() + " Thread");
    	// Note: it's s on pool-4-thread-1 Thread
    	//count++;
   		//System.out.println("count : " + count);

        long lastTimeDiff;

        if (!isPaused) {
            // Update elapsed milliseconds.
            updateElapsedMilliseconds();
            // Get the time pulse length in millisols.
            double timePulse = getTimePulse();
            // Incrementing total time pulse number.
            totalPulses++;
            long startTime = System.nanoTime();
            //System.out.println("resolution : " + (System.nanoTime() - startTime));
            // Add time pulse length to Earth and Mars clocks.
            double earthTimeDiff = getElapsedmillis() * timeRatio / 1000D;
            // TODO : if null
            //if (earthTime == null)
            //	earthTime = Simulation.instance().getMasterClock().getEarthClock();
            if (keepRunning) {
            	earthTime.addTime(earthTimeDiff);
            	if (timePulse > 0)
            		marsTime.addTime(timePulse);
            }
		  	if (!isPaused
		  			|| !clockListenerExecutor.isTerminating()
		  			|| !clockListenerExecutor.isTerminated()
		  			|| !clockListenerExecutor.isShutdown() )
		  		fireClockPulse(timePulse);

            long endTime = System.nanoTime();
            lastTimeDiff = (long) ((endTime - startTime) / 1_000_000D);
            // TODO: how to prevent crashing autosaveTimer ?
            // will it help by restarting the autosaveTimer ?
            //Simulation.instance().getAutosaveTimer().playFromStart();
            //logger.finest("Pulse #" + totalPulses + " time: " + lastTimeDiff + " ms");
        }

        if (saveType != 0) {
            // Save the simulation as default.sim
            try {
                Simulation.instance().saveSimulation(saveType, file);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not save the simulation as "
                        + (file == null ? "null" : file.getPath()), e);
                e.printStackTrace();
            }

            saveType = 0;
        }

/*
        else if (loadSimulation) {
            // Load the simulation from a file.
            if (file.exists() && file.canRead()) {
                Simulation.instance().loadSimulation(file);
               	//logger.info("just done running Simulation's loadSimulation().");
                //Simulation.instance().start(false);
            }
            else {
                logger.warning("Cannot access file " + file.getPath() + ", not reading");
            }

            loadSimulation = false;
        }

*/

        // Exit program if exitProgram flag is true.
        if (exitProgram) {
            //exitProgram = false;
        	if (Simulation.instance().getAutosaveTimer() != null)
        		Simulation.instance().getAutosaveTimer().stop();
            System.exit(0);
        }

    }


    /**
     * Looks at the clock listener list and checks if each listener has already had a corresponding task in the clock listener task list.
     */
    // 2015-04-02 setupClockListenerTask()
    public void setupClockListenerTask() {
		listeners.forEach(t -> {
			// Check if it has a corresponding task or not, if it doesn't, create a task for t
			addClockListenerTask(t);
		});
    }

    /**
     * Prepare clocklistener tasks for setting up threads.
     */
    // 2015-04-02 Added ClockListenerTask
	public class ClockListenerTask implements Runnable {

		//long SLEEP_TIME = 1;
		double time;
		private ClockListener listener;

		public ClockListener getClockListener() {
			return listener;
		}

		private ClockListenerTask(ClockListener listener) {
			//logger.info("MasterClock's ClockListenerTask's constructor is on " + Thread.currentThread().getName() + " Thread");
			// It's on pool-5-thread-1 Thread
			this.listener = listener;

		}

		public void addTime(double time) {
			this.time = time;
		}

		@Override
		public void run() {
			try {
				//while (!clockListenerExecutor.isTerminated()){
				//while (!isPaused)
					listener.clockPulse(time);
					//TimeUnit.SECONDS.sleep(SLEEP_TIME);
			} catch (ConcurrentModificationException e) {} //Exception e) {}
		}
	}


    /**
     * Fires the clock pulse to each clock listener
     */
    // 2015-04-02 Modified fireClockPulse() to make use of ThreadPoolExecutor
	public void fireClockPulse(double time) {
		//logger.info("MasterClock's ClockListenerTask's constructor is on " + Thread.currentThread().getName() + " Thread");
		// it's on pool-5-thread-1 Thread

		// java 8 internal iterator style
		//listeners.forEach(cl -> cl.clockPulse(time));

/*	      synchronized (listeners) {
	            Iterator<ClockListener> i = listeners.iterator();
	            while (i.hasNext()) {
	                ClockListener cl = i.next();
	                try {
	                    cl.clockPulse(time);
	                    try {
	                        Thread.yield();
	                    }
	                    catch (Exception e) {
	                        logger.log(Level.WARNING, "Problem with Thread.yield() in MasterClock.run() ", e);
	                    }
	                } catch (Exception e) {
	            		throw new IllegalStateException("Error while firing clock pulse", e);
	                }
	            }
	       }

		/////

		if (!clockListenerTaskList.isEmpty() || clockListenerTaskList != null) {
			// run all clockListener Tasks

			clockListenerTaskList.forEach(t -> {
				// TODO: check if the thread for t is running
		  			try {
		  		  		if ( t != null || !clockListenerExecutor.isTerminating() || !clockListenerExecutor.isTerminated() || !clockListenerExecutor.isShutdown() ) {
			  		  		t.addTime(time);
		  		  			clockListenerExecutor.execute(t);
		  		  		}
		  		  		else
		  		  			return;
		  				//}
	                } catch (Exception e) {
	            		//throw new IllegalStateException("Error while firing clock pulse", e);
	                }

			});
*/
		Iterator<ClockListenerTask> i = clockListenerTaskList.iterator();
		while (i.hasNext()) {
			try {
				ClockListenerTask task = i.next();

  		  		if ((task != null) && !(clockListenerExecutor.isTerminating() || clockListenerExecutor.isTerminated() || clockListenerExecutor.isShutdown())) {
	  		  		task.addTime(time);
  		  			clockListenerExecutor.execute(task);
  		  		}
  		  		else
  		  			return;
  				//}
            } catch (Exception e) {
				e.printStackTrace();
        		//throw new IllegalStateException("Error while firing clock pulse", e);
            }
        }

	  	//endClockListenerExecutor();

    }

    /**
     * Stop the clock
     */
	// called by stop() in Simulation.java
    public void stop() {
        keepRunning = false;
    }

    public void restart() {
        keepRunning = true;
    }

    /**
     * Set if the simulation is paused or not.
     *
     * @param isPaused true if simulation is paused.
     */
    public void setPaused(boolean isPaused) {
        //logger.info("MasterClock's setPaused() is on " + Thread.currentThread().getName());
    	//System.out.println("MasterClock : calling setPaused()");
        uptimer.setPaused(isPaused);
        if (isPaused)
			Simulation.instance().getAutosaveTimer().pause(); // note: using sim (instead of Simulation.instance()) won't work when loading a saved sim.
		else
			Simulation.instance().getAutosaveTimer().play();
    	//if (isPaused) System.out.println("MasterClock.java : setPaused() : isPause is true");
        this.isPaused = isPaused;
        // Fire pause change to all clock listeners.
        firePauseChange();
    }

    /**
     * Checks if the simulation is paused or not.
     *
     * @return true if paused.
     */
    public boolean isPaused() {
    	//System.out.println("MasterClock : isPause is " + isPaused);
        return isPaused;
    }

    /**
     * Send a pulse change event to all clock listeners.
     */
    public void firePauseChange() {

        listeners.forEach(cl -> cl.pauseChange(isPaused));
/*
        synchronized (listeners) {
            Iterator<ClockListener> i = listeners.iterator();
            while (i.hasNext()) {
                ClockListener cl = i.next();
                try {
                    cl.pauseChange(isPaused);
                } catch (Exception e) {
                    throw new IllegalStateException("Error while firing pase change", e);
                }
            }
        }
*/
    }

    public double getPulsesPerSecond() {
        //System.out.println("pulsespersecond: "+((double) totalPulses / (uptimer.getUptimeMillis()/1000 ) ));
        return ((double) totalPulses / (uptimer.getUptimeMillis() / 1000D));
    }

    public double getPulses() {
        //System.out.println("pulsespersecond: "+((double) totalPulses / (uptimer.getUptimeMillis()/1000 ) ));
        return ((double) totalPulses);
    }

    /**
     * Update the milliseconds elapsed since last time pulse.
     */
    private void updateElapsedMilliseconds() {
    	if (uptimer == null) {
    		//System.out.println("MasterClock : uptimer == null");
    		uptimer = new UpTimer(this);
    	}
        long tnow = uptimer.getUptimeMillis();
        elapsedMilliseconds = tnow - elapsedlast;
        elapsedlast = tnow;
        //System.out.print ("elapsedMilliseconds : " + elapsedMilliseconds + " ");
    }

    private long getElapsedmillis() {
        return elapsedMilliseconds;
    }

    public static final int secspmin = 60, secsphour = 3600, secspday = 86400, secsperyear = 31536000;


    /**
     * the following is a utility. It may be slow. It returns a string in YY:DDD:HH:MM:SS.SSS format
     * note: it is set up currently to only return hh:mm:ss.s
     */
    public String getTimeString(double seconds) {

        //long years = (int) Math.floor(seconds / secsperyear);
        //long days = (int) ((seconds % secsperyear) / secspday);
        int hours = (int) ((seconds % secspday) / secsphour);
        int minutes = (int) ((seconds % secsphour) / secspmin);
        double secs = (seconds % secspmin);

        StringBuilder b = new StringBuilder();
/*
        b.append(years);
        if(years>0){
            b.append("yr:");
        }

        if (days > 0) {
            b.append(String.format("%03d", days)).append("mon:");
        } else {
            b.append("0mon:");
        }
*/
        if (hours > 0) {
            b.append(String.format("%02d", hours)).append("h ");
        }
        //} else {
        //    b.append("00h ");
        //}

        if (minutes > 0) {
            b.append(String.format("%02d", minutes)).append("m ");
        } else {
            b.append("00m ");
        }

        //b.append(String.format("%5.3f", secs));
        b.append(String.format("%05.2f", secs) + "s");

        return b.toString();
    }

    /**
     * Starts clock listener thread pool executor
     */
    public void startClockListenerExecutor() {
	   	//logger.info("MasterClock's startClockListenerExecutor() is on " + Thread.currentThread().getName() + " Thread");
    	// it's in pool-2-thread-1 Thread

    	//if ( clockListenerExecutor.isTerminated() || clockListenerExecutor.isShutdown() )
    	if (clockListenerExecutor == null)
    		clockListenerExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    	//Executors.newSingleThreadExecutor();// Executors.newFixedThreadPool(1); // newCachedThreadPool(); //
    }

    /**
     * Shuts down clock listener thread pool executor
     */
    public void endClockListenerExecutor() {
    	clockListenerExecutor.shutdownNow();
    	//Simulation.instance().getUnitManager().getPersonExecutor().shutdownNow();
/*
    	if ( clockListenerExecutor.isTerminating() )
			try {
				TimeUnit.MILLISECONDS.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

    	if ( clockListenerExecutor.isTerminated() || !clockListenerExecutor.isShutdown() )
    		///
*/
    }


	public ThreadPoolExecutor getClockListenerExecutor() {
		return clockListenerExecutor;
	}


    /**
     * Prepare object for garbage collection.
     */
	public void destroy() {
		config = null;
    	sim = null;
    	marsTime = null;
    	initialMarsTime = null;
    	earthTime = null;
    	uptimer = null;
    	//listeners.clear();
    	listeners = null;
    	file = null;
    	clockListenerExecutor = null;
    }
}