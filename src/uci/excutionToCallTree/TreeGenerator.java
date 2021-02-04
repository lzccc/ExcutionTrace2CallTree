package uci.excutionToCallTree;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import uci.util.PhaseDetector;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;


public class TreeGenerator {
	//The path of the trace file generated by Blinky profiler
	static String filePath = "src/output.txt";
	public Map<Integer, Map<Integer, List<FunctionNode>>> threadMap = new HashMap<>();
	Map<String, String> eventId2MethodIdMap = new HashMap<>();
	Map<String, Method> methodId2Method = new HashMap<>();
	List<FunctionNode> nodeInOneClass = new ArrayList<>();

	public void generateTree(String filePath)throws IOException {
		//BufferedReader read file by line
		FileInputStream inputStream = new FileInputStream(filePath);
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
		String str = null;
		while((str = bufferedReader.readLine()) != null)
		{
			if(str.length() == 0) continue;
			String[] strarr = str.split("\\s+");
			if(strarr[0].equals("$$method$$")) processMethod(strarr);
			if(strarr[0].equals("$enter$")) processEvent(strarr);
			if(strarr[0].equals("$return$")) processEvent(strarr);
			if(strarr[0].equals("$$$")) processExcution(strarr);
		}			
		changeDummyEndtime();
		//close
		inputStream.close();
		bufferedReader.close();
	}
	
	private void changeDummyEndtime() {
		for(Entry<Integer, Map<Integer, List<FunctionNode>>> entry : threadMap.entrySet()) {
			Map<Integer, List<FunctionNode>> layerMap = entry.getValue();
			for(Entry<Integer, List<FunctionNode>> tmpEntry : layerMap.entrySet()) {
				List<FunctionNode> tmp = tmpEntry.getValue();
				for(FunctionNode a : tmp) {
					if(a.name.startsWith("Dummy")) {
						a.endTime = a.children.get(0).endTime;
					}
				}
			}	
		}
			
	}

	void processMethod(String[] strarr) {
		String methodId = strarr[strarr.length-1].substring(3);
		String methodName = strarr[1].substring(0, strarr[1].indexOf("("));
		methodId2Method.put(methodId, new Method(methodName, strarr[2], methodId));
	}
	
	void processEvent(String[] strarr) {
		eventId2MethodIdMap.put(strarr[1], strarr[2]);	
	}
	
	void processExcution(String[] strarr) {
		if(strarr[1].equals("$enter$")) runtimeEnter(strarr);
		if(strarr[1].equals("$exit$")) runtimeExit(strarr);
	}
	
	boolean isInitMethod(FunctionNode currFunctionNode) {
		return currFunctionNode.name.startsWith("<init>") || currFunctionNode.name.startsWith("<clinit>");
	}
	
	void runtimeEnter(String[] strarr) {
		String eventId = strarr[2].replaceAll("\\D+","");
		int depth = Integer.parseInt(strarr[3].replaceAll("\\D+",""));
		long startTime = Long.parseLong(strarr[4].replaceAll("\\D+",""));
		int threadId = Integer.parseInt(strarr[5].replaceAll("\\D+",""));
		
		threadMap.putIfAbsent(threadId, new TreeMap<>());
		Map<Integer, List<FunctionNode>> layerMap = threadMap.get(threadId);
		 
		
		
		String methodId = eventId2MethodIdMap.get(eventId);
		Method currMethod = methodId2Method.get(methodId);

		FunctionNode currFunctionNode = new FunctionNode(currMethod.name, currMethod.className, startTime, depth);
		List<FunctionNode> parentLayerList = layerMap.get(depth-1);
		if(parentLayerList != null && parentLayerList.size() != 0) {
			FunctionNode lastNode = parentLayerList.get(parentLayerList.size()-1);
			if(lastNode.endTime == -1) {
				lastNode.children.add(currFunctionNode);
			} else {
				if(!isInitMethod(currFunctionNode)) createDummyNode(currFunctionNode, layerMap);
			}
		}
		layerMap.putIfAbsent(depth, new ArrayList<>());
		layerMap.get(depth).add(currFunctionNode);
	}
	
