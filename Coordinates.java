/**
 * Mars Simulation Project
 * Coordinates.java
 * @version 2.70 2000-08-31
 * @author Scott Davis
 */

import java.awt.Point;

/** Spherical Coordinates. Represents a location on virtual Mars in
 *  spherical coordinates. It provides some useful methods involving
 *  those coordinates, as well as some static methods for general
 *  coordinate calculations.
 */
public class Coordinates {

    private double phi;        // Phi value of coordinates
    private double theta;      // Theta value of coordinates

    private double sinPhi;     // Sine of phi (stored for efficiency)
    private double sinTheta;   // Sine of theta (stored for efficiency)
    private double cosPhi;     // Cosine of phi (stored for efficiency)
    private double cosTheta;   // Cosine of theta (stored for efficiency)

    public static final double someConst = 1440D;
    public static final double some2Const = 3393D;
    public static final double twoPI = (2.0 * Math.PI);
	
    public Coordinates(double phi, double theta) {
		
	// Set Coordinates
	this.phi = phi;
	this.theta = theta;
		
	// Set trigonometric functions
	setTrigFunctions();
    }
	
    /** copy constructor */
    public Coordinates(Coordinates originalCoordinates) {
	this(originalCoordinates.getPhi(), originalCoordinates.getTheta());
    }
	
    /** Sets commonly-used trigonometric functions of coordinates */
    private void setTrigFunctions() {
	sinPhi = Math.sin(phi);
	sinTheta = Math.sin(theta);
	cosPhi = Math.cos(phi);
	cosTheta = Math.cos(theta);
    }

    /** phi accessor */
    public double getPhi() {
	return phi;
    }
	
    /** phi mutator */
    public void setPhi(double newPhi) {
	phi = newPhi;
	setTrigFunctions();
    }
	
    /** theta accessor */
    public double getTheta() {
	return theta;
    }
	
    /** theta mutator */
    public void setTheta(double newTheta) {
	theta = newTheta;
	setTrigFunctions();
    }
	
    /** sine of phi */
    public double getSinPhi() { return sinPhi; }
	
    /** sine of theta */
    public double getSinTheta() { return sinTheta; }
	
    /** cosine of phi */
    public double getCosPhi() { return cosPhi; }
	
    /** cosine of theta */
    public double getCosTheta() { return cosTheta; }
	
    /** Set coordinates */
    public void setCoords(Coordinates newCoordinates) {
		
	// Update coordinates
	phi = newCoordinates.phi;
	theta = newCoordinates.theta;
		
	// Update trigonometric functions
	setTrigFunctions();
    }
	
    /** Returns true if coordinates have equal phi and theta values */
    public boolean equals(Object otherCoords) {
	
	if (Coordinates.class.isInstance(otherCoords)) {
	    // this temp usage is unnecessary...
	    Coordinates temp = (Coordinates) otherCoords;
	    if ((phi == temp.getPhi()) && (theta == temp.getTheta())) return true;
	}

	return false;
    }
	
    /** Returns the arc angle in radians between this location and the
     *  given coordinates */
    public double getAngle(Coordinates otherCoords) {

	double rho = someConst / Math.PI;
	double temp1 = Math.pow(rho * (phi - otherCoords.getPhi()), 2D);
	double radius1 = rho * sinPhi;
	double radius2 = rho * otherCoords.getSinPhi();
	double averageR = (radius1 + radius2) / 2D;
	double theta_difference = Math.abs(theta - otherCoords.getTheta());
	if (theta_difference >= Math.PI) theta_difference = twoPI - theta_difference;
	double temp2 = Math.pow(averageR * theta_difference, 2D);
	double angle = Math.sqrt(temp1 + temp2) / rho;
	
	return angle;
    }
	
    /** Returns the distance in kilometers between this location and
     *  the given coordinates */
    public double getDistance(Coordinates otherCoords) {
	
	double rho = some2Const;
	double angle = getAngle(otherCoords);
	
	return rho * angle;
    }
	
    /** Returns common formatted string to represent longitude for
     *  this location ex. "35.6� E" */
    public String getFormattedLongitudeString() {

	double degrees;
	char direction;

	if ((theta < Math.PI) && (theta > 0D)) {
	    degrees = Math.toDegrees(theta);
	    direction = 'E';
	} else if (theta >= Math.PI) {
	    degrees = Math.toDegrees(twoPI - theta);
	    direction = 'W';
	} else {
	    degrees = 0.0;
	    direction = ' ';
	}

	int first = (int) Math.abs(Math.round(degrees));
	int last = (int) Math.abs(Math.round((degrees - first) * 100.0));

	return new String(first + "." + last + "\u00BA " + direction);
    }
	
