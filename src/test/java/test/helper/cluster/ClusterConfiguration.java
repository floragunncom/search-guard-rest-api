package test.helper.cluster;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum ClusterConfiguration {
	// TODO: 2 master nodes?
	DEFAULT(new NodeSettings(true, false, false), new NodeSettings(true, true,false), new NodeSettings(false, true, false)),
	SINGLENODE(new NodeSettings(true, true, false));
	
	private List<NodeSettings> nodeSettings;
	
	private ClusterConfiguration(NodeSettings ... settings) {
		nodeSettings.addAll(Arrays.asList(settings));
	}
	
	public  List<NodeSettings> getNodeSettings() {
		return Collections.unmodifiableList(nodeSettings);
	}
	
	public static class NodeSettings {
		public boolean masterNode;
		public boolean dataNode;
		public boolean tribeNode;
		
		public NodeSettings(boolean masterNode, boolean dataNode, boolean tribeNode) {
			super();
			this.masterNode = masterNode;
			this.dataNode = dataNode;
			this.tribeNode = tribeNode;
		}
		
	}
}
