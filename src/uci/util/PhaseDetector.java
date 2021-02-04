package uci.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import uci.excutionToCallTree.FunctionNode;
import uci.excutionToCallTree.TreeGenerator;
import at.unisalzburg.dbresearch.apted.costmodel.StringUnitCostModel;
import at.unisalzburg.dbresearch.apted.distance.APTED;
import at.unisalzburg.dbresearch.apted.node.*;

public class PhaseDetector {
	
	static APTED rted = new APTED<StringUnitCostModel, StringNodeData>(new StringUnitCostModel());
	static int similarPair = 0;
	static int totalCompareTime = 0;
	static int groupId = 1;
	
	//Factors that can be changed.
	static int minSizeToCompare = 5;
	static double minNumDifference = 0.8;
	static double preSimilarThreshold = 0.5;
	static double similarThreshold = 0.5;
	static boolean checkRootName = false;
	
	static void compareWithOther (Map<Integer, List<FunctionNode>> layerMap, Set<FunctionNode> nodesOfRoot, FunctionNode node) {
		boolean tmpFlag = false;
		for(Entry<Integer, List<FunctionNode>> tmpEntry : layerMap.entrySet()) {
			List<FunctionNode> tmp = tmpEntry.getValue();
			for(FunctionNode a : tmp) {
				tmpFlag = false;
				if(node.name.equals("_findCustomDeser") || a.name.equals("_findCustomDeser")) tmpFlag = true;
				if(a.depth >= node.depth && !nodesOfRoot.contains(a)) {
					Node first = Node2AptedNodeAdapter.ConvertToAptedNode(node, node.endTime - node.startTime);
					Node second = Node2AptedNodeAdapter.ConvertToAptedNode(a, a.endTime - a.startTime);
					float minSize = (float)Math.min(first.getNodeCount(), second.getNodeCount());
					float maxSize = (float)Math.max(first.getNodeCount(), second.getNodeCount());

					if(minSize < minSizeToCompare ||
						minSize < minNumDifference * maxSize ||
						(!node.name.equals(a.name) && checkRootName)) continue;
					
					
					//To accelerate the comparing speed, we first need to test if the first 2 layers are pretty similar
					if(!node.name.equals(a.name) && !checkRootName) {
						Node preTestOne = Node2AptedNodeAdapter.ConvertToTrimmedAptedNode(node, 2, 0);
						Node preTestTwo = Node2AptedNodeAdapter.ConvertToTrimmedAptedNode(a, 2, 0);
						float preTestResult = rted.computeEditDistance(preTestOne, preTestTwo);
						if(preTestResult > preSimilarThreshold * Math.min(preTestOne.getNodeCount(), preTestTwo.getNodeCount())) {
							continue;
						}
					}
					
					totalCompareTime++;
					
					float result = rted.computeEditDistance(first, second);
					if(tmpFlag) {
						System.out.println(node.name);
						System.out.println(a.name);
						System.out.println(result + " "+ minSize + " ");
						tmpFlag = false;
					}

					if(result/minSize < similarThreshold) {
						similarPair++;
						// Use Union Find to group phases
						if(node.groupId == 0 && a.groupId == 0) {
							node.groupId = groupId;
							a.groupId = groupId;
							groupId++;
						} else if (node.groupId != 0 && a.groupId != 0){
							int tmpId = Math.min(node.groupId, a.groupId);
							node.groupId = tmpId;
							a.groupId = tmpId;
						} else {
							int tmpId = Math.max(node.groupId, a.groupId);
							node.groupId = tmpId;
							a.groupId = tmpId;
						}
					}
					
				}			
			}
		}	
	}
	
	public static void getSimilarity (Map<Integer, List<FunctionNode>> layerMap) {
		Set<String> pairedName = new HashSet<>();
		for(Entry<Integer, List<FunctionNode>> tmpEntry : layerMap.entrySet()) { 
			List<FunctionNode> tmp = tmpEntry.getValue();
			Set<FunctionNode> nodesOfRoot = new HashSet<>();
			for(FunctionNode a : tmp) {
				//if(pairedName.contains(a.name)) continue;
				nodesOfRoot.clear();
				addNodesOfRoot(nodesOfRoot, a);
				compareWithOther(layerMap, nodesOfRoot, a);
				pairedName.add(a.name);
			}
		}
		
		System.out.println("Similar Pair:" + similarPair);
		System.out.println("Total Compared Number" + totalCompareTime);
		System.out.println("Time: " + totalCompareTime);
		
	}

	static void addNodesOfRoot(Set<FunctionNode> nodesOfRoot, FunctionNode a) {
		nodesOfRoot.add(a);
		for(int i=0; i<a.children.size(); i++) {
			addNodesOfRoot(nodesOfRoot, a.children.get(i));
		}
	}

	public static void main(String[] args) throws IOException {
		TreeGenerator generator = new TreeGenerator();
		generator.generateTree("src/output.txt");
		getSimilarity(generator.threadMap.get(1));
		System.out.println(similarPair);
		System.out.println(totalCompareTime);
		System.out.println(groupId);
	}

}