    /** Returns common formatted string to represent latitude for this
     *  location ex. "35.6� S" */
    public String getFormattedLatitudeString() {

	double degrees;
	double piHalf = Math.PI / 2.0;
	char direction;

	if (phi < piHalf) {
	    degrees = ((piHalf - phi) / piHalf) * 90D;
	    direction = 'N';
	} else if (phi > piHalf) {
	    degrees = ((phi - piHalf) / piHalf) * 90D;
	    direction = 'S';
	} else {
	    degrees = 0D;
	    direction = ' ';
	}

	int first = (int) Math.abs(Math.round(degrees));
	int last = (int) Math.abs(Math.round((degrees - first) * 100D));
	
	return new String(first + "." + last + "\u00BA " + direction);
    }
	
    /** Converts spherical coordinates to rectangular coordinates.
     *  Returns integer x and y display coordinates for spherical
     *  location. */
    static public IntPoint findRectPosition(Coordinates newCoords, Coordinates centerCoords, double rho, int half_map, int low_edge) {
	
	double sin_offset = Math.sin(centerCoords.getPhi() + Math.PI);
	double cos_offset = Math.cos(centerCoords.getPhi() + Math.PI); 
	double col_correction = (Math.PI / -2D) - centerCoords.getTheta();
	double temp_col = newCoords.getTheta() + col_correction;
	double temp_buff_x = rho * newCoords.getSinPhi();
	double temp_buff_y1 = temp_buff_x * cos_offset;
	double temp_buff_y2 = rho * newCoords.getCosPhi() * sin_offset; 		
	int buff_x = (int) Math.round(temp_buff_x * Math.cos(temp_col)) + half_map;
	int buff_y = (int) Math.round((temp_buff_y1 * Math.sin(temp_col)) + temp_buff_y2) + half_map;
	
	return new IntPoint(buff_x - low_edge, buff_y - low_edge);
    }
	
    /** Converts linear rectangular XY position change to spherical coordinates */
    public Coordinates convertRectToSpherical(double x, double y) {
		
	double rho = someConst / Math.PI;
	
	double z = Math.sqrt((rho * rho) - (x * x) - (y * y));

	double x2 = x;
	double y2 = (y * cosPhi) + (z * sinPhi);
	double z2 = (z * cosPhi) - (y * sinPhi);

	double x3 = (x2 * cosTheta) + (y2 * sinTheta);
	double y3 = (y2 * cosTheta) - (x2 * sinTheta);
	double z3 = z2;

	double phi_new = Math.acos(z3 / rho);
	double theta_new = Math.asin(x3 / (rho * Math.sin(phi_new)));

	if (x3 >= 0) {
	    if (y3 < 0) theta_new = Math.PI - theta_new;
	} else {
	    if (y3 < 0) theta_new = Math.PI - theta_new;
	    else theta_new = twoPI + theta_new;
	} 

	Coordinates result = new Coordinates(phi_new, theta_new);

	return result;
    }
	
    /** Returns angle direction to another location on surface of
     *  sphere 0 degrees is north (clockwise)
     */
    public double getDirectionToPoint(Coordinates otherCoords) {
		
	double rho = someConst / Math.PI;
	int half_map = 720;
	int low_edge = 0;
		
	IntPoint pos = findRectPosition(otherCoords, this, rho, half_map, low_edge);
	pos.setLocation(pos.getX() - half_map, pos.getY() - half_map);

	double result = 0D;
		
	if ((pos.getX() == 0) && (pos.getY() == 0)) {
	    double tempAngle = getAngle(otherCoords);
	    if (tempAngle > (Math.PI / 2D)) {
		result = 0D;
	    } else { 
		if (getDistance(otherCoords) <= 1D) {
		    result = 0D;
		} else {
		    if ((otherCoords.getPhi() - phi) != 0D) {
			result = Math.atan((otherCoords.getTheta() - theta) / (otherCoords.phi - phi));
		    }
		}
	    }
	} else {
	    result = Math.atan(Math.abs((double)pos.getX() / (double)(pos.getY())));
	}
		
	if (pos.getX() < 0) {
	    if (pos.getY() < 0) {
		result = twoPI - result; 
	    } else {
		result = Math.PI + result;
	    }
	} else {
	    if (pos.getY() < 0) {
		result = result;
	    } else {
		result = Math.PI - result;
	    }
	}
	
	return result;
    }
	
    /** Makes sure an angle isn't above 2PI or less than zero */
    public static double cleanAngle(double angle) {
	if ( (angle < 0.0) || (angle > twoPI) ) {
	    angle = Math.IEEEremainder(angle, twoPI);
	}
	//while (angle > (2D * Math.PI)) angle -= (2D * Math.PI);
	//while (angle < 0D) angle += (2D * Math.PI);	
	return angle;
    }
}
