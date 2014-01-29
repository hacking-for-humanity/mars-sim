/**
 * Mars Simulation Project
 * EarthClock.java
 * @version 3.06 2014-01-29
 * @author Scott Davis
 */

package org.mars_sim.msp.core.time;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;

/** The EarthClock class keeps track of Earth Universal Time.
 *  It should be synchronized with the Mars clock. 
 */
public class EarthClock implements Serializable/*extends GregorianCalendar implements Serializable*/ {

    private final GregorianCalendar cal;

    // Data members
    private final SimpleDateFormat formatter;

    /** 
     * Constructor
     * @param dateString the UT date string in format: "MM/dd/yyyy hh:mm:ss".
     * @throws Exception if date string is invalid. 
     */
    public EarthClock(String dateString) {
        
        // Use GregorianCalendar constructor
        cal = new GregorianCalendar();

        // Set GMT timezone for calendar
        SimpleTimeZone zone = new SimpleTimeZone(0, "GMT");
        cal.setTimeZone(zone);

        // Initialize formatter
        formatter = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss 'UT'");
        formatter.setTimeZone(zone);

        // Set Earth clock to Martian Zero-orbit date-time. 
        // This date may need to be adjusted if it is inaccurate.
        cal.clear();
        DateFormat tempFormatter = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss");
        tempFormatter.setTimeZone(zone);
        try {
            cal.setTime(tempFormatter.parse(dateString));
        } catch (ParseException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Returns the date/time formatted in a string 
     *  @return date/time formatted in a string. ex "2055-05-06 03:37:22 UT"
     */
    public synchronized String getTimeStamp() {
        return formatter.format(cal.getTime());// + " UT";
    }

    /** Returns the date formatted in a string 
     *  @return date formatted in a string. ex "2055-05-06"
     */
    public synchronized String getDateString() {
        return getTimeStamp().substring(0,10);
    }
    
    /** Adds time to the calendar 
     *  @param seconds seconds added to the calendar
     */
    public synchronized void addTime(double seconds) {
        cal.add(Calendar.MILLISECOND, (int) (seconds * 1000D));
    }
    
    /**
     * Displays the string version of the clock.
     * @return time stamp string.
     */
    public synchronized String toString() {
    	return getTimeStamp();
    }

    public int getDayOfMonth()
    {
        return cal.get(Calendar.DATE);
    }

    public int getMonth()
    {
        return cal.get(Calendar.MONTH);
    }

    public int getYear()
    {
        return cal.get(Calendar.YEAR);
    }
}