	void createDummyNode(FunctionNode currNode, Map<Integer, List<FunctionNode>> layerMap) {
		FunctionNode dummyNode = new FunctionNode("Dummy"+ currNode.name, currNode.className, currNode.startTime, currNode.depth-1);
		dummyNode.endTime = dummyNode.startTime;
		List<FunctionNode> parentLayerList = layerMap.get(currNode.depth-1);
		dummyNode.children.add(currNode);
		parentLayerList.add(dummyNode);
		List<FunctionNode> grandParentLayerList = layerMap.get(dummyNode.depth-1);
		if(grandParentLayerList != null && grandParentLayerList.size() != 0) {
			FunctionNode lastGrandNode = grandParentLayerList.get(grandParentLayerList.size()-1);
			if(lastGrandNode.endTime == -1) {
				lastGrandNode.children.add(dummyNode);
				return;
			} else {
				createDummyNode(dummyNode, layerMap);
			}
		}
	}
	
	void runtimeExit(String[] strarr) {
		int depth = Integer.parseInt(strarr[3].replaceAll("\\D+",""));
		long endTime = Long.parseLong(strarr[4].replaceAll("\\D+",""));
		int threadId = Integer.parseInt(strarr[5].replaceAll("\\D+",""));		
		threadMap.putIfAbsent(threadId, new TreeMap<>());
		Map<Integer, List<FunctionNode>> layerMap = threadMap.get(threadId);
		
		List<FunctionNode> currLayerList = layerMap.get(depth);
		FunctionNode currNode = null;
		FunctionNode lastNode = null;
		if(currLayerList != null && currLayerList.size() != 0) {
			currNode = currLayerList.get(currLayerList.size()-1);
			currNode.endTime = endTime;
			if(currLayerList.size() > 1) lastNode = currLayerList.get(currLayerList.size()-2);
		}
		/*To handle Exception, call this function to change all the children with endTime -1 
		to parent endTime.*/
		changeChildrenWithNoReturnEventEndtime(currNode);
		checkOverhang(currNode, layerMap.get(depth+1), lastNode);
	}
	
	void checkOverhang(FunctionNode currNode, List<FunctionNode> list, FunctionNode lastNode) {
		if(list == null) return;
		if(lastNode == null) {
			if(list.get(0).startTime < currNode.startTime) {
				currNode.startTime = list.get(0).startTime;
				for(FunctionNode a : list) {					
					currNode.children.add(a);
				}
			}
		} else {
			int overHangNum = 0;
			long lastEndTime = lastNode.endTime;
			long currStartTime = currNode.startTime;
			for(int i=list.size()-1; i>=0; i--) {
				if(list.get(i).startTime > lastEndTime && list.get(i).startTime < currStartTime) {
					overHangNum++;
				} else {
					break;
				}
			}
			List<FunctionNode> removeList = list.subList(list.size() - overHangNum, list.size());
			if(removeList.size() == 0 || !isInitMethod(removeList.get(0))) return;
			for(FunctionNode a : removeList) {
				currNode.children.add(a);
				if(lastNode.children.contains(a)) {
					lastNode.children.remove(a);
				}
			}
			currNode.startTime = removeList.get(0).startTime - 1;
		}
	}

	void changeChildrenWithNoReturnEventEndtime(FunctionNode currNode) {
		if(currNode == null) return;
		List<FunctionNode> childrenList = currNode.children;
		for(FunctionNode a : childrenList) {
			if(a.endTime == -1) {
				a.endTime = currNode.endTime;
				changeChildrenWithNoReturnEventEndtime(a);
			}
		}
	}
	public static void main(String[] args) throws IOException {
		TreeGenerator generator = new TreeGenerator();
		generator.generateTree(filePath);
		Object[] firstNodeList = generator.threadMap.values().toArray();
		List<FunctionNode> funList = new ArrayList<>();
		for(Entry<Integer, Map<Integer, List<FunctionNode>>> fn : generator.threadMap.entrySet()) {
			PhaseDetector.getSimilarity(fn.getValue());
			for (Entry<Integer, List<FunctionNode>> entry : fn.getValue().entrySet()) {
	            funList.add(entry.getValue().get(0));
	            break;
	        }
			
		}

		ObjectMapper mapper = new ObjectMapper();
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        try {
            mapper.writeValue(new File("objectJson.json"), funList);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
	}
}
