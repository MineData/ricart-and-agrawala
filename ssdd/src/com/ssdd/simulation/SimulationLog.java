package com.ssdd.simulation;

import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ssdd.util.constants.IConstants;
import com.ssdd.util.logging.SSDDLogFactory;

/** 
 * Writes in the required format the simulation log file with the process's ins and outs.
 * @see <a href="https://www.codejava.net/java-se/file-io/how-to-read-and-write-text-file-in-java">https://www.codejava.net/java-se/file-io/how-to-read-and-write-text-file-in-java</a>
 * 
 * @version 1.0
 * @author H�ctor S�nchez San Blas
 * @author Francisco Pinto Santos
*/
public class SimulationLog {

	/**
	 * Class logger generated with {@link com.ssdd.util.logging.SSDDLogFactory#logger(Class)}
	 * */
    private final static Logger LOGGER = SSDDLogFactory.logger(SimulationLog.class);
    
    /**
     * Format to log the ins to critical section
     * */
	private static final String IN_FORMAT = "P%s E %d\n";
    /**
     * Format to log the outs to critical section
     * */
	private static final String OUT_FORMAT = "P%s S %d\n";


    /**
     * process's id
     * */
	private String processId;
	/**
     * Data stream to write to file
     * */
	private FileWriter file;
	
	public SimulationLog(String processId, String logFile) {
		this.processId = processId;
		try {
			this.file = new FileWriter(logFile, true);
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, String.format("constructor: error %s", e.getMessage()), e);
			System.exit(IConstants.EXIT_CODE_IO_ERROR);
		}
	}
	
	/** 
	 * logs an enter into the critical section on the log file.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas
	 * @author Francisco Pinto Santos
	*/
	public void logIn() {
		try {
			String logEntry = String.format(SimulationLog.IN_FORMAT, this.processId, System.currentTimeMillis());
			LOGGER.log(Level.INFO,String.format(" new log entry %s", logEntry.replace("\n", "")));
			this.file.write(logEntry);
			this.file.flush();
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, String.format("logIn: error %s", e.getMessage()), e);
			System.exit(IConstants.EXIT_CODE_IO_ERROR);
		}
	}
	
	/** 
	 * logs an exit into the critical section on the log file.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas
	 * @author Francisco Pinto Santos
	*/
	public void logOut() {
		try {
			String logEntry = String.format(SimulationLog.OUT_FORMAT, this.processId, System.currentTimeMillis());
			LOGGER.log(Level.INFO,String.format(" new log entry %s", logEntry.replace("\n", "")));
			this.file.write(logEntry);
			this.file.flush();
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, String.format("logOut: error %s", e.getMessage()), e);
			System.exit(IConstants.EXIT_CODE_IO_ERROR);
		}
	}
	
	public void close(){
		try {
			this.file.close();
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, String.format("close: error %s", e.getMessage()), e);
		}	
	}
	
}
