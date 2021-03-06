//******************************************************************************
//
// File:    Simulate.java
// Package: 
// Unit:    Class Simulate
//
//******************************************************************************

import edu.rit.numeric.ExponentialPrng;
import edu.rit.numeric.UniformPrng;
//import edu.rit.numeric.ListXYSeries;
//import edu.rit.numeric.plot.Plot;
import edu.rit.sim.Event;
import edu.rit.sim.Simulation;
import edu.rit.util.Random;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Class Simulate is used to generate tasks at a random arrival rate and random[[
 * task size
 *
 * @author Hardik Nagda
 * 
 * @version Jan 30, 2013
 * 
 */

/*
 * Referred Prof. Alan Kaminsky's Book: Simulation Simplified
 */

public class Simulate {

	public static Simulation sim;
	private static Random blockPrng,clientPrng;
	private static UniformPrng blockCasePrng;
	private static ExponentialPrng requestPrng;
	public static ExponentialPrng serverPrng;
	private static long requestCount;
	//private static 
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length != 1)
			usage();
		
		String fileName = args[0];
		File file = new File(fileName);

		ConfigReader config = null;
		try {
			config = new ConfigReader(file);
		} catch (FileNotFoundException e) {
			System.err.println ("File: " + fileName +"does not exist");
			return;
		}
		
		blockPrng = Random.getInstance(ConfigReader.getBlockSeed());
		clientPrng = Random.getInstance(ConfigReader.getClientSeed());
		requestPrng = new ExponentialPrng(Random.getInstance(ConfigReader.getRequestSeed()), config.getRequestLambda());
		serverPrng = new ExponentialPrng(Random.getInstance(ConfigReader.getRequestSeed()), 12);
		
		FileSystem fs = new FileSystem(config);
		fs.SetUpServer();
		fs.SetUpManager();
		
		double accessTime;
		long localHit, remoteHit, diskHit;
		//int N = 3;
		System.out.println(config.getAlgorithm());
		
		for(int N = config.getN_L(); N <= config.getN_U(); N+=config.getN_D()) 
		{
			sim = new Simulation();
			blockCasePrng = new UniformPrng(Random.getInstance(ConfigReader.getRequestSeed()), 0, config.getClientCacheSize()/config.getBlockSize()*N);
			accessTime = 0.0;
			localHit = 0 ;
			remoteHit = 0 ;
			diskHit = 0;
			fs.SetUpClient(N);
			generateRequest();
			sim.run();
			
			for(int i=0 ; i < N ; i++)
			{
				accessTime+= FileSystem.setOfClient[i].blockAccessTime;
				localHit+= FileSystem.setOfClient[i].localCacheHit;
				remoteHit+=FileSystem.setOfClient[i].remoteCacheHit;
				diskHit+=FileSystem.setOfClient[i].diskCacheHit;
			}
						
			System.out.println ("Block Access Time for all Client is: "+ accessTime/ConfigReader.getNumberOfRequests() + " Local Cache Hit is : "
					+ localHit + " Remote Cache Hit is : " + remoteHit + " Disk hit is : " 
					+ diskHit);
			
			FileSystem.server.clearServerCache();
			FileSystem.manager.clearManagerEntries();
			fs.clearUpClients();
			requestCount = 0;
		}		
	}
	
    private static void usage() {
    	System.err.println ("Usage: java CooperativeCaching <filename>");
    	System.exit (1);
    }
    
    private static void generateRequest() {
    	
    	requestCount++;
    	Client client = null;
    	int blockID = -1;
    	
    	if(ConfigReader.getBaseCase().equals("disk"))
    	{
     		blockID = (int) (requestCount % ConfigReader.getNumberOfBlocks());
     		client = FileSystem.setOfClient[(int) (requestCount%FileSystem.getNumberOfClients())];
    	}
    	else
    	{
	    	client = forwardingClient();
	    	if(ConfigReader.getBaseCase().equals("local"))
	    		blockID = client.getNextBlockNumber();
	    	else if(ConfigReader.getBaseCase().equals("remote"))
	    		{
		    		blockID = (int)blockCasePrng.next();
		    		while(blockID >= client.clientID*client.cacheSize && blockID <= (client.clientID+1)*client.cacheSize)
		    			blockID = (int)blockCasePrng.next();
	    		}
	    	else
	    		blockID = blockPrng.nextInt (ConfigReader.getNumberOfBlocks());
    	}
    	
    	//System.out.println("Client " + client.clientID + " with block "+ blockID);
    	CacheBlockRequest blockRequest = new CacheBlockRequest (blockID);
    	
    	System.out.printf ("%.3f %s request passed to client %s %n", sim.time(), blockRequest, client);
    	client.addToQueue (blockRequest);
    	
    	if(requestCount<ConfigReader.getNumberOfRequests())
    		sim.doAfter (requestPrng.next(), new Event()
    		{
    			public void perform() { generateRequest(); }
    		});
    	
    }
    
    private static Client forwardingClient() {
    	int clientID = clientPrng.nextInt(FileSystem.getNumberOfClients());
    	return FileSystem.setOfClient[clientID];
    }
    
}
