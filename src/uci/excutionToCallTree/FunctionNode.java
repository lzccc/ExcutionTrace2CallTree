package uci.excutionToCallTree;

import java.util.ArrayList;
import java.util.List;

public class FunctionNode {
	public String name;
	public String className;
	public int groupId = 0;
	public List<Integer> groupIds = new ArrayList<>();
	public List<FunctionNode> children = new ArrayList<>();
	public long startTime;
	public long endTime = -1;
	public int depth;
	public FunctionNode(String name, String className, long startTime, int depth) {
		this.name = name;
		this.className = className;
		this.startTime = startTime;
		this.depth = depth;
	}
}
