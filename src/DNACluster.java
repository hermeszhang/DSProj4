import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import mpi.*;


//tag 99 : send centroids
//tag 1 : send new centroids
//tag0 : final result
public class DNACluster {
	public String outFile;
	public int numCluster;
	public ArrayList<String>dnaList;

	public DNACluster(int numCluster, String outFile, ArrayList<String> dnaList) throws MPIException{
		this.numCluster = numCluster;
		this.outFile = outFile;
		this.dnaList = dnaList;

		//step 1 init args
		int myRank = MPI.COMM_WORLD.Rank();
		int size = MPI.COMM_WORLD.Size();
		System.out.println("rank: " + myRank + "is working...");
		//slave process
		if (myRank != 0) {
			
			//step 1 get centroids from master
			String [] centroids = new String[numCluster];
			MPI.COMM_WORLD.Recv(centroids, 0, numCluster, MPI.OBJECT, 0, 99);
			System.out.println("Slave " + myRank + "gets centroids: ");
			System.out.println(Arrays.toString(centroids));
			
			//step 2 calculate dif of its part
			int numStrandsSlave = dnaList.size() / (size -1);
			int [] resultCluster = new int[dnaList.size()];
			int [] resultDif = new int[dnaList.size()];
			for (int i = 0; i < numStrandsSlave; i++) {
				int dif = Integer.MAX_VALUE;
				int cluster = -1;
				for (int j = 0; j < numCluster; j++) {
					//compare strand with centroid one by one
					int tempdif = calDif(dnaList.get((myRank - 1) * numStrandsSlave + i),centroids[j]);
					if(tempdif < dif ){
						dif = tempdif;
						cluster = j;
					}else {
						continue;
					}
				}
				resultCluster[(myRank - 1) * numStrandsSlave + i] = cluster;
				resultDif[(myRank - 1) * numStrandsSlave + i] = dif;
			}
//			System.out.println(Arrays.toString(resultDif));
			System.out.println("Slave " + myRank +"clusters: ");
			System.out.println(Arrays.toString(resultCluster));
			
			//step 3 
			String[] tempCluster = new String[dnaList.size()];
			HashMap<Integer,ArrayList<String>> map = new HashMap<Integer,ArrayList<String>>();
			
			for (int i = 0; i < numCluster; i++) {
				map.put(i,new ArrayList<String>());
			}

			for (int i = 0; i < numStrandsSlave; i++) {
				map.get(resultCluster[i]).add(dnaList.get((myRank - 1) * numStrandsSlave + i));
			}//now we have arraylists of each cluster

			
			//step 4 
			int dnaLength = dnaList.get(0).length();
			int [][][] sum = new int [numCluster][dnaLength][4];
			int [][][] newSum = new int [numCluster][dnaLength][4];
			for (int i = 0; i < numCluster; i++) {
				for (int j = 0 ; j < dnaLength; j++) {
					int [] frequence = new int[4];
					for (String strand : map.get(i)) {
						char cur = strand.charAt(j);
						switch (cur) {
							case 'A' : 
								frequence[0]++;
								break;
							case 'C' :
								frequence[1]++;
								break;
							case 'T' : 
								frequence[2]++;
								break;
							case 'G' : 
								frequence[3]++;
								break;
						}
					}
					
					
					for (int k = 0; k < 4; k++ ) {
						sum[i][j][k] = frequence[k]; 	
					} 
					//testing...
					System.out.println("Slave "+myRank+" frequence : " + Arrays.toString(frequence));
				}
			}
			
			//testing...
//			for (int i = 0; i < numCluster; i++) {
//				for (int j = 0 ; j < dnaLength; j++) {
//					for(int k = 0 ; k < 4; k++){
//						System.out.println(i + "," +j +sum[i][j][k] );
//					}
//				}
//			}
				
			//step 5 all reduce last digit for AGCT
			System.out.println("Start all reduce!!!  " + myRank);
			for (int i = 0; i < numCluster; i++) {
				for (int j = 0 ; j < dnaLength; j++) {
					int [] xSum = new int[4], xSumNew = new int[4];
					xSum = sum[i][j];
					MPI.COMM_WORLD.Allreduce(xSum, 0, xSumNew, 0, xSum.length, MPI.INT, MPI.SUM);
					sum[i][j] = xSum;
					//testing...
					System.out.println("Slave " + myRank + "xSum " + Arrays.toString(sum[i][j]));
				}
			}
			

			
			//test...
//			for (int i = 0; i < numCluster; i++) {
//				for (int j = 0 ; j < dnaLength; j++) {
//					System.out.println(Arrays.toString(sum[i][j]));
//				}
//			}
			
			//step 6 receive for recalculate
			MPI.COMM_WORLD.Recv(centroids, 0, numCluster, MPI.OBJECT, 0, 1);
			System.out.println("Rank " +myRank +"receive centroids");
			System.out.println(Arrays.toString(centroids));
			//step 7 recalculate
			resultCluster = new int[dnaList.size()];
			resultDif = new int[dnaList.size()];
			//init data
			for(int i = 0 ;i < dnaList.size();i++){
				resultCluster[i] = -1;
			}
			
			for (int i = 0; i < numStrandsSlave; i++) {
				int dif = Integer.MAX_VALUE;
				int cluster = -1;
				for (int j = 0; j < numCluster; j++) {
					//compare strand with centroid one by one
					int tempdif = calDif(dnaList.get((myRank - 1) * numStrandsSlave + i),centroids[j]);
					if(tempdif < dif ){
						dif = tempdif;
						cluster = j;
					}else {
						continue;
					}
				}
				
				resultCluster[(myRank - 1) * numStrandsSlave + i] = cluster;
				resultDif[(myRank - 1) * numStrandsSlave + i] = dif;
			}
			System.out.println("recalculate!!!");
			System.out.println(Arrays.toString(resultDif));
			System.out.println(Arrays.toString(resultCluster));
			//step 8 recalculating...
			tempCluster = new String[dnaList.size()];
			map = new HashMap<Integer,ArrayList<String>>();
			
			for (int i = 0; i < numCluster; i++) {
				map.put(i,new ArrayList<String>());
			}

			for (int i = 0; i < numStrandsSlave; i++) {
				map.get(resultCluster[i]).add(dnaList.get((myRank - 1) * numStrandsSlave + i));
			}//now we have arraylists of each cluster
			
			//continue...
			for (int i = 0; i < numCluster; i++) {
				for (int j = 0 ; j < dnaLength; j++) {
					int [] frequence = new int[4];
					for (String strand : map.get(i)) {
						char cur = strand.charAt(j);
						switch (cur) {
							case 'A' : 
								frequence[0]++;
								break;
							case 'C' :
								frequence[1]++;
								break;
							case 'T' : 
								frequence[2]++;
								break;
							case 'G' : 
								frequence[3]++;
								break;
						}
					}
					
					for (int k = 0; k < 4; k++ ) {
						sum[i][j][k] = frequence[k]; 	
					} 
				}
			}
			//step 9 all reduce last digit for AGCT again...
			System.out.println("Start all reduce!!!  " + myRank);
			for (int i = 0; i < numCluster; i++) {
				for (int j = 0 ; j < dnaLength; j++) {
					int [] xSum = new int[4], xSumNew = new int[4];
					xSum = sum[i][j];
					MPI.COMM_WORLD.Allreduce(xSum, 0, xSumNew, 0, xSum.length, MPI.INT, MPI.SUM);
					sum[i][j] = xSum;
				}
			}
			
			//for last time send final result
//			MPI.COMM_WORLD.Recv(DNA2clusterSlave, 0, numDNAStrandsSlave, MPI.INT,MPI.ANY_SOURCE, 100);
			MPI.COMM_WORLD.Send(resultCluster, 0, resultCluster.length, MPI.OBJECT, 0, 0);
			
		}

		//master process
		else {
			//step1 select init centroids randomly from dnaList
			ArrayList<Integer> randomPool = generateRandom(numCluster, dnaList.size());
			String [] centroids = new String[numCluster];
			int n = 0;
			for(int i : randomPool) {
				if(n < numCluster){
					centroids[n] = dnaList.get(i).toString();
					n++;
				} else {
					break;
				}
			}

			//step 2 send centriod to every slave
			for (int slaveRank = 1; slaveRank < size ; slaveRank++) {
				MPI.COMM_WORLD.Send(centroids, 0, numCluster, MPI.OBJECT, slaveRank, 99);
			}
			
			//all reduce...
			System.out.println("Start all reduce!!!  " + myRank);
			int dnaLength = dnaList.get(0).length();
			int [][][] sum = new int [numCluster][dnaLength][4];
			for (int i = 0; i < numCluster; i++) {
				for (int j = 0 ; j < dnaLength; j++) {
					int [] xSum = new int[4], xSumNew = new int[4];
					xSum = sum[i][j];
					MPI.COMM_WORLD.Allreduce(xSum, 0, xSumNew, 0, xSum.length, MPI.INT, MPI.SUM);
					sum[i][j] = xSum;
					//testing...
					System.out.println("Master xSum" + Arrays.toString(sum[i][j]) );
				}
			}
//			int [] xSum = new int[4], xSumNew = new int[4];
//			MPI.COMM_WORLD.Allreduce(xSum, 0, xSumNew, 0, xSum.length, MPI.INT, MPI.SUM);
//			System.out.println(Arrays.toString(xSumNew));
			
			//test...
//			for (int i = 0; i < numCluster; i++) {
//				for (int j = 0 ; j < dnaLength; j++) {
//					System.out.println(Arrays.toString(sum[i][j]));
//				}
//			}
			//step 3  calculate  centroids and send to slaves
			
			for(int cluster = 0; cluster < numCluster; cluster++) {
				StringBuilder newCentroid = new StringBuilder();
				
				char [] bases = {'A','C','T','G'};
				for(int pos = 0; pos < dnaLength; pos++){
					int index = -1;
					int [] temp = sum[cluster][pos];
					int max = Integer.MIN_VALUE;
					for(int i = 0; i < 4; i++){
						if(temp[i] > max) {
							max = temp[i];
							index = i;
						}
					}
					newCentroid.append(bases[index]);
				}
				centroids[cluster] = new String(newCentroid);
			}
			//testing....
			System.out.println("Master cal new centroids " + Arrays.toString(centroids));
			
			//send again.... really need a loop...
			for (int slaveRank = 1; slaveRank < size ; slaveRank++) {
				MPI.COMM_WORLD.Send(centroids, 0, numCluster, MPI.OBJECT, slaveRank, 1);
			}
			
			//get all reduce
			System.out.println("Start all reduce!!!  " + myRank);
			for (int i = 0; i < numCluster; i++) {
				for (int j = 0 ; j < dnaLength; j++) {
					int [] xSum = new int[4], xSumNew = new int[4];
					xSum = sum[i][j];
					MPI.COMM_WORLD.Allreduce(xSum, 0, xSumNew, 0, xSum.length, MPI.INT, MPI.SUM);
					sum[i][j] = xSum;
				}
			}
			
			//for last time...
			int [] clusters = new int[dnaList.size()];
			for(int slaveRank = 1; slaveRank < size; slaveRank++){
				int[] tempClusters = new int[dnaList.size()];
				MPI.COMM_WORLD.Recv(tempClusters, 0, tempClusters.length, MPI.INT,MPI.ANY_SOURCE, 0);
				for(int i = 0 ; i < tempClusters.length;i++){
					if(tempClusters[i]!=-1) clusters[i] = tempClusters[i];
				}
			}
			writeFile(dnaList, clusters, outFile);

		}

		
	}
	
