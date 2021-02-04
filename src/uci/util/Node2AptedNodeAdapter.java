package uci.util;
import at.unisalzburg.dbresearch.apted.node.*;
import uci.excutionToCallTree.*;

public class Node2AptedNodeAdapter {
	
	public static Node<StringNodeData> ConvertToAptedNode(FunctionNode fn, long time) {
		Node<StringNodeData> node = new Node<StringNodeData>(new StringNodeData(fn.name));
		long duration = fn.endTime - fn.startTime;
		for(int i = 0; i < fn.children.size(); i++) {
			FunctionNode tmpChild = fn.children.get(i);
			long childDuration = tmpChild.endTime - tmpChild.startTime;
			if(childDuration > time * 0.05) {
				node.addChild(ConvertToAptedNode(tmpChild, time));
			}
		}
	        
		return node;
	}
	
	public static Node<StringNodeData> ConvertToTrimmedAptedNode(FunctionNode fn, int depth, int currDepth) {
		Node<StringNodeData> node = new Node<StringNodeData>(new StringNodeData(fn.name));
		if(depth == currDepth) return null; 
		for(int i = 0; i < fn.children.size(); i++) {
			Node tmp = ConvertToTrimmedAptedNode(fn.children.get(i), depth, currDepth+1);
			if(tmp != null )
				node.addChild(tmp);
		}       
		return node;
	}

}
