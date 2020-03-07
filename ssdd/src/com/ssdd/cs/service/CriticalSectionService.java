package com.ssdd.cs.service;


import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.ssdd.cs.bean.CriticalSectionMessage;
import com.ssdd.cs.bean.CriticalSectionMessageType;
import com.ssdd.cs.bean.CriticalSectionState;
import com.ssdd.cs.bean.LamportCounter;
import com.ssdd.util.logging.SSDDLogFactory;


public class CriticalSectionService{

    private final static Logger LOGGER = SSDDLogFactory.logger(CriticalSectionService.class);
	
	private Semaphore accessSemaphore; // TODO: solo provisional, es por si da problemas de concurrencia, para regular el acceso a los elementos de la clase
	private Map<String, LamportCounter> lamportTime;
	private Map<String, CriticalSectionState> state;
	private Map<String, Queue<CriticalSectionMessage>> accessRequests;
	
	public CriticalSectionService() {
		LOGGER.log(Level.INFO, "Creating critical section");
		this.accessSemaphore = new Semaphore(1);
		this.lamportTime = new ConcurrentHashMap<String, LamportCounter>();
		this.state = new ConcurrentHashMap<String, CriticalSectionState>();
		this.accessRequests = new ConcurrentHashMap<String, Queue<CriticalSectionMessage>>();
	}

	/**
	 * Factory method, to build a proxy to access an instance of this service in remote.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas and Francisco Pinto Santos
	 * 
	 * @param host the IP adress and PORT of server in which the service is allocated.
	 * */
	public static CriticalSectionService buildProxy(String host) {
		String serviceUri = CriticalSectionService.buildServiceUri(host);
		return new CriticalSectionServiceProxy(serviceUri);
	}

	/**
	 * Factory method, to build a URI for a CriticalSectionService from the host IP and port.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas and Francisco Pinto Santos
	 * 
	 * @param host the IP adress and PORT of server in which the service is allocated.
	 * */
	public static String buildServiceUri(String host) {
		return String.format("http://%s/ssdd/cs", host);
	}
	
	/**
	 * shows service status.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas and Francisco Pinto Santos
	 * */
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/status")
	public String status() {
		return "{ \"service\": \"cs\", \"status\": \"ok\"}";
	}

	/**
	 * registers a node, and create the neccesary structures to work with it.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas and Francisco Pinto Santos
	 * 
	 * @param nodeId the id of the node who wants to suscribe to current service.
	 * */
	public void subscribe(String nodeId){
		LOGGER.log(Level.INFO, String.format("[node: %s] /cs/subscribe", nodeId));
		this.lamportTime.put(nodeId, new LamportCounter());
		this.state.put(nodeId, CriticalSectionState.FREE);
		this.accessRequests.put(nodeId, new ConcurrentLinkedQueue<>());
	}

	/**
	 * return a list of suscribed nodes.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas and Francisco Pinto Santos
	 * 
	 * @return String containing the list of suscribed nodes separated with the '_character'.
	  */
	public String suscribed(){
		LOGGER.log(Level.INFO, String.format("/cs/suscribed"));

		StringBuilder sb = new StringBuilder();
		
		for(String nodeId : this.state.keySet()) {
			sb.append(nodeId).append("_");
		}
		
		String result = sb.toString();
		String response = result.substring(0, result.length() - 1);
		
		return response;
	}
	
	
	/**
	 * processes the requests to the critical section access send by other nodes.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas and Francisco Pinto Santos
	 * 
	 * @param nodeId the id of the node trying to accces the critical section. Must be a suscribed node.
	 * @throws NodeNotSuscribedInServiceException when then nodeId doesn't corresponds to any node suscribed to current service.
	 * */
	public String requestAccess(String nodeId, String request) throws NodeNotSuscribedInServiceException {
		LOGGER.log(Level.INFO, String.format("[node: %s] /cs/requestAccess", nodeId));
		
		// check if given nodeId corresponds to a suscribed process
		if(! this.isSuscribed(nodeId)) {
			LOGGER.log(Level.WARNING, String.format("[node: %s] /cs/release: ERROR the given node is not subscribed", nodeId));
			throw new NodeNotSuscribedInServiceException(nodeId);
		}
		
		// get node response
		CriticalSectionMessage response;
		CriticalSectionMessage r = CriticalSectionMessage.fromJson(request);

		// update process time
		this.lamportTime.get(nodeId).update(r.getTime());
		
		// get node state
		LamportCounter lamportTime = this.lamportTime.get(nodeId);
		CriticalSectionState state = this.state.get(nodeId);
		
		if(state == CriticalSectionState.ACQUIRED 
				|| (state == CriticalSectionState.REQUESTED && r.hasPriority(nodeId, r.getTime()))) {
			this.accessRequests.get(nodeId).add(r);
			// build response
			response = new CriticalSectionMessage(nodeId, lamportTime, CriticalSectionMessageType.RESPONSE_DELAYED);
		}else {
			//TODO: no se si hay que responder esto
			response = new CriticalSectionMessage(nodeId, lamportTime, CriticalSectionMessageType.RESPONSE_ALLOW);
		}
		
		return response.toJson();
	}
	