	public static void main(String[] args) {
		//sequential version
		
		/*
		 * arg0 : numCluster
		 * arg1	: outFile
		 * arg2 : inFile
		 * 
		 */
		if(args.length != 3) {
			System.out.println("You should give following args");
			System.out.println("arg0 : numCluster | arg1: outFile | arg2 : inFile");
		}
		
		// int numCluster = Integer.parseInt(args[0]);
		// String outFile = args[1];
		// String inFile = args[2];

		int numCluster = 4;
		String outFile = "./output/out.csv";
		String inFile = "./input/dna.csv";


		ReadCSV reader = new ReadCSV(inFile, "dna");
		try {
			ArrayList<String> dnaList = reader.read();
			System.out.println(dnaList.size());
			//step1 select init centroids randomly from dnaList
			ArrayList<Integer> randomPool = generateRandom(numCluster, dnaList.size());
			String [] centroids = new String[numCluster];
			int n = 0;
			for(int i : randomPool) {
				if(n < numCluster){
					centroids[n] = dnaList.get(i).toString();
					n++;
				} else {
					break;
				}
			}
			//test
			System.out.println(Arrays.toString(centroids));
			
			//step2 calculate dif of each dna strand
			int [] resultCluster = new int[dnaList.size()];
			int [] resultDif = new int[dnaList.size()];
			for (int i = 0; i < dnaList.size(); i++) {
				int dif = Integer.MAX_VALUE;
				int cluster = -1;
				for (int j = 0; j < numCluster; j++) {
					//compare strand with centroid one by one
					int tempdif = calDif(dnaList.get(i),centroids[j]);
					if(tempdif < dif ){
						dif = tempdif;
						cluster = j;
					}else {
						continue;
					}
				}
				resultCluster[i] = cluster;
				resultDif[i] = dif;
			}

			System.out.println(Arrays.toString(resultDif));
			System.out.println(Arrays.toString(resultCluster));

			//step 3 recalculate centriod
			String[] tempCluster = new String[dnaList.size()];
			HashMap<Integer,ArrayList<String>> map = new HashMap<Integer,ArrayList<String>>();
			
			for (int i = 0; i < numCluster; i++) {
				map.put(i,new ArrayList<String>());
			}

			for (int i = 0; i < dnaList.size(); i++) {
				map.get(resultCluster[i]).add(dnaList.get(i));
			}//now we have arraylists of each cluster

			//do recalculate
			String[] newCentroids = new String[numCluster];
			for (int i = 0; i < numCluster; i++) {
				newCentroids[i] = reCal(map.get(resultCluster[i]));
			}//now we have new centroids
			
			//test
			System.out.println(Arrays.toString(newCentroids));

			//step 4 recalcualte cluster
			//can be while loop...
			int [] resultCluster2 = new int[dnaList.size()];
			int [] resultDif2 = new int[dnaList.size()];
			for (int i = 0; i < dnaList.size(); i++) {
				int dif = Integer.MAX_VALUE;
				int cluster = -1;
				for (int j = 0; j < numCluster; j++) {
					//compare strand with centroid one by one
					int tempdif = calDif(dnaList.get(i),newCentroids[j]);
					if(tempdif < dif ){
						dif = tempdif;
						cluster = j;
					}else {
						continue;
					}
				}
				resultCluster2[i] = cluster;
				resultDif2[i] = dif;
			}
			
			System.out.println(Arrays.toString(resultDif2));
			System.out.println(Arrays.toString(resultCluster2));
			writeFile(dnaList, resultCluster2, outFile);

			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/*
	 * Generate numCluster ints within range of 0 to listSize
	 * @return list
	 */
	public static ArrayList<Integer> generateRandom(int numCluster, int listSize){
		ArrayList randomPool = new ArrayList<Integer>();
		Random r = new Random();
		while(randomPool.size() < numCluster) {
			int random = r.nextInt(listSize);
			if(!randomPool.contains(random)){
				randomPool.add(random);
			}
		}
		
		return randomPool;
	}
	
	/*
	 * calculate dif
	 * @return dif between src strand and centroid strand
	 */
	public static int calDif (String src, String dest) {
		int dif = 0;
		char [] str1 = src.toCharArray();
		char []	str2 = dest.toCharArray();

		for(int i = 0; i < str1.length ; i++) {
			if (str1[i] != str2[i]) {
				dif++;
			}
		}

		return dif;
	}

	/*
	 * re-calculate centroids
	 * @return String of new centroid
	 */
	public static String reCal(ArrayList<String> strands) {
		StringBuilder newCentroid = new StringBuilder();
		int dnaLength = strands.get(0).length();
		for (int i = 0; i < dnaLength; i++) {
			int [] frequence = new int[4];
			for (String strand : strands) {
				char cur = strand.charAt(i);
				switch (cur) {
					case 'A' : 
						frequence[0]++;
						break;
					case 'C' :
						frequence[1]++;
						break;
					case 'T' : 
						frequence[2]++;
						break;
					case 'G' : 
						frequence[3]++;
						break;
				}
			}

			//compare ACTG frequence and decide which one is the most
			int max = Integer.MIN_VALUE;
			int index = -1;
			for(int j = 0; j < 4; j++) {
				if (frequence[j] > max) {
					max = frequence[j];
					index = j;
				}
			}
			char [] bases = {'A','C','T','G'};
			newCentroid.append(bases[index]);
		}


		return new String(newCentroid);
	} 
	
	/*
	 * Write results into csv files
	 */
	private static void writeFile(ArrayList<String> dnaList, int[] clusters,String outFile){
		try {
			PrintWriter writer = new PrintWriter(new File(outFile));
			System.out.println(outFile);
			for(int i = 0; i < dnaList.size(); i++) {
				writer.println(dnaList.get(i) + "," + clusters[i]);
				System.out.println(dnaList.get(i) + "," + clusters[i]);
				writer.flush();
			}
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
}