	/**
	 * processes the delayed responses to the critical seciton access send by other nodes. Only can be called by a suscribed node.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas and Francisco Pinto Santos
	 * 
	 * @param nodeId the id of the node for which the delayed answer is directed. Must be a suscribed node.
	 * @throws NodeNotSuscribedInServiceException when then nodeId doesn't corresponds to any node suscribed to current service.
	 * */
	public void treatDelayedresponses(String nodeId) throws NodeNotSuscribedInServiceException {
		LOGGER.log(Level.INFO, String.format("[node: %s] /cs/release", nodeId));

		// check if given nodeId corresponds to a suscribed process
		if(! this.isSuscribed(nodeId)) {
			LOGGER.log(Level.WARNING, String.format("[node: %s] /cs/release: ERROR the given node is not subscribed", nodeId));
			throw new NodeNotSuscribedInServiceException(nodeId);
		}
		
		//TODO: esto es un problema porque necesitamos tratar las respuestas retardadas
	}
	
	/**
	 * releases critical section. Only can be called by a suscribed node.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas and Francisco Pinto Santos
	 * 
	 * @param nodeId the id of the node which is releasing the critical section. Must be a suscribed node.
	 * @throws NodeNotSuscribedInServiceException when then nodeId doesn't corresponds to any node suscribed to current service.
	 * */
	public void release(String nodeId) throws NodeNotSuscribedInServiceException {
		LOGGER.log(Level.INFO, String.format("[node: %s] /cs/release", nodeId));

		// check if given nodeId corresponds to a suscribed process
		if(! this.isSuscribed(nodeId)) {
			LOGGER.log(Level.WARNING, String.format("[node: %s] /cs/release: ERROR the given node is not subscribed", nodeId));
			throw new NodeNotSuscribedInServiceException(nodeId);
		}
		
		// update process time
		LamportCounter myTime = this.lamportTime.get(nodeId);
		myTime.update();
		
		// set state to released
		LOGGER.log(Level.INFO, String.format("[node: %s] /cs/release: changing cs state to FREE", nodeId));
		this.state.put(nodeId, CriticalSectionState.FREE);
		
		// answer to responses
		LOGGER.log(Level.INFO, String.format("[node: %s] /cs/release: replying request", nodeId));
		for(CriticalSectionMessage r : this.accessRequests.get(nodeId)) {
			//TODO: responder a nodo
			CriticalSectionMessage response = new CriticalSectionMessage(nodeId, myTime, CriticalSectionMessageType.RESPONSE_ALLOW);
			// TODO: responder con proxy (de este servicio con direccion a otro servicio) a servidor que lo envio
		}
	}
	
	/**
	 * given a nodeId checks if is suscribed to current service instance.
	 * 
	 * @version 1.0
	 * @author H�ctor S�nchez San Blas and Francisco Pinto Santos
	 * 
	 * @param nodeId id of the node we want to check if you have subscribed to this service.
	 * */
	private boolean isSuscribed(String nodeId) {
		return this.state.keySet().contains(nodeId);
	}